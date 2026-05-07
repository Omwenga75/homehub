package com.example.homehub.other

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface MpesaApi {

    /**
     * Generate OAuth access token
     * @param authorization Basic Base64(ConsumerKey:ConsumerSecret)
     */
    @GET("oauth/v1/generate")
    fun generateAccessToken(
        @Header("Authorization") authorization: String,
        @Query("grant_type") grantType: String = "client_credentials"
    ): Call<MpesaAuthResponse>

    /**
     * Initiate STK Push (LIPA NA MPESA ONLINE)
     * @param authorization Bearer [access_token]
     */
    @POST("mpesa/stkpush/v1/processrequest")
    fun sendStkPush(
        @Header("Authorization") authorization: String,
        @Body request: STKPushRequest
    ): Call<STKPushResponse>
}
