# Hardware test checklist — standalone AiDex on Galaxy Watch 8

Run this when a Galaxy Watch 8 is available. It assumes the wiring from
`aidex-standalone-plan.md` (items 1–6) is in place. The current `dev-watch` build
compiles the pieces but is not yet UI‑wired, so some steps need that wiring first.

## 0. Preconditions
- [ ] Galaxy Watch 8, Wear OS updated, developer options + ADB over Wi‑Fi enabled.
- [ ] One AiDex/iCan sensor (worn or on a test rig).
- [ ] **Release the AiDex from the phone first** (phone JugglucoNG → unpair/clear the
      sensor). AiDex is single‑bond; the watch cannot pair while the phone holds it.
- [ ] Note: use a **spare/expendable sensor** if possible — re‑pairing consumes a
      pairing cycle and you may not be able to move it back cleanly mid‑session.

## 1. Build & install
- [ ] `./gradlew :Common:assembleWearLibre3SiDexGoogleDebug`
- [ ] `adb -s <watch> install -r Common/build/outputs/apk/wearLibre3SiDexGoogle/debug/Common-wear-libre3-si-dex-google-debug.apk`
- [ ] App launches on the watch without crash.
- [ ] Start logcat: `adb -s <watch> logcat -v time AiDexWearPairing:* AiDexBleManager:* SensorBluetooth:* AiDexReconnect:* *:S`

## 2. Permissions
- [ ] Menu → "Add AiDex sensor" requests BLUETOOTH_SCAN / BLUETOOTH_CONNECT
      (and location on older APIs).
- [ ] Granting proceeds to scan; denying shows a clear message and does not crash.

## 3. Scan & detect
- [ ] Scan lists the AiDex within ~10 s; the entry shows the `X-…` serial.
- [ ] Non‑AiDex BLE devices are filtered out (unless a "show all" toggle is added).

## 4. Pair & first reading
- [ ] Selecting the sensor triggers `addAiDexSensor` (see log) and the system BLE
      bond prompt if required; bond completes.
- [ ] Handshake/session‑key exchange succeeds (AiDexBleManager logs).
- [ ] First live glucose value appears on the watch within the expected interval.
- [ ] History backfill (if applicable) populates without blocking live readings.

## 5. Display
- [ ] Value shows on the app screen and on watch‑face complications.
- [ ] Trend/arrow updates over subsequent readings.

## 6. Standalone (phone absent)
- [ ] Turn the phone off / out of range. Watch keeps receiving readings directly.
- [ ] No dependency on the phone/Data Layer for live values.

## 7. Reconnection & persistence
- [ ] Walk out of range, then back: watch reconnects automatically (AiDexReconnect).
- [ ] Reboot the watch: after boot the driver re‑creates from the stored
      `aidex_sensors` pref and reconnects without re‑pairing.
- [ ] Screen off for 30+ min (doze): readings continue (foreground service alive).

## 8. Alarms (if wired)
- [ ] Low/high alerts fire on the watch from watch‑sourced readings.
- [ ] Snooze/dismiss actions behave (note: currently stubbed on wear — expect this
      to be a no‑op until the receiver routing is implemented).

## 9. Battery
- [ ] Record battery drain over a few hours of standalone operation; note whether a
      battery‑optimization exemption is needed.

## 10. Rollback
- [ ] `adb uninstall tk.glucodata.ng.debug` (the test build; side‑by‑side with the
      release, does not touch it).
- [ ] Re‑pair the AiDex to the phone JugglucoNG if you want the phone setup back.

## Data to capture for each run
- logcat with the filters above, the sensor serial, timestamps of pair/first
  reading/reconnect, and any crash stacks. Attach to the `dev-watch` notes.
