package ai.synheart.behavior.example

import ai.synheart.behavior.BehaviorEvent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val MAX_EVENTS_TO_KEEP = 50
        private const val EVENT_RETENTION_MINUTES = 5
    }

    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var sessionInfoLayout: LinearLayout
    private lateinit var sessionIdText: TextView
    private lateinit var startSessionBtn: Button
    private lateinit var endSessionBtn: Button

    private lateinit var requestNotificationPermissionBtn: Button
    private lateinit var requestCallPermissionBtn: Button
    private lateinit var refreshStatsBtn: Button

    private lateinit var testRecyclerView: RecyclerView
    private lateinit var mainScrollView: ScrollView
    private lateinit var statsCard: com.google.android.material.card.MaterialCardView
    private lateinit var scrollVelocityValue: TextView
    private lateinit var appSwitchesPerMinuteValue: TextView
    private lateinit var stabilityIndexValue: TextView
    private lateinit var typingTestField: BehaviorEditText

    private val behavior by lazy { (application as ExampleApplication).behavior }
    private var currentSessionId: String? = null
    private var isSessionActive = false
    private val sessionEvents = mutableListOf<BehaviorEvent>()
    private val allEvents = mutableListOf<BehaviorEvent>()

    // Auto-end session after 1 minute in background
    private var backgroundTimer: Job? = null
    private var sessionAutoEnded = false
    private var autoEndedSummary: ai.synheart.behavior.BehaviorSessionSummary? = null
    private var autoEndedEvents: List<BehaviorEvent>? = null

    // Window features removed - not needed for real-time event tracking
    // private var shortWindowFeatures: ai.synheart.behavior.BehaviorWindowFeatures? = null
    // private var longWindowFeatures: ai.synheart.behavior.BehaviorWindowFeatures? = null

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // Swipe tracking
    private var touchStartX: Float? = null
    private var touchStartY: Float? = null
    private var touchStartTime: Long? = null

    // Permission request launchers
    private val notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    AlertDialog.Builder(this)
                            .setTitle("Permission Granted")
                            .setMessage(
                                    "Notification permission has been granted. Hot restarting collectors..."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    // Hot restart collectors to pick up permission changes (like Flutter hot
                    // restart)
                    try {
                        behavior.hotRestartCollectors()
                        android.util.Log.d("MainActivity", "Collectors hot restarted successfully")
                    } catch (e: Exception) {
                        android.util.Log.d(
                                "MainActivity",
                                "No active session - hot restart skipped"
                        )
                    }
                } else {
                    AlertDialog.Builder(this)
                            .setTitle("Permission Denied")
                            .setMessage(
                                    "Notification permission was denied. Notification events will not be tracked."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                }
            }

    private val callPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    AlertDialog.Builder(this)
                            .setTitle("Permission Granted")
                            .setMessage(
                                    "Phone state permission has been granted. Hot restarting collectors to enable call tracking..."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    // Hot restart collectors to pick up permission changes (like Flutter hot
                    // restart)
                    try {
                        behavior.hotRestartCollectors()
                        android.util.Log.d("MainActivity", "Collectors hot restarted successfully")
                    } catch (e: Exception) {
                        // If no session is active, just reinitialize phone state listener
                        behavior.reinitializePhoneStateListener()
                        android.util.Log.d(
                                "MainActivity",
                                "Phone state listener reinitialized (no active session)"
                        )
                    }
                } else {
                    AlertDialog.Builder(this)
                            .setTitle("Permission Denied")
                            .setMessage(
                                    "Phone state permission was denied. Call events will not be tracked."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupRecyclerView()
        setupEventHandlers()
        setupInputListeners() // Attach gesture tracking to views
        setupKeyboardDismiss() // Must be called after setupInputListeners to chain listeners
        // Window features removed - not needed for real-time event tracking
        // setupWindowFeaturesListeners()

        // Add lifecycle observer to detect app background/foreground
        lifecycle.addObserver(
                object : LifecycleEventObserver {
                    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                        when (event) {
                            Lifecycle.Event.ON_PAUSE -> {
                                onAppBackgrounded()
                            }
                            Lifecycle.Event.ON_RESUME -> {
                                onAppForegrounded()
                            }
                            else -> {}
                        }
                    }
                }
        )

        // Auto-start session for testing (matching Flutter app behavior)
        lifecycleScope.launch {
            delay(500) // Small delay to ensure UI is ready
            startSession()
        }
    }

    private fun initializeViews() {
        statusIcon = findViewById(R.id.statusIcon)
        statusText = findViewById(R.id.statusText)
        sessionInfoLayout = findViewById(R.id.sessionInfoLayout)
        sessionIdText = findViewById(R.id.sessionIdText)
        startSessionBtn = findViewById(R.id.startSessionBtn)
        endSessionBtn = findViewById(R.id.endSessionBtn)

        requestNotificationPermissionBtn = findViewById(R.id.requestNotificationPermissionBtn)
        requestCallPermissionBtn = findViewById(R.id.requestCallPermissionBtn)
        refreshStatsBtn = findViewById(R.id.refreshStatsBtn)

        testRecyclerView = findViewById(R.id.testRecyclerView)
        mainScrollView = findViewById(R.id.mainScrollView)
        statsCard = findViewById(R.id.statsCard)
        scrollVelocityValue = findViewById(R.id.scrollVelocityValue)
        appSwitchesPerMinuteValue = findViewById(R.id.appSwitchesPerMinuteValue)
        stabilityIndexValue = findViewById(R.id.stabilityIndexValue)
        typingTestField = findViewById(R.id.typingTestField)

        updateUI()
    }

    private fun setupRecyclerView() {
        // Match Flutter app: 10 items (itemCount: 10)
        val adapter = TestItemAdapter((0..9).map { "Item $it" })
        testRecyclerView.layoutManager = LinearLayoutManager(this)
        testRecyclerView.adapter = adapter
        testRecyclerView.setHasFixedSize(false)
        testRecyclerView.isNestedScrollingEnabled = false

        // Automatically track gestures on this RecyclerView
        behavior.attachToView(testRecyclerView)
    }

    private fun setupEventHandlers() {
        // Set up SDK event handler
        behavior.setEventHandler { event -> runOnUiThread { handleEvent(event) } }

        startSessionBtn.setOnClickListener { startSession() }

        endSessionBtn.setOnClickListener { endSession() }

        refreshStatsBtn.setOnClickListener { refreshStats() }

        requestNotificationPermissionBtn.setOnClickListener { requestNotificationPermission() }

        requestCallPermissionBtn.setOnClickListener { requestCallPermission() }

        // Set up typing field event handler
        typingTestField.onTypingEvent = { event ->
            // Update session ID if we have an active session
            val sessionId = currentSessionId ?: "current"
            val updatedEvent = event.copy(sessionId = sessionId)
            behavior.sendEvent(updatedEvent)
        }
    }

    private fun setupInputListeners() {
        // Attach gesture tracking to views
        behavior.attachToView(mainScrollView)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        // Handle keyboard dismissal when touching outside EditText
        // This is called before touch events are dispatched to child views
        if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
            val focusedView = currentFocus
            if (focusedView != null && focusedView is android.widget.EditText) {
                // Check if touch is outside the focused EditText
                val editTextRect = android.graphics.Rect()
                focusedView.getGlobalVisibleRect(editTextRect)
                val touchX = ev.rawX.toInt()
                val touchY = ev.rawY.toInt()

                if (!editTextRect.contains(touchX, touchY)) {
                    // Touch is outside EditText - dismiss keyboard
                    val imm =
                            getSystemService(Context.INPUT_METHOD_SERVICE) as
                                    android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
                    focusedView.clearFocus()
                }
            }
        }
        // Let the event continue to child views (including gesture collector)
        return super.dispatchTouchEvent(ev)
    }

    private fun setupKeyboardDismiss() {
        // Keyboard dismissal is now handled in dispatchTouchEvent
        // No need for separate setup
    }

    // Touch event handling is now done via attachToView or internal TransparentGestureOverlay if
    // implemented
    // For this example, we rely on attachToView for specific views.
    // If we wanted global touch tracking, we would need to attach to the root view.
    // However, since we removed reportTouchDown/Up from public API, we remove these overrides.

    private fun handleEvent(event: BehaviorEvent) {
        // Add to all events list
        allEvents.add(0, event)

        // Keep only recent events within retention period
        val now = System.currentTimeMillis()
        val cutoffTime = now - (EVENT_RETENTION_MINUTES * 60 * 1000)
        allEvents.removeAll {
            val eventTime =
                    try {
                        java.time.Instant.parse(it.timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
            eventTime < cutoffTime
        }
        if (allEvents.size > MAX_EVENTS_TO_KEEP) {
            allEvents.removeAt(allEvents.size - 1)
        }

        // Store events for current session
        if (isSessionActive && currentSessionId != null) {
            if (event.sessionId == currentSessionId) {
                sessionEvents.add(event)
            }
        }
    }

    private fun startSession() {
        try {
            val session = behavior.startSession()
            currentSessionId = session.sessionId
            isSessionActive = true
            sessionEvents.clear()
            updateUI()
        } catch (e: Exception) {
            showError(getString(R.string.failed_to_start_session, e.message))
        }
    }

    private fun endSession(autoEnded: Boolean = false) {
        if (currentSessionId == null || !isSessionActive) {
            if (!autoEnded) {
                showError(getString(R.string.no_active_session))
            }
            return
        }

        // End any active typing sessions (keyboard might still be open)
        BehaviorEditText.endAllTypingSessions()

        // Cancel background timer if it exists
        backgroundTimer?.cancel()
        backgroundTimer = null

        try {
            val summary = behavior.endSession(currentSessionId!!)
            // Use events from sessionEvents (collected via event handler)
            // These should match events in SessionTracker
            val events = ArrayList(sessionEvents)

            // Debug: Log event count
            android.util.Log.d(
                    "MainActivity",
                    "Session ended (autoEnded: $autoEnded). Events count: ${events.size}"
            )
            android.util.Log.d(
                    "MainActivity",
                    "Summary total events: ${summary.activitySummary.totalEvents}"
            )

            currentSessionId = null
            isSessionActive = false
            sessionEvents.clear()
            updateUI()

            if (autoEnded) {
                // Store summary and events to show when app returns to foreground
                sessionAutoEnded = true
                autoEndedSummary = summary
                autoEndedEvents = events
                android.util.Log.d(
                        "MainActivity",
                        "Session auto-ended. Will show results when app returns to foreground."
                )
            } else {
                // Store in Application for SessionResultsActivity
                (application as ExampleApplication).lastSessionSummary = summary
                (application as ExampleApplication).lastSessionEvents = events

                // Navigate to session results immediately
                startActivity(Intent(this, SessionResultsActivity::class.java))
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to end session: $e")
            if (!autoEnded) {
                showError(getString(R.string.failed_to_end_session, e.message))
            }
        }
    }

    private fun onAppBackgrounded() {
        // End any active typing sessions when app goes to background
        BehaviorEditText.endAllTypingSessions()

        if (isSessionActive) {
            // Start timer to auto-end session after 1 minute
            backgroundTimer =
                    lifecycleScope.launch {
                        delay(60000) // 1 minute
                        if (isSessionActive) {
                            android.util.Log.d(
                                    "MainActivity",
                                    "App has been in background for 1 minute. Auto-ending session..."
                            )
                            endSession(autoEnded = true)
                        }
                    }
        }
    }

    private fun onAppForegrounded() {
        // Cancel background timer if app returns before 1 minute
        backgroundTimer?.cancel()
        backgroundTimer = null

        // If session was auto-ended, show results
        if (sessionAutoEnded) {
            android.util.Log.d(
                    "MainActivity",
                    "App returned to foreground. Session was auto-ended. Showing results..."
            )

            // Store values in local variables to avoid null issues
            val summary = autoEndedSummary
            val events = autoEndedEvents

            // Reset auto-ended flags immediately
            sessionAutoEnded = false
            autoEndedSummary = null
            autoEndedEvents = null

            if (summary != null && events != null) {
                // Store in Application for SessionResultsActivity
                (application as ExampleApplication).lastSessionSummary = summary
                (application as ExampleApplication).lastSessionEvents = ArrayList(events)

                // Navigate to session results
                startActivity(Intent(this, SessionResultsActivity::class.java))
            }
        }
    }

    private fun updateUI() {
        // Update status
        statusIcon.setImageResource(android.R.drawable.ic_dialog_info)
        statusIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))
        statusText.text = getString(R.string.initialized)

        // Update session info
        if (isSessionActive && currentSessionId != null) {
            sessionInfoLayout.visibility = View.VISIBLE
            sessionIdText.text = getString(R.string.session_active, currentSessionId)
        } else {
            sessionInfoLayout.visibility = View.GONE
        }

        // Update buttons
        startSessionBtn.isEnabled = !isSessionActive

        endSessionBtn.isEnabled = isSessionActive
        if (isSessionActive) {
            endSessionBtn.setBackgroundColor(android.graphics.Color.RED)
            endSessionBtn.setTextColor(android.graphics.Color.WHITE)
            endSessionBtn.text = getString(R.string.end_session)
        } else {
            endSessionBtn.setBackgroundColor(android.graphics.Color.LTGRAY)
            endSessionBtn.setTextColor(android.graphics.Color.BLACK)
            endSessionBtn.text = "End Session (Disabled)"
        }

        // Show/hide stats card (only show when session is NOT active)
        statsCard.visibility = if (!isSessionActive) View.VISIBLE else View.GONE
    }

    private fun refreshStats() {
        try {
            val stats = behavior.getCurrentStats()
            scrollVelocityValue.text =
                    stats.scrollVelocity?.let { String.format("%.2f", it) }
                            ?: getString(R.string.na)
            appSwitchesPerMinuteValue.text = stats.appSwitchesPerMinute.toString()
            stabilityIndexValue.text =
                    stats.stabilityIndex?.let { String.format("%.2f", it) }
                            ?: getString(R.string.na)
        } catch (e: Exception) {
            showError(getString(R.string.failed_to_get_stats, e.message))
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners =
                Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        // Check for the specific service class name to avoid false positives from old services
        val expectedService = "ai.synheart.behavior.SynheartNotificationListenerService"
        return enabledListeners != null &&
                (enabledListeners.contains(expectedService) ||
                        enabledListeners.contains(packageName) &&
                                !enabledListeners.contains("BehaviorNotificationListenerService"))
    }

    private fun requestNotificationPermission() {
        val hasPermission = behavior.checkNotificationPermission()
        val hasListenerEnabled = isNotificationListenerEnabled()
        val isListenerConnected = behavior.checkNotificationListenerConnected()

        android.util.Log.d(
                "MainActivity",
                "Notification status: permission=$hasPermission, listenerEnabled=$hasListenerEnabled, listenerConnected=$isListenerConnected"
        )

        if (hasPermission && hasListenerEnabled && isListenerConnected) {
            AlertDialog.Builder(this)
                    .setTitle("Permission Status")
                    .setMessage(
                            "Notification permission and listener service are both enabled and connected. Notifications will be automatically detected."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            return
        }

        // If enabled but not connected, show a message
        if (hasListenerEnabled && !isListenerConnected) {
            AlertDialog.Builder(this)
                    .setTitle("Notification Service Not Connected")
                    .setMessage(
                            "Notification access is enabled in settings, but the service is not connected yet.\n\n" +
                                    "Please try:\n" +
                                    "1. Disable and re-enable notification access in settings\n" +
                                    "2. Restart the app\n" +
                                    "3. If the issue persists, restart your device"
                    )
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent =
                                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            return
        }

        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Request POST_NOTIFICATIONS permission on Android 13+
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        // Show dialog to enable NotificationListenerService
        AlertDialog.Builder(this)
                .setTitle("Enable Notification Detection")
                .setMessage(
                        if (hasListenerEnabled) {
                            "Notification listener is enabled. Notifications will be automatically detected."
                        } else {
                            "To automatically detect notifications from other apps, you need to enable Notification Access in system settings.\n\n" +
                                    "1. Tap 'Open Settings' below\n" +
                                    "2. Find this app in the list\n" +
                                    "3. Enable the toggle\n" +
                                    "4. Return to this app"
                        }
                )
                .setPositiveButton(if (hasListenerEnabled) "OK" else "Open Settings") { _, _ ->
                    if (!hasListenerEnabled) {
                        // Open notification access settings
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback for older Android versions
                            val intent =
                                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            startActivity(intent)
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
    }

    private fun requestCallPermission() {
        val hasPermission = behavior.checkCallPermission()
        if (hasPermission) {
            AlertDialog.Builder(this)
                    .setTitle("Permission Status")
                    .setMessage("Phone state permission is already granted.")
                    .setPositiveButton("OK", null)
                    .show()
            return
        }

        // Request READ_PHONE_STATE permission
        callPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
    }

    override fun onResume() {
        super.onResume()

        // Check notification service connection status when app resumes
        // (user might have enabled notification access in settings)
        val isListenerConnected = behavior.checkNotificationListenerConnected()
        val hasListenerEnabled = isNotificationListenerEnabled()

        if (hasListenerEnabled && !isListenerConnected) {
            android.util.Log.w(
                    "MainActivity",
                    "Notification listener is enabled but not connected. Service may need time to connect."
            )
        } else if (hasListenerEnabled && isListenerConnected) {
            android.util.Log.d(
                    "MainActivity",
                    "Notification listener is enabled and connected. Notifications will be tracked."
            )
        }
        // Check if notification listener was enabled while user was in settings
        if (isNotificationListenerEnabled()) {
            // Hot restart collectors to pick up notification listener
            if (isSessionActive) {
                try {
                    behavior.hotRestartCollectors()
                    android.util.Log.d(
                            "MainActivity",
                            "Collectors hot restarted after notification listener enabled"
                    )
                } catch (e: Exception) {
                    android.util.Log.d("MainActivity", "Could not hot restart: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel background timer
        backgroundTimer?.cancel()
        backgroundTimer = null
        // Note: We don't dispose the behavior here as it's managed by Application
    }
}

// Simple adapter for test items
class TestItemAdapter(private val items: List<String>) :
        RecyclerView.Adapter<TestItemAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.testItemText)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view =
                android.view.LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_test, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = items[position]
    }

    override fun getItemCount() = items.size
}
