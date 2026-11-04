package com.aira.assistant.mobile

data class ScamShieldAssessment(
    val isSuspiciousOrDangerous: Boolean,
    val riskLevel: String, // SAFE, WARNING, CRITICAL_BLOCK
    val matchedReasons: List<String>
)

/**
 * Real-Time Fraud, Phishing & Scam Link Shield (`ScamAndPhishingShield`).
 *
 * Inspects visible SMS, WhatsApp messages, and browser URLs before AIRA or the user clicks them,
 * flagging fake KYC suspension threats, lottery/reward scams, and untrusted `.apk` downloads.
 */
class ScamAndPhishingShield {

    private val criticalScamPatterns = listOf(
        "your bank account will be blocked" to "Fake Bank KYC Suspension Scam",
        "update pan card immediately via link" to "Fake PAN/KYC Phishing Link",
        "you have won a lottery" to "Lottery / Prize Fraud",
        "install this apk for kyc" to "Malicious Remote-Access APK Trap",
        "share your 6 digit otp" to "OTP Social Engineering Attempt",
        ".ngrok-free.app/login" to "Suspicious Tunnel Phishing URL",
        "bit.ly/free-cash" to "Shortened Financial Scam Link"
    )

    fun inspectTextOrUrl(textOrUrl: String): ScamShieldAssessment {
        val lower = textOrUrl.lowercase()
        val reasons = mutableListOf<String>()

        for ((pattern, reason) in criticalScamPatterns) {
            if (lower.contains(pattern)) {
                reasons.add(reason)
            }
        }

        if (lower.endsWith(".apk") && !lower.contains("github.com") && !lower.contains("fdroid.org")) {
            reasons.add("Unverified third-party APK download link")
        }

        return when {
            reasons.isEmpty() -> ScamShieldAssessment(
                isSuspiciousOrDangerous = false,
                riskLevel = "SAFE",
                matchedReasons = emptyList()
            )
            reasons.size >= 1 -> ScamShieldAssessment(
                isSuspiciousOrDangerous = true,
                riskLevel = "CRITICAL_BLOCK",
                matchedReasons = reasons
            )
            else -> ScamShieldAssessment(
                isSuspiciousOrDangerous = false,
                riskLevel = "SAFE",
                matchedReasons = emptyList()
            )
        }
    }
}
