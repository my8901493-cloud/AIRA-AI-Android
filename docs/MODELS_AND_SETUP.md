# AIRA AI — Model Selection, Hardware Tiers & Setup Guide

## 1. Device Hardware Tier Recommendations

`ModelCatalogManager` automatically profiles the Android device's total RAM, available storage, CPU cores, and ABIs before recommending downloadable models:

| Device RAM Tier | Recommended On-Device LLM | Model Size | STT Engine | TTS Engine |
| :--- | :--- | :--- | :--- | :--- |
| **3 GB – 4 GB RAM** | AIRA Built-In Instant Engine + **Qwen 2.5 0.5B Instruct (`Q4_K_M`)** | 398 MB | Android Streaming STT / Whisper Tiny (`Q5_1`, 32 MB) | Android Native Female Voice (`hi-IN` / `en-IN`) |
| **6 GB RAM** | **Llama 3.2 1B Instruct (`Q4_K_M`)** | 808 MB | Android Streaming STT / Vosk Small (`en-in`, 45 MB) | Piper Neural TTS (`hi_IN-priyamvada-medium`, 64 MB) |
| **8 GB – 12 GB+ RAM** | **Google Gemma 2 2B Instruct (`Q4_K_M`)** | 1,630 MB | Whisper Tiny / Vosk + Android Streaming STT | Piper Neural TTS + Android Female Voice |
| **Any RAM (Wi-Fi LAN)** | **Local Network Ollama / llama.cpp Server** | 0 MB on phone | Any STT Engine | Any TTS Engine |

---

## 2. Connecting to a Local Network Ollama Server (Optional)

If you run [Ollama](https://ollama.com) or `llama.cpp` server on your laptop/PC on the same Wi-Fi network:

1. Start Ollama bound to your LAN interface:
   ```bash
   OLLAMA_HOST=0.0.0.0:11434 ollama serve
   ollama pull llama3.2:1b
   ```
2. Open **AIRA AI -> Settings & Model Manager**, select **Local Network LLM (Ollama / llama.cpp LAN Server)**, and set your PC's LAN IP (`http://192.168.1.X:11434/v1/chat/completions`).
3. If you leave Wi-Fi range, `CompositeLlmRouter` automatically falls back to AIRA's on-device local engine without crashing or losing conversation context.
