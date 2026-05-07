package com.example.homehub.other

import com.google.gson.annotations.SerializedName

/**
 * M-Pesa OAuth Authentication Response
 */
data class MpesaAuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: String
)

/**
 * STK Push Request Payload
 */
data class STKPushRequest(
    @SerializedName("BusinessShortCode") val businessShortCode: String,
    @SerializedName("Password") val password: String,
    @SerializedName("Timestamp") val timestamp: String,
    @SerializedName("TransactionType") val transactionType: String = "CustomerPayBillOnline",
    @SerializedName("Amount") val amount: Int,
    @SerializedName("PartyA") val partyA: String, // Phone number sending (2547XXXXXXXX)
    @SerializedName("PartyB") val partyB: String, // ShortCode
    @SerializedName("PhoneNumber") val phoneNumber: String, // Same as PartyA
    @SerializedName("CallBackURL") val callBackUrl: String,
    @SerializedName("AccountReference") val accountReference: String,
    @SerializedName("TransactionDesc") val transactionDesc: String
)

/**
 * STK Push Response (Gateway acknowledgement)
 */
data class STKPushResponse(
    @SerializedName("MerchantRequestID") val merchantRequestId: String,
    @SerializedName("CheckoutRequestID") val checkoutRequestId: String,
    @SerializedName("ResponseCode") val responseCode: String,
    @SerializedName("ResponseDescription") val responseDescription: String,
    @SerializedName("CustomerMessage") val customerMessage: String
)

/**
 * Error Model for Daraja API
 */
data class MpesaErrorResponse(
    @SerializedName("requestId") val requestId: String?,
    @SerializedName("errorCode") val errorCode: String?,
    @SerializedName("errorMessage") val errorMessage: String?
)
