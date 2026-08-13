package com.bru

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bru.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BruService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: SmsRepository
    private var observer: SmsObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val repository = SmsRepository(AppDatabase.get(this).messages(), contentResolver)
        repo = repository
        startForegroundNotice()
        val app = applicationContext
        IrohNet.startServing(app) { dispatch(app, repository, it) }
        ensureSmsIngest()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureSmsIngest()
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        scope.cancel()
        IrohNet.stopServing()
        super.onDestroy()
    }

    private fun ensureSmsIngest() {
        if (observer != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "READ_SMS not granted — SMS ingest paused until it is")
            return
        }
        scope.launch { ingest(notify = false) }
        val obs = SmsObserver(Handler(Looper.getMainLooper())) {
            scope.launch { ingest(notify = true) }
        }
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, obs)
        observer = obs
    }

    private suspend fun ingest(notify: Boolean) {
        val added = try {
            repo.ingestNew()
        } catch (e: SecurityException) {
            Log.w(TAG, "SMS ingest skipped — READ_SMS revoked")
            return
        }
        Log.i(TAG, "ingest: $added new")
        if (notify && added > 0) WakeNotifier(this).fire()
    }

    private fun startForegroundNotice() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Bru", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Bru active")
            .setSmallIcon(R.drawable.ic_bru_notification)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    companion object {
        private const val TAG = "bru"
        private const val CHANNEL = "bru_status"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BruService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BruService::class.java))
        }
    }
}
