package com.mj.blescorecounterremotecontroller.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mj.blescorecounterremotecontroller.BleScoreCounterApp
import com.mj.blescorecounterremotecontroller.R
import com.mj.blescorecounterremotecontroller.view.MainActivity
import timber.log.Timber


class BleService : Service() {

    companion object {
        const val CHANNEL_ID = "BleService"
        const val NOTIFICATION_CHANNEL_NAME = "BleServiceChannel"
        const val NOTIFICATION_ID = 1
    }

    private val app: BleScoreCounterApp by lazy {
        application as BleScoreCounterApp
    }

    private var isRunning = false

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // TODO Move to onStartCommand?

        val notificationIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notificationChannel = NotificationChannel(
            CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        this.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(notificationChannel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Score counter")
            .setContentText("Handling Bluetooth communication...")
            .setContentIntent(pendingIntent)
            .build()

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            Timber.i("BleService started.")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Timber.e( "App is not in valid state to start a foreground service.", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // onStartCommand could be called multiple times
        if (!isRunning) {
            isRunning = true

            if (app != null && app.hasBtPermissions()) {
                // TODO always check if app reference is valid before performing some operations
                // If null, stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Timber.i("BleService stopped.")
        Toast.makeText(this, "BleService stopped", Toast.LENGTH_SHORT).show()
    }

}