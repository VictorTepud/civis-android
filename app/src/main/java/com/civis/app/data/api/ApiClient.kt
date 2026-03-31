package com.civis.app.data.api

import com.civis.app.utils.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "http://10.0.2.2:3000/api/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authInterceptor = Interceptor { chain ->
        val token = TokenManager.getInstance().getToken()
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrEmpty()) {
                addHeader("Authorization", "Bearer $token")
            }
            addHeader("Content-Type", "application/json")
        }.build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val usersApi: UsersApi = retrofit.create(UsersApi::class.java)
    val messagesApi: MessagesApi = retrofit.create(MessagesApi::class.java)
    val contactsApi: ContactsApi = retrofit.create(ContactsApi::class.java)
    val groupsApi: GroupsApi = retrofit.create(GroupsApi::class.java)
    val statusApi: StatusApi = retrofit.create(StatusApi::class.java)
    val communitiesApi: CommunitiesApi = retrofit.create(CommunitiesApi::class.java)
    val callsApi: CallsApi = retrofit.create(CallsApi::class.java)
    val uploadApi: UploadApi = retrofit.create(UploadApi::class.java)
}
