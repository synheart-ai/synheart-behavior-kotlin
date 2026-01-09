package ai.synheart.behavior.example

import ai.synheart.behavior.BehaviorConfig
import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorSessionSummary
import ai.synheart.behavior.SynheartBehavior
import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

class ExampleApplication : Application() {

    lateinit var behavior: SynheartBehavior
        private set

    // Temporary storage for session data
    var lastSessionSummary: BehaviorSessionSummary? = null
    var lastSessionEvents: MutableList<BehaviorEvent> = mutableListOf()

    // Track recent notification removals to detect opens (package -> removal time)
    val recentNotificationRemovals = ConcurrentHashMap<String, Long>()

    // Track current foreground package
    private var currentForegroundPackage: String? = null

    override fun onCreate() {
        super.onCreate()

        val config =
                BehaviorConfig(
                        enableInputSignals = true,
                        enableAttentionSignals = true,
                        enableMotionLite = true // Enable motion data collection
                )

        behavior = SynheartBehavior.create(this, config)
        behavior.initialize()

        // Register lifecycle callbacks to track app switches for notification open detection
        registerActivityLifecycleCallbacks(
                object : ActivityLifecycleCallbacks {
                    override fun onActivityResumed(activity: Activity) {
                        val packageName = activity.packageName
                        currentForegroundPackage = packageName

                        // Check if this app switch corresponds to a notification open
                        checkNotificationOpen(packageName)
                    }

                    override fun onActivityCreated(
                            activity: Activity,
                            savedInstanceState: Bundle?
                    ) {}
                    override fun onActivityStarted(activity: Activity) {}
                    override fun onActivityPaused(activity: Activity) {}
                    override fun onActivityStopped(activity: Activity) {}
                    override fun onActivitySaveInstanceState(
                            activity: Activity,
                            outState: Bundle
                    ) {}
                    override fun onActivityDestroyed(activity: Activity) {}
                }
        )
    }

    /**
     * Check if an app switch to the given package corresponds to opening a notification. Matches
     * Flutter SDK behavior.
     */
    private fun checkNotificationOpen(packageName: String) {
        val removalTime = recentNotificationRemovals.remove(packageName)
        if (removalTime != null) {
            val timeSinceRemoval = System.currentTimeMillis() - removalTime
            // If app switched to this package within 3 seconds of notification removal, it was
            // opened
            if (timeSinceRemoval <= 3000) {
                android.util.Log.d(
                        "ExampleApplication",
                        "Notification opened: $packageName (switched after ${timeSinceRemoval}ms)"
                )

                // Report as opened
                // The handler in NotificationListenerService will check if entry was removed
                // and won't fire the "ignored" event
                behavior.reportNotificationOpened()
            } else {
                // Too late - put it back and let the timer handle it
                recentNotificationRemovals.put(packageName, removalTime)
            }
        }
    }
}
