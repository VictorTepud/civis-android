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
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): Response<ApiResponse>

    @Multipart
    @POST("upload/media")
    suspend fun uploadMedia(@Part media: MultipartBody.Part): Response<ApiResponse>

    @Multipart
    @POST("upload/status")
    suspend fun uploadStatus(@Part status: MultipartBody.Part): Response<ApiResponse>

    @Multipart
    @POST("upload/attachment")
    suspend fun uploadAttachment(@Part attachment: MultipartBody.Part): Response<ApiResponse>
}
