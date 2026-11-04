package com.aira.assistant.autonomous

import com.aira.assistant.domain.model.AutonomousToolMetadata
import com.aira.assistant.domain.model.ToolCostTier

/**
 * Enforces AIRA's strict Cost & Resource Optimization hierarchy:
 * 1. Free local tools (`FREE_LOCAL`)
 * 2. Free open-source models (`FREE_OPEN_SOURCE_MODEL`)
 * 3. Free cloud build infrastructure (`FREE_CLOUD_CI`)
 * 4. Available authorized integrations (`AUTHORIZED_INTEGRATION`)
 * 5. Paid services (`PAID_API_REQUIRES_APPROVAL`) ONLY when necessary and explicitly approved.
 */
class CostManager {

    fun isAllowedUnderBudget(tool: AutonomousToolMetadata, userApprovedPaidApis: Boolean): Boolean {
        if (tool.costTier == ToolCostTier.PAID_API_REQUIRES_APPROVAL && !userApprovedPaidApis) {
            return false
        }
        return true
    }

    fun computeCostPreferenceScore(tool: AutonomousToolMetadata): Float {
        return when (tool.costTier) {
            ToolCostTier.FREE_LOCAL -> 1.00f
            ToolCostTier.FREE_OPEN_SOURCE_MODEL -> 0.95f
            ToolCostTier.FREE_CLOUD_CI -> 0.88f
            ToolCostTier.AUTHORIZED_INTEGRATION -> 0.78f
            ToolCostTier.PAID_API_REQUIRES_APPROVAL -> 0.25f
        }
    }
}
