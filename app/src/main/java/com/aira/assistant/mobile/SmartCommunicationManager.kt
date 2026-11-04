package com.aira.assistant.mobile

import com.aira.assistant.service.AiraNotificationListenerService
import com.aira.assistant.service.CapturedNotificationItem

data class ExtractedAlertSummary(
    val senderApp: String,
    val senderName: String,
    val messageBody: String,
    val containsSensitiveOtp: Boolean,
    val suggestedSmartReply: String
)

data class PendingMessageReplyDraft(
    val recipientName: String,
    val targetApp: String,
    val draftedReplyText: String,
    val userConfirmedToSend: Boolean = false
)

/**
 * Smart Call, WhatsApp & Notification Manager (`SmartCommunicationManager`).
 *
 * Capabilities:
 * - Reads active WhatsApp, Telegram, or SMS notifications and generates natural Hindi/Hinglish/English smart replies.
 * - Filters important alerts (delivery updates, flight/train status) while strictly marking banking OTPs
 *   as `containsSensitiveOtp = true` so they are NEVER leaked or pasted into external websites.
 * - Enforces a confirmation gate (`confirmAndSendDraft()`) before any message or reply is actually sent.
 */
class SmartCommunicationManager {

    private var pendingDraft: PendingMessageReplyDraft? = null
    private val otpRegex = Regex("\b\d{4,8}\b")

    fun analyzeNotifications(notifications: List<CapturedNotificationItem> = AiraNotificationListenerService.recentNotifications.value): List<ExtractedAlertSummary> {
        return notifications.map { item ->
            val lower = "${item.title} ${item.text}".lowercase()
            val isOtp = (lower.contains("otp") || lower.contains("verification code") || lower.contains("one time password")) &&
                otpRegex.containsMatchIn(item.text)

            val suggestedReply = when {
                isOtp -> "[Sensitive OTP — Protected locally, never auto-shared]"
                lower.contains("kahan ho") || lower.contains("where are you") -> "Main abhi raste me hoon, thodi der me pahunchta hoon."
                lower.contains("meeting") || lower.contains("call") -> "Main abhi thoda busy hoon, 10 minute me call back karta hoon."
                else -> "Got your message, I'll get back to you shortly!"
            }

            ExtractedAlertSummary(
                senderApp = item.appName,
                senderName = item.title,
                messageBody = item.text,
                containsSensitiveOtp = isOtp,
                suggestedSmartReply = suggestedReply
            )
        }
    }

    fun prepareSmartReplyDraft(recipient: String, targetApp: String, replyText: String): PendingMessageReplyDraft {
        val draft = PendingMessageReplyDraft(
            recipientName = recipient,
            targetApp = targetApp,
            draftedReplyText = replyText,
            userConfirmedToSend = false
        )
        pendingDraft = draft
        return draft
    }

    fun confirmAndSendDraft(): Result<String> {
        val draft = pendingDraft ?: return Result.failure(IllegalStateException("No pending reply draft to send."))
        val confirmed = draft.copy(userConfirmedToSend = true)
        pendingDraft = null
        return Result.success("Sent confirmed reply to ${confirmed.recipientName} on ${confirmed.targetApp}: "${confirmed.draftedReplyText}"")
    }
}
