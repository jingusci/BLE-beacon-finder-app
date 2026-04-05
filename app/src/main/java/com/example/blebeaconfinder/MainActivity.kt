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
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var viewBeaconsButton: Button
    private lateinit var manageKnownBeaconsButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scanResults = linkedMapOf<String, BeaconCandidate>()
    private var isScanning = false
    private lateinit var soundPool: SoundPool
    private val loadedAudioResIds = mutableSetOf<Int>()
    private val soundIdsByResId = mutableMapOf<Int, Int>()
    private var pendingAudioResId: Int? = null
    private var pendingAudioLabel: String? = null
    private var activeStreamId: Int? = null

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
        volumeControlStream = AudioManager.STREAM_MUSIC
        initializeSoundPool()

        statusText = findViewById(R.id.statusText)
        actionButton = findViewById(R.id.findBeaconButton)
        viewBeaconsButton = findViewById(R.id.viewBeaconsButton)
        manageKnownBeaconsButton = findViewById(R.id.manageKnownBeaconsButton)

        actionButton.setOnClickListener {
            triggerNearestBeaconSearch()
        }

        viewBeaconsButton.setOnClickListener {
            startActivity(Intent(this, BeaconScannerActivity::class.java))
        }

        manageKnownBeaconsButton.setOnClickListener {
            startActivity(Intent(this, KnownBeaconsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        preloadAudioResources()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                triggerNearestBeaconSearch()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        stopScan()
        releaseSoundPool()
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

        val nearestBeacon = scanResults.values.maxByOrNull { it.rssi }
        if (nearestBeacon == null) {
            updateStatus("No se detectaron balizas iBeacon.")
            playAudioResource(BeaconCatalog.NO_BEACON_AUDIO_RES_ID, "sin balizas conocidas")
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
        nearestBeacon.audioResId?.let { audioResId ->
            playAudioResource(audioResId, nearestBeacon.name)
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

        val iBeacon = BeaconParser.extractIBeacon(scanRecord) ?: return null
        val knownBeacon = BeaconCatalog.findKnownBeacon(this, iBeacon.uuid)

        return BeaconCandidate(
            name = knownBeacon?.name ?: "iBeacon desconocido (${safeDeviceName(deviceName)})",
            address = address,
            uuid = iBeacon.uuid,
            major = iBeacon.major,
            minor = iBeacon.minor,
            rssi = rssi,
            audioResId = knownBeacon?.audioResId,
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

    private fun safeDeviceName(name: String?): String {
        return name?.takeIf { it.isNotBlank() } ?: "Sin nombre"
    }

    private fun updateStatus(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun initializeSoundPool() {
        soundPool =
            SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) {
                updateStatus("No se pudo cargar un audio de la app.")
                return@setOnLoadCompleteListener
            }

            val loadedResId = soundIdsByResId.entries.firstOrNull { it.value == sampleId }?.key ?: return@setOnLoadCompleteListener
            loadedAudioResIds += loadedResId

            if (pendingAudioResId == loadedResId) {
                playLoadedSound(loadedResId, pendingAudioLabel ?: "audio")
            }
        }

        preloadAudioResources()
    }

    private fun preloadAudioResources() {
        val audioResIds =
            buildList {
                add(BeaconCatalog.NO_BEACON_AUDIO_RES_ID)
                addAll(BeaconCatalog.getKnownBeacons(this@MainActivity).mapNotNull { it.audioResId })
            }.distinct()

        audioResIds.forEach { audioResId ->
            if (soundIdsByResId.containsKey(audioResId).not()) {
                soundIdsByResId[audioResId] = soundPool.load(this, audioResId, 1)
            }
        }
    }

    private fun setMediaVolumeToMax() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
    }

    private fun playAudioResource(audioResId: Int, label: String) {
        setMediaVolumeToMax()

        if (loadedAudioResIds.contains(audioResId)) {
            playLoadedSound(audioResId, label)
        } else {
            pendingAudioResId = audioResId
            pendingAudioLabel = label
        }
    }

    private fun playLoadedSound(audioResId: Int, label: String) {
        val soundId = soundIdsByResId[audioResId]
        if (soundId == null) {
            updateStatus("No se encontro el audio para $label")
            return
        }

        activeStreamId?.let(soundPool::stop)
        val streamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        if (streamId == 0) {
            updateStatus("No se pudo reproducir el audio para $label")
            return
        }

        activeStreamId = streamId
        pendingAudioResId = null
        pendingAudioLabel = null
    }

    private fun releaseSoundPool() {
        if (::soundPool.isInitialized) {
            soundPool.release()
        }
    }

    data class BeaconCandidate(
        val name: String,
        val address: String,
        val uuid: String,
        val major: Int?,
        val minor: Int?,
        val rssi: Int,
        val audioResId: Int?,
    )

    companion object {
        private const val SCAN_DURATION_MS = 3_500L
    }
}
