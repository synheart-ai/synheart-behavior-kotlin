package ai.synheart.behavior

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * NotificationListenerService implementation for detecting notifications.
 *
 * This service automatically tracks received and opened notifications based on system events. It
 * requires the BIND_NOTIFICATION_LISTENER_SERVICE permission and the user must enable notification
 * access in system settings.
 *
 * To use this, add it to your AndroidManifest.xml:
 * ```xml
 * <service android:name="ai.synheart.behavior.SynheartNotificationListenerService"
 *          android:label="Synheart Behavior"
 *          android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
 *     <intent-filter>
 *         <action android:name="android.service.notification.NotificationListenerService" />
 *     </intent-filter>
 * </service>
 * ```
 */
class SynheartNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SynheartNotifService"
        private var behaviorInstance: SynheartBehavior? = null
        @Volatile private var isConnected = false
        @Volatile private var serviceInstance: SynheartNotificationListenerService? = null

        /**
         * Set the SynheartBehavior instance to report events to. This is typically called
         * automatically by SynheartBehavior when initialized.
         */
        internal fun setBehavior(behavior: SynheartBehavior?) {
            behaviorInstance = behavior
            Log.d(
                    TAG,
                    "Behavior instance set: ${behavior != null}, Service connected: $isConnected"
            )
            // If service is already created, check connection status
            val service = serviceInstance
            if (service != null) {
                try {
                    val ranking = service.currentRanking
                    if (ranking != null && !isConnected) {
                        isConnected = true
                        Log.d(TAG, "Service connection detected via currentRanking check")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Service not fully connected: ${e.message}")
                }
            }
        }

        /** Check if the notification listener service is currently connected. */
        internal fun isServiceConnected(): Boolean {
            // Also check if service instance exists and can access currentRanking
            val service = serviceInstance
            if (service != null) {
                try {
                    val ranking = service.currentRanking
                    if (ranking != null && !isConnected) {
                        isConnected = true
                        Log.d(TAG, "Connection status updated: service is connected")
                    }
                } catch (e: Exception) {
                    // Service not connected - ignore
                }
            }
            return isConnected
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        Log.d(
                TAG,
                "SynheartNotificationListenerService onCreate() called - service instance created"
        )

        // Try to detect connection status immediately
        try {
            val ranking = currentRanking
            if (ranking != null) {
                isConnected = true
                Log.d(TAG, "Service appears connected (can access currentRanking)")
                if (behaviorInstance != null) {
                    Log.d(TAG, "Behavior instance ready - notifications will be tracked")
                } else {
                    Log.w(TAG, "Service connected but behavior instance not set yet")
                }
            } else {
                Log.d(TAG, "Service created but currentRanking is null - waiting for connection")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Service not fully connected yet: ${e.message}")
        }

        // Also try to get active notifications as a test
        try {
            val activeNotifications = activeNotifications
            Log.d(
                    TAG,
                    "Service can access activeNotifications: ${activeNotifications != null}, count: ${activeNotifications?.size ?: 0}"
            )
            if (activeNotifications != null) {
                isConnected = true
                Log.d(TAG, "Service is connected! (verified via activeNotifications)")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cannot access activeNotifications yet: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        Log.d(TAG, "SynheartNotificationListenerService onDestroy() called")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.d(TAG, "NotificationListenerService connected! Can now detect notifications.")

        // Try to get active notifications to verify connection
        try {
            val activeNotifications = activeNotifications
            Log.d(
                    TAG,
                    "Service connected - can access ${activeNotifications?.size ?: 0} active notifications"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not access active notifications: ${e.message}")
        }

        // If behavior instance was set before connection, log that it's ready
        if (behaviorInstance != null) {
            Log.d(
                    TAG,
                    "Behavior instance is ready and service is connected - notifications will be tracked"
            )
        } else {
            Log.w(
                    TAG,
                    "Service connected but behavior instance not set yet. Will be set when session starts."
            )
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.w(TAG, "NotificationListenerService disconnected! Notifications will not be detected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        // If this is called, we're definitely connected
        if (!isConnected) {
            isConnected = true
            Log.d(TAG, "Service connected! (detected via onNotificationPosted)")
        }

        Log.d(TAG, "onNotificationPosted: ${sbn?.packageName}")
        if (sbn != null) {
            if (!shouldTrackNotification(sbn)) {
                Log.d(TAG, "Notification filtered out: ${sbn.packageName}")
                return
            }

            Log.d(TAG, "Reporting notification received: ${sbn.packageName}, behaviorInstance=${behaviorInstance != null}")
            // Report received
            if (behaviorInstance == null) {
                Log.w(TAG, "Behavior instance is null! Notification will not be tracked.")
            }
            behaviorInstance?.reportNotification("received")
        } else {
            Log.w(TAG, "onNotificationPosted: sbn is null")
        }
    }

    override fun onNotificationRemoved(
            sbn: StatusBarNotification?,
            rankingMap: RankingMap?,
            reason: Int
    ) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        Log.d(TAG, "onNotificationRemoved: ${sbn?.packageName}, reason=$reason")
        // REASON_CLICK = 1 means user clicked the notification
        if (reason == REASON_CLICK && sbn != null) {
            if (shouldTrackNotification(sbn)) {
                Log.d(TAG, "Reporting notification opened: ${sbn.packageName}")
                behaviorInstance?.reportNotificationOpened()
            }
        } else if ((reason == REASON_CANCEL || reason == REASON_CANCEL_ALL) && sbn != null) {
            if (shouldTrackNotification(sbn)) {
                Log.d(TAG, "Reporting notification ignored: ${sbn.packageName}")
                behaviorInstance?.reportNotificationIgnored()
            }
        }
    }

    /**
     * Determines if a notification should be tracked. Filters out:
     * - System notifications
     * - Silent notifications
     * - Persistent/ongoing notifications
     * - Group summary notifications
     * - Notifications from the app itself
     */
    private fun shouldTrackNotification(sbn: StatusBarNotification): Boolean {
        val packageName = sbn.packageName
        val notification = sbn.notification

        // Filter out system notifications
        if (packageName == "android" || packageName == "com.android.systemui") {
            Log.d(TAG, "Filtered: system notification from $packageName")
            return false
        }

        // Filter out group summary notifications
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            Log.d(TAG, "Filtered: group summary notification from $packageName")
            return false
        }

        // Filter out notifications from this app itself
        val appPackageName = applicationContext.packageName
        if (packageName == appPackageName) {
            Log.d(TAG, "Filtered: notification from app itself ($packageName)")
            return false
        }

        // Filter out silent notifications (LOW or MIN importance)
        val isSilentOrLowPriority =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val ranking = Ranking()
                    val rankingMap = currentRanking
                    if (rankingMap != null && rankingMap.getRanking(sbn.key, ranking)) {
                        val importance = ranking.importance
                        importance == NotificationManager.IMPORTANCE_LOW ||
                                importance == NotificationManager.IMPORTANCE_MIN
                    } else {
                        false
                    }
                } else {
                    val priority = notification.priority
                    priority == Notification.PRIORITY_LOW || priority == Notification.PRIORITY_MIN
                }

        if (isSilentOrLowPriority) {
            Log.d(TAG, "Filtered: low-importance/priority notification from $packageName")
            return false
        }

        // Filter out persistent/ongoing notifications
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            Log.d(TAG, "Filtered: ongoing notification from $packageName")
            return false
        }

        return true
    }
}
