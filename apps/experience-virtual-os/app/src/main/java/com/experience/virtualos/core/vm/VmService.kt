package com.experience.virtualos.core.vm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.experience.virtualos.App
import com.experience.virtualos.MainActivity
import com.experience.virtualos.R
import com.experience.virtualos.graph

/**
 * Keeps the app process alive while a QEMU process runs. Without a foreground
 * service, aggressive task killers (EMUI included) terminate the guest as soon
 * as the app leaves the screen.
 */
class VmService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Thread({ graph.vmController.stopAll() }, "evos-vm-stop").start()
            return START_NOT_STICKY
        }
        val controller = graph.vmController
        val id = controller.runningDeviceId
        val name = id?.let { graph.deviceStore.get(it)?.name } ?: "Virtual device"
        val accel = id?.let { controller.statusOf(it).accelerator } ?: ""
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(name, accel), type)
        if (id == null) stopSelf()
        return START_NOT_STICKY
    }

    private fun notification(name: String, accel: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, VmService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, App.CHANNEL_VM)
            .setSmallIcon(R.drawable.ic_stat_vm)
            .setContentTitle("$name is running")
            .setContentText("QEMU · ${accel.ifBlank { "TCG" }} · tap to open")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The guest keeps running in the background by design; nothing to do.
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        const val ACTION_STOP = "com.experience.virtualos.STOP_VM"
        private const val NOTIFICATION_ID = 17
    }
}
