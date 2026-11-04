package com.aira.assistant.operator

import com.aira.assistant.autonomous.ResultValidator
import java.io.File

data class OperatorVerificationResult(
    val passed: Boolean,
    val verifiedFiles: List<String>,
    val summary: String
)

/**
 * Verifies that every operator workflow produced a real, non-empty, valid output deliverable
 * (never marking a task `COMPLETED` merely because the last button was tapped).
 */
class ResultVerificationEngine(
    private val resultValidator: ResultValidator = ResultValidator()
) {
    fun verifyDeliverables(files: List<File>): OperatorVerificationResult {
        if (files.isEmpty()) {
            return OperatorVerificationResult(
                passed = false,
                verifiedFiles = emptyList(),
                summary = "Verification failed: no output deliverable files were produced."
            )
        }

        val verifiedPaths = mutableListOf<String>()
        for (file in files) {
            if (!file.exists() || file.length() == 0L) {
                return OperatorVerificationResult(
                    passed = false,
                    verifiedFiles = verifiedPaths,
                    summary = "Verification failed: expected output '${file.name}' does not exist or is empty."
                )
            }

            val ext = file.extension.lowercase()
            val valid = when (ext) {
                "mp4" -> resultValidator.validateVideoDeliverable(file, 30.0f, 5).passed
                "wav", "mp3" -> resultValidator.validateAudioDeliverable(file).passed
                "zip", "apk" -> resultValidator.validateZipOrApkArchive(file).passed
                else -> file.length() > 16L
            }

            if (!valid) {
                return OperatorVerificationResult(
                    passed = false,
                    verifiedFiles = verifiedPaths,
                    summary = "Verification failed: '${file.name}' failed container/stream integrity check."
                )
            }
            verifiedPaths.add(file.absolutePath)
        }

        return OperatorVerificationResult(
            passed = true,
            verifiedFiles = verifiedPaths,
            summary = "Verified ${verifiedPaths.size} deliverable(s): ${files.joinToString { "${it.name} (${it.length()} bytes)" }}"
        )
    }
}
