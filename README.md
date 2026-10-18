# GAC1_yfac_controller — Standalone YF AC Controller

> **A brand-new Android app (24 KB) for managing AC, Adigo smart parking, and seat AC on a YF Tech automotive head unit.** Built from scratch — no shared code with the platform's `com.yftech.settings`, no `sharedUserId`, installable as a separate app.

## What this is

A minimal Android app (package `com.zai.yfac`) that wraps three subsystems of the YF Tech vehicle SDK via **reflection** — no compile-time dependency on the platform SDK. The app calls into the device's shared libraries (`com.yftech.vehicle.sdk`, `com.yftech.vehicle.internal.adapter`, `com.yftech.car.framework`) at runtime through `<uses-library>` declarations.

Three tabs:

| Tab | Fragment | SDK manager | Wrapped API |
|---|---|---|---|
| **AC** | `AcFragment` | `com.yftech.vehicle.sdk.AirConditionManager` | `setAirSelfDryingEnabled(Z)`, `setAirCirculationModeOfAutoMode(...)`, `setAirUnlockVentilationEnabled(Z)`, `setComfortCurveMode(...)`, `setFanSpeedOfAutoMode(...)` |
| **Parking** | `ParkingFragment` | `com.yftech.vehicle.sdk.CarParkingManager` | `setParkingVoiceSwitchEnabled(Z)`, `setParkingRadarAlarmEnabled(Z)`, `performParkingRadarAlarmButtonClick()` |
| **Seat AC** | `SeatAcFragment` | `com.yftech.settings.manager.CarPartsManager` + `AirConditionManager` | `setSeatDriverConvenientAccessEnabled(Z)` (confirmed) + a "probe seat APIs" button that dumps every `seat/vent/heat/massage` method on the manager so you can extend the app |

## Why reflection (and why 24 KB)

The platform's vehicle SDK classes live in shared libraries on the device — not packaged inside the platform APKs themselves. So any 3rd-party app that wants to call them has two paths:

1. **Compile against the SDK jar** — requires getting the SDK jar (often impossible without an NDA with YF Tech)
2. **Compile against stub classes + use `<uses-library>` + reflection** — what this app does

The app's `YfVehicle` helper does `Class.forName("com.yftech.vehicle.sdk.AirConditionManager")` + `m.invoke(...)` at runtime. No SDK jar needed at build time.

Because of this, the entire app — 5 Java source files, no resources, no native libs — compiles to **24 KB**.

## Install

```bash
adb install -r yfac.apk
# → Success
adb shell pm list packages | grep yfac
# → package:com.zai.yfac
```

Your launcher will show a new "YF AC Controller" icon next to the original Settings.

Open it. You'll see three tabs at the top. Each tab tries to load its respective SDK manager at runtime; if the manager is loaded successfully, the controls are interactive; if not (e.g. running on a non-YF-Tech device), an inline error explains why.

A status bar at the bottom of the screen shows the result of every SDK call (`FanSpeed=NORMAL → OK`, `Parking Voice Switch: FAIL (want=true)`, etc.) so you can immediately see what's working.

## How it was built

This app was built without gradle / Android Studio — using only the raw Android SDK command-line tools. The build pipeline is in [`build.sh`](build.sh):

```bash
# 1. javac against android.jar (platform 28)
javac -source 1.8 -target 1.8 \
  -bootclasspath $ANDROID_HOME/platforms/android-28/android.jar \
  -classpath  $ANDROID_HOME/platforms/android-28/android.jar \
  -d build/classes \
  src/MainActivity.java src/YfVehicle.java \
  src/AcFragment.java src/ParkingFragment.java src/SeatAcFragment.java

# 2. d8 to DEX (min SDK 26)
d8 --release --min-api 26 \
   --lib $ANDROID_HOME/platforms/android-28/android.jar \
   --output build/obj $(find build/classes -name "*.class")

# 3. aapt2 link (manifest → APK skeleton + resources.arsc)
aapt2 link -I $ANDROID_HOME/platforms/android-28/android.jar \
  --manifest AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 28 \
  --version-code 1 --version-name 1.0 \
  -o build/apk/yfac.base.apk

# 4. merge classes.dex into the APK
cp build/apk/yfac.base.apk build/apk/yfac.unsigned.apk
cd build/obj && zip -j ../yfac.unsigned.apk classes.dex

# 5. zipalign + sign with debug keystore
zipalign -p -f -v 4 build/apk/yfac.unsigned.apk build/apk/yfac.aligned.apk
apksigner sign --ks yftech-debug.keystore --ks-key-alias yftech-debug \
  --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --in build/apk/yfac.aligned.apk --out build/apk/yfac.apk
```

## What works vs. what's a stub

