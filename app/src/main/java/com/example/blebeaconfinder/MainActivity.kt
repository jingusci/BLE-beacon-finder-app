package com.example.blebeaconfinder

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scanResults = linkedMapOf<String, BeaconCandidate>()
    private var isScanning = false

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val deniedPermission = permissions.entries.firstOrNull { !it.value }?.key
            if (deniedPermission == null) {
                startNearestBeaconScan()
            } else {
                updateStatus("Faltan permisos para escanear balizas BLE.")
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapter?.isEnabled == true) {
                startNearestBeaconScan()
            } else {
                updateStatus("Bluetooth desactivado. Activarlo para buscar balizas.")
            }
        }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            registerCandidate(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::registerCandidate)
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            updateStatus("No se pudo iniciar el escaneo BLE. Error: $errorCode")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        actionButton = findViewById(R.id.findBeaconButton)

        actionButton.setOnClickListener {
            ensureBluetoothAndPermissions()
        }
    }

    override fun onDestroy() {
        stopScan()
        super.onDestroy()
    }

    private fun ensureBluetoothAndPermissions() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            updateStatus("Este dispositivo no soporta Bluetooth Low Energy.")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            updateStatus("No se encontro adaptador Bluetooth.")
            return
        }

        if (!adapter.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableIntent)
            return
        }

        val missingPermissions = requiredPermissions().filterNot(::hasPermission)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && !isLocationEnabled()) {
            updateStatus("Activa la ubicacion del telefono para detectar balizas BLE.")
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        startNearestBeaconScan()
    }

    private fun startNearestBeaconScan() {
        if (isScanning) {
            return
        }

        val scanner = bluetoothLeScanner()
        if (scanner == null) {
            updateStatus("No se pudo acceder al escaner BLE.")
            return
        }

        scanResults.clear()
        isScanning = true
        updateStatus("Buscando balizas cercanas...")

        scanner.startScan(scanCallback)
        mainHandler.postDelayed(
            {
                finishNearestBeaconScan()
            },
            SCAN_DURATION_MS
        )
    }

    private fun finishNearestBeaconScan() {
        stopScan()

        val nearestBeacon = scanResults.values.maxByOrNull { it.rssi }
        if (nearestBeacon == null) {
            updateStatus("No se detectaron balizas iBeacon.")
            return
        }

        updateStatus(
            buildString {
                append("Baliza mas cercana: ${nearestBeacon.name}")
                append("\nUUID: ${nearestBeacon.uuid}")
                nearestBeacon.major?.let { append("\nMajor: $it") }
                nearestBeacon.minor?.let { append("\nMinor: $it") }
                append("\nRSSI: ${nearestBeacon.rssi} dBm")
            }
        )
    }

    private fun stopScan() {
        if (!isScanning) {
            return
        }

        bluetoothLeScanner()?.stopScan(scanCallback)
        mainHandler.removeCallbacksAndMessages(null)
        isScanning = false
    }

    private fun registerCandidate(result: ScanResult) {
        val device = result.device
        val address = device.address ?: return
        val beacon = buildBeaconCandidate(result.scanRecord, address, result.rssi, device.name) ?: return
        val candidateKey = "${beacon.uuid}:${beacon.major ?: -1}:${beacon.minor ?: -1}"
        val existing = scanResults[candidateKey]

        if (existing == null || result.rssi > existing.rssi) {
            scanResults[candidateKey] = beacon
        }
    }

    private fun buildBeaconCandidate(
        scanRecord: ScanRecord?,
        address: String,
        rssi: Int,
        deviceName: String?
    ): BeaconCandidate? {
        if (scanRecord == null) {
            return null
        }

        val iBeacon = extractIBeacon(scanRecord) ?: return null
        val knownBeacon = KNOWN_BEACONS.firstOrNull { it.uuid.equals(iBeacon.uuid, ignoreCase = true) }

        return BeaconCandidate(
            name = knownBeacon?.name ?: "iBeacon desconocido (${safeDeviceName(deviceName)})",
            address = address,
            uuid = iBeacon.uuid,
            major = iBeacon.major,
            minor = iBeacon.minor,
            rssi = rssi
        )
    }

    private fun extractIBeacon(scanRecord: ScanRecord): IBeaconData? {
        val manufacturerData = scanRecord.getManufacturerSpecificData(APPLE_COMPANY_ID) ?: return null
        if (manufacturerData.size < IBEACON_TOTAL_LENGTH) {
            return null
        }

        if (
            manufacturerData[0].toInt() != IBEACON_TYPE_VALUE_0 ||
            manufacturerData[1].toInt() != IBEACON_TYPE_VALUE_1
        ) {
            return null
        }

        val uuidBytes = manufacturerData.copyOfRange(
            IBEACON_PREFIX_LENGTH,
            IBEACON_PREFIX_LENGTH + UUID_BYTE_LENGTH
        )
        val uuidBuffer = ByteBuffer.wrap(uuidBytes)
        val uuid = UUID(uuidBuffer.long, uuidBuffer.long)
        val major = readUnsignedShort(manufacturerData, IBEACON_PREFIX_LENGTH + UUID_BYTE_LENGTH)
        val minor = readUnsignedShort(manufacturerData, IBEACON_PREFIX_LENGTH + UUID_BYTE_LENGTH + 2)

        return IBeaconData(
            uuid = normalizeUuid(uuid.toString()),
            major = major,
            minor = minor
        )
    }

    private fun normalizeUuid(uuid: String): String {
        return uuid.lowercase(Locale.US)
    }

    private fun readUnsignedShort(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun bluetoothLeScanner(): BluetoothLeScanner? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        ) {
            return null
        }
        return bluetoothAdapter?.bluetoothLeScanner
    }

    private fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val locationMode = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF
        )
        return locationMode != Settings.Secure.LOCATION_MODE_OFF
    }

    private fun safeDeviceName(name: String?): String {
        return name?.takeIf { it.isNotBlank() } ?: "Sin nombre"
    }

    private fun updateStatus(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    data class BeaconCandidate(
        val name: String,
        val address: String,
        val uuid: String,
        val major: Int?,
        val minor: Int?,
        val rssi: Int,
    )

    data class BeaconDefinition(
        val name: String,
        val uuid: String,
    )

    data class IBeaconData(
        val uuid: String,
        val major: Int,
        val minor: Int,
    )

    companion object {
        private const val SCAN_DURATION_MS = 3_500L
        private const val APPLE_COMPANY_ID = 0x004C
        private const val IBEACON_PREFIX_LENGTH = 4
        private const val UUID_BYTE_LENGTH = 16
        private const val IBEACON_TOTAL_LENGTH = IBEACON_PREFIX_LENGTH + UUID_BYTE_LENGTH + 2 + 2 + 1
        private const val IBEACON_TYPE_VALUE_0 = 0x02
        private const val IBEACON_TYPE_VALUE_1 = 0x15

        // Reemplaza estos UUID por los de tus balizas reales.
        private val KNOWN_BEACONS =
            listOf(
                BeaconDefinition(
                    name = "Baliza A - Cocina",
                    uuid = "B9407F30-F5F8-466E-AFF9-25556B57FE6D",
                ),
                BeaconDefinition(
                    name = "Baliza B - Pieza",
                    uuid = "A1B2C3D4-E5F6-4789-ABCD-1234567890AB",
                ),
                BeaconDefinition(
                    name = "Baliza C - Living",
                    uuid = "9F8E7D6C-5B4A-4321-9876-ABCDEF123456",
                ),
            ).map { beacon ->
                beacon.copy(uuid = beacon.uuid.lowercase(Locale.US))
            }
    }
}
