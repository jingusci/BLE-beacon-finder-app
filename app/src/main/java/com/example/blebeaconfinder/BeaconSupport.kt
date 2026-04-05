package com.example.blebeaconfinder

import android.content.Context
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanRecord
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.Locale
import java.util.UUID

data class BeaconDefinition(
    val name: String,
    val uuid: String,
    val audioResId: Int? = null,
)

data class IBeaconData(
    val uuid: String,
    val major: Int,
    val minor: Int,
)

object BeaconCatalog {
    private const val PREFS_NAME = "beacon_catalog"
    private const val KEY_KNOWN_BEACONS = "known_beacons"

    val NO_BEACON_AUDIO_RES_ID = R.raw.nobeacon

    val availableAudioOptions =
        listOf(
            BeaconAudioOption(null, "Sin audio"),
            BeaconAudioOption(R.raw.cocina, "Cocina"),
            BeaconAudioOption(R.raw.pieza, "Pieza"),
            BeaconAudioOption(R.raw.living, "Living"),
        )

    private val defaultKnownBeacons =
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
        ).map { beacon ->
            beacon.copy(uuid = beacon.uuid.lowercase(Locale.US))
        }

    fun getKnownBeacons(context: Context): List<BeaconDefinition> {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedCatalog = preferences.getString(KEY_KNOWN_BEACONS, null) ?: return defaultKnownBeacons

        return runCatching {
            val jsonArray = JSONArray(storedCatalog)
            List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                BeaconDefinition(
                    name = jsonObject.getString("name"),
                    uuid = normalizeUuid(jsonObject.getString("uuid")),
                    audioResId = jsonObject.takeIf { it.has("audioResId") && !it.isNull("audioResId") }?.getInt("audioResId"),
                )
            }
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
                        }
                    )
                }
            }

        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KNOWN_BEACONS, jsonArray.toString())
            .apply()
    }

    fun findKnownBeacon(context: Context, uuid: String): BeaconDefinition? {
        val normalizedUuid = normalizeUuid(uuid)
        return getKnownBeacons(context).firstOrNull { it.uuid == normalizedUuid }
    }

    fun audioLabelFor(audioResId: Int?): String {
        return availableAudioOptions.firstOrNull { it.audioResId == audioResId }?.label ?: "Sin audio"
    }

    fun normalizeUuid(uuid: String): String {
        return uuid.trim().lowercase(Locale.US)
    }
}

data class BeaconAudioOption(
    val audioResId: Int?,
    val label: String,
)

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
