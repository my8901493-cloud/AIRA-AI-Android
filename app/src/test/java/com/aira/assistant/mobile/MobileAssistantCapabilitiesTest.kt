package com.aira.assistant.mobile

import com.aira.assistant.domain.model.ScreenSnapshot
import com.aira.assistant.domain.model.UiBounds
import com.aira.assistant.domain.model.UiElementNode
import com.aira.assistant.operator.AccessibilityInteractionManager
import com.aira.assistant.service.CapturedNotificationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MobileAssistantCapabilitiesTest {

    @Test
    fun test1_setOfMarkGroundingAndScreenDiffVerification() {
        val engine = ScreenDiffAndGroundingEngine()
        val before = ScreenSnapshot(
            foregroundPackage = "com.android.chrome",
            elements = listOf(
                UiElementNode("n1", text = "Search", bounds = UiBounds(100, 100, 300, 180), isClickable = true)
            )
        )
        val after = ScreenSnapshot(
            foregroundPackage = "com.android.chrome",
            elements = listOf(
                UiElementNode("n1", text = "Search", bounds = UiBounds(100, 100, 300, 180), isClickable = true),
                UiElementNode("n2", text = "Top Result: Kotlin Agents", bounds = UiBounds(100, 300, 900, 450), isClickable = true)
            )
        )

        val marks = engine.buildSetOfMarkTags(after)
        assertEquals(2, marks.size)
        assertEquals(1, marks[0].markIndex)
        assertEquals(2, marks[1].markIndex)

        val diff = engine.verifyScreenChanged(before, after)
        assertTrue(diff.screenChangedAfterAction)
        assertEquals(1, diff.newElementsAppeared)
    }

    @Test
    fun test2_systemHardwareControllerTorchAndVolume() {
        val hw = SystemHardwareController(context = null)
        hw.setFlashlight(true)
        assertTrue(hw.isTorchEnabled)

        hw.setMediaVolumePercent(45)
        assertEquals(45, hw.currentVolumePercent)
    }

    @Test
    fun test3_smartCommunicationManagerOtpProtectionAndConfirmedReply() {
        val comm = SmartCommunicationManager()
        val notifications = listOf(
            CapturedNotificationItem("com.whatsapp", "WhatsApp", "Rahul", "Bhai kahan ho?", 1000L),
            CapturedNotificationItem("com.bank", "HDFC Bank", "Bank Alert", "Your OTP is 482910 for transaction", 2000L)
        )

        val analyzed = comm.analyzeNotifications(notifications)
        assertFalse(analyzed[0].containsSensitiveOtp)
        assertTrue(analyzed[1].containsSensitiveOtp)

        comm.prepareSmartReplyDraft("Rahul", "WhatsApp", "Main 10 min me pahunch raha hoon")
        val sendRes = comm.confirmAndSendDraft()
        assertTrue(sendRes.isSuccess)
    }

    @Test
    fun test4_cameraAndGalleryOcrSearchAndPdfScanner() {
        val gallery = CameraAndGalleryIntelligence()
        gallery.indexDocumentOrScreenshot("/sdcard/DCIM/bill_oct.jpg", "APDCL Electricity Bill Guwahati Total Rs. 1450")

        val hits = gallery.searchGalleryByNaturalQuery("electricity bill")
        assertEquals(1, hits.size)
        assertEquals("BILL_INVOICE", hits.first().detectedCategory)

        val tempPdf = File(System.getProperty("java.io.tmpdir"), "scanned_test_doc.pdf")
        gallery.scanPagesToPdf(listOf("Page 1: Scanned Invoice Rs. 1450"), tempPdf)
        assertTrue(tempPdf.exists() && tempPdf.length() > 20L)
        tempPdf.delete()
    }

    @Test
    fun test5_macroRoutineRecorderShowOnceRepeatForever() {
        val a11y = AccessibilityInteractionManager(null)
        val recorder = MacroRoutineRecorder(a11y)

        recorder.startRecording()
        recorder.recordStep("TAP_COORDS", "Search", 500, 200)
        recorder.recordStep("TYPE_TEXT", "Guwahati weather")
        recorder.recordStep("SCROLL_DOWN", "")
        val saved = recorder.stopAndSaveMacro("Morning Weather Check", "subah ka weather")

        assertEquals(3, saved.steps.size)
        val replayRes = recorder.replayMacroByPhrase("subah ka weather chala do")
        assertTrue(replayRes.isSuccess)
        assertEquals(3, replayRes.getOrThrow())
    }

    @Test
    fun test6_universalClipboardAndScamPhishingShield() {
        val clip = UniversalClipboardAndConverter()
        clip.pushCopiedItem("https://developer.android.com/kotlin", "com.android.chrome")
        assertNotNull(clip.findRecentCopiedItem("chrome"))

        val shield = ScamAndPhishingShield()
        val scamCheck = shield.inspectTextOrUrl("URGENT: Your bank account will be blocked. Install this apk for kyc")
        assertTrue(scamCheck.isSuspiciousOrDangerous)
        assertEquals("CRITICAL_BLOCK", scamCheck.riskLevel)

        val safeCheck = shield.inspectTextOrUrl("https://developer.android.com/jetpack/compose")
        assertFalse(safeCheck.isSuspiciousOrDangerous)
    }
}
