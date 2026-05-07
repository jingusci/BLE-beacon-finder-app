package com.example.blebeaconfinder

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.util.UUID

class CustomAudioActivity : AppCompatActivity() {

    private lateinit var backButton: MaterialButton
    private lateinit var recordButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var nameInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var audioListView: ListView

    private var mediaRecorder: MediaRecorder? = null
    private var pendingAudioFile: File? = null
    private var isRecording = false
    private var hasRecordedAudio = false

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startRecording()
            } else {
                Toast.makeText(this, R.string.custom_audio_permission_required, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_audio)

        backButton = findViewById(R.id.backCustomAudioButton)
        recordButton = findViewById(R.id.recordCustomAudioButton)
        saveButton = findViewById(R.id.saveCustomAudioButton)
        nameInput = findViewById(R.id.customAudioNameInput)
        statusText = findViewById(R.id.customAudioStatusText)
        emptyText = findViewById(R.id.emptyCustomAudioText)
        audioListView = findViewById(R.id.customAudioListView)

        backButton.setOnClickListener { finish() }
        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                ensureMicrophonePermissionAndRecord()
            }
        }
        saveButton.setOnClickListener { saveRecordedAudio() }
        audioListView.setOnItemClickListener { _, _, position, _ ->
            CustomAudioStore.getCustomAudios(this).getOrNull(position)?.let(::showAudioActionsDialog)
        }

        renderCustomAudios()
        updateSaveButtonState()
    }

    override fun onStop() {
        if (isRecording) {
            stopRecording()
        }
        super.onStop()
    }

    override fun onDestroy() {
        mediaRecorder?.release()
        if (hasRecordedAudio.not()) {
            pendingAudioFile?.delete()
        }
        super.onDestroy()
    }

    private fun ensureMicrophonePermissionAndRecord() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        pendingAudioFile?.delete()
        pendingAudioFile = CustomAudioStore.newAudioFile(this)
        hasRecordedAudio = false

        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

        runCatching {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(pendingAudioFile?.absolutePath)
                prepare()
                start()
            }
        }.onSuccess {
            mediaRecorder = recorder
            isRecording = true
            recordButton.text = getString(R.string.stop_recording)
            saveButton.isEnabled = false
            updateSaveButtonState()
            statusText.text = getString(R.string.custom_audio_status_recording)
        }.onFailure {
            recorder.release()
            pendingAudioFile?.delete()
            pendingAudioFile = null
            Toast.makeText(this, R.string.custom_audio_recording_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        val recorder = mediaRecorder ?: return

        runCatching {
            recorder.stop()
        }

        recorder.release()
        mediaRecorder = null
        isRecording = false
        hasRecordedAudio = pendingAudioFile?.exists() == true
        recordButton.text = getString(R.string.start_recording)
        saveButton.isEnabled = hasRecordedAudio
        updateSaveButtonState()
        statusText.text =
            if (hasRecordedAudio) getString(R.string.custom_audio_status_ready) else getString(R.string.custom_audio_recording_failed)
    }

    private fun saveRecordedAudio() {
        val audioName = nameInput.text?.toString()?.trim().orEmpty()
        val audioFile = pendingAudioFile
        if (audioName.isBlank()) {
            Toast.makeText(this, R.string.custom_audio_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (audioFile == null || audioFile.exists().not()) {
            Toast.makeText(this, R.string.custom_audio_record_first, Toast.LENGTH_SHORT).show()
            return
        }

        CustomAudioStore.addCustomAudio(
            this,
            CustomAudioDefinition(
                id = UUID.randomUUID().toString(),
                name = audioName,
                fileName = audioFile.name,
            )
        )

        pendingAudioFile = null
        hasRecordedAudio = false
        saveButton.isEnabled = false
        updateSaveButtonState()
        nameInput.setText("")
        statusText.text = getString(R.string.custom_audio_status_idle)
        Toast.makeText(this, R.string.custom_audio_saved, Toast.LENGTH_SHORT).show()
        renderCustomAudios()
    }

    private fun renderCustomAudios() {
        val audios = CustomAudioStore.getCustomAudios(this)
        emptyText.visibility = if (audios.isEmpty()) View.VISIBLE else View.GONE
        audioListView.visibility = if (audios.isEmpty()) View.GONE else View.VISIBLE
        audioListView.adapter = CustomAudioAdapter(audios)
    }

    private fun showAudioActionsDialog(audio: CustomAudioDefinition) {
        val actions = arrayOf(
            getString(R.string.rename_audio),
            getString(R.string.delete_audio),
        )

        AlertDialog.Builder(this)
            .setTitle(audio.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showRenameDialog(audio)
                    1 -> confirmDeleteAudio(audio)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(audio: CustomAudioDefinition) {
        val input = TextInputEditText(this).apply {
            setText(audio.name)
            setSelection(audio.name.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.rename_audio)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isBlank()) {
                    Toast.makeText(this, R.string.custom_audio_name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                CustomAudioStore.renameCustomAudio(this, audio.id, newName)
                renderCustomAudios()
                Toast.makeText(this, R.string.custom_audio_renamed, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun confirmDeleteAudio(audio: CustomAudioDefinition) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_audio)
            .setMessage(getString(R.string.delete_audio_message, audio.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                CustomAudioStore.deleteCustomAudio(this, audio.id)
                renderCustomAudios()
                Toast.makeText(this, R.string.custom_audio_deleted, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun updateSaveButtonState() {
        saveButton.alpha = if (saveButton.isEnabled) 1f else 0.82f
    }

    private inner class CustomAudioAdapter(
        private val audios: List<CustomAudioDefinition>,
    ) : BaseAdapter() {
        override fun getCount(): Int = audios.size

        override fun getItem(position: Int): CustomAudioDefinition = audios[position]

        override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rowView = convertView ?: layoutInflater.inflate(R.layout.item_custom_audio, parent, false)
            val audio = getItem(position)
            rowView.findViewById<TextView>(R.id.customAudioNameText).text = audio.name
            rowView.findViewById<TextView>(R.id.customAudioFileText).text = audio.fileName
            return rowView
        }
    }
}
