# AIRA AI — Next-Generation Native Android Personal AI Assistant

AIRA AI is a modular, privacy-first, offline-capable personal AI assistant built natively for Android using **Kotlin**, **Jetpack Compose**, **Clean Architecture + MVVM**, **Room Database**, and a **C++17 JNI DSP & Model Validation Bridge**.

Designed with a warm, intelligent female persona fluent in **Hindi (हिन्दी)**, **Hinglish**, and **English**, AIRA AI features a full-duplex real-time voice conversation pipeline with hardware echo cancellation, voice activity detection (VAD), streaming Text-to-Speech, and instant **Barge-In interruption**.

---

## Key Capabilities & Architecture Highlights

1. **Premium Futuristic UI (`ui/components/AiraOrb.kt`, `ui/screens/MainAssistantScreen.kt`)**
   - Deep obsidian dark theme with cosmic violet (`#8B5CF6`) and cyan/sapphire (`#00E5FF`) lighting and glassmorphic controls.
   - Central animated multi-layered Canvas AI Orb reacting dynamically to 6 explicit states (`IDLE`, `LISTENING`, `THINKING`, `SPEAKING`, `INTERRUPTING`, `ERROR`) with zero text clutter below the orb.
   - Side-edge ambient glow (`ScreenSharingEdgeGlow`) whenever user-authorized Screen Understanding is active.

2. **Full-Duplex Real-Time Voice Pipeline & Instant Barge-In (`voice/VoiceConversationCoordinator.kt`)**
   - Pipeline: `Microphone -> AudioRecord (VOICE_COMMUNICATION) -> Hardware AEC/NS/AGC -> Native C++ DSP & VAD -> Streaming STT -> AI Reasoning -> Sentence-Chunk Streaming TTS -> Audio Playback`.
   - **Echo-Guarded Barge-In (`voice/vad/VadProcessor.kt`)**: Elevates energy thresholds during assistant playback and filters by human-speech Zero-Crossing Rate (`0.015..0.45`) so AIRA never interrupts herself from speaker bleed, while stopping within `<50ms` when the user speaks.
   - **Strict Turn-Generation Fencing (`turnGenerationId`)**: Immediately cancels active LLM generation, flushes pending TTS sentence buffers, and discards stale tokens on interruption.

3. **Provider-Independent AI Brain & Hardware-Aware Model Manager (`ai/`)**
   - **Built-In Instant Local Bilingual Engine (`LocalOnDeviceLlmEngine.kt`)**: Works immediately offline out of the box with zero external download required.
   - **On-Device GGUF Model Catalog (`ModelCatalogManager.kt`)**: Benchmarks device RAM, free storage, CPU cores, and ABIs before enabling downloads for **Qwen 2.5 0.5B Instruct (Q4_K_M)**, **Llama 3.2 1B Instruct (Q4_K_M)**, and **Google Gemma 2 2B Instruct (Q4_K_M)**, verifying GGUF magic headers natively via JNI (`NativeBridge.kt`).
   - **Local Network / LAN Server Engine (`LocalNetworkLlmEngine.kt`)**: Streams from self-hosted **Ollama**, **llama.cpp HTTP server**, or **LM Studio** over Wi-Fi with zero mandatory paid APIs.
   - **Automatic Fallback (`CompositeLlmRouter.kt`)**: Seamlessly falls back to on-device execution if a LAN server disconnects.

4. **Modular Speech Recognition (STT) & Streaming Female TTS (`voice/stt/`, `voice/tts/`)**
   - **STT**: Supports Android low-latency streaming `SpeechRecognizer` (with `en-IN` and `hi-IN` offline/online packs) alongside downloadable **Whisper.cpp Tiny Q5_1** and **Vosk Small Indian English/Hindi** models.
   - **TTS**: Selects a natural female voice by default and streams clause-by-clause as tokens arrive rather than waiting for full paragraphs.

