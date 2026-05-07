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
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class BeaconScannerActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var beaconListView: ListView
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
        beaconListView = findViewById(R.id.beaconListView)
        backButton = findViewById(R.id.backButton)

        backButton.setOnClickListener {
            finish()
        }
        beaconListView.setOnItemClickListener { _, _, position, _ ->
            observedDevices.values
                .sortedWith(compareByDescending<ObservedDevice> { it.rssi }.thenBy { it.displayName.lowercase(Locale.US) })
                .getOrNull(position)
                ?.let(::showObservedBeaconEditor)
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

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun ensureBluetoothAndPermissions() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            updateStatus("Este dispositivo no soporta Bluetooth Low Energy.")
            return
        }

        val missingPermissions = requiredPermissions().filterNot(::hasPermission)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
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
        beaconListView.adapter = ObservedBeaconsAdapter(emptyList())
        isScanning = true
        updateStatus(getString(R.string.scanner_status_monitoring))
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
        val iBeacon = scanRecord?.let(BeaconParser::extractIBeacon) ?: return
        val knownBeacon = BeaconCatalog.findKnownBeacon(this, iBeacon.uuid)

        observedDevices[address] =
            ObservedDevice(
                address = address,
                name = device.name?.takeIf { it.isNotBlank() } ?: getString(R.string.observed_beacon_name_fallback),
                rssi = result.rssi,
                lastSeenAt = System.currentTimeMillis(),
                beaconName = knownBeacon?.name,
                beaconUuid = knownBeacon?.uuid,
                iBeacon = iBeacon,
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
            beaconListView.adapter = ObservedBeaconsAdapter(emptyList())
            return
        }

        val sortedDevices =
            observedDevices.values
                .sortedWith(compareByDescending<ObservedDevice> { it.rssi }.thenBy { it.name.lowercase(Locale.US) })
        beaconListView.adapter = ObservedBeaconsAdapter(sortedDevices)
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

    private fun showObservedBeaconEditor(device: ObservedDevice) {
        val existingBeacon = BeaconCatalog.findKnownBeacon(this, device.iBeacon.uuid)
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_beacon, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.beaconNameInput)
        val uuidInput = dialogView.findViewById<TextInputEditText>(R.id.beaconUuidInput)
        val audioInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.beaconAudioInput)
        val audioOptions = BeaconCatalog.availableAudioOptions(this)
        val audioLabels = audioOptions.map(BeaconAudioOption::label)
        val initialAudioIndex =
            audioOptions.indexOfFirst {
                it.builtInResId == existingBeacon?.audioResId &&
                    it.customAudioId == existingBeacon?.customAudioId
            }.coerceAtLeast(0)

        audioInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, audioLabels))
        audioInput.setText(audioLabels[initialAudioIndex], false)
        nameInput.setText(existingBeacon?.name ?: device.displayName)
        uuidInput.setText(device.iBeacon.uuid)

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(if (existingBeacon == null) R.string.add_known_beacon else R.string.edit_known_beacon)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val rawUuid = uuidInput.text?.toString()?.trim().orEmpty()
                val selectedAudio = audioOptions.firstOrNull { it.label == audioInput.text?.toString() } ?: audioOptions.first()
                val validationError = validateBeaconInput(name, rawUuid, existingBeacon?.uuid)
                if (validationError != null) {
                    Toast.makeText(this, validationError, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val updatedBeacons = BeaconCatalog.getKnownBeacons(this).toMutableList()
                val updatedBeacon =
                    BeaconDefinition(
                        name = name,
                        uuid = BeaconCatalog.normalizeUuid(rawUuid),
                        audioResId = selectedAudio.builtInResId,
                        customAudioId = selectedAudio.customAudioId,
                    )

                val existingIndex = updatedBeacons.indexOfFirst { it.uuid == existingBeacon?.uuid }
                if (existingIndex >= 0) {
                    updatedBeacons[existingIndex] = updatedBeacon
                } else {
                    updatedBeacons += updatedBeacon
                }

                BeaconCatalog.saveKnownBeacons(this, updatedBeacons)
                renderObservedDevices()
                dialog.dismiss()
                Toast.makeText(this, R.string.observed_beacon_saved, Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun validateBeaconInput(name: String, rawUuid: String, originalUuid: String?): String? {
        if (name.isBlank()) {
            return getString(R.string.known_beacon_name_required)
        }

        val normalizedUuid = BeaconCatalog.normalizeUuid(rawUuid)
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        if (!uuidRegex.matches(normalizedUuid)) {
            return getString(R.string.known_beacon_uuid_invalid)
        }

        val alreadyExists =
            BeaconCatalog.getKnownBeacons(this)
                .any { beacon -> beacon.uuid == normalizedUuid && beacon.uuid != originalUuid }
        if (alreadyExists) {
            return getString(R.string.known_beacon_uuid_duplicate)
        }

        return null
    }

    data class ObservedDevice(
        val address: String,
        val name: String,
        val rssi: Int,
        val lastSeenAt: Long,
        val beaconName: String?,
        val beaconUuid: String?,
        val iBeacon: IBeaconData,
    ) {
        val displayName: String
            get() = beaconName ?: name
    }

    private inner class ObservedBeaconsAdapter(
        private val devices: List<ObservedDevice>,
    ) : BaseAdapter() {
        override fun getCount(): Int = devices.size

        override fun getItem(position: Int): ObservedDevice = devices[position]

        override fun getItemId(position: Int): Long = getItem(position).address.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rowView = convertView ?: layoutInflater.inflate(R.layout.item_observed_beacon, parent, false)
            val device = getItem(position)
            val secondsSinceLastSeen = (System.currentTimeMillis() - device.lastSeenAt) / 1000.0
            val isKnown = device.beaconUuid != null

            rowView.findViewById<TextView>(R.id.observedBeaconNameText).text = device.displayName
            rowView.findViewById<TextView>(R.id.observedBeaconStateText).text =
                getString(if (isKnown) R.string.observed_beacon_known else R.string.observed_beacon_unknown)
            rowView.findViewById<TextView>(R.id.observedBeaconUuidText).text =
                getString(R.string.observed_beacon_uuid_line, device.iBeacon.uuid)
            rowView.findViewById<TextView>(R.id.observedBeaconDetailsText).text =
                getString(
                    R.string.observed_beacon_details,
                    device.iBeacon.major,
                    device.iBeacon.minor,
                    device.rssi,
                    secondsSinceLastSeen
                )
            rowView.findViewById<TextView>(R.id.observedBeaconActionHintText).text =
                getString(if (isKnown) R.string.observed_beacon_action_edit else R.string.observed_beacon_action_add)

            rowView.findViewById<MaterialCardView>(R.id.observedBeaconCard).strokeWidth =
                resources.getDimensionPixelSize(R.dimen.known_beacon_card_stroke)

            return rowView
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 1_000L
        private const val DEVICE_TTL_MS = 5_000L
    }
}
