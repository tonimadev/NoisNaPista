package com.ipirangatech.fidd.core.sensor.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.ipirangatech.fidd.core.analytics.AnalyticsEvent
import com.ipirangatech.fidd.core.analytics.AnalyticsTracker
import com.ipirangatech.fidd.core.sensor.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {
    @Inject
    lateinit var potholeDetector: PotholeDetector

    @Inject
    lateinit var analytics: AnalyticsTracker

    // Registrado aqui e não no botão da Home: a detecção também começa por reinício do sistema
    // (START_STICKY) e para pela notificação, sem passar pela tela.
    private var startedAtMillis = 0L
    private var stopSource = AnalyticsEvent.StopSource.APP

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        potholeDetector.startDetection()
        startedAtMillis = SystemClock.elapsedRealtime()
        analytics.log(AnalyticsEvent.DetectionStarted)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Ação "Pausar detecção" da notificação: sem ela, o único jeito de desligar era abrir o
        // app e achar o botão — e quem não sabe desligar tende a desinstalar.
        if (intent?.action == ACTION_STOP) {
            stopSource = AnalyticsEvent.StopSource.NOTIFICATION
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        potholeDetector.stopDetection()
        // Minutos inteiros: basta para saber quanto tempo a detecção fica ligada, sem um valor
        // exato que, junto do horário, ajude a reconhecer uma viagem específica.
        val minutes = (SystemClock.elapsedRealtime() - startedAtMillis) / MILLIS_PER_MINUTE
        analytics.log(AnalyticsEvent.DetectionStopped(stopSource, minutes))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        // Tocar na notificação abre o app (antes não fazia nada). O launch intent do pacote evita
        // que este módulo core precise conhecer a MainActivity do módulo :app.
        val openAppIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.tracking_notification_stop_action), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.tracking_notification_channel_name),
                    // Notificação de serviço contínuo: fica visível, mas sem som/vibração a cada viagem.
                    NotificationManager.IMPORTANCE_LOW,
                )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "tracking_channel"
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val ACTION_STOP = "com.ipirangatech.fidd.action.STOP_TRACKING"
    }
}
