package com.civis.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.civis.app.data.model.User

class TokenManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("civis_prefs", Context.MODE_PRIVATE)
    private val gson = appGson

    companion object {
        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(): TokenManager {
            return instance ?: throw IllegalStateException("TokenManager not initialized. Call init() first.")
        }

        fun init(context: Context) {
            if (instance == null) {
                instance = TokenManager(context.applicationContext)
            }
        }
    }

    fun saveToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }

    fun clearToken() {
        prefs.edit().remove("auth_token").apply()
    }

    fun saveUser(user: User) {
        val json = gson.toJson(user)
        prefs.edit().putString("user_data", json).apply()
    }

    fun getUser(): User? {
        val json = prefs.getString("user_data", null) ?: return null
        return try {
            gson.fromJson(json, User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrEmpty()
    }
}
