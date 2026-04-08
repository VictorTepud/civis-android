package com.civis.app.data.api

import com.civis.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface PollsApi {
    @POST("polls")
    suspend fun createPoll(@Body req: CreatePollRequest): Response<ApiResponse>

    @POST("polls/{pollId}/vote")
    suspend fun votePoll(@Path("pollId") pollId: String, @Body req: VotePollRequest): Response<ApiResponse>

    @GET("polls/{pollId}")
    suspend fun getPoll(@Path("pollId") pollId: String): Response<ApiResponse>
}
