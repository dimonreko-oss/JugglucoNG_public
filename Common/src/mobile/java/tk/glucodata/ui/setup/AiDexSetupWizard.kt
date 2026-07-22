package tk.glucodata.ui.setup

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tk.glucodata.Log
import tk.glucodata.R
import tk.glucodata.SensorBluetooth
import tk.glucodata.drivers.aidex.AiDexScanDetection
import tk.glucodata.ui.util.BleDeviceScanner
import tk.glucodata.ui.util.rememberBleScanner
import java.util.UUID

enum class AiDexSetupStep {
    SCAN,
    CONNECTING,
    SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDexSetupWizard(
    onDismiss: () -> Unit,
    onNavigateToReadiness: () -> Unit = {},
    onComplete: () -> Unit
) {
    val tag = "AiDexSetupWizard"
    val ui = rememberWizardUiMetrics()
    var currentStep by remember { mutableStateOf(AiDexSetupStep.SCAN) }
    var selectedDeviceName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    BackHandler {
        if (currentStep == AiDexSetupStep.SCAN) onDismiss() else currentStep = AiDexSetupStep.SCAN
    }

    LaunchedEffect(currentStep) {
        if (currentStep == AiDexSetupStep.SUCCESS) {
            delay(SENSOR_SETUP_SUCCESS_AUTO_ADVANCE_MS)
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aidex_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier.padding(padding),
            label = "AiDexWizard"
        ) { step ->
            when (step) {
                AiDexSetupStep.SCAN -> AiDexScanStep(
                    ui = ui,
                    onNavigateToReadiness = onNavigateToReadiness,
                    onDeviceSelected = { selectedName, address ->
                        try {
                            val name = selectedName.trim()
                            if (name.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.aidex_parse_error, selectedName),
                                    Toast.LENGTH_LONG
                                ).show()
                                return@AiDexScanStep
                            }

                            selectedDeviceName = name
                            currentStep = AiDexSetupStep.CONNECTING

                            // Initiate Connection Logic
                            scope.launch {
                                try {
                                    // 1. Add to Persistence & SensorBluetooth
                                    SensorBluetooth.addAiDexSensor(context, name, address)

                                    // 2. Wait a bit then show success
                                    kotlinx.coroutines.delay(2000)
                                    currentStep = AiDexSetupStep.SUCCESS
                                } catch (t: Throwable) {
                                    Log.e(tag, "Failed to add/select AiDex sensor: ${t.message}")
                                    Toast.makeText(context, context.getString(R.string.nobluetooth), Toast.LENGTH_LONG).show()
                                    currentStep = AiDexSetupStep.SCAN
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e(tag, "onDeviceSelected failed: ${t.message}")
                            Toast.makeText(context, context.getString(R.string.nobluetooth), Toast.LENGTH_LONG).show()
                            currentStep = AiDexSetupStep.SCAN
                        }
                    }
                )
                AiDexSetupStep.CONNECTING -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    SensorSetupConnectingScreen(
                        ui = ui,
                        sensorLabel = selectedDeviceName.ifBlank { null }
                    )
                }
                AiDexSetupStep.SUCCESS -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    SensorSetupSuccessScreen(
                        ui = ui,
                        sensorLabel = selectedDeviceName.ifBlank { null }
                    )
                }
            }
        }
    }
}

