# AIRA AI — Termux Unzip, APK Build & Installation Guide (Hinglish + English)

Follow these exact steps inside **Termux** on your Android phone to extract `AIRA-AI-Android-Source.zip`, compile/fetch `AIRA-AI-debug.apk`, and install it on your device.

---

## Step 1: Termux Me Storage Access Aur Required Tools Install Karein

Open **Termux** and run:

```bash
termux-setup-storage
pkg update -y && pkg upgrade -y
pkg install -y unzip git openjdk-17 gradle gh android-tools curl wget
```

---

## Step 2: Downloaded `AIRA-AI-Android-Source.zip` Ko Unzip Karein

Assuming `AIRA-AI-Android-Source.zip` is in your phone's `Download` folder:

```bash
cd ~
cp ~/storage/downloads/AIRA-AI-Android-Source.zip ~/
unzip -q AIRA-AI-Android-Source.zip
cd AIRA-AI-Android
```

---

## Step 3: One-Command Cloud APK Build + Auto-Download + Install (Recommended for Phone)

Because the full Android NDK + CMake + SDK 35 takes several GBs of storage on a phone, the fastest way inside Termux is our automated GitHub Actions builder script (`scripts/termux_setup_and_build.sh`), which pushes the repo to your GitHub, compiles the APK in the cloud, downloads it back into Termux, and opens the Android Package Installer automatically:

```bash
# 1. Login to your GitHub account once:
gh auth login

# 2. Run the automated build & installer script:
bash scripts/termux_setup_and_build.sh
```

What `bash scripts/termux_setup_and_build.sh` does automatically:
1. Creates your `AIRA-AI-Android` repository on GitHub and pushes branch `main`.
2. Triggers `.github/workflows/android-build.yml` (`gradle testDebugUnitTest assembleDebug`).
3. Watches the build live in Termux and downloads `AIRA-AI-Debug-APK` (`app-debug.apk`).
4. Copies `AIRA-AI-debug.apk` to your phone's `Download` folder (`/sdcard/Download/AIRA-AI-debug.apk`).
5. Calls `termux-open` so Android's **Install App** popup appears directly on your screen!

---

## Step 4: Alternative — Local Gradle Build Inside Termux (If Android SDK Installed in Termux)

If you have an ARM64 Android SDK installed inside Termux at `$HOME/android-sdk`:

```bash
export ANDROID_HOME="$HOME/android-sdk"
export JAVA_HOME="/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk"
bash scripts/termux_setup_and_build.sh
```

---

## Step 5: Install Ke Baad Yeh 3 Permissions On Karein (Full Screen Control Ke Liye)

Jab APK install ho jaye, toh AIRA ko full autonomous screen operator banane ke liye yeh 3 settings on karein:

1. **Accessibility Screen Control**:
   - Go to **Android Settings -> Accessibility -> Installed apps -> AIRA Autonomous Screen Operator** -> Toggle **ON**.
2. **Floating Live HUD Overlay (`STOP` Button)**:
   - Go to **Android Settings -> Apps -> AIRA AI -> Display over other apps** -> Toggle **ON**.
3. **Quick Settings Tile (Optional 1-Tap Trigger)**:
   - Notification shade niche swipe karein -> **Edit (Pencil icon)** dabayein -> **AIRA Operator** tile ko top par drag kar lein.
