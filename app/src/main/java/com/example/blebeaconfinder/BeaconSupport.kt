package com.example.blebeaconfinder

import android.content.Context
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanRecord
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.io.File
import java.util.Locale
import java.util.UUID

data class BeaconDefinition(
    val name: String,
    val uuid: String,
    val audioResId: Int? = null,
    val customAudioId: String? = null,
)

data class IBeaconData(
    val uuid: String,
    val major: Int,
    val minor: Int,
)

object BeaconCatalog {
    private const val PREFS_NAME = "beacon_catalog"
    private const val KEY_KNOWN_BEACONS = "known_beacons"
    private const val KEY_DEFAULTS_VERSION = "defaults_version"
    private const val CURRENT_DEFAULTS_VERSION = 2

    val NO_BEACON_AUDIO_RES_ID = R.raw.nobeacon

    private val defaultKnownBeaconsV1 =
        listOf(
            BeaconDefinition(
                name = "Baliza A - Cocina",
                uuid = "B9407F30-F5F8-466E-AFF9-25556B57FE6D",
                audioResId = R.raw.cocina,
            ),
            BeaconDefinition(
                name = "Baliza B - Pieza",
                uuid = "A1B2C3D4-E5F6-4789-ABCD-1234567890AB",
                audioResId = R.raw.pieza,
            ),
            BeaconDefinition(
                name = "Baliza C - Living",
                uuid = "9F8E7D6C-5B4A-4321-9876-ABCDEF123456",
                audioResId = R.raw.living,
            ),
        )

    private val defaultKnownBeaconsV2 =
        listOf(
            BeaconDefinition(
                name = "Baliza D - Infierno",
                uuid = "A2B3C4D5-E6F7-4889-ABCD-1234567890AB",
            ),
            BeaconDefinition(
                name = "Baliza E - Cuartito de los Cachibaches",
                uuid = "A1B2C3D4-E5F6-4789-ABCD-34567890ABCD",
            ),
        )

    private val defaultKnownBeacons =
        (defaultKnownBeaconsV1 + defaultKnownBeaconsV2).map { beacon ->
            beacon.copy(uuid = beacon.uuid.lowercase(Locale.US))
        }

    private val newDefaultKnownBeacons =
        defaultKnownBeaconsV2.map { beacon ->
            beacon.copy(uuid = beacon.uuid.lowercase(Locale.US))
        }

    fun getKnownBeacons(context: Context): List<BeaconDefinition> {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedCatalog = preferences.getString(KEY_KNOWN_BEACONS, null) ?: return defaultKnownBeacons

        return runCatching {
            val jsonArray = JSONArray(storedCatalog)
            val storedBeacons = List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                BeaconDefinition(
                    name = jsonObject.getString("name"),
                    uuid = normalizeUuid(jsonObject.getString("uuid")),
                    audioResId = jsonObject.takeIf { it.has("audioResId") && !it.isNull("audioResId") }?.getInt("audioResId"),
                    customAudioId = jsonObject.takeIf { it.has("customAudioId") && !it.isNull("customAudioId") }?.getString("customAudioId"),
                )
            }
            migrateDefaultBeaconsIfNeeded(context, storedBeacons)
        }.getOrElse {
            defaultKnownBeacons
        }
    }

    fun saveKnownBeacons(context: Context, beacons: List<BeaconDefinition>) {
        val normalizedBeacons =
            beacons.map { beacon ->
                beacon.copy(uuid = normalizeUuid(beacon.uuid))
            }

        val jsonArray =
            JSONArray().apply {
                normalizedBeacons.forEach { beacon ->
                    put(
                        JSONObject().apply {
                            put("name", beacon.name)
                            put("uuid", beacon.uuid)
                            if (beacon.audioResId != null) {
                                put("audioResId", beacon.audioResId)
                            } else {
                                put("audioResId", JSONObject.NULL)
                            }
                            if (beacon.customAudioId != null) {
                                put("customAudioId", beacon.customAudioId)
                            } else {
                                put("customAudioId", JSONObject.NULL)
                            }
                        }
                    )
                }
            }

        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KNOWN_BEACONS, jsonArray.toString())
            .putInt(KEY_DEFAULTS_VERSION, CURRENT_DEFAULTS_VERSION)
            .apply()
    }

    private fun migrateDefaultBeaconsIfNeeded(
        context: Context,
        storedBeacons: List<BeaconDefinition>,
    ): List<BeaconDefinition> {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultsVersion = preferences.getInt(KEY_DEFAULTS_VERSION, 1)
        if (defaultsVersion >= CURRENT_DEFAULTS_VERSION) {
            return storedBeacons
        }

        val mergedBeacons =
            storedBeacons + newDefaultKnownBeacons.filterNot { defaultBeacon ->
                storedBeacons.any { storedBeacon -> storedBeacon.uuid == defaultBeacon.uuid }
            }

        saveKnownBeacons(context, mergedBeacons)
        return mergedBeacons
    }

    fun findKnownBeacon(context: Context, uuid: String): BeaconDefinition? {
        val normalizedUuid = normalizeUuid(uuid)
        return getKnownBeacons(context).firstOrNull { it.uuid == normalizedUuid }
    }

    fun availableAudioOptions(context: Context): List<BeaconAudioOption> {
        val builtInOptions =
            R.raw::class.java.fields
                .filter { it.name != "item" && it.name != "nobeacon" }
                .map { field ->
                    BeaconAudioOption(
                        id = "builtin_${field.name}",
                        builtInResId = field.getInt(null),
                        customAudioId = null,
                        label = field.name
                            .replace("_", " ")
                            .split(" ")
                            .joinToString(" ") { word ->
                                word.replaceFirstChar { char -> char.uppercase() }
                            },
                    )
                }
                .sortedBy { it.label }
                .let { listOf(BeaconAudioOption(id = "none", builtInResId = null, customAudioId = null, label = "Sin audio")) + it }
        val customOptions =
            CustomAudioStore.getCustomAudios(context).map { audio ->
                BeaconAudioOption(
                    id = "custom_${audio.id}",
                    builtInResId = null,
                    customAudioId = audio.id,
                    label = "Personalizado: ${audio.name}",
                )
            }

        return builtInOptions + customOptions
    }

    fun audioLabelFor(context: Context, audioResId: Int?, customAudioId: String?): String {
        return availableAudioOptions(context)
            .firstOrNull { it.builtInResId == audioResId && it.customAudioId == customAudioId }
            ?.label ?: "Sin audio"
    }

    fun normalizeUuid(uuid: String): String {
        return uuid.trim().lowercase(Locale.US)
    }
}

