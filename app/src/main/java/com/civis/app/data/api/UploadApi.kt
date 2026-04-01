package com.civis.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody

/** Respuesta directa del servidor: { url: "/uploads/..." } */
data class UploadResponse(
    val url: String? = null,
    val error: String? = null
)

interface UploadApi {
    @Multipart
    @POST("upload/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): Response<UploadResponse>

    @Multipart
    @POST("upload/media")
    suspend fun uploadMedia(@Part media: MultipartBody.Part): Response<UploadResponse>

    @Multipart
    @POST("upload/status")
    suspend fun uploadStatus(@Part status: MultipartBody.Part): Response<UploadResponse>

    @Multipart
    @POST("upload/attachment")
    suspend fun uploadAttachment(@Part attachment: MultipartBody.Part): Response<UploadResponse>
}
