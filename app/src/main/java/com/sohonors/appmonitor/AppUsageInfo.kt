package com.sohonors.appmonitor

import android.graphics.drawable.Drawable

/**
 * Data model untuk satu aplikasi yang ditampilkan di daftar.
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val totalTimeInForegroundMs: Long, // total waktu aktif di layar depan (indikasi seberapa sering dipakai)
    val lastTimeUsedMs: Long,          // kapan terakhir dipakai
    val isSystemApp: Boolean
)