5. **Long-Term Memory System (`data/repository/MemoryRepository.kt`, `data/local/AiraDatabase.kt`)**
   - Stores 5 structured memory categories in Room (`SHORT_TERM_CONTEXT`, `USER_PREFERENCE`, `IMPORTANT_FACT`, `TASK_PROJECT_CONTEXT`, `CONVERSATION_SUMMARY`) strictly separated from raw conversation history (`ConversationRepository.kt`).
   - Automatic implicit memory extraction from Hindi, Hinglish, and English utterances, in-place deduplication/contradiction resolution on `(category, subjectKey)`, user consent toggle, and regex-based sensitive data blocking (passwords, OTPs, card/Aadhaar/PAN numbers).

6. **Personal Assistant Tools & Multi-Step Agent Engine (`tools/`, `agent/AgentOrchestrator.kt`)**
   - 14 verified built-in tools: Current Time/Date, System Alarms (`AlarmClock.ACTION_SET_ALARM`), System Timers (`AlarmClock.ACTION_SET_TIMER`), Local Reminders (`AlarmManager`), Installed App Launcher (`PackageManager`), Deterministic Math Evaluator (`ExpressionCalculator.kt`), Local Notes, To-Do Tasks, Permission-Gated Notification Reader (`AiraNotificationListenerService`), Keyless Weather (`Open-Meteo`), Web Search (`DuckDuckGo`), Text Summarizer, and Confirmation-Gated Local File Search.

7. **Permission-Gated Screen Understanding (`service/ScreenCaptureService.kt`)**
   - Uses Android `MediaProjection` only when explicitly toggled by the user, displaying a persistent foreground stop action, visual edge glow, and on-device Latin + Devanagari (Hindi) OCR via Google ML Kit Text Recognition.

---

## Project Structure

```text
AIRA-AI-Android/
├── .github/workflows/android-build.yml   # Automated CI: Unit tests + Debug APK artifact upload
├── app/
│   ├── build.gradle.kts                  # App Gradle config (Compose, Room KSP, NDK CMake, ML Kit)
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # Permissions, Activities, Share target, Foreground Services
│       │   ├── cpp/
│       │   │   ├── CMakeLists.txt
│       │   │   └── aira_native.cpp       # C++17 JNI DSP audio metrics (RMS/ZCR) & GGUF verifier
│       │   ├── java/com/aira/assistant/
│       │   │   ├── AiraApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── agent/                # AgentOrchestrator (Intent -> Plan -> Verify -> Execute)
│       │   │   ├── ai/                   # LlmEngine, CompositeLlmRouter, ModelCatalogManager, Persona
│       │   │   ├── core/                 # AiraLogger, SecurityPreferences, NetworkConnectivityObserver
│       │   │   ├── data/                 # Room Entities, DAOs, AiraDatabase, Memory & Chat Repos
│       │   │   ├── domain/model/         # Immutable Kotlin domain models
│       │   │   ├── service/              # Voice, ScreenCapture (MediaProjection), Notification services
│       │   │   ├── tools/                # AiraToolRegistry & ExpressionCalculator
│       │   │   ├── ui/                   # AiraOrb, MainAssistantScreen, History, Memory, Settings, VM
│       │   │   └── voice/                # VoiceConversationCoordinator, VadProcessor, STT, TTS, Mic
│       │   └── res/                      # XML themes, strings, adaptive launcher icons
│       └── test/java/com/aira/assistant/ # JUnit 4 + Coroutines test suites (Voice, Barge-In, Memory, Tools)
├── docs/
│   ├── ARCHITECTURE.md
│   └── MODELS_AND_SETUP.md
├── DEVELOPMENT_REPORT.md
├── build.gradle.kts
├── gradle/libs.versions.toml             # Centralized Gradle Version Catalog
├── gradle.properties
└── settings.gradle.kts
```

---

## Quick Start: Building & Installing the APK

