package com.civis.app.data.api

import com.civis.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): Response<AuthResponse>

    @GET("auth/verify-token")
    suspend fun verifyToken(): Response<ApiResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<ApiResponse>
}