| Feature | Status | Notes |
|---|---|---|
| App installs cleanly | ✅ | Package `com.zai.yfac`, no `sharedUserId`, debug-signed. Coexists with the original `com.yftech.settings`. |
| App launches, UI shows 3 tabs | ✅ | Pure programmatic UI — no XML layouts, no resources. |
| AC tab — toggles for self-drying / unlock ventilation | ✅ UI / ⚠️ functional | UI loads and toggles; the underlying `setAirSelfDryingEnabled` call may be denied if the device's signature-protected permission (`com.yftech.permission.service.main.audio`) is not granted. The status bar will show `FAIL` in that case. |
| AC tab — Fan Speed / Comfort Curve / Air Circulation radio buttons | ✅ UI / ⚠️ functional | Calls `setFanSpeedOfAutoMode`, `setComfortCurveMode`, `setAirCirculationModeOfAutoMode` with enum values resolved by name. If an enum name doesn't match (e.g. `AirCirculationMode.INTERNAL` vs `INSIDE`), the button shows "not found" — try the alternative names already in the UI. |
| Parking tab — Voice Switch / Radar Alarm toggles | ✅ UI / ⚠️ functional | Calls `setParkingVoiceSwitchEnabled(Z)` and `setParkingRadarAlarmEnabled(Z)` (the latter is presumed from the get/set pattern; the SDK has `getParkingRadarAlarmEnableState` confirmed). |
| Parking tab — Trigger Radar Alarm button | ✅ UI / ⚠️ functional | Calls `performParkingRadarAlarmButtonClick()` — confirmed in the SDK. |
| Parking tab — "List SDK methods" probe button | ✅ | Dumps every `get/set/is/perform/register/unregister` method on the manager to the status bar — for further hacking. |
| Seat AC tab — Seat Driver Convenient Access toggle | ✅ UI / ⚠️ functional | The setter is private on `CarPartsManager` — `YfVehicle.invokePrivate()` uses `setAccessible(true)` to reach it. |
| Seat AC tab — "Probe seat APIs" button | ✅ | Dumps every `seat/vent/heat/massage` method on the managers — for discovering seat heating/ventilation methods to extend the app. |

## Why some calls may FAIL at runtime

The original `com.yftech.settings` APK runs as the **system UID** (`android:sharedUserId="android.uid.system"`, platform-signed) and is granted the YF Tech signature-protected permissions automatically. This app runs as a normal UID and is **NOT** granted those permissions.

Permissions the app would need for full functionality:

- `com.yftech.permission.service.main.audio` (signature-protected) — to talk to the system bus
- `com.yftech.permission.service.bluetooth` — Bluetooth subsystem
- `com.yftech.permission.service.vehicle.COMMON` — vehicle data
- ...and others

Three paths to bypass this (same as documented in the unhidden-settings repo's README §4):

1. **Frida runtime hook** — `frida -U -n com.zai.yfac -l bypass_perms.js` to make `checkSelfPermission()` always return `GRANTED`
2. **Magisk signature spoofing** — spoof the app's signing identity as the platform key
3. **Re-sign with the YF Tech platform private key** — keeps the package name `com.zai.yfac` but grants signature-protected perms

## Repository contents

| File | What |
|---|---|
| `yfac.apk` | **The signed APK. Install this.** |
| `yfac.unsigned.apk` | Unsigned APK for re-signing with the platform key. |
| `yftech-debug.keystore` | Debug keystore (password `android`). Rotate before production. |
| `src/` | 5 Java source files (MainActivity, YfVehicle, AcFragment, ParkingFragment, SeatAcFragment). |
| `AndroidManifest.xml` | The manifest — 1 activity, 3 `<uses-library>` declarations, no `sharedUserId`. |
| `build.sh` | The complete build script (javac + d8 + aapt2 + zipalign + apksigner). |
| `README.md` | This file. |

## Extending the app

To add new features:

1. Open the installed app, go to the Parking or Seat AC tab.
2. Tap the "Probe / List SDK methods" button — the status bar shows every public method on the manager.
3. Identify the method you want (e.g. `setSeatHeatingLevel(int)`).
4. Add a new control in the appropriate Fragment that calls `YfVehicle.invoke(mgr, "setSeatHeatingLevel", new Class[]{int.class}, new Object[]{2})`.
5. Rebuild with `bash build.sh`.

Because everything goes through the reflection wrapper, you don't need any SDK JAR — just the method name and signature.

## Disclaimer

- This app calls into the YF Tech vehicle SDK via reflection. If a method name is wrong (the platform renames it between versions), the call returns `null` and shows an error in the status bar; the app does not crash.
- The app is debug-signed. **Rotate the debug keystore before deploying to a production device.**
- Do not install on hardware you do not own. All YF Tech trademarks and IP belong to YF Tech / 御驾 (YF) Automotive.
- If you reached this repo via the originating chat session, **the GitHub Personal Access Token used to push these repos has been exposed in chat history — rotate it now** at https://github.com/settings/tokens.
