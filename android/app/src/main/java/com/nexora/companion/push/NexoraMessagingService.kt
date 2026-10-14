package com.nexora.companion.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nexora.companion.MainActivity
import com.nexora.companion.NexoraApplication
import com.nexora.companion.R

class NexoraMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { PushSyncWorker.enqueue(applicationContext) }
    override fun onMessageReceived(message: RemoteMessage) {
        val c=(application as NexoraApplication).container
        val user=c.repository.session.value ?: return
        if(!c.preferences.consent.value.notifications || message.data["user_id"]!=user.userId)return
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("nexora_updates","Nexora updates",NotificationManager.IMPORTANCE_DEFAULT))
        val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Only data messages are sent: no OS-generated notifications bypass local consent.
        val notification=NotificationCompat.Builder(this,"nexora_updates").setSmallIcon(R.drawable.ic_notification).setContentTitle(message.data["title"]?.take(100) ?: "Nexora").setContentText(message.data["body"]?.take(300)).setContentIntent(intent).setAutoCancel(true).build()
        manager.notify((message.messageId ?: System.currentTimeMillis().toString()).hashCode(),notification)
    }
}
