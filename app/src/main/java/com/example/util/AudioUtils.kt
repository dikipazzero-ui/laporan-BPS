package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class VoicePlaybackState(
    val activeMessageId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0
)

class VoiceRecorderController(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecording(): Boolean {
        stopAndDiscard()
        return try {
            val audioDir = File(context.cacheDir, "voice_rec").apply { mkdirs() }
            val file = File(audioDir, "vn_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(24000)
                setAudioChannels(1)
                setMaxDuration(60_000)
                setMaxFileSize(240_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            outputFile = file
            startTimeMs = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            stopAndDiscard()
            false
        }
    }

    fun stopAndEncode(): Pair<String, Int>? {
        val file = outputFile
        val elapsedSec = (((System.currentTimeMillis() - startTimeMs) / 1000L).toInt()).coerceAtLeast(1)
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Ignore stop failure on ultra-short tap
        } finally {
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
            recorder = null
            outputFile = null
        }

        if (file == null || !file.exists() || file.length() < 128L) {
            file?.delete()
            return null
        }

        return try {
            val bytes = file.readBytes()
            file.delete()
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (encoded.length > 345_000) null else encoded to elapsedSec
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun stopAndDiscard() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class VoicePlayerController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    fun togglePlay(messageId: String, audioBase64: String?) {
        if (audioBase64.isNullOrBlank()) return
        val current = _state.value
        if (current.activeMessageId == messageId && current.isPlaying) {
            pause()
            return
        }
        if (current.activeMessageId == messageId && !current.isPlaying && mediaPlayer != null) {
            resume()
            return
        }
        playNew(messageId, audioBase64)
    }

    private fun playNew(messageId: String, audioBase64: String) {
        stop()
        try {
            val dir = File(context.cacheDir, "voice_cache").apply { mkdirs() }
            val safeName = messageId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val file = File(dir, "$safeName.m4a")
            if (!file.exists()) {
                val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
                file.writeBytes(bytes)
            }
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                }
                start()
            }
            mediaPlayer = player
            _state.value = VoicePlaybackState(
                activeMessageId = messageId,
                isPlaying = true,
                positionMs = 0,
                durationMs = player.duration.coerceAtLeast(1)
            )
            startProgressPolling()
        } catch (_: Exception) {
            stop()
        }
    }

    private fun pause() {
        try {
            mediaPlayer?.pause()
            progressJob?.cancel()
            _state.value = _state.value.copy(isPlaying = false)
        } catch (_: Exception) {
            stop()
        }
    }

    private fun resume() {
        try {
            mediaPlayer?.start()
            _state.value = _state.value.copy(isPlaying = true)
            startProgressPolling()
        } catch (_: Exception) {
            stop()
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive && mediaPlayer != null && _state.value.isPlaying) {
                val mp = mediaPlayer ?: break
                if (mp.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = mp.currentPosition,
                        durationMs = mp.duration.coerceAtLeast(1)
                    )
                }
                delay(150L)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _state.value = VoicePlaybackState()
    }
}
