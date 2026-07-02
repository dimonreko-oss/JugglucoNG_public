# Make the WearOS variant buildable again + AiDex-on-watch groundwork

## Summary
The `wear` variant of JugglucoNG no longer compiled — shared `main` code had
drifted to depend on mobile-only classes/signatures while development targeted
the phone. This branch repairs the wear build end-to-end (Java/Kotlin **and**
native), then lays verifiable groundwork for running an **AiDex** sensor
standalone on the watch. No device was available, so everything here is
**build- and unit-test-verified only** — no on-hardware runtime validation.

## What changed

### 1. Wear variant compiles again
- **Java/Kotlin** (`main`↔`wear` divergence): removed duplicate `wear/Flash.java`;
  added wear stubs for `receivers.AlarmActionReceiver` / `AlarmLaunchReceiver`
  (constants used by `Notify`) and `service.FloatingGlucoseService` (phone-only);
  added missing broadcast overloads to wear `XInfuus`/`EverSense`/`Gadgetbridge`
  (dead code under `!isWearable`); added `wear/res/values/ids.xml` (`reconnect`/
  `finish`).
- **Native** (`libg.so` link under `-DWEAROS`): guarded the `makeICE*/makeHome*`
  backup JNI wrappers in `backupjava.cpp` (return -1 on wear, mirroring the
  existing `makeHomeSender` guard); `uploader.cpp` now includes `datestring.hpp`
  for the inline `sha1encode`. These two files are shared C++, but the guards are
  wear-only — **mobile behaviour is unchanged**.
- Result: `assembleWearLibre3SiDexGoogleDebug` produces a full APK with `libg.so`
  for all 4 ABIs (incl. arm64-v8a).

### 2. AiDex standalone-on-watch groundwork
- `AiDexScanDetection` (in `main`): pure, Android-free AiDex scan detection lifted
  from the mobile wizard; **single source of truth** for phone + watch.
- `AiDexSetupWizard` (mobile) migrated onto it (~127 duplicated lines removed, no
  behaviour change).
- `AiDexWearPairing` (wear): scaffold entry point — BLE scan → detection →
  `SensorBluetooth.addAiDexSensor` (same shared entry the phone uses). Compiles
  into the APK; **not yet wired to UI / not run on hardware.**

### 3. Tests + CI
- `AiDexScanDetectionTests` (21) and `AiDexCryptoTests` (11) — **32 pure JVM tests**
  (detection, CRC-16/CCITT-FALSE, CRC-8/MAXIM, AES-128-CFB incl. bond-data
  recovery). All green.
- `.github/workflows/wear-build-check.yml`: compiles the wear variant (Java+Kotlin,
  no NDK) + runs the AiDex tests on push/PR touching `main`/`wear`, to catch
  `main`↔`wear` divergence in CI.

### 4. Docs
- `docs/wear/aidex-standalone-plan.md` — remaining wiring steps.
- `docs/wear/hardware-test-checklist.md` — on-device test plan for GW8 + AiDex.

## Verification
- `assembleWearLibre3SiDexGoogleDebug` ✅ (full APK, arm64-v8a)
- `assembleMobileLibre3SiDexGoogleDebug` ✅ (mobile unaffected)
- `testMobileLibre3SiDexGoogleDebugUnitTest --tests "tk.glucodata.drivers.aidex.*"` ✅ (32/32)

## Not covered (needs hardware)
- Wiring `AiDexWearPairing` into the watch menu, foreground service, runtime
  permissions, end-to-end pair/read on a Galaxy Watch 8.
- Wear alarm snooze/dismiss routing (receivers are stubs for now).
- **AiDex auto-failover is intentionally out of scope** — impossible by protocol
  (single-device bond); only standalone is viable. See the plan doc.

## Review notes
- Behaviour-affecting review should focus on the two shared C++ files
  (`backupjava.cpp`, `uploader.cpp`) — everything else is wear-only or additive.
- The mobile change is a pure refactor (dedup onto `AiDexScanDetection`).
