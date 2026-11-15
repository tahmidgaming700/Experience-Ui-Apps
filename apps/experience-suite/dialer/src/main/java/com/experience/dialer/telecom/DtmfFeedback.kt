package com.experience.dialer.telecom

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings

/**
 * Local keypad tones while dialling. Respects Settings.System.DTMF_TONE_WHEN_DIALING
 * ("Dial pad tones") and stays silent in silent/vibrate ringer modes.
 */
class DtmfFeedback(private val context: Context) {
    private var generator: ToneGenerator? = null

    private fun enabled(): Boolean {
        val setting = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1)
        }.getOrDefault(1)
        val audio = context.getSystemService(AudioManager::class.java)
        return setting == 1 && audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    fun play(key: Char) {
        val tone = toneFor(key) ?: return
        if (!enabled()) return
        val gen = generator ?: runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 80) }.getOrNull()?.also { generator = it } ?: return
        gen.startTone(tone, 150)
    }

    fun release() {
        generator?.release()
        generator = null
    }

    companion object {
        fun toneFor(key: Char): Int? = when (key) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> null
        }
    }
}
