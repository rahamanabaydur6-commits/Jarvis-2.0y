# JARVIS Assistant (Kotlin + Jetpack Compose)

Hinglish/Hindi/Bengali/English voice + chat assistant on Gemini. **Gemini only plans** (JSON). A separate, allow-listed executor runs actions, and only after you tap **Approve**.

> Status: source code only. It was NOT compiled or run by the author. Expect to fix small compile errors on first build (see Troubleshooting).

## Features that really work (and what they need)
| Feature | Needs |
|---|---|
| Chat, memory, personas, image understanding, code workspace, study/translate/writing/summary | Gemini API key (Settings) |
| Voice in/out (hi-IN / en-IN / bn-IN) | Mic permission + phone's speech services. Continuous mode works only while the app is open |
| Open app by name, YouTube search hand-off, open links, Settings | none |
| Flashlight, volume, media keys | none (media keys work only if a player listens) |
| Back / Home / Scroll / Tap text | You enable Accessibility service manually (Control tab) |
| Dial / SMS draft (by name) | Contacts permission. App only opens dialer/composer; YOU press Call/Send |
| Weather | none (Open-Meteo) |
| Web search / AI-tool discovery | Google Programmable Search key + cx (Settings) |
| Notes, to-dos, reminders, calculator, converter | none (offline) |

Not implemented (honest list): wake-word / always-on background listening, streaming replies, direct auto-calling (`CALL_PHONE`), app installation, calendar integration, screenshot analysis (attach via 📷 in Chat instead), custom user-defined commands.

## Termux build (aarch64)
Needs ~6 GB free storage, 4 GB+ RAM. Compose builds are slow on phones.

```bash
pkg update && pkg upgrade -y
pkg install -y openjdk-17 wget unzip aapt2
termux-setup-storage

# 1) Gradle 8.9 (AGP 8.5.2 needs 8.7+, and NOT Gradle 9)
cd ~ && wget https://services.gradle.org/distributions/gradle-8.9-bin.zip
unzip -q gradle-8.9-bin.zip
echo 'export PATH=$HOME/gradle-8.9/bin:$PATH' >> ~/.bashrc

# 2) Android SDK command-line tools
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip && mv cmdline-tools latest
echo 'export ANDROID_HOME=$HOME/android-sdk' >> ~/.bashrc
echo 'export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk' >> ~/.bashrc
source ~/.bashrc
yes | sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0"

# 3) Use Termux's native aapt2 (the SDK's aapt2 is x86 and will not run)
mkdir -p ~/.gradle
cat >> ~/.gradle/gradle.properties <<EOP
android.aapt2FromMavenOverride=$PREFIX/bin/aapt2
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx1536m
EOP

# 4) Build
cd ~ && unzip -q /sdcard/Download/JarvisAssistant.zip && cd JarvisAssistant
echo "sdk.dir=$HOME/android-sdk" > local.properties
gradle assembleDebug --no-daemon -Dorg.gradle.workers.max=2
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
```bash
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/jarvis.apk   # then tap it in a file manager
```
Run unit tests: `gradle testDebugUnitTest --no-daemon`

## If Termux build fails
- **Cloud fallback:** push this folder to GitHub; `.github/workflows/build.yml` builds the APK and uploads it as an artifact.
- **Android Studio** on a PC: open the folder, Build > Build APK.

## Troubleshooting
- `aapt2 ... Syntax error / cannot execute`: step 3 missing. Check `ls $PREFIX/bin/aapt2`.
- `Android resource linking failed` with Termux aapt2: version mismatch; use the cloud fallback.
- `Unsupported Java` / `JAVA_HOME`: need JDK 17 (`java -version`).
- `SDK location not found`: create `local.properties`.
- `OutOfMemoryError / Daemon disappeared`: lower `-Xmx`, close other apps, keep `--no-daemon`.
- `Could not resolve ...`: need internet; retry.
- Kotlin compile error mentioning `withLink` / `LinkAnnotation` (Compose text < 1.7): bump `compose-bom` to a newer stable version.
- Gemini `404 model not found`: Settings > Test connection > pick a listed model. `403/400`: bad key. `429`: quota, wait and Retry.
- `speech recognition not available`: install/enable Google speech services; Hindi/Bengali packs may need download.

## Security notes
API keys are AES-GCM encrypted with an Android Keystore key. Nothing is hardcoded. For production, route Gemini calls through your own backend instead of shipping user keys on-device.
