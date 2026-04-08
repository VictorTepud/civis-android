package com.civis.app.data.api

import com.civis.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface StatusApi {
    @POST("status")
    suspend fun createStatus(@Body req: CreateStatusRequest): Response<ApiResponse>

    @GET("status")
    suspend fun getStatuses(): Response<ApiResponse>

    @GET("status/my")
    suspend fun getMyStatuses(): Response<ApiResponse>

    @GET("status/user/{userId}")
    suspend fun getUserStatuses(@Path("userId") userId: String): Response<ApiResponse>

    @POST("status/{statusId}/view")
    suspend fun viewStatus(@Path("statusId") statusId: String): Response<ApiResponse>

    @POST("status/{statusId}/reply")
    suspend fun replyStatus(
        @Path("statusId") statusId: String,
        @Body req: ReplyStatusRequest
    ): Response<ApiResponse>

    @DELETE("status/{statusId}")
    suspend fun deleteStatus(@Path("statusId") statusId: String): Response<ApiResponse>
}
