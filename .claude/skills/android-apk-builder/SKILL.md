---
name: android-apk-builder
description: Build Android APK files from React web applications using Capacitor. Use when the user wants to create an APK, build an Android app from a web project, set up Capacitor for Android, or convert a React/Vite app into an installable Android application.
---

# Android APK Builder Skill

Build Android APK files from React web applications using Capacitor. Handles environment setup, project configuration, and APK compilation.

## Prerequisites

- Linux/Unix environment (or WSL on Windows)
- Root or sudo access
- Minimum 4GB RAM, 10GB free disk space
- Internet connection

## Workflow

Make a todo list for all the tasks in this workflow and work on them one after another.

### 1. Assess Project State

Before doing anything, analyze the current project:

- Check if `package.json` exists (is this already a Node.js project?)
- Check if `capacitor.config.json` or `capacitor.config.ts` exists (Capacitor already set up?)
- Check if `android/` directory exists (Android platform already added?)
- Check if `vite.config.js` or `vite.config.ts` exists (what bundler is in use?)
- Check if `dist/` or `build/` directory exists (has the web app been built before?)
- Check current `android/app/build.gradle` for version info if it exists

Based on these findings, skip any phases that are already complete. Do NOT re-initialize or overwrite existing configuration without user confirmation.

### 2. Environment Setup (One-time)

Check each tool before installing. Skip any that are already present and at a compatible version.

#### 2.1 Java Development Kit

```bash
# Check if Java is installed
java -version 2>&1

# Install only if missing or incompatible (need JDK 17+)
apt-get update && apt-get install -y default-jdk-headless

# Find and record JAVA_HOME
JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
```

#### 2.2 Android SDK

```bash
# Check if Android SDK exists
ls /usr/local/android-sdk/cmdline-tools/latest/bin/sdkmanager 2>/dev/null

# Install only if missing
mkdir -p /usr/local/android-sdk
cd /usr/local/android-sdk

wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip

# Extract - use Python if unzip is unavailable
python3 -c "
import zipfile
with zipfile.ZipFile('commandlinetools-linux-11076708_latest.zip', 'r') as z:
    z.extractall('.')
"

# Rearrange to expected directory structure
mkdir -p cmdline-tools/latest
mv cmdline-tools/bin cmdline-tools/lib cmdline-tools/latest/ 2>/dev/null || true
```

#### 2.3 Android SDK Components

```bash
export ANDROID_HOME=/usr/local/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

# Accept all licenses non-interactively
yes | sdkmanager --licenses 2>/dev/null || true

# Install required SDK components
sdkmanager "platform-tools" "build-tools;36.0.0" "platforms;android-36"
```

#### 2.4 Environment Variables

Set these before every build (and add to `~/.bashrc` for persistence):

```bash
export ANDROID_HOME=/usr/local/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64  # Adjust path based on installed version
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
```

IMPORTANT: Verify `JAVA_HOME` points to an actual directory. Use `readlink -f $(which java)` to find the correct path if the default does not exist.

### 3. Project Setup (One-time per project)

Skip steps that are already done based on the assessment in Step 1.

#### 3.1 Create React Project (only if no project exists)

```bash
npm create vite@latest my-app -- --template react
cd my-app
npm install
```

#### 3.2 Configure Vite for Android

Ensure `vite.config.js` has `base: './'` — this is critical for assets to load correctly inside the Android WebView:

```javascript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: 'dist',
    assetsDir: 'assets'
  }
})
```

IMPORTANT: If `base: './'` is missing, the app will show a blank screen or "LOADING..." on Android. Always verify this setting.

#### 3.3 Add Capacitor

```bash
npm install @capacitor/core @capacitor/android @capacitor/preferences

# Initialize Capacitor (adjust app name and ID)
npx cap init "AppName" "com.example.appname" --web-dir dist
```

Use the actual project name and a proper reverse-domain app ID. Ask the user if unsure.

#### 3.4 Add Android Platform

```bash
npx cap add android
```

#### 3.5 Create local.properties

Create `android/local.properties` to point to the SDK:

```
sdk.dir=/usr/local/android-sdk
```

This prevents "SDK location not found" errors during Gradle builds.

### 4. Build APK

#### 4.1 Install Dependencies

```bash
npm install
```

#### 4.2 Build Web Assets

```bash
npm run build
```

Verify `dist/` (or configured output directory) contains `index.html` and asset files.

