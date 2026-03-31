package com.civis.app.data.api

import com.civis.app.data.model.ApiResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadApi {
    @Multipart
    @POST("upload/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<ApiResponse>

    @Multipart
    @POST("upload/media")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): Response<ApiResponse>

    @Multipart
    @POST("upload/status")
    suspend fun uploadStatus(@Part file: MultipartBody.Part): Response<ApiResponse>

    @Multipart
    @POST("upload/attachment")
    suspend fun uploadAttachment(@Part file: MultipartBody.Part): Response<ApiResponse>
}
