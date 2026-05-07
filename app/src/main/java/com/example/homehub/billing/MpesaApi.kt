package com.example.homehub.billing

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface MpesaApi {
    @GET("oauth/v1/generate?grant_type=client_credentials")
    suspend fun getAccessToken(@Header("Authorization") authorization: String): MpesaAccessToken

    @POST("mpesa/stkpushquery/v1/query")
    suspend fun querySTKStatus(
        @Header("Authorization") authorization: String,
        @Body request: STKPushQueryRequest
    ): Response<STKPushQueryResult>

    @POST("mpesa/stkpush/v1/processrequest")
    suspend fun sendSTKPush(
        @Header("Authorization") authorization: String,
        @Body request: STKPushRequest
    ): Response<STKPushResponse>
}
