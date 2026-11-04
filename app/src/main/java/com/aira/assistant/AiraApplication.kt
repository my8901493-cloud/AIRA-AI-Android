package com.aira.assistant

import android.app.Application
import android.content.ComponentCallbacks2
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.data.local.AiraDatabase

class AiraApplication : Application() {

    val database: AiraDatabase by lazy { AiraDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        AiraLogger.i("AiraApplication", "AIRA AI Application initialized.")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            AiraLogger.w("AiraApplication", "System memory pressure detected (level=$level).")
        }
    }
}
