package com.aira.assistant.autonomous

import com.aira.assistant.domain.model.AutonomousToolMetadata
import com.aira.assistant.domain.model.SelectedToolDecision

/**
 * Compares candidate tools for each required capability and selects the optimal verified tool
 * using a weighted multi-criteria score across:
 * 1. Output quality (25%)
 * 2. Reliability (25%)
 * 3. Execution speed (15%)
 * 4. Cost (20% — strictly favoring free & open-source tools)
 * 5. Privacy (15% — favoring local on-device execution)
 */
class ToolSelectionEngine(
    private val toolRegistry: ToolRegistry,
    private val discoveryEngine: ToolDiscoveryEngine,
    private val costManager: CostManager
) {
    fun selectBestTool(
        capability: String,
        userApprovedPaidApis: Boolean = false
    ): SelectedToolDecision {
        val candidates = toolRegistry.findToolsByCapability(capability)
            .filter { discoveryEngine.isToolAvailable(it) && costManager.isAllowedUnderBudget(it, userApprovedPaidApis) }

        if (candidates.isEmpty()) {
            throw IllegalStateException("No available verified tool found for capability: $capability")
        }

        val ranked = candidates.map { tool ->
            val costScore = costManager.computeCostPreferenceScore(tool)
            val composite = (tool.qualityScore * 0.25f) +
                (tool.reliabilityScore * 0.25f) +
                (tool.speedScore * 0.15f) +
                (costScore * 0.20f) +
                (tool.privacyScore * 0.15f)
            tool to composite
        }.sortedByDescending { it.second }

        val best = ranked.first()
        val fallback = ranked.getOrNull(1)?.first?.toolId

        return SelectedToolDecision(
            capabilityRequired = capability,
            selectedTool = best.first,
            compositeScore = best.second,
            selectionRationale = "Selected '${best.first.name}' (${best.first.costTier.label}) with score ${String.format("%.3f", best.second)} " +
                "[Quality=${best.first.qualityScore}, Reliability=${best.first.reliabilityScore}, Privacy=${best.first.privacyScore}]",
            fallbackToolId = fallback
        )
    }
}
