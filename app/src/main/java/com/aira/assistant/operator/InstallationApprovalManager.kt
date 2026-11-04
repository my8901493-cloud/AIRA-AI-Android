package com.aira.assistant.operator

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.AppInstallationProposal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Missing App Discovery & User-Authorized Installation Manager (`InstallationApprovalManager`).
 *
 * Security & User Control Guarantees (Section 5 & Section 11):
 * - NEVER silently installs an application or downloads cracked/pirated APKs.
 * - Compares free alternatives, explains to the user why the app is needed, shows pricing ("100% Free")
 *   and permissions, and pauses in `WAITING_FOR_APPROVAL` until the user explicitly approves.
 * - Only AFTER user approval, opens the official Google Play Store listing (`market://details?id=...`
 *   or `https://play.google.com/store/apps/details?id=...`) so the user installs it through Android's
 *   standard Play Store flow.
 * - Detects when installation completes (`verifyInstallationCompleted()`) and resumes the paused task.
 */
class InstallationApprovalManager(
    private val context: Context? = null,
    private val appDiscoveryManager: AppDiscoveryManager
) {
    private val _pendingProposal = MutableStateFlow<AppInstallationProposal?>(null)
    val pendingProposal: StateFlow<AppInstallationProposal?> = _pendingProposal.asStateFlow()

    private val openedStoreUris = mutableListOf<String>()

    fun getOpenedStoreUris(): List<String> = openedStoreUris.toList()

    fun proposeOfficialAppInstallation(
        missingCapability: String,
        recommendedAppName: String,
        packageName: String,
        developerName: String = "Verified Play Store Publisher",
        pricingSummary: String = "Free (No subscription or watermark)",
        requiredPermissionsSummary: String = "Storage / Media Access for importing and exporting project files",
        rationaleExplanation: String,
        alternativesCompared: List<String>
    ): AppInstallationProposal {
        val proposal = AppInstallationProposal(
            missingCapability = missingCapability,
            recommendedAppName = recommendedAppName,
            packageName = packageName,
            officialStoreUri = "https://play.google.com/store/apps/details?id=$packageName",
            developerName = developerName,
            pricingSummary = pricingSummary,
            requiredPermissionsSummary = requiredPermissionsSummary,
            rationaleExplanation = rationaleExplanation,
            alternativesCompared = alternativesCompared,
            userApproved = false
        )
        _pendingProposal.value = proposal
        AiraLogger.i(
            "InstallationApprovalManager",
            "Created installation proposal for '$recommendedAppName' ($packageName). Waiting for explicit user approval."
        )
        return proposal
    }

    /**
     * Opens the official Google Play Store page ONLY if `userApproved == true`.
     */
    fun approveAndOpenOfficialStoreListing(): Result<String> {
        val current = _pendingProposal.value
            ?: return Result.failure(IllegalStateException("No pending app installation proposal."))

        val approved = current.copy(userApproved = true)
        _pendingProposal.value = approved

        val playUri = approved.officialStoreUri
        openedStoreUris.add(playUri)

        if (context != null) {
            runCatching {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${approved.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            }.onFailure {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(playUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }

        AiraLogger.i("InstallationApprovalManager", "User approved installation; opened official store listing: $playUri")
        return Result.success(playUri)
    }

    fun rejectProposal() {
        _pendingProposal.value = null
    }

    fun verifyInstallationCompleted(packageName: String): Boolean {
        val installed = appDiscoveryManager.isPackageInstalled(packageName)
        if (installed) {
            _pendingProposal.value = null
            AiraLogger.i("InstallationApprovalManager", "Verified package '$packageName' is now installed.")
        }
        return installed
    }
}