### Option 1: Android Studio (Recommended for Local Development)
1. Extract `AIRA-AI-Android-Source.zip` or clone the `AIRA-AI-Android` repository.
2. Open the folder in **Android Studio Ladybug (2024.2.1) or newer** with **JDK 17** and **Android SDK 35 + NDK**.
3. Let Gradle sync dependencies, then click **Run 'app'** (`Shift + F10`) to install on a connected Android 8.0+ (`minSdk 26`) device.

### Option 2: Command Line Gradle Build
```bash
# If gradle is installed on your workstation:
gradle wrapper --gradle-version 8.9
./gradlew testDebugUnitTest assembleDebug

# Install Debug APK onto connected Android phone via ADB:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Option 3: Automated Cloud APK Build via GitHub Actions
Push this repository to GitHub:
```bash
git remote add origin https://github.com/<your-username>/AIRA-AI-Android.git
git push -u origin main
```
GitHub Actions (`.github/workflows/android-build.yml`) will automatically run all unit tests, compile the C++ NDK library and Kotlin sources, and publish the installable `AIRA-AI-Debug-APK` artifact under the **Actions** tab.

---

## Autonomous Execution Engine (`com.aira.assistant.autonomous`)

AIRA AI now includes a full **Autonomous Execution Engine** that accepts high-level objectives, source code, Android project ZIPs, video scripts, or narration text and independently executes the entire pipeline:

`USER INPUT -> InputAnalyzer -> TaskClassifier -> PlanningEngine -> ToolDiscoveryEngine -> ToolSelectionEngine -> PermissionManager -> ExecutionEngine -> ResultValidator -> ErrorRecoveryEngine -> Final Delivery`

### Modular Autonomous Components (`app/src/main/java/com/aira/assistant/autonomous/`)
- `AutonomousTaskManager.kt`: Top-level orchestrator managing live progress stages (`Analyzing -> Planning -> Selecting Tools -> Executing -> Validating -> Completed`), checkpointing, approval gates, and safe cancellation.
- `InputAnalyzer.kt` & `TaskClassifier.kt`: Automatically detects input formats, languages (`hi-IN`, `en-IN`), Android project structure, and syntax health without asking the user which tool to pick.
- `ToolRegistry.kt`, `ToolDiscoveryEngine.kt`, `CostManager.kt`, & `ToolSelectionEngine.kt`: Verifies binary/SDK/hardware availability at runtime and ranks candidate tools across Quality (25%), Reliability (25%), Speed (15%), Cost (20% free-first), and Privacy (15% local-first).
- `PlanningEngine.kt` & `ExecutionEngine.kt`: Builds multi-step workflow DAGs with parallel execution hints and saves persistent Room checkpoints (`AutonomousTaskEntity`) via `TaskHistoryRepository.kt`.
- `CodeBuildAgent.kt`: Inspects Android/Kotlin/Java/C++ code, auto-repairs syntax/delimiter/import defects with versioned backups, runs local Gradle builds when `ANDROID_HOME` is present, or prepares/triggers GitHub Actions cloud APK builds.
- `VideoProductionAgent.kt`: 6-Step Autonomous Script-to-Video Engine (Script Analysis & Storyboard -> Watermark-Free HD Visuals & Motion Graphics -> Segmented Voiceover -> Music & SFX with `-14dB` Sidechain Ducking -> H.264/AAC MP4 Composition in `16:9` & `9:16` with Subtitles -> Quality Gate).
- `AudioProductionAgent.kt`: Synthesizes segmented female voiceover with breathing pauses, ambient score, transition chimes, sidechain ducking, and `-1.0 dBFS` peak normalization into `.wav` and `.mp3`.
- `FileWorkspaceManager.kt`, `ResultValidator.kt`, & `ErrorRecoveryEngine.kt`: Enforces isolated task sandboxes, atomic `.tmp` staging writes, instant rollback on cancellation, bounded self-healing retries, and container header/stream validation.
