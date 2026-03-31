package com.civis.app.data.api

import com.civis.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface CallsApi {
    @POST("calls/initiate")
    suspend fun initiateCall(@Body req: InitiateCallRequest): Response<ApiResponse>

    @POST("calls/{callId}/answer")
    suspend fun answerCall(@Path("callId") callId: String): Response<ApiResponse>

    @POST("calls/{callId}/reject")
    suspend fun rejectCall(@Path("callId") callId: String): Response<ApiResponse>

    @POST("calls/{callId}/end")
    suspend fun endCall(@Path("callId") callId: String): Response<ApiResponse>

    @POST("calls/{callId}/signal")
    suspend fun sendSignal(
        @Path("callId") callId: String,
        @Body req: SignalRequest
    ): Response<ApiResponse>

    @GET("calls/history")
    suspend fun getCallHistory(): Response<ApiResponse>
}
