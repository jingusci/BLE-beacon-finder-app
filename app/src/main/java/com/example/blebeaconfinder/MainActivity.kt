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
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var viewBeaconsButton: Button
    private lateinit var manageKnownBeaconsButton: Button
    private lateinit var volumeButtonsSwitch: MaterialSwitch

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scanMeasurements = linkedMapOf<String, BeaconMeasurement>()
    private var isScanning = false
    private var mediaPlayer: MediaPlayer? = null
    private var pendingActionAfterPermissions = PendingPermissionAction.NONE
    private val appPreferences by lazy {
        getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val deniedPermission = permissions.entries.firstOrNull { !it.value }?.key
            if (deniedPermission == null) {
                if (pendingActionAfterPermissions == PendingPermissionAction.SCAN) {
                    continueScanAfterPermissions()
                }
            } else {
                updateStatus("Faltan permisos para escanear balizas BLE.")
            }
            pendingActionAfterPermissions = PendingPermissionAction.NONE
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
        volumeControlStream = AudioManager.STREAM_MUSIC

        statusText = findViewById(R.id.statusText)
        actionButton = findViewById(R.id.findBeaconButton)
        viewBeaconsButton = findViewById(R.id.viewBeaconsButton)
        manageKnownBeaconsButton = findViewById(R.id.manageKnownBeaconsButton)
        volumeButtonsSwitch = findViewById(R.id.volumeButtonsSwitch)

        actionButton.setOnClickListener {
            triggerNearestBeaconSearch()
        }

        viewBeaconsButton.setOnClickListener {
            startActivity(Intent(this, BeaconScannerActivity::class.java))
        }

        manageKnownBeaconsButton.setOnClickListener {
            startActivity(Intent(this, KnownBeaconsActivity::class.java))
        }

        volumeButtonsSwitch.isChecked = areVolumeButtonsEnabled()
        volumeButtonsSwitch.setOnCheckedChangeListener { _, isChecked ->
            appPreferences.edit().putBoolean(KEY_VOLUME_BUTTONS_ENABLED, isChecked).apply()
        }

        requestStartupPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (areVolumeButtonsEnabled()) {
                    triggerNearestBeaconSearch()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        stopScan()
        stopAndReleaseMediaPlayer()
        super.onDestroy()
    }

    private fun triggerNearestBeaconSearch() {
        ensureBluetoothAndPermissions()
    }

    private fun ensureBluetoothAndPermissions() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            updateStatus("Este dispositivo no soporta Bluetooth Low Energy.")
            return
        }

        val missingPermissions = requiredPermissions().filterNot(::hasPermission)
        if (missingPermissions.isNotEmpty()) {
            pendingActionAfterPermissions = PendingPermissionAction.SCAN
            permissionLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        continueScanAfterPermissions()
    }

    private fun continueScanAfterPermissions() {
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

        scanMeasurements.clear()
        isScanning = true
        updateStatus("Buscando balizas cercanas...")

        scanner.startScan(emptyList(), BeaconScanConfig.scanSettings(), scanCallback)
        mainHandler.postDelayed(
            {
                finishNearestBeaconScan()
            },
            SCAN_DURATION_MS
        )
    }

    private fun finishNearestBeaconScan() {
        stopScan()

        val nearestMeasurement = scanMeasurements.values.maxByOrNull { it.averageRssi }
        if (nearestMeasurement == null) {
            updateStatus("No se detectaron balizas iBeacon.")
            playBuiltInAudio(BeaconCatalog.NO_BEACON_AUDIO_RES_ID, "sin balizas conocidas")
            return
        }

        val nearestBeacon = nearestMeasurement.beacon
        val averageRssi = nearestMeasurement.averageRssi

        updateStatus(
            buildString {
                append("Baliza mas cercana: ${nearestBeacon.name}")
                append("\nRSSI promedio: ${averageRssi} dBm")
                append("\nMuestras: ${nearestMeasurement.sampleCount}")
            }
        )
        when {
            nearestBeacon.customAudioId != null -> playCustomAudio(nearestBeacon.customAudioId, nearestBeacon.name)
            nearestBeacon.audioResId != null -> playBuiltInAudio(nearestBeacon.audioResId, nearestBeacon.name)
        }
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
        val beacon = buildBeaconCandidate(result.scanRecord, address, result.rssi) ?: return
        val candidateKey = "${beacon.uuid}:${beacon.major ?: -1}:${beacon.minor ?: -1}"
        val existing = scanMeasurements[candidateKey]

        if (existing == null) {
            scanMeasurements[candidateKey] = BeaconMeasurement(beacon = beacon, totalRssi = beacon.rssi.toLong(), sampleCount = 1)
            return
        }

        scanMeasurements[candidateKey] =
            existing.copy(
                beacon = existing.beacon.copy(
                    address = address,
                    rssi = beacon.rssi
                ),
                totalRssi = existing.totalRssi + beacon.rssi,
                sampleCount = existing.sampleCount + 1
            )
    }

    private fun buildBeaconCandidate(
        scanRecord: ScanRecord?,
        address: String,
        rssi: Int
    ): BeaconCandidate? {
        if (scanRecord == null) {
            return null
        }

        val iBeacon = BeaconParser.extractIBeacon(scanRecord) ?: return null
        val knownBeacon = BeaconCatalog.findKnownBeacon(this, iBeacon.uuid) ?: return null

        return BeaconCandidate(
            name = knownBeacon.name,
            address = address,
            uuid = iBeacon.uuid,
            major = iBeacon.major,
            minor = iBeacon.minor,
            rssi = rssi,
            audioResId = knownBeacon.audioResId,
            customAudioId = knownBeacon.customAudioId,
        )
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

    private fun requestStartupPermissionsIfNeeded() {
        val missingPermissions = requiredPermissions().filterNot(::hasPermission)
        if (missingPermissions.isNotEmpty()) {
            pendingActionAfterPermissions = PendingPermissionAction.NONE
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun areVolumeButtonsEnabled(): Boolean {
        return appPreferences.getBoolean(KEY_VOLUME_BUTTONS_ENABLED, true)
    }

    private fun setMediaVolumeToMax() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
    }

    private fun playBuiltInAudio(audioResId: Int, label: String) {
        setMediaVolumeToMax()
        stopAndReleaseMediaPlayer()
        playMediaPlayer(
            label = label,
            configure = { setDataSource(this@MainActivity, android.net.Uri.parse("android.resource://${packageName}/$audioResId")) }
        )
    }

    private fun playCustomAudio(customAudioId: String, label: String) {
        setMediaVolumeToMax()

        val audioFile = CustomAudioStore.resolveAudioFile(this, customAudioId)
        if (audioFile == null) {
            updateStatus("No se encontro el audio para $label")
            return
        }

        stopAndReleaseMediaPlayer()
        playMediaPlayer(label = label, configure = { setDataSource(audioFile.absolutePath) })
    }

    private fun playMediaPlayer(label: String, configure: MediaPlayer.() -> Unit) {
        val player = MediaPlayer()
        runCatching {
            player.apply {
                configure()
                setOnCompletionListener {
                    runCatching { it.release() }
                    if (mediaPlayer === it) {
                        mediaPlayer = null
                    }
                }
                prepare()
                start()
            }
        }.onSuccess {
            mediaPlayer = player
        }.onFailure {
            runCatching { player.release() }
            updateStatus("No se pudo reproducir el audio para $label")
        }
    }

    private fun stopAndReleaseMediaPlayer() {
        val player = mediaPlayer ?: return
        runCatching { player.stop() }
        runCatching { player.release() }
        mediaPlayer = null
    }

    data class BeaconCandidate(
        val name: String,
        val address: String,
        val uuid: String,
        val major: Int?,
        val minor: Int?,
        val rssi: Int,
        val audioResId: Int?,
        val customAudioId: String?,
    )

    data class BeaconMeasurement(
        val beacon: BeaconCandidate,
        val totalRssi: Long,
        val sampleCount: Int,
    ) {
        val averageRssi: Int
            get() = (totalRssi.toDouble() / sampleCount).toInt()
    }

    companion object {
        private const val APP_PREFS_NAME = "ble_beacon_finder_prefs"
        private const val KEY_VOLUME_BUTTONS_ENABLED = "volume_buttons_enabled"
        private const val SCAN_DURATION_MS = 3_000L
    }

    private enum class PendingPermissionAction {
        NONE,
        SCAN,
    }
}