#### 4.3 Sync to Android

```bash
npx cap sync android
```

This copies the built web assets into `android/app/src/main/assets/public/` and syncs native plugins.

#### 4.4 Update Version (if requested)

Edit `android/app/build.gradle` — increment `versionCode` (integer, must increase for updates) and `versionName` (display string):

```gradle
android {
    defaultConfig {
        versionCode 2
        versionName "1.1"
    }
}
```

#### 4.5 Compile APK

```bash
cd android
chmod +x gradlew
./gradlew clean assembleDebug --no-daemon
```

The `--no-daemon` flag prevents background Gradle processes in CI/container environments.

#### 4.6 Locate and Copy APK

```bash
# APK is at:
# android/app/build/outputs/apk/debug/app-debug.apk

# Copy with a descriptive name
cp android/app/build/outputs/apk/debug/app-debug.apk ./AppName-v1.0.apk
```

### 5. Validate Build

IMPORTANT: Always validate before declaring success.

- Verify the APK file exists and has a reasonable size (typically 2-10MB)
- Check for build warnings or errors in the Gradle output
- If possible, list the APK contents to confirm web assets are bundled:

```bash
# Check APK size
ls -lh android/app/build/outputs/apk/debug/app-debug.apk

# Optionally inspect contents
python3 -c "
import zipfile
with zipfile.ZipFile('android/app/build/outputs/apk/debug/app-debug.apk', 'r') as z:
    assets = [f for f in z.namelist() if f.startswith('assets/public/')]
    print(f'Web assets found: {len(assets)} files')
    for a in assets[:10]:
        print(f'  {a}')
    if len(assets) > 10:
        print(f'  ... and {len(assets) - 10} more')
"
```

Confirm that `assets/public/index.html` exists inside the APK.

### 6. Commit and Push

Commit the changes and push to the working branch.

Do NOT commit:
- `node_modules/`
- `android/app/build/`
- `.apk` files (unless user explicitly wants them in the repo)
- `android/local.properties` (contains machine-specific paths)

Ensure `.gitignore` excludes these paths.

## Troubleshooting

If the build fails, check these common issues in order:

### "JAVA_HOME is not set" or wrong Java version
```bash
# Find Java installation
readlink -f $(which java)
# Set JAVA_HOME to the directory two levels up from the java binary
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

### "SDK location not found"
Create or fix `android/local.properties`:
```
sdk.dir=/usr/local/android-sdk
```

### "Could not resolve project :capacitor-android"
Capacitor native dependencies may not have synced properly:
```bash
npx cap sync android
```

### Gradle build fails with dependency resolution errors
```bash
cd android
./gradlew clean --no-daemon
./gradlew assembleDebug --no-daemon --refresh-dependencies
```

### App shows blank screen or "LOADING..." on device
1. Confirm `base: './'` is set in `vite.config.js`
2. Confirm `dist/index.html` exists and references assets with relative paths
3. Confirm `npx cap sync android` was run after `npm run build`
4. Check that `android/app/src/main/assets/public/index.html` exists

### Data not persisting across app restarts
Use Capacitor Preferences API instead of localStorage:
```javascript
import { Preferences } from '@capacitor/preferences';

// Save
await Preferences.set({ key: 'data', value: JSON.stringify(data) });

// Load
const { value } = await Preferences.get({ key: 'data' });
const data = value ? JSON.parse(value) : null;
```

## Expected Project Structure

For reference, a fully configured project should look like:

```
project/
├── src/                    # React source code
├── android/                # Android project (generated by Capacitor)
│   ├── app/
│   │   ├── src/main/assets/public/   # Synced web assets
│   │   └── build.gradle              # Version config
│   ├── gradlew
│   └── local.properties              # SDK path
├── dist/                   # Built web assets
├── package.json
├── vite.config.js
└── capacitor.config.json
```

## Wrap Up

Provide a summary to the user with:

* What was set up or changed
* Build result
  - APK file location and size
  - Version info (versionCode / versionName)
* Validation results
  - Web assets confirmed bundled in APK (yes/no)
  - Any warnings from the build
* Installation instructions for the user:
  1. Transfer APK to Android device
  2. Open the file on the device
  3. Allow "Install from Unknown Sources" if prompted
  4. Tap Install
* If this is an update: remind user to uninstall the previous version first if the signing key differs (debug builds use a per-machine key)