@Composable
fun AiDexScanStep(
    ui: WizardUiMetrics,
    onNavigateToReadiness: () -> Unit,
    onDeviceSelected: (String, String) -> Unit
) {
    data class ScanCandidate(
        val address: String,
        val rawName: String,
        val selectionName: String,
        val serial: String?,
        val isLikelyAiDex: Boolean,
        val detectedViaFf30: Boolean,
    )

    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<ScanCandidate>>(emptyList()) }
    val scanner = rememberBleScanner()
    var scanPermissionGranted by remember { mutableStateOf(hasBleScanPermissions(context)) }
    var bluetoothEnabled by remember { mutableStateOf(scanner.isBluetoothEnabled()) }
    var scanRetryKey by remember { mutableStateOf(0) }
    var scanError by remember { mutableStateOf<BleDeviceScanner.ScanStartError?>(null) }
    var requestedPermissionOnce by remember { mutableStateOf(false) }
    var showAllDevices by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scanPermissionGranted = hasBleScanPermissions(context)
        bluetoothEnabled = scanner.isBluetoothEnabled()
        scanError = null
        scanRetryKey += 1
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        bluetoothEnabled = scanner.isBluetoothEnabled()
        scanError = null
        scanRetryKey += 1
    }

    val requestScanPermission = {
        val required = requiredBleScanPermissions()
        if (required.isEmpty()) {
            scanPermissionGranted = true
            scanRetryKey += 1
        } else {
            permissionLauncher.launch(required)
        }
    }

    LaunchedEffect(Unit) {
        if (!scanPermissionGranted && !requestedPermissionOnce) {
            requestedPermissionOnce = true
            requestScanPermission()
        }
    }

    // Start Scanning Effect
    DisposableEffect(scanPermissionGranted, bluetoothEnabled, scanRetryKey, showAllDevices) {
        if (!scanPermissionGranted || !bluetoothEnabled) {
            scanner.stopScan()
            return@DisposableEffect onDispose { scanner.stopScan() }
        }

        scanner.startScan(
            onResult = { result ->
                val device = result.device
                val address = try {
                    device.address
                } catch (_: SecurityException) {
                    null
                } ?: return@startScan
                val record = result.scanRecord
                val candidate = AiDexScanDetection.detect(
                    address = address,
                    deviceName = try {
                        device.name
                    } catch (_: SecurityException) {
                        null
                    },
                    scanRecordName = record?.deviceName,
                    scanRecordBytes = record?.bytes,
                    advertisedServiceUuids = record?.serviceUuids?.map { it.uuid }
                )

                if (!showAllDevices && !candidate.isLikelyAiDex) return@startScan
                if (devices.none { it.address == address }) {
                    devices = devices + ScanCandidate(
                        address = address,
                        rawName = candidate.displayName,
                        selectionName = candidate.selectionName,
                        serial = candidate.serial,
                        isLikelyAiDex = candidate.isLikelyAiDex,
                        detectedViaFf30 = candidate.detectedViaFf30,
                    )
                }
            },
            onError = { error ->
                scanError = error
                when (error) {
                    BleDeviceScanner.ScanStartError.NoPermission -> scanPermissionGranted = false
                    BleDeviceScanner.ScanStartError.BluetoothDisabled -> bluetoothEnabled = false
                    else -> Unit
                }
            }
        )
        onDispose { scanner.stopScan() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        tk.glucodata.ui.CgmReadinessSetupBanner(
            modifier = Modifier.padding(horizontal = ui.horizontalPadding, vertical = ui.spacerMedium),
            onOpenReadiness = onNavigateToReadiness
        )
        Spacer(Modifier.height(ui.spacerMedium))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ui.horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.aidex_searching_sensors),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(
                onClick = { showAllDevices = !showAllDevices }
            ) {
                Text(
                    if (showAllDevices) {
                        stringResource(R.string.show_sensors_only)
                    } else {
                        stringResource(R.string.see_all_devices)
                    }
                )
            }
        }
        if (!scanPermissionGranted || !bluetoothEnabled || scanError != null) {
            Spacer(Modifier.height(ui.spacerMedium))
            Card(
                modifier = Modifier
                    .padding(horizontal = ui.horizontalPadding)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val messageRes = when {
                        !scanPermissionGranted && Build.VERSION.SDK_INT >= 31 -> R.string.turn_on_nearby_devices_permission
                        !scanPermissionGranted -> R.string.turn_on_location_permission
                        !bluetoothEnabled || scanError is BleDeviceScanner.ScanStartError.BluetoothDisabled -> R.string.bluetooth_is_turned_off
                        else -> R.string.nobluetooth
                    }
                    Text(
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(ui.spacerMedium))
                    val buttonRes = when {
                        !scanPermissionGranted -> R.string.permission
                        !bluetoothEnabled || scanError is BleDeviceScanner.ScanStartError.BluetoothDisabled -> R.string.enable_bluetooth
                        else -> R.string.search_bluetooth
                    }
                    Button(
                        onClick = {
                            when {
                                !scanPermissionGranted -> requestScanPermission()
                                !bluetoothEnabled || scanError is BleDeviceScanner.ScanStartError.BluetoothDisabled -> {
                                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                }
                                else -> {
                                    scanError = null
                                    scanPermissionGranted = hasBleScanPermissions(context)
                                    bluetoothEnabled = scanner.isBluetoothEnabled()
                                    scanRetryKey += 1
                                }
                            }
                        },
                        modifier = Modifier.height(ui.buttonHeight)
                    ) {
                        Text(stringResource(buttonRes))
                    }
                }
            }
        }
        LazyColumn {
            items(devices) { device ->
                val name = device.rawName.ifBlank { stringResource(R.string.unknown) }
                val serial = device.serial

                // If we're in "sensors only" mode, skip non-matching devices.
                if (!showAllDevices && !device.isLikelyAiDex) return@items

                val canSelect = device.isLikelyAiDex || showAllDevices

                ListItem(
                    headlineContent = {
                        Text(
                            if (serial != null) "$name ($serial)" else name
                        )
                    },
                    supportingContent = {
                        Text(
                            when {
                                serial != null -> device.address
                                device.detectedViaFf30 -> stringResource(R.string.aidex_detected_via_ff30, device.address)
                                device.isLikelyAiDex -> stringResource(R.string.aidex_selectable_unrecognized, device.address)
                                else -> stringResource(R.string.aidex_not_recognized, device.address)
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Bluetooth, null) },
                    modifier = Modifier.clickable(enabled = canSelect) {
                        onDeviceSelected(device.selectionName, device.address)
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

internal fun requiredBleScanPermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= 31 -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        Build.VERSION.SDK_INT >= 23 -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> emptyArray()
    }
}

internal fun hasBleScanPermissions(context: Context): Boolean {
    return requiredBleScanPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
