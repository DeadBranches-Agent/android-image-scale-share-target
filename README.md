# Image Scale — multi-target share sheet image scaler for Android

Share one or many images to a **share target you configured**, and the app
scales them and writes JPGs into that target's output folder. No dialogs on
the share path — each target is a single action with its own settings, name,
and icon in the Android share sheet.

## Features

- **Multiple share targets** (Android Sharing Shortcuts): each appears in the
  share sheet with its own label and icon (chosen from ~1100 bundled
  [Tabler](https://tabler.io/icons) icons, searchable by name/tags).
- Per-target settings: **JPG quality** (1–100), **scale factor** (10–100 %),
  **discard metadata** (off by default — EXIF including orientation is
  preserved; when on, rotation is baked into the pixels and no EXIF is
  written), **output folder** (Storage Access Framework — no storage
  permissions).
- **Hide/unhide** removes a target from the share sheet without deleting its
  configuration; **delete** removes it entirely.
- Sharing to the generic app entry uses the only visible target, or shows a
  mini picker when several exist.
- Conversion runs in a **foreground service** with a notification showing the
  current filename, a per-file progress bar, an overall `iC/nT` bar, and
  **Pause/Stop** buttons. Stop (or swiping the notification away) asks
  *"Really cancel image conversion?"* with an optional
  *"Also delete images already converted this session"*.
- **Export/Import settings** as JSON via the system file picker (v1
  single-target files are migrated on import; output-folder permissions
  cannot travel between devices and are re-validated).
- **Debug logging**: *Start Logging* creates a `.jsonl` file via the file
  picker; every settings change, share, processed image, pause/resume/cancel,
  and error is appended as one JSON line until *Stop Logging*.

## Building

Requirements: JDK 17+ and an Android SDK with platform 36.

```bash
# Bootstrap an SDK if you don't have one:
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools
curl -Lo /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q /tmp/clt.zip -d $ANDROID_HOME/cmdline-tools
mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM unit tests (no emulator needed)
./gradlew lintDebug
```

Stack: Kotlin 2.2, Jetpack Compose (Material 3), AGP 8.13, compileSdk/targetSdk 36,
minSdk 26, DataStore, kotlinx.serialization, androidx ExifInterface/DocumentFile.

### Targeting Android 17 (API 37) later

The API 37 SDK is not stable yet. When it is: set `compileSdk = 37` and
`targetSdk = 37` in `app/build.gradle.kts`, install `platforms;android-37`,
and bump AGP to the first version supporting API 37. No code here uses APIs
newer than 26, so no source changes are expected.

## Regenerating the bundled icons

```bash
node tools/generate-icons.mjs        # fetches @tabler/icons from npm
```

The script converts a curated category subset of Tabler outline icons to
vector drawables, and regenerates `assets/tabler_tags.json` (search metadata)
and `IconIndex.kt`. Output is committed, so normal builds never need npm.
Tabler icons are MIT-licensed (see `tools/TABLER_ICONS_LICENSE`); brand logos
are excluded.

## Known caveats

- The share sheet caps how many app shortcuts it shows (typically 15,
  launcher-dependent). Extra visible targets keep working via the app row's
  mini picker.
- Writing EXIF to the output needs a seekable `rw` file descriptor; the rare
  providers that can't offer one produce a valid JPG without metadata (an
  `error` line is logged).
- The `.jsonl` debug log is appended via SAF `wa` streams; if the provider
  revokes access or doesn't support append, logging turns itself off with a
  toast.
- Per-file progress is stage-based (decode → scale → write), not byte-exact.
- On Android 15+, foreground services of this type have a ~6 h runtime cap —
  far beyond any realistic batch.
- If notifications are denied, the job still runs; there is just no progress
  UI (a toast reports the result).
