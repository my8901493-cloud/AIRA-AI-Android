package com.aira.assistant.tools

import com.aira.assistant.agent.AgentOrchestrator
import com.aira.assistant.domain.model.ToolExecutionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsAndAgentTest {

    @Test
    fun expressionCalculatorEvaluatesComplexMathSafely() {
        val r1 = ExpressionCalculator.evaluate("(250 * 4) + sqrt(144) - 12")
        assertTrue(r1.isSuccess)
        assertEquals(1000.0, r1.getOrThrow(), 1e-6)

        val divZero = ExpressionCalculator.evaluate("100 / 0")
        assertTrue(divZero.isFailure)
    }

    @Test
    fun agentOrchestratorDetectsIntentsAndExecutesVerifiedTools() = runTest {
        val registry = AiraToolRegistry(context = null, itemsDao = null)
        val orchestrator = AgentOrchestrator(registry)

        val calcPlan = orchestrator.createPlanForUtterance("Calculate 45 * 20 + 10")
        assertNotNull(calcPlan)
        assertEquals("CALCULATE_MATH", calcPlan!!.detectedIntent)

        val results = orchestrator.executePlan(calcPlan, userConfirmed = true)
        assertEquals(1, results.size)
        assertEquals(ToolExecutionStatus.SUCCESS, results.first().status)
        assertTrue(results.first().outputText.contains("910"))
    }

    @Test
    fun sensitiveLocalFileSearchRequiresExplicitConfirmation() = runTest {
        val registry = AiraToolRegistry(context = null, itemsDao = null)
        val orchestrator = AgentOrchestrator(registry)

        val filePlan = orchestrator.createPlanForUtterance("Search local files for invoice")
        assertNotNull(filePlan)
        assertTrue(filePlan!!.requiresUserConfirmation)

        // Calling without confirmation must block execution
        val unconfirmedResult = registry.executeTool(
            toolName = "search_local_files",
            arguments = mapOf("keyword" to "invoice"),
            userConfirmed = false
        )
        assertEquals(ToolExecutionStatus.REQUIRES_CONFIRMATION, unconfirmedResult.status)
    }
}
