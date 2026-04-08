package com.civis.app.data.api

import com.civis.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface MessagesApi {
    @GET("messages/conversations")
    suspend fun getConversations(): Response<ApiResponse>

    @POST("messages/send")
    suspend fun sendMessage(@Body req: SendMessageRequest): Response<ApiResponse>

    @POST("messages/conversations/{conversationId}/messages")
    suspend fun sendMessageToConversation(
        @Path("conversationId") convId: String,
        @Body req: SendMessageRequest
    ): Response<ApiResponse>

    @GET("messages/{conversationId}")
    suspend fun getMessages(
        @Path("conversationId") convId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ApiResponse>

    @PUT("messages/{messageId}/read")
    suspend fun markRead(@Path("messageId") msgId: String): Response<ApiResponse>

    @POST("messages/{messageId}/reply")
    suspend fun replyMessage(
        @Path("messageId") msgId: String,
        @Body req: ReplyRequest
    ): Response<ApiResponse>

    @POST("messages/{messageId}/forward")
    suspend fun forwardMessage(
        @Path("messageId") msgId: String,
        @Body req: ForwardRequest
    ): Response<ApiResponse>

    @DELETE("messages/{messageId}")
    suspend fun deleteMessage(@Path("messageId") msgId: String): Response<ApiResponse>
}