data class BeaconAudioOption(
    val id: String,
    val builtInResId: Int?,
    val customAudioId: String?,
    val label: String,
)

data class CustomAudioDefinition(
    val id: String,
    val name: String,
    val fileName: String,
)

object CustomAudioStore {
    private const val PREFS_NAME = "custom_audio_catalog"
    private const val KEY_CUSTOM_AUDIOS = "custom_audios"
    private const val DIRECTORY_NAME = "custom_audio"

    fun getCustomAudios(context: Context): List<CustomAudioDefinition> {
        val storedCatalog =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM_AUDIOS, null) ?: return emptyList()

        return runCatching {
            val jsonArray = JSONArray(storedCatalog)
            List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                CustomAudioDefinition(
                    id = jsonObject.getString("id"),
                    name = jsonObject.getString("name"),
                    fileName = jsonObject.getString("fileName"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addCustomAudio(context: Context, audio: CustomAudioDefinition) {
        val updatedAudios = getCustomAudios(context).filterNot { it.id == audio.id } + audio
        saveCustomAudios(context, updatedAudios.sortedBy { it.name.lowercase(Locale.US) })
    }

    fun renameCustomAudio(context: Context, audioId: String, newName: String) {
        val updatedAudios =
            getCustomAudios(context).map { audio ->
                if (audio.id == audioId) audio.copy(name = newName) else audio
            }
        saveCustomAudios(context, updatedAudios.sortedBy { it.name.lowercase(Locale.US) })
    }

    fun deleteCustomAudio(context: Context, audioId: String) {
        val currentAudios = getCustomAudios(context)
        val targetAudio = currentAudios.firstOrNull { it.id == audioId } ?: return

        File(directory(context), targetAudio.fileName).delete()
        saveCustomAudios(context, currentAudios.filterNot { it.id == audioId })

        val updatedBeacons =
            BeaconCatalog.getKnownBeacons(context).map { beacon ->
                if (beacon.customAudioId == audioId) {
                    beacon.copy(customAudioId = null, audioResId = null)
                } else {
                    beacon
                }
            }
        BeaconCatalog.saveKnownBeacons(context, updatedBeacons)
    }

    fun resolveAudioFile(context: Context, customAudioId: String): File? {
        val audio = getCustomAudios(context).firstOrNull { it.id == customAudioId } ?: return null
        val audioFile = File(directory(context), audio.fileName)
        return audioFile.takeIf(File::exists)
    }

    fun newAudioFile(context: Context): File {
        return File(directory(context), "${UUID.randomUUID()}.m4a")
    }

    private fun saveCustomAudios(context: Context, audios: List<CustomAudioDefinition>) {
        val jsonArray =
            JSONArray().apply {
                audios.forEach { audio ->
                    put(
                        JSONObject().apply {
                            put("id", audio.id)
                            put("name", audio.name)
                            put("fileName", audio.fileName)
                        }
                    )
                }
            }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_AUDIOS, jsonArray.toString())
            .apply()
    }

    private fun directory(context: Context): File {
        return File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
    }
}

object BeaconParser {
    private const val APPLE_COMPANY_ID = 0x004C
    private const val IBEACON_PREFIX_LENGTH = 2
    private const val UUID_BYTE_LENGTH = 16
    private const val IBEACON_TOTAL_LENGTH = IBEACON_PREFIX_LENGTH + UUID_BYTE_LENGTH + 2 + 2 + 1
    private const val IBEACON_TYPE_VALUE_0 = 0x02
    private const val IBEACON_TYPE_VALUE_1 = 0x15

    fun extractIBeacon(scanRecord: ScanRecord): IBeaconData? {
        val manufacturerData = scanRecord.getManufacturerSpecificData(APPLE_COMPANY_ID) ?: return null
        if (manufacturerData.size < IBEACON_TOTAL_LENGTH) {
            return null
        }

        if (
            (manufacturerData[0].toInt() and 0xFF) != IBEACON_TYPE_VALUE_0 ||
            (manufacturerData[1].toInt() and 0xFF) != IBEACON_TYPE_VALUE_1
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
            uuid = BeaconCatalog.normalizeUuid(uuid.toString()),
            major = major,
            minor = minor
        )
    }

    private fun readUnsignedShort(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }
}

object BeaconScanConfig {
    fun scanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()
    }
}
