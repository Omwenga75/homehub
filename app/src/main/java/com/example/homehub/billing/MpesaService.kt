package com.example.homehub.billing

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

object MpesaService {
    private const val TAG = "MpesaService"
    private var api: MpesaApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(MpesaConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        api = retrofit.create(MpesaApi::class.java)
    }

    suspend fun getAccessToken(): String? {
        val keys = "${MpesaConfig.CONSUMER_KEY}:${MpesaConfig.CONSUMER_SECRET}"
        val auth = "Basic " + Base64.encodeToString(keys.toByteArray(), Base64.NO_WRAP)
        
        return try {
            val response = api.getAccessToken(auth)
            response.accessToken
        } catch (e: Exception) {
            Log.e(TAG, "Auth Error: ${e.message}")
            null
        }
    }

    suspend fun sendSTKPushAsync(
        phoneNumber: String,
        amount: Int,
        accountReference: String
    ): STKPushResponse? {
        val token = getAccessToken() ?: return STKPushResponse(
            merchantRequestID = "",
            checkoutRequestID = "",
            responseCode = "401",
            responseDescription = "Failed",
            customerMessage = "Safaricom Error: Failed to authenticate with Daraja Sandbox"
        )
        
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val password = Base64.encodeToString(
            "${MpesaConfig.BUSINESS_SHORT_CODE}${MpesaConfig.PASSKEY}$timestamp".toByteArray(),
            Base64.NO_WRAP
        )

        val formattedPhone = formatPhoneNumber(phoneNumber) ?: return STKPushResponse(
            merchantRequestID = "",
            checkoutRequestID = "",
            responseCode = "400",
            responseDescription = "Failed",
            customerMessage = "Invalid phone number format for M-Pesa"
        )

        val request = STKPushRequest(
            businessShortCode = MpesaConfig.BUSINESS_SHORT_CODE,
            password = password,
            timestamp = timestamp,
            amount = amount,
            partyA = formattedPhone,
            partyB = MpesaConfig.BUSINESS_SHORT_CODE,
            phoneNumber = formattedPhone,
            callBackURL = MpesaConfig.CALLBACK_URL,
            accountReference = accountReference,
            transactionDesc = "HomeHub Payment"
        )

        return try {
            val response = api.sendSTKPush("Bearer $token", request)
            if (response.isSuccessful) {
                response.body()
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Log.e(TAG, "STK Push HTTP ${response.code()} Error: $errorBody")
                
                // Try to extract readable error from Safaricom's JSON
                val errorMsg = try {
                    val json = org.json.JSONObject(errorBody)
                    json.optString("errorMessage", "HTTP ${response.code()}: $errorBody")
                } catch (e: Exception) {
                    "HTTP ${response.code()}: $errorBody"
                }
                
                STKPushResponse(
                    merchantRequestID = "",
                    checkoutRequestID = "",
                    responseCode = response.code().toString(),
                    responseDescription = "Failed",
                    customerMessage = "Safaricom Error: $errorMsg"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "STK Push Error: ${e.message}")
            STKPushResponse(
                merchantRequestID = "",
                checkoutRequestID = "",
                responseCode = "500",
                responseDescription = "Failed",
                customerMessage = "Network Error: ${e.message}"
            )
        }
    }

    private fun formatPhoneNumber(phone: String?): String? {
        if (phone == null) return null
        var p = phone.replace(" ", "").replace("+", "")
        if (p.startsWith("0")) p = "254" + p.substring(1)
        if (p.startsWith("7") || p.startsWith("1")) p = "254" + p
        return if (p.length == 12 && p.startsWith("254")) p else null
    }

    /**
     * Queries the STK push status. Returns:
     * - STKPushQueryResult with actual data on success
     * - STKPushQueryResult with PENDING resultCode if Safaricom says "being processed"
     * - null only on genuine network / auth errors
     */
    suspend fun querySTKStatusAsync(checkoutId: String): STKPushQueryResult? {
        val token = getAccessToken() ?: return null
        
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val password = Base64.encodeToString(
            "${MpesaConfig.BUSINESS_SHORT_CODE}${MpesaConfig.PASSKEY}$timestamp".toByteArray(),
            Base64.NO_WRAP
        )

        val request = STKPushQueryRequest(
            businessShortCode = MpesaConfig.BUSINESS_SHORT_CODE,
            password = password,
            timestamp = timestamp,
            checkoutRequestID = checkoutId
        )

        return try {
            val response = api.querySTKStatus("Bearer $token", request)
            
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "Query OK: code=${body?.resultCode} desc=${body?.resultDesc}")
                body
            } else {
                // Safaricom returns HTTP 500 when transaction is still being processed
                val errorBody = response.errorBody()?.string() ?: ""
                val httpCode = response.code()
                Log.d(TAG, "Query HTTP $httpCode: $errorBody")
                
                if (errorBody.contains("being processed", ignoreCase = true) ||
                    errorBody.contains("500.001.1001", ignoreCase = true)) {
                    // Transaction still in progress — return a "pending" marker
                    Log.d(TAG, "Transaction still processing (HTTP $httpCode)")
                    null // null = still pending, polling will continue
                } else {
                    // Unexpected HTTP error — log it but don't treat as terminal failure
                    Log.w(TAG, "Query returned HTTP $httpCode — will retry: $errorBody")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query network error: ${e.message}")
            null
        }
    }
}
