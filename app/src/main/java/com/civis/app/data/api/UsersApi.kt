package com.civis.app.data.api

import com.civis.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface UsersApi {
    @GET("users/profile")
    suspend fun getProfile(): Response<ApiResponse>

    @PUT("users/profile")
    suspend fun updateProfile(@Body req: UpdateProfileRequest): Response<ApiResponse>

    @PUT("users/privacy")
    suspend fun updatePrivacy(@Body req: PrivacySettings): Response<ApiResponse>

    @PUT("users/password")
    suspend fun changePassword(@Body req: ChangePasswordRequest): Response<ApiResponse>

    @PUT("users/fcm-token")
    suspend fun updateFcmToken(@Body req: FcmTokenRequest): Response<ApiResponse>

    @GET("users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): Response<ApiResponse>
}
