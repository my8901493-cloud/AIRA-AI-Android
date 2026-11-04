# AIRA AI — System Architecture & Voice Pipeline Design

## 1. Clean Architecture & Layer Separation

AIRA AI is structured into five decoupled layers inside `com.aira.assistant`:

1. **Presentation & UI Layer (`ui/`)**
   - Built with **Jetpack Compose** and Material 3 (`AiraTheme`).
   - `AiraOrb.kt` renders a 60fps hardware-accelerated multi-layered radial/sweep gradient orb reacting to `VoiceState` and real-time audio RMS/TTS amplitude.
   - `AiraViewModel.kt` exposes unidirectional `StateFlow` streams to `MainAssistantScreen`, `ConversationHistoryScreen`, `MemoryManagerScreen`, and `SettingsScreen`.

2. **Full-Duplex Voice & Audio Layer (`voice/`)**
   - `AudioCaptureManager.kt`: Captures 16kHz 16-bit mono PCM audio on `AudioSource.VOICE_COMMUNICATION` and binds hardware `AcousticEchoCanceler`, `NoiseSuppressor`, and `AutomaticGainControl`.
   - `VadProcessor.kt` & `NativeBridge.kt`: Evaluates every 20ms frame (`320` samples) in C++ JNI (`aira_native.cpp`) for normalized RMS energy and Zero-Crossing Rate (ZCR).
   - `VoiceConversationCoordinator.kt`: State machine managing `IDLE`, `LISTENING`, `THINKING`, `SPEAKING`, `INTERRUPTING`, and `ERROR`.

3. **AI Brain & Model Layer (`ai/`)**
   - `LlmEngine.kt`: Provider-independent streaming interface.
   - `LocalOnDeviceLlmEngine.kt`: Executes local GGUF models or the built-in zero-download bilingual reasoning engine.
   - `LocalNetworkLlmEngine.kt`: Connects to self-hosted LAN endpoints (Ollama / `llama.cpp` server).
   - `CompositeLlmRouter.kt`: Provides automatic failover to on-device execution when offline.

4. **Agent & Tool Layer (`agent/`, `tools/`)**
   - `AgentOrchestrator.kt`: Detects intents from Hindi, Hinglish, and English queries, builds multi-step `AgentPlan` objects, enforces confirmation gates on sensitive tools, and verifies `ToolExecutionResult` outputs.
   - `AiraToolRegistry.kt` & `ExpressionCalculator.kt`: Implements 14 verified tools.

5. **Persistence & Security Layer (`data/`, `core/`)**
   - `AiraDatabase.kt` (Room SQLite): Stores `conversations`, `messages`, `memories`, `notes`, `tasks`, and `reminders`.
   - `MemoryRepository.kt`: Enforces user consent, blocks sensitive patterns (passwords, OTPs, card/ID numbers), and deduplicates contradictory facts on `(category, subjectKey)`.
   - `SecurityPreferences.kt`: Uses Android Keystore `EncryptedSharedPreferences` (`AES256_SIV` / `AES256_GCM`).

---

## 2. Barge-In & Echo Rejection Sequence

```text
[AIRA is in SPEAKING state playing TTS chunks]
       │
       ▼
[AudioCaptureManager] reads 20ms PCM frame (with Hardware AEC + NS enabled)
       │
       ▼
[NativeBridge (C++ JNI)] computes (rmsNormalized, zeroCrossingRate) in <0.2ms
       │
       ▼
[VadProcessor] checks:
  1. Is ZCR in human speech band (0.015 .. 0.45)?
  2. Does RMS exceed (baseThreshold * playbackEchoGuardMultiplier)?
  3. Have >= 3 consecutive speech frames occurred (~60ms)?
       │ (YES -> Barge-In Triggered)
       ▼
[VoiceConversationCoordinator.triggerBargeIn()]
  1. Increments atomic `turnGenerationId` (invalidates any in-flight LLM tokens)
  2. Cancels `activeReasoningJob` & calls `llmEngine.cancelActiveGeneration()`
  3. Calls `ttsEngine.stopImmediately()` (clears sentence buffer + flushes Android TTS queue)
  4. Transitions state: SPEAKING -> INTERRUPTING -> LISTENING
```
