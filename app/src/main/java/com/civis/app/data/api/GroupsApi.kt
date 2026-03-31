package com.civis.app.data.api

import com.civis.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface GroupsApi {
    @POST("groups")
    suspend fun createGroup(@Body req: CreateGroupRequest): Response<ApiResponse>

    @GET("groups")
    suspend fun getGroups(): Response<ApiResponse>

    @GET("groups/{groupId}")
    suspend fun getGroup(@Path("groupId") groupId: String): Response<ApiResponse>

    @PUT("groups/{groupId}")
    suspend fun updateGroup(
        @Path("groupId") groupId: String,
        @Body req: UpdateGroupRequest
    ): Response<ApiResponse>

    @POST("groups/{groupId}/members")
    suspend fun addMember(
        @Path("groupId") groupId: String,
        @Body req: AddMemberRequest
    ): Response<ApiResponse>

    @DELETE("groups/{groupId}/members/{userId}")
    suspend fun removeMember(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String
    ): Response<ApiResponse>

    @POST("groups/{groupId}/messages")
    suspend fun sendGroupMessage(
        @Path("groupId") groupId: String,
        @Body req: SendMessageRequest
    ): Response<ApiResponse>

    @GET("groups/{groupId}/messages")
    suspend fun getGroupMessages(
        @Path("groupId") groupId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ApiResponse>
}
