package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class VoiceAudioEngine {
    private val _amplitudeLevel = MutableStateFlow(0f)
    val amplitudeLevel: StateFlow<Float> = _amplitudeLevel.asStateFlow()

    private val _isMicActive = MutableStateFlow(false)
    val isMicActive: StateFlow<Boolean> = _isMicActive.asStateFlow()

    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope, hasPermission: Boolean) {
        stopCapture()
        _isMicActive.value = true
        if (!hasPermission) {
            _amplitudeLevel.value = 0f
            return
        }

        recordJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                _amplitudeLevel.value = 0f
                return@launch
            }

            try {
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    return@launch
                }
                audioRecord = recorder
                recorder.startRecording()
                val buffer = ShortArray(minBuffer / 2)

                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var peak = 0
                        for (i in 0 until read) {
                            val v = abs(buffer[i].toInt())
                            if (v > peak) peak = v
                        }
                        val normalized = (peak / 12000f).coerceIn(0f, 1f)
                        _amplitudeLevel.value = normalized
                    }
                    delay(80L)
                }
            } catch (e: SecurityException) {
                Log.w("VoiceAudioEngine", "RECORD_AUDIO permission missing: ${e.message}")
            } catch (e: Exception) {
                Log.w("VoiceAudioEngine", "AudioRecord unavailable on device: ${e.message}")
            } finally {
                releaseRecorder()
            }
        }
    }

    fun stopCapture() {
        _isMicActive.value = false
        _amplitudeLevel.value = 0f
        recordJob?.cancel()
        recordJob = null
        releaseRecorder()
    }

    private fun releaseRecorder() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }
}
