package com.civis.app.data.api

import com.civis.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ContactsApi {
    @POST("contacts/add")
    suspend fun addContact(@Body req: AddContactRequest): Response<ApiResponse>

    @DELETE("contacts/remove/{contactId}")
    suspend fun removeContact(@Path("contactId") contactId: String): Response<ApiResponse>

    @PUT("contacts/{contactId}/block")
    suspend fun toggleBlock(@Path("contactId") contactId: String): Response<ApiResponse>

    @PUT("contacts/{contactId}/mute")
    suspend fun toggleMute(@Path("contactId") contactId: String): Response<ApiResponse>

    @PUT("contacts/{contactId}/nickname")
    suspend fun setNickname(
        @Path("contactId") contactId: String,
        @Body req: NicknameRequest
    ): Response<ApiResponse>

    @GET("contacts")
    suspend fun getContacts(): Response<ApiResponse>
}
