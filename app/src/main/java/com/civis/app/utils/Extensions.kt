package com.civis.app.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import com.bumptech.glide.request.RequestOptions
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.*

/** Gson configurado para convertir camelCase (Kotlin) <-> snake_case (JSON del servidor) */
val appGson: Gson by lazy {
    GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
}

fun String.formatTime(): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(this) ?: return this
        val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        outputFormat.format(date)
    } catch (e: Exception) {
        this
    }
}

fun String.formatDate(): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(this) ?: return this
        val now = Calendar.getInstance()
        val messageDate = Calendar.getInstance().apply { time = date }

        return when {
            isSameDay(now, messageDate) -> {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            }
            isYesterday(messageDate) -> "Ayer"
            isSameWeek(now, messageDate) -> {
                SimpleDateFormat("EEEE", Locale("es")).format(date)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es")) else it.toString() }
            }
            else -> {
                SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
            }
        }
    } catch (e: Exception) {
        this
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(cal: Calendar): Boolean {
    val yesterday = Calendar.getInstance()
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return isSameDay(yesterday, cal)
}

private fun isSameWeek(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR)
}

fun String.toGlideUrl(): String {
    return if (this.startsWith("http://") || this.startsWith("https://")) {
        this
    } else {
        "${com.civis.app.config.ServerConfig.BASE_URL}$this"
    }
}

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun View.visible() {
    this.visibility = View.VISIBLE
}

fun View.gone() {
    this.visibility = View.GONE
}

fun View.invisible() {
    this.visibility = View.INVISIBLE
}
