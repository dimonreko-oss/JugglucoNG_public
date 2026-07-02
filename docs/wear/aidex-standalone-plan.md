# Standalone AiDex on the WearOS watch — implementation plan

Branch: `dev-watch`. Target device: Galaxy Watch 8 (Wear OS 5/6, arm64-v8a).

## Goal and hard constraint

Run an **AiDex/iCan** sensor **directly on the watch** over BLE, so the watch shows
glucose without depending on the phone.

**Auto‑failover (phone → watch on signal loss) is NOT possible for AiDex** and is
out of scope: AiDex binds to a single device at the sensor level (Android bond +
session key). Releasing it is a command (`Natives.aidexXunpair`) that only the
current owner can send. If the phone is gone it cannot release the sensor, so the
watch cannot take it over. The only viable model for AiDex is **standalone**: the
sensor is paired to the watch (and, while paired to the watch, not to the phone).

(Failover *is* feasible for **Sibionics**, which has no bonding — that is a
separate future feature, see "Sibionics failover" below.)

## Current state (already done on `dev-watch`)

- The `wear` variant compiles and produces a full APK
  (`assembleWearLibre3SiDexGoogleDebug` → `Common-wear-libre3-si-dex-google-debug.apk`,
  `libg.so` for all ABIs incl. arm64‑v8a). See commits "wear: fix … compilation"
  and "wear: fix native link errors …".
- The **native AiDex driver already compiles for wear**: everything under
  `Common/src/main/java/tk/glucodata/drivers/aidex/` (incl. `native/ble/AiDexBleManager.kt`,
  `native/crypto`, `native/protocol`) is in the shared `main` source set and needs
  no vendor `.so` (native mode is the default). The `Ascon` classes in `mobile`
  are unused by this driver — not a blocker.
- The **entry point is shared**: `SensorBluetooth.addAiDexSensor(Context, name, address)`
  (`Common/src/main/java/tk/glucodata/SensorBluetooth.java`) persists the sensor and
  starts the driver via `AiDexNativeFactory.createBleManager(...)`. It is callable
  from wear.
- A **scaffold** exists: `Common/src/wear/java/tk/glucodata/aidexwear/AiDexWearPairing.kt`
  (BLE scan → detect → `addAiDexSensor`), plus the shared, unit‑tested detection
  core `Common/src/main/java/tk/glucodata/drivers/aidex/AiDexScanDetection.kt`
  (11 passing JVM tests). Compiles into the wear APK; **not yet wired to UI, not run
  on hardware.**

So the driver, crypto, protocol and pairing entry point are in place. What remains
is watch‑side wiring and on‑device validation.

## Remaining work (wiring)

### 1. Watch UI entry point
Hook `AiDexWearPairing` into the watch menu.
- File: `Common/src/wear/java/tk/glucodata/Menus.java` (`show(Object act)`), and/or the
  sensor screen. Add an "Add AiDex sensor" item.
- On tap: request permissions (below) → `AiDexWearPairing.startScan(context) { candidate -> … }`
  → show discovered candidates (rotary/scrolling list; Galaxy Watch 8 is round) →
  on selection `AiDexWearPairing.pair(context, candidate)` → `stopScan()`.
- A minimal list UI is enough (reuse existing wear list styling, e.g. the pattern
  used by `MeterList`/`Menus`).

### 2. Runtime permissions
- Verify the merged manifest declares `BLUETOOTH_SCAN` (with
  `usesPermissionFlags="neverForLocation"` if we never derive location),
  `BLUETOOTH_CONNECT`, and (for < API 31) `ACCESS_FINE_LOCATION`. The wear app
  already does BLE (Sibionics), so most should be present — confirm in
  `Common/src/wear/AndroidManifest.xml` and the main manifest it merges.
- Request them at runtime before scanning. `AiDexWearPairing.requiredScanPermissions()`
  / `hasScanPermissions()` already encapsulate the list; wire an
  `ActivityResultLauncher` (or the existing wear permission flow) to request.

### 3. Background execution / foreground service
- BLE must survive screen‑off / doze on the watch. Confirm the existing
  keep‑running/foreground service used by the wear app covers the AiDex driver
  connection (the driver runs under the shared BLE machinery, so if Sibionics stays
  connected in background today, AiDex should too — verify on device).
- Check battery‑optimization exemption prompts on Wear OS.

### 4. Persistence and auto‑reconnect
- `addAiDexSensor` stores the sensor in the `aidex_sensors` pref. Confirm the wear
  app **re‑creates the driver on boot / app start** from that pref (the phone does
  this via `SensorBluetooth` startup; ensure the same path runs on wear).
- Validate `AiDexBleManager`'s reconnect policy (`native/ble/AiDexReconnect.kt`,
  `AiDexRuntimePolicy.kt`) on the watch (reconnect after out‑of‑range, after reboot).

### 5. Pairing / bonding UX (important)
- First pair triggers the system BLE bond on the watch. Because AiDex is
  **single‑bond**, the sensor must **not currently be bonded to the phone**. Practical
  flow for the user: in the phone JugglucoNG, release/clear the AiDex first
  (the phone's unpair), then pair it to the watch. Document this in the UI copy.
- Handle bond failure/retry (the driver already has `removeBondSafely` via reflection).

### 6. Display
- Glucose rendering on the watch already exists (wear UI + complications consume
  the native store `Natives.*`). Once the driver feeds readings in, the existing
  watch face complications and app screens should show them — verify.

## Sibionics failover (separate, future)
If a Sibionics sensor is added later, its lack of bonding makes a
staleness‑triggered watch takeover feasible. That would introduce a small pure
"ownership controller" (watch watches mirror‑data freshness; if stale > N minutes
and no phone data, the watch starts its own scan/connect; yields when the phone
returns). Unit‑testable, and independent of the AiDex work here. Not needed for
AiDex.

## Risks / unknowns (resolve on hardware)
- Background BLE reliability and battery on Wear OS 6 / One UI Watch.
- Whether the watch's own Bluetooth can pair the AiDex cleanly while it is released
  from the phone.
- Foreground‑service and permission prompts differences on Galaxy Watch 8.
- 16 KB page size / precompiled `.so` behavior on the watch OS (already flagged in
  the repo `BUGS`).

## Definition of done
Watch scans, pairs, and shows live AiDex glucose standalone (phone off/away),
reconnects after out‑of‑range and after reboot, survives screen‑off, for a full
sensor session — validated per `hardware-test-checklist.md`.
