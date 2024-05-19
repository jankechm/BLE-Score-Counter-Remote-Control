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

        val notificationIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notificationChannel = NotificationChannel(
            CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        val notificationManager = this.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(notificationChannel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Score counter")
            .setContentText("Handling Bluetooth communication...")
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
//            notificationManager.notify(NOTIFICATION_ID, notification)
            Timber.i("BleService started.")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Timber.e( "App is not in valid state to start a foreground service.", e)
        }
    }

    // TODO not yet implemented
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // onStartCommand could be called multiple times
        if (!isRunning) {
            isRunning = true

            Timber.i("${this::class.java.simpleName} is running")

            if (app.hasBtPermissions()) {
                // TODO always check if app reference is valid before performing some operations
            }

            // TODO temporary testing; remove when not needed
            val worker = object : Thread() {
                override fun run() {
                    while (true) {
                        Timber.d("app == null: ${app == null}")
                        if (app != null) {
                            Timber.d("manuallyDisconnected: ${app.manuallyDisconnected}")
                        }
                        sleep(3000L)
                    }
                }
            }

            worker.start()
        }

        return START_STICKY
    }


    override fun onDestroy() {
        Timber.i("BleService stopped.")
        Toast.makeText(this, "BleService stopped", Toast.LENGTH_SHORT).show()
    }

}