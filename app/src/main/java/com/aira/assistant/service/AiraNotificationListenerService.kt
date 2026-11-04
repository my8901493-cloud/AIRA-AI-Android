package com.aira.assistant.service

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotificationItem(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAtMs: Long
)

/**
 * Permission-gated Notification Reader (`AiraNotificationListenerService`).
 * Reads incoming notifications ONLY when the user has explicitly granted Notification Access
 * in Android system settings.
 */
class AiraNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _recentNotifications = MutableStateFlow<List<CapturedNotificationItem>>(emptyList())
        val recentNotifications: StateFlow<List<CapturedNotificationItem>> = _recentNotifications.asStateFlow()

        fun isNotificationAccessGranted(context: Context?): Boolean {
            if (context == null) return false
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            return enabledListeners.contains(context.packageName)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        val pkg = sbn.packageName
        if (pkg == packageName) return // Ignore AIRA's own foreground notifications

        val appLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(pkg)

        val item = CapturedNotificationItem(
            packageName = pkg,
            appName = appLabel,
            title = title,
            text = text,
            postedAtMs = sbn.postTime
        )
        _recentNotifications.value = (listOf(item) + _recentNotifications.value).take(25)
    }
}
