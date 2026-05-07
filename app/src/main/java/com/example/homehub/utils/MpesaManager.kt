package com.example.homehub.utils

import android.util.Base64
import android.util.Log
import com.example.homehub.other.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

/**
 * MpesaManager handles STK Push payment logic using Safaricom's Daraja API.
 */
object MpesaManager {
    private const val TAG = "MpesaManager"

    // Sandbox Credentials
    private const val BASE_URL = "https://sandbox.safaricom.co.ke/"
    private const val CONSUMER_KEY = "zQ2STtENqcQKxWw1ProX7KkB1jNLKtg8ffoXfZIHY1wV5aMC"
    private const val CONSUMER_SECRET = "nUpcLHHrLPOwutfVECeOr0kMUnjU0mO0GYpbX02eG4mHn9SEsb5z4nQ2AppLmHPL"
    private const val BUSINESS_SHORT_CODE = "174379"
    private const val PASSKEY = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919"
    private const val CALLBACK_URL = "https://developer.safaricom.co.ke/dashboard/myapps"

    private lateinit var api: MpesaApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(MpesaApi::class.java)
    }

    /**
     * Initiate an STK Push payment request
     * @param phone The recipient's phone number (e.g., 0712345678 or 254712345678)
     * @param amount The transaction amount (KSh)
     * @param accountRef A reference for the account (title of booking/property)
     * @param callback Result listener
     */
    fun initiatePayment(
        phone: String,
        amount: Int,
        accountRef: String,
        callback: (Boolean, String) -> Unit
    ) {
        val formattedPhone = formatPhoneNumber(phone)
        if (formattedPhone == null) {
            callback(false, "Invalid phone number format")
            return
        }

        generateAccessToken { token ->
            if (token == null) {
                callback(false, "Authentication with Safaricom failed")
                return@generateAccessToken
            }

            val timestamp = getCurrentTimestamp()
            val password = generatePassword(BUSINESS_SHORT_CODE, PASSKEY, timestamp)

            val request = STKPushRequest(
                businessShortCode = BUSINESS_SHORT_CODE,
                password = password,
                timestamp = timestamp,
                amount = amount,
                partyA = formattedPhone,
                partyB = BUSINESS_SHORT_CODE,
                phoneNumber = formattedPhone,
                callBackUrl = CALLBACK_URL,
                accountReference = accountRef,
                transactionDesc = "HomeHub Payment: $accountRef"
            )

            api.sendStkPush("Bearer $token", request).enqueue(object : Callback<STKPushResponse> {
                override fun onResponse(call: Call<STKPushResponse>, response: Response<STKPushResponse>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        Log.d(TAG, "STK Push Initiated: ${body?.checkoutRequestId}")
                        callback(true, "Please enter your M-Pesa PIN on your phone")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "STK Push Failed: $errorBody")
                        callback(false, "M-Pesa request failed. Please try again.")
                    }
                }

                override fun onFailure(call: Call<STKPushResponse>, t: Throwable) {
                    Log.e(TAG, "Network Error: ${t.message}")
                    callback(false, "Connection error. Check your internet.")
                }
            })
        }
    }

    private fun generateAccessToken(callback: (String?) -> Unit) {
        val auth = Base64.encodeToString("$CONSUMER_KEY:$CONSUMER_SECRET".toByteArray(), Base64.NO_WRAP)
        api.generateAccessToken("Basic $auth").enqueue(object : Callback<MpesaAuthResponse> {
            override fun onResponse(call: Call<MpesaAuthResponse>, response: Response<MpesaAuthResponse>) {
                callback(response.body()?.accessToken)
            }
            override fun onFailure(call: Call<MpesaAuthResponse>, t: Throwable) {
                Log.e(TAG, "Auth Failure: ${t.message}")
                callback(null)
            }
        })
    }

    private fun generatePassword(shortCode: String, passKey: String, timestamp: String): String {
        return Base64.encodeToString("$shortCode$passKey$timestamp".toByteArray(), Base64.NO_WRAP)
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
    }

    private fun formatPhoneNumber(phone: String?): String? {
        if (phone == null) return null
        var p = phone.replace(" ", "").replace("+", "")
        if (p.startsWith("0")) p = "254" + p.substring(1)
        if (p.startsWith("7") || p.startsWith("1")) p = "254" + p
        return if (p.length == 12 && p.startsWith("254")) p else null
    }
}
