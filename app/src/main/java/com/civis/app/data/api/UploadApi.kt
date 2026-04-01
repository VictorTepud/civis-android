package com.civis.app.data.api

import com.civis.app.data.model.ApiResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface UploadApi {
    @Multipart
    @POST("upload/")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part, @Query("type") type: String = "avatar"): Response<ApiResponse>

    @Multipart
    @POST("upload/")
    suspend fun uploadMedia(@Part file: MultipartBody.Part, @Query("type") type: String = "media"): Response<ApiResponse>

    @Multipart
    @POST("upload/")
    suspend fun uploadStatus(@Part file: MultipartBody.Part, @Query("type") type: String = "status"): Response<ApiResponse>

    @Multipart
    @POST("upload/")
    suspend fun uploadAttachment(@Part file: MultipartBody.Part, @Query("type") type: String = "media"): Response<ApiResponse>
}
