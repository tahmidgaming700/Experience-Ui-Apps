package com.experience.clock.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Plays an alarm/timer sound on the ALARM stream (audible even when media is muted),
 * raising the volume gradually over [rampMs], plus an optional repeating vibration.
 *
 * Falls back to the default alarm sound, then to a ToneGenerator beep — the latter also
 * works during direct boot when media content URIs are not yet readable.
 */
class Ringer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var vibrating = false

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** @param ringtone null = default alarm sound, "" = silent, else a URI string. */
    fun start(ringtone: String?, vibrate: Boolean, rampMs: Long) {
        stop()
        if (ringtone != "") {
            val uri = ringtone?.let(Uri::parse) ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            val ok = (uri != null && play(uri, rampMs)) ||
                play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), rampMs) ||
                play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), rampMs)
            if (!ok) beep()
        }
        if (vibrate) {
            val vib = context.getSystemService(Vibrator::class.java)
            if (vib?.hasVibrator() == true) {
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0), attrs)
                vibrating = true
            }
        }
    }

    private fun play(uri: Uri?, rampMs: Long): Boolean {
        uri ?: return false
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.prepare()
            val startVolume = if (rampMs > 0) 0.05f else 1f
            mp.setVolume(startVolume, startVolume)
            mp.start()
            player = mp
            if (rampMs > 0) ramp(System.currentTimeMillis(), rampMs)
            true
        } catch (e: Exception) {
            player?.release(); player = null
            false
        }
    }

    /** Raises the volume in steps; the perceived loudness curve is roughly quadratic. */
    private fun ramp(start: Long, rampMs: Long) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val p = player ?: return
                val f = ((System.currentTimeMillis() - start).toFloat() / rampMs).coerceIn(0f, 1f)
                val v = 0.05f + 0.95f * f * f
                runCatching { p.setVolume(v, v) }
                if (f < 1f) handler.postDelayed(this, 250)
            }
        }, 250)
    }

    private fun beep() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            tone = tg
            handler.post(object : Runnable {
                override fun run() {
                    val t = tone ?: return
                    t.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                    handler.postDelayed(this, 1200)
                }
            })
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        tone?.release()
        tone = null
        if (vibrating) context.getSystemService(Vibrator::class.java)?.cancel()
        vibrating = false
    }
}
