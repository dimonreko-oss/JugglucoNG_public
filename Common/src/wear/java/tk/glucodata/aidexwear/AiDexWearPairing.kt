package tk.glucodata.aidexwear

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import tk.glucodata.Log
import tk.glucodata.SensorBluetooth
import tk.glucodata.drivers.aidex.AiDexScanDetection

/**
 * SCAFFOLD — standalone AiDex pairing entry point for the WearOS app.
 *
 * The phone pairs AiDex through a Compose wizard (mobile-only). This is the
 * watch-side equivalent, reduced to the parts that are actually watch-specific:
 * a BLE scan, AiDex detection (shared [AiDexScanDetection]), and handing the
 * chosen sensor to the shared [SensorBluetooth.addAiDexSensor], which starts the
 * native AiDex driver that already compiles for wear.
 *
 * Status: compiles into the wear APK but is NOT yet wired into the watch UI and
 * has NOT been run on hardware (no watch available). It exists so the on-device
 * work is a matter of hooking [startScan]/[pair] into a menu entry and testing.
 * See the wear standalone-AiDex implementation plan for the wiring points.
 */
object AiDexWearPairing {

    private const val LOG_ID = "AiDexWearPairing"

    /** A discovered AiDex candidate ready to be paired. */
    data class Candidate(
        val address: String,
        val name: String,
        val serial: String,
        val likelyAiDex: Boolean,
    )

    fun requiredScanPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 31 -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasScanPermissions(context: Context): Boolean =
        requiredScanPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    @Volatile
    private var scanner: BluetoothLeScanner? = null

    @Volatile
    private var callback: ScanCallback? = null

    /**
     * Start scanning for AiDex sensors. [onCandidate] is invoked (on a binder
     * thread) for each newly seen likely-AiDex device. Returns false if scanning
     * could not start (no permission / Bluetooth off / no adapter).
     */
    @SuppressLint("MissingPermission")
    fun startScan(context: Context, onCandidate: (Candidate) -> Unit): Boolean {
        if (!hasScanPermissions(context)) {
            Log.w(LOG_ID, "startScan: missing BLE scan permissions")
            return false
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val leScanner = manager?.adapter?.bluetoothLeScanner
        if (leScanner == null) {
            Log.w(LOG_ID, "startScan: no BluetoothLeScanner (adapter off?)")
            return false
        }
        stopScan()
        val seen = HashSet<String>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = try {
                    result.device.address
                } catch (_: SecurityException) {
                    null
                } ?: return
                val record = result.scanRecord
                val detection = AiDexScanDetection.detect(
                    address = address,
                    deviceName = try {
                        result.device.name
                    } catch (_: SecurityException) {
                        null
                    },
                    scanRecordName = record?.deviceName,
                    scanRecordBytes = record?.bytes,
                    advertisedServiceUuids = record?.serviceUuids?.map { it.uuid },
                )
                if (!detection.isLikelyAiDex) return
                if (seen.add(address)) {
                    onCandidate(
                        Candidate(
                            address = address,
                            name = detection.displayName,
                            serial = detection.selectionName,
                            likelyAiDex = detection.isLikelyAiDex,
                        )
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(LOG_ID, "onScanFailed: $errorCode")
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        return try {
            leScanner.startScan(null, settings, cb)
            scanner = leScanner
            callback = cb
            true
        } catch (t: Throwable) {
            Log.stack(LOG_ID, "startScan failed", t)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val s = scanner
        val cb = callback
        if (s != null && cb != null) {
            try {
                s.stopScan(cb)
            } catch (t: Throwable) {
                Log.stack(LOG_ID, "stopScan failed", t)
            }
        }
        scanner = null
        callback = null
    }

    /**
     * Register the chosen candidate as the active AiDex sensor and start the
     * (shared) native driver. Delegates to the same entry point the phone uses.
     */
    fun pair(context: Context, candidate: Candidate) {
        stopScan()
        Log.i(LOG_ID, "pair ${candidate.serial} (${candidate.address})")
        SensorBluetooth.addAiDexSensor(context, candidate.serial, candidate.address)
    }
}
