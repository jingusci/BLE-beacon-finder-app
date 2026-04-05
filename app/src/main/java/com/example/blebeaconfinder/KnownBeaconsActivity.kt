package com.example.blebeaconfinder

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

class KnownBeaconsActivity : AppCompatActivity() {

    private lateinit var emptyText: TextView
    private lateinit var beaconListView: ListView
    private lateinit var addBeaconButton: MaterialButton
    private lateinit var backButton: MaterialButton

    private var knownBeacons = emptyList<BeaconDefinition>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_known_beacons)

        emptyText = findViewById(R.id.emptyKnownBeaconsText)
        beaconListView = findViewById(R.id.knownBeaconsListView)
        addBeaconButton = findViewById(R.id.addBeaconButton)
        backButton = findViewById(R.id.backKnownBeaconsButton)

        backButton.setOnClickListener { finish() }
        addBeaconButton.setOnClickListener { showBeaconEditor(existingBeacon = null) }
        beaconListView.setOnItemClickListener { _, _, position, _ ->
            knownBeacons.getOrNull(position)?.let { beacon ->
                showBeaconEditor(existingBeacon = beacon)
            }
        }
        beaconListView.setOnItemLongClickListener { _, _, position, _ ->
            knownBeacons.getOrNull(position)?.let { beacon ->
                confirmDelete(beacon)
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        renderKnownBeacons()
    }

    private fun renderKnownBeacons() {
        knownBeacons = BeaconCatalog.getKnownBeacons(this).sortedBy { it.name.lowercase() }

        emptyText.visibility = if (knownBeacons.isEmpty()) View.VISIBLE else View.GONE
        beaconListView.visibility = if (knownBeacons.isEmpty()) View.GONE else View.VISIBLE

        val rows =
            knownBeacons.map { beacon ->
                buildString {
                    append(beacon.name)
                    append("\nUUID: ${beacon.uuid}")
                    append("\nAudio: ${BeaconCatalog.audioLabelFor(beacon.audioResId)}")
                }
            }

        beaconListView.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                rows
            )
    }

    private fun showBeaconEditor(existingBeacon: BeaconDefinition?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_beacon, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.beaconNameInput)
        val uuidInput = dialogView.findViewById<TextInputEditText>(R.id.beaconUuidInput)
        val audioInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.beaconAudioInput)
        val audioOptions = BeaconCatalog.availableAudioOptions
        val audioLabels = audioOptions.map(BeaconAudioOption::label)
        val initialAudioIndex = audioOptions.indexOfFirst { it.audioResId == existingBeacon?.audioResId }.coerceAtLeast(0)

        audioInput.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                audioLabels
            )
        )
        audioInput.setText(audioLabels[initialAudioIndex], false)
        nameInput.setText(existingBeacon?.name.orEmpty())
        uuidInput.setText(existingBeacon?.uuid.orEmpty())

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
                val selectedAudio =
                    audioOptions.firstOrNull { it.label == audioInput.text?.toString() } ?: audioOptions.first()

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
                        audioResId = selectedAudio.audioResId,
                    )

                if (existingBeacon == null) {
                    updatedBeacons += updatedBeacon
                } else {
                    val targetIndex = updatedBeacons.indexOfFirst { it.uuid == existingBeacon.uuid }
                    if (targetIndex >= 0) {
                        updatedBeacons[targetIndex] = updatedBeacon
                    } else {
                        updatedBeacons += updatedBeacon
                    }
                }

                BeaconCatalog.saveKnownBeacons(this, updatedBeacons)
                renderKnownBeacons()
                dialog.dismiss()
                Toast.makeText(this, R.string.known_beacon_saved, Toast.LENGTH_SHORT).show()
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
            BeaconCatalog
                .getKnownBeacons(this)
                .any { beacon -> beacon.uuid == normalizedUuid && beacon.uuid != originalUuid }
        if (alreadyExists) {
            return getString(R.string.known_beacon_uuid_duplicate)
        }

        return null
    }

    private fun confirmDelete(beacon: BeaconDefinition) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_known_beacon)
            .setMessage(getString(R.string.delete_known_beacon_message, beacon.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val updatedBeacons =
                    BeaconCatalog.getKnownBeacons(this).filterNot { it.uuid == beacon.uuid }
                BeaconCatalog.saveKnownBeacons(this, updatedBeacons)
                renderKnownBeacons()
                Toast.makeText(this, R.string.known_beacon_deleted, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
