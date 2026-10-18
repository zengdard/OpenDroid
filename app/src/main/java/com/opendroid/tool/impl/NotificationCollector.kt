package com.opendroid.tool.impl

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Notification active captée par le listener. */
data class CapturedNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

/** Stocke les notifications actives pour l'outil `notifications_list`. */
object NotificationCollector {
    private val map = LinkedHashMap<String, CapturedNotification>()

    @Synchronized
    fun add(n: CapturedNotification) {
        map[n.packageName + n.title] = n
        while (map.size > 50) map.remove(map.keys.first())
    }

    @Synchronized
    fun remove(key: String) {
        map.remove(key)
    }

    @Synchronized
    fun active(): List<CapturedNotification> = map.values.sortedByDescending { it.postedAt }
}

/** Service d'écoute des notifications (à activer dans Paramètres → Accès notifications). */
class OpenDroidNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        NotificationCollector.add(CapturedNotification(sbn.packageName, title, text, sbn.postTime))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence("android.title")?.toString().orEmpty()
        NotificationCollector.remove(sbn.packageName + title)
    }
}
