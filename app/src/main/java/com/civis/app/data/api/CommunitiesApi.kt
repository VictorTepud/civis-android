package com.civis.app.data.api

import com.civis.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface CommunitiesApi {
    @POST("communities")
    suspend fun createCommunity(@Body req: CreateCommunityRequest): Response<ApiResponse>

    @GET("communities")
    suspend fun getCommunities(): Response<ApiResponse>

    @GET("communities/discover")
    suspend fun discoverCommunities(): Response<ApiResponse>

    @GET("communities/{communityId}")
    suspend fun getCommunity(@Path("communityId") communityId: String): Response<ApiResponse>

    @POST("communities/{communityId}/join")
    suspend fun joinCommunity(@Path("communityId") communityId: String): Response<ApiResponse>

    @POST("communities/{communityId}/leave")
    suspend fun leaveCommunity(@Path("communityId") communityId: String): Response<ApiResponse>

    @GET("communities/{communityId}/channels")
    suspend fun getChannels(@Path("communityId") communityId: String): Response<ApiResponse>

    @POST("communities/{communityId}/channels")
    suspend fun createChannel(
        @Path("communityId") communityId: String,
        @Body req: CreateChannelRequest
    ): Response<ApiResponse>

    @POST("communities/{communityId}/channels/{channelId}/messages")
    suspend fun sendChannelMessage(
        @Path("communityId") cid: String,
        @Path("channelId") chId: String,
        @Body req: SendMessageRequest
    ): Response<ApiResponse>

    @GET("communities/{communityId}/channels/{channelId}/messages")
    suspend fun getChannelMessages(
        @Path("communityId") cid: String,
        @Path("channelId") chId: String
    ): Response<ApiResponse>
}
