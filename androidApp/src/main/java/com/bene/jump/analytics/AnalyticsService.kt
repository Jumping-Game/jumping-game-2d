package com.bene.jump.analytics

import android.util.Log

interface AnalyticsService {
    fun track(
        event: String,
        params: Map<String, Any?> = emptyMap(),
    )

    fun reportCrash(throwable: Throwable)
}

class LogAnalyticsService : AnalyticsService {
    override fun track(
        event: String,
        params: Map<String, Any?>,
    ) {
        Log.d("Analytics", "event=$event params=$params")
    }

    override fun reportCrash(throwable: Throwable) {
        Log.e("Analytics", "crash", throwable)
    }
}

object AnalyticsRegistry {
    @Volatile
    var service: AnalyticsService = LogAnalyticsService()
}
