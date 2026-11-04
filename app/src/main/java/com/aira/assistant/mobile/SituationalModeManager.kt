package com.aira.assistant.mobile

enum class AiraSituationalMode(val displayName: String) {
    STANDARD("Standard Assistant Mode"),
    DRIVING_HANDS_FREE("Driving Mode (Hands-Free Voice & Aloud Alerts)"),
    STUDY_FOCUS("Study & Deep Focus Mode (DND + Screen Explainer)"),
    EMERGENCY_SOS("Emergency SOS Survival Mode")
}

/**
 * Manages Driving Mode, Study/Focus Mode, and Emergency SOS Survival Mode (`SituationalModeManager`).
 */
class SituationalModeManager(
    private val hardwareController: SystemHardwareController
) {
    var activeMode: AiraSituationalMode = AiraSituationalMode.STANDARD
        private set

    fun activateMode(mode: AiraSituationalMode): String {
        activeMode = mode
        return when (mode) {
            AiraSituationalMode.STANDARD -> "Switched to Standard Assistant Mode."
            AiraSituationalMode.DRIVING_HANDS_FREE -> {
                hardwareController.setMediaVolumePercent(85)
                "Driving Mode active: incoming messages will be read aloud and all controls are hands-free."
            }
            AiraSituationalMode.STUDY_FOCUS -> {
                "Study & Focus Mode active: distracting notifications muted and live screen explainer enabled."
            }
            AiraSituationalMode.EMERGENCY_SOS -> {
                hardwareController.setFlashlight(true)
                hardwareController.setMediaVolumePercent(100)
                "EMERGENCY SOS Mode active: flashlight beacon turned ON and emergency alert prepared."
            }
        }
    }
}
