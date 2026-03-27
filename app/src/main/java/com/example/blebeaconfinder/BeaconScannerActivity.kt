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
import java.util.Locale

class BeaconScannerActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var beaconListText: TextView
    private lateinit var backButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private val observedDevices = linkedMapOf<String, ObservedDevice>()
    private var isScanning = false

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val deniedPermission = permissions.entries.firstOrNull { !it.value }?.key
            if (deniedPermission == null) {
                startMonitoring()
            } else {
                updateStatus("Faltan permisos para monitorear balizas BLE.")
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapter?.isEnabled == true) {
                startMonitoring()
            } else {
                updateStatus("Bluetooth desactivado. Activarlo para monitorear balizas.")
            }
        }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            registerObservedDevice(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::registerObservedDevice)
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            updateStatus("No se pudo iniciar el monitoreo BLE. Error: $errorCode")
        }
    }

    private val renderRunnable =
        object : Runnable {
            override fun run() {
                pruneOldDevices()
                renderObservedDevices()
                if (isScanning) {
                    mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_beacon_scanner)

        statusText = findViewById(R.id.scannerStatusText)
        beaconListText = findViewById(R.id.beaconListText)
        backButton = findViewById(R.id.backButton)

        backButton.setOnClickListener {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        ensureBluetoothAndPermissions()
    }

    override fun onStop() {
        stopMonitoring()
        super.onStop()
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

        if (!isLocationEnabled()) {
            updateStatus("Activa la ubicacion del telefono para monitorear balizas BLE.")
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        startMonitoring()
    }

    private fun startMonitoring() {
        if (isScanning) {
            return
        }

        val scanner = bluetoothLeScanner()
        if (scanner == null) {
            updateStatus("No se pudo acceder al escaner BLE.")
            return
        }

        observedDevices.clear()
        beaconListText.text = getString(R.string.monitoring_empty)
        isScanning = true
        updateStatus("Monitoreando dispositivos BLE. Actualiza cada 1 segundo.")
        scanner.startScan(emptyList(), BeaconScanConfig.scanSettings(), scanCallback)
        mainHandler.post(renderRunnable)
    }

    private fun stopMonitoring() {
        if (!isScanning) {
            return
        }

        bluetoothLeScanner()?.stopScan(scanCallback)
        mainHandler.removeCallbacks(renderRunnable)
        isScanning = false
    }

    private fun registerObservedDevice(result: ScanResult) {
        val device = result.device
        val address = device.address ?: return
        val scanRecord = result.scanRecord
        val iBeacon = scanRecord?.let(BeaconParser::extractIBeacon)
        val knownBeacon = iBeacon?.uuid?.let(BeaconCatalog::findKnownBeacon)
        val manufacturerIds = scanRecord?.manufacturerSpecificData?.collectManufacturerIds().orEmpty()
        val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString() }.orEmpty()

        observedDevices[address] =
            ObservedDevice(
                address = address,
                name = device.name?.takeIf { it.isNotBlank() } ?: "Sin nombre",
                rssi = result.rssi,
                lastSeenAt = System.currentTimeMillis(),
                beaconName = knownBeacon?.name,
                iBeacon = iBeacon,
                manufacturerIds = manufacturerIds,
                serviceUuids = serviceUuids
            )
    }

    private fun pruneOldDevices() {
        val cutoff = System.currentTimeMillis() - DEVICE_TTL_MS
        val iterator = observedDevices.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenAt < cutoff) {
                iterator.remove()
            }
        }
    }

    private fun renderObservedDevices() {
        if (observedDevices.isEmpty()) {
            beaconListText.text = getString(R.string.monitoring_empty)
            return
        }

        val now = System.currentTimeMillis()
        val renderedDevices =
            observedDevices.values
                .sortedWith(compareByDescending<ObservedDevice> { it.rssi }.thenBy { it.name.lowercase(Locale.US) })
                .joinToString(separator = "\n\n") { device ->
                    buildString {
                        append(device.beaconName ?: device.name)
                        append("\nMAC: ${device.address}")
                        append("\nRSSI: ${device.rssi} dBm")
                        append("\nUltima deteccion: hace ${(now - device.lastSeenAt) / 1000.0} s")

                        device.iBeacon?.let { iBeacon ->
                            append("\nTipo: iBeacon")
                            append("\nUUID: ${iBeacon.uuid}")
                            append("\nMajor: ${iBeacon.major}")
                            append("\nMinor: ${iBeacon.minor}")
                        }

                        if (device.manufacturerIds.isNotEmpty()) {
                            append("\nManufacturer IDs: ")
                            append(device.manufacturerIds.joinToString { id -> "0x%04X".format(Locale.US, id) })
                        }

                        if (device.serviceUuids.isNotEmpty()) {
                            append("\nService UUIDs: ${device.serviceUuids.joinToString()}")
                        }
                    }
                }

        beaconListText.text = renderedDevices
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
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
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

    private fun updateStatus(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun android.util.SparseArray<ByteArray>.collectManufacturerIds(): List<Int> {
        val ids = mutableListOf<Int>()
        for (index in 0 until size()) {
            ids += keyAt(index)
        }
        return ids
    }

    data class ObservedDevice(
        val address: String,
        val name: String,
        val rssi: Int,
        val lastSeenAt: Long,
        val beaconName: String?,
        val iBeacon: IBeaconData?,
        val manufacturerIds: List<Int>,
        val serviceUuids: List<String>,
    )

    companion object {
        private const val REFRESH_INTERVAL_MS = 1_000L
        private const val DEVICE_TTL_MS = 5_000L
    }
}
