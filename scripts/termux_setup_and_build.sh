#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# AIRA AI — Automated Termux Unpack, Build, Cloud APK Fetch & Install Script
# Run inside Termux on Android:
#   bash scripts/termux_setup_and_build.sh
# ==============================================================================
set -e

echo "============================================================"
echo "  AIRA AI — Termux Setup, APK Build & Installer"
echo "============================================================"

# 1. Install required packages inside Termux if running on Android Termux
if command -v pkg >/dev/null 2>&1; then
    echo "[1/5] Updating Termux packages and installing OpenJDK 17, Git, Gradle, GitHub CLI & Android Tools..."
    pkg update -y
    pkg install -y openjdk-17 git gradle android-tools gh unzip wget curl
    termux-setup-storage || true
fi

echo "[2/5] Verifying AIRA-AI-Android project files..."
if [ ! -f "settings.gradle.kts" ]; then
    echo "Error: Please run this script from the root of AIRA-AI-Android directory."
    exit 1
fi

echo "[3/5] Choosing optimal APK build method for your device..."
echo "  Option A (Recommended on Mobile): Push to GitHub & download the GitHub Actions compiled APK automatically via 'gh'."
echo "  Option B (Local Termux Gradle Build): Run 'gradle assembleDebug' if Android SDK (ANDROID_HOME) is configured in Termux."

if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
    echo "[4/5] Local ANDROID_HOME detected ($ANDROID_HOME). Building Debug APK locally..."
    gradle testDebugUnitTest assembleDebug --no-daemon
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
elif command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    echo "[4/5] GitHub CLI authenticated! Creating/pushing AIRA-AI-Android and triggering cloud APK build..."
    REPO_NAME="AIRA-AI-Android"
    gh repo create "$REPO_NAME" --public --source=. --remote=origin --push || git push -u origin main
    echo "Waiting for GitHub Actions workflow (android-build.yml) to finish compiling app-debug.apk..."
    sleep 10
    RUN_ID=$(gh run list --workflow=android-build.yml --limit 1 --json databaseId --jq '.[0].databaseId')
    gh run watch "$RUN_ID"
    mkdir -p build_artifacts
    gh run download "$RUN_ID" -n AIRA-AI-Debug-APK -D build_artifacts
    APK_PATH=$(find build_artifacts -name "*.apk" | head -n 1)
else
    echo "[4/5] Notice: Neither local ANDROID_HOME nor 'gh auth login' is active yet."
    echo "To build and download the APK in 2 commands inside Termux, run:"
    echo "  1) gh auth login"
    echo "  2) bash scripts/termux_setup_and_build.sh"
    exit 0
fi

if [ -n "$APK_PATH" ] && [ -f "$APK_PATH" ]; then
    echo "[5/5] APK Ready at: $APK_PATH"
    # Copy to Android Shared Downloads folder for easy 1-tap install
    if [ -d "$HOME/storage/downloads" ]; then
        cp -f "$APK_PATH" "$HOME/storage/downloads/AIRA-AI-debug.apk"
        echo "Copied APK to /sdcard/Download/AIRA-AI-debug.apk"
    fi
    # Trigger Android Package Installer dialog directly from Termux
    if command -v termux-open >/dev/null 2>&1; then
        echo "Launching Android Package Installer..."
        termux-open "$APK_PATH"
    elif command -v adb >/dev/null 2>&1; then
        adb install -r "$APK_PATH" || true
    fi
    echo "============================================================"
    echo "  SUCCESS! Confirm 'Install' on your screen, then enable:"
    echo "  1. Settings -> Accessibility -> AIRA Autonomous Screen Operator"
    echo "  2. Settings -> Apps -> AIRA AI -> Display over other apps"
    echo "============================================================"
fi
