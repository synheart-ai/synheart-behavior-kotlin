package ai.synheart.behavior.example

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import ai.synheart.behavior.BehaviorSessionSummary
import ai.synheart.behavior.SynheartBehavior
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class SessionResultsActivity : AppCompatActivity() {

    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var eventsTimelineTitle: TextView
    private lateinit var pickStartTimeBtn: Button
    private lateinit var pickEndTimeBtn: Button
    private lateinit var calculateMetricsBtn: Button

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private var selectedStartTime: ZonedDateTime? = null
    private var selectedEndTime: ZonedDateTime? = null
    private var sessionSummary: BehaviorSessionSummary? = null
    private var behavior: SynheartBehavior? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_results)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.session_results)

        // Get data from Application
        val app = application as ExampleApplication
        val summary = app.lastSessionSummary
        val events = app.lastSessionEvents.toList()

        if (summary == null) {
            finish()
            return
        }

        sessionSummary = summary
        behavior = app.behavior

        // Initialize time range to session start/end
        try {
            val sessionStartUtc = Instant.parse(summary.startAt)
            val sessionEndUtc = Instant.parse(summary.endAt)
            selectedStartTime = sessionStartUtc.atZone(ZoneId.systemDefault())
            selectedEndTime = sessionEndUtc.atZone(ZoneId.systemDefault())
        } catch (e: Exception) {
            android.util.Log.e("SessionResults", "Failed to parse session times: ${e.message}")
        }

        initializeViews()
        displayAllSections(summary, events)
        setupTimeRangeSelection()
    }

    private fun initializeViews() {
        eventsRecyclerView = findViewById(R.id.eventsRecyclerView)
        eventsTimelineTitle = findViewById(R.id.eventsTimelineTitle)
        pickStartTimeBtn = findViewById(R.id.pickStartTimeBtn)
        pickEndTimeBtn = findViewById(R.id.pickEndTimeBtn)
        calculateMetricsBtn = findViewById(R.id.calculateMetricsBtn)
    }

    private fun setupTimeRangeSelection() {
        updateTimeRangeButtons()

        pickStartTimeBtn.setOnClickListener { pickStartTime() }

        pickEndTimeBtn.setOnClickListener { pickEndTime() }

        calculateMetricsBtn.setOnClickListener { calculateAndLog() }
    }

    private fun updateTimeRangeButtons() {
        pickStartTimeBtn.text =
                selectedStartTime?.let { "Start: ${formatDateTimeWithSeconds(it)}" }
                        ?: "Pick Start Time"

        pickEndTimeBtn.text =
                selectedEndTime?.let { "End: ${formatDateTimeWithSeconds(it)}" } ?: "Pick End Time"

        calculateMetricsBtn.isEnabled = selectedStartTime != null && selectedEndTime != null
    }

    private fun formatDateTimeWithSeconds(dateTime: ZonedDateTime): String {
        return String.format(
                "%04d-%02d-%02d %02d:%02d:%02d",
                dateTime.year,
                dateTime.monthValue,
                dateTime.dayOfMonth,
                dateTime.hour,
                dateTime.minute,
                dateTime.second
        )
    }

    private fun displayAllSections(summary: BehaviorSessionSummary, events: List<BehaviorEvent>) {
        val sessionStartTime =
                try {
                    java.time.Instant.parse(summary.startAt).toEpochMilli()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

        // Session Information
        findViewById<TextView>(R.id.sessionIdLabel)?.text = "Session ID"
        findViewById<TextView>(R.id.sessionIdValue)?.text = summary.sessionId
        findViewById<TextView>(R.id.startTimeLabel)?.text = "Start Time"
        findViewById<TextView>(R.id.startTimeValue)?.text = formatDateTime(summary.startAt)
        findViewById<TextView>(R.id.endTimeLabel)?.text = "End Time"
        findViewById<TextView>(R.id.endTimeValue)?.text = formatDateTime(summary.endAt)
        findViewById<TextView>(R.id.durationLabel)?.text = "Duration"
        findViewById<TextView>(R.id.durationValue)?.text = formatMs(summary.durationMs.toLong())
        findViewById<TextView>(R.id.microSessionLabel)?.text = "Micro Session"
        findViewById<TextView>(R.id.microSessionValue)?.text =
                if (summary.microSession) "Yes" else "No"
        findViewById<TextView>(R.id.osLabel)?.text = "OS"
        findViewById<TextView>(R.id.osValue)?.text = summary.os
        if (summary.appId != null) {
            findViewById<TextView>(R.id.appIdLabel)?.text = "App ID"
            findViewById<TextView>(R.id.appIdValue)?.text = summary.appId!!
            findViewById<View>(R.id.appIdRow)?.visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.appIdRow)?.visibility = View.GONE
        }
        if (summary.appName != null) {
            findViewById<TextView>(R.id.appNameLabel)?.text = "App Name"
            findViewById<TextView>(R.id.appNameValue)?.text = summary.appName!!
            findViewById<View>(R.id.appNameRow)?.visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.appNameRow)?.visibility = View.GONE
        }
        findViewById<TextView>(R.id.sessionSpacingLabel)?.text = "Session Spacing"
        findViewById<TextView>(R.id.sessionSpacingValue)?.text =
                formatMs(summary.sessionSpacing.toLong())
        findViewById<TextView>(R.id.totalEventsLabel)?.text = "Total Events"
        findViewById<TextView>(R.id.totalEventsValue)?.text = events.size.toString()

        // Motion Data Debug Card
        val motionDataAvailable = summary.motionData != null && summary.motionData!!.isNotEmpty()
        val motionStateAvailable = summary.motionState != null

        if (motionDataAvailable || motionStateAvailable) {
            findViewById<MaterialCardView>(R.id.motionDataDebugCard)?.visibility = View.VISIBLE
            findViewById<TextView>(R.id.motionDataAvailableLabel)?.text = "Motion Data Available"
            findViewById<TextView>(R.id.motionDataAvailableValue)?.text =
                    if (motionDataAvailable) "Yes" else "No"
            findViewById<TextView>(R.id.motionDataCountLabel)?.text = "Motion Data Count"
            findViewById<TextView>(R.id.motionDataCountValue)?.text =
                    "${summary.motionData?.size ?: 0} windows"
            findViewById<TextView>(R.id.motionStateAvailableLabel)?.text = "Motion State Available"
            findViewById<TextView>(R.id.motionStateAvailableValue)?.text =
                    if (motionStateAvailable) "Yes" else "No"
        } else {
            findViewById<MaterialCardView>(R.id.motionDataDebugCard)?.visibility = View.GONE
        }

        // Motion State (if available)
        summary.motionState?.let { motionState ->
            findViewById<MaterialCardView>(R.id.motionStateCard)?.visibility = View.VISIBLE

            // Major State
            findViewById<TextView>(R.id.motionMajorStateLabel)?.text = "Major State"
            findViewById<TextView>(R.id.motionMajorStateValue)?.text = motionState.majorState

            // Major State %
            findViewById<TextView>(R.id.motionMajorStatePctLabel)?.text = "Major State %"
            findViewById<TextView>(R.id.motionMajorStatePctValue)?.text =
                    String.format("%.1f%%", motionState.majorStatePct * 100.0)

            // ML Model
            findViewById<TextView>(R.id.motionModelLabel)?.text = "ML Model"
            findViewById<TextView>(R.id.motionModelValue)?.text = motionState.mlModel

            // Confidence
            findViewById<TextView>(R.id.motionConfidenceLabel)?.text = "Confidence"
            findViewById<TextView>(R.id.motionConfidenceValue)?.text =
                    String.format("%.2f", motionState.confidence)

            // State Array
            findViewById<TextView>(R.id.motionStateArrayLabel)?.text =
                    "State Array (${motionState.state.size} windows):"

            // Display as JSON-like array
            val stateArrayJson = "[${motionState.state.joinToString(", ") { "\"$it\"" }}]"
            findViewById<TextView>(R.id.motionStateArrayValue)?.text = stateArrayJson

            // Display as chips
            val chipGroup =
                    findViewById<com.google.android.material.chip.ChipGroup>(
                            R.id.motionStateChipGroup
                    )
            chipGroup?.removeAllViews()
            motionState.state.forEachIndexed { index, state ->
                val chip =
                        com.google.android.material.chip.Chip(this).apply {
                            text = "${index + 1}: $state"
                            textSize = 11f
                            setPadding(8, 4, 8, 4)
                            chipMinHeight = 32f
                        }
                chipGroup?.addView(chip)
            }
        }
                ?: run {
                    findViewById<MaterialCardView>(R.id.motionStateCard)?.visibility = View.GONE
                }

        // Device Context
        findViewById<TextView>(R.id.screenBrightnessLabel)?.text = "Avg Screen Brightness"
        findViewById<TextView>(R.id.screenBrightnessValue)?.text =
                String.format("%.3f", summary.deviceContext.avgScreenBrightness)
        findViewById<TextView>(R.id.startOrientationLabel)?.text = "Start Orientation"
        findViewById<TextView>(R.id.startOrientationValue)?.text =
                summary.deviceContext.startOrientation
        findViewById<TextView>(R.id.orientationChangesLabel)?.text = "Orientation Changes"
        findViewById<TextView>(R.id.orientationChangesValue)?.text =
                summary.deviceContext.orientationChanges.toString()

        // Activity Summary
        findViewById<TextView>(R.id.activityTotalEventsLabel)?.text = "Total Events"
        findViewById<TextView>(R.id.activityTotalEventsValue)?.text =
                summary.activitySummary.totalEvents.toString()
        findViewById<TextView>(R.id.activityAppSwitchLabel)?.text = "App Switch Count"
        findViewById<TextView>(R.id.activityAppSwitchValue)?.text =
                summary.activitySummary.appSwitchCount.toString()

        // Behavior Metrics
        val metrics = summary.behavioralMetrics
        findViewById<TextView>(R.id.interactionIntensityLabel)?.text = "Interaction Intensity"
        findViewById<TextView>(R.id.interactionIntensityValue)?.text =
                String.format("%.3f", metrics.interactionIntensity)
        findViewById<TextView>(R.id.taskSwitchRateLabel)?.text = "Task Switch Rate"
        findViewById<TextView>(R.id.taskSwitchRateValue)?.text =
                String.format("%.3f", metrics.taskSwitchRate)
        findViewById<TextView>(R.id.taskSwitchCostLabel)?.text = "Task Switch Cost"
        findViewById<TextView>(R.id.taskSwitchCostValue)?.text =
                formatMs(metrics.taskSwitchCost.toLong())
        findViewById<TextView>(R.id.idleTimeRatioLabel)?.text = "Idle Time Ratio"
        findViewById<TextView>(R.id.idleTimeRatioValue)?.text =
                String.format("%.3f", metrics.idleTimeRatio)
        findViewById<TextView>(R.id.activeTimeRatioLabel)?.text = "Active Time Ratio"
        findViewById<TextView>(R.id.activeTimeRatioValue)?.text =
                String.format("%.3f", metrics.activeTimeRatio)
        findViewById<TextView>(R.id.notificationLoadLabel)?.text = "Notification Load"
        findViewById<TextView>(R.id.notificationLoadValue)?.text =
                String.format("%.3f", metrics.notificationLoad)
        findViewById<TextView>(R.id.burstinessLabel)?.text = "Burstiness"
        findViewById<TextView>(R.id.burstinessValue)?.text =
                String.format("%.3f", metrics.burstiness)
        findViewById<TextView>(R.id.distractionScoreLabel)?.text = "Distraction Score"
        findViewById<TextView>(R.id.distractionScoreValue)?.text =
                String.format("%.3f", metrics.behavioralDistractionScore)
        findViewById<TextView>(R.id.focusHintLabel)?.text = "Focus Hint"
        findViewById<TextView>(R.id.focusHintValue)?.text = String.format("%.3f", metrics.focusHint)
        findViewById<TextView>(R.id.fragmentedIdleRatioLabel)?.text = "Fragmented Idle Ratio"
        findViewById<TextView>(R.id.fragmentedIdleRatioValue)?.text =
                String.format("%.3f", metrics.fragmentedIdleRatio)
        findViewById<TextView>(R.id.scrollJitterRateLabel)?.text = "Scroll Jitter Rate"
        findViewById<TextView>(R.id.scrollJitterRateValue)?.text =
                String.format("%.3f", metrics.scrollJitterRate)
        findViewById<TextView>(R.id.deepFocusBlocksLabel)?.text = "Deep Focus Blocks"
        findViewById<TextView>(R.id.deepFocusBlocksValue)?.text =
                metrics.deepFocusBlocks.size.toString()

        // Deep Focus Blocks Details
        val deepFocusDetails = findViewById<LinearLayout>(R.id.deepFocusBlocksDetails)
        if (metrics.deepFocusBlocks.isNotEmpty()) {
            deepFocusDetails.visibility = View.VISIBLE
            deepFocusDetails.removeAllViews()
            metrics.deepFocusBlocks.forEachIndexed { index, block ->
                val textView =
                        TextView(this).apply {
                            text =
                                    "Block ${index + 1}: ${formatDateTime(block.startAt)} - ${formatDateTime(block.endAt)} (${formatMs(block.durationMs.toLong())})"
                            textSize = 12f
                            setPadding(32, 4, 0, 4)
                        }
                deepFocusDetails.addView(textView)
            }
        } else {
            deepFocusDetails.visibility = View.GONE
        }

        // Notification Summary
        val notifSummary = summary.notificationSummary
        findViewById<TextView>(R.id.notificationCountLabel)?.text = "Notification Count"
        findViewById<TextView>(R.id.notificationCountValue)?.text =
                notifSummary.notificationCount.toString()
        findViewById<TextView>(R.id.notificationIgnoredLabel)?.text = "Notifications Ignored"
        findViewById<TextView>(R.id.notificationIgnoredValue)?.text =
                notifSummary.notificationIgnored.toString()
        findViewById<TextView>(R.id.notificationIgnoreRateLabel)?.text = "Ignore Rate"
        findViewById<TextView>(R.id.notificationIgnoreRateValue)?.text =
                String.format("%.3f", notifSummary.notificationIgnoreRate)
        findViewById<TextView>(R.id.notificationClusteringLabel)?.text = "Clustering Index"
        findViewById<TextView>(R.id.notificationClusteringValue)?.text =
                String.format("%.3f", notifSummary.notificationClusteringIndex)
        findViewById<TextView>(R.id.callCountLabel)?.text = "Call Count"
        findViewById<TextView>(R.id.callCountValue)?.text = notifSummary.callCount.toString()
        findViewById<TextView>(R.id.callIgnoredLabel)?.text = "Calls Ignored"
        findViewById<TextView>(R.id.callIgnoredValue)?.text = notifSummary.callIgnored.toString()

        // System State
        val systemState = summary.systemState
        findViewById<TextView>(R.id.internetStateLabel)?.text = "Internet"
        findViewById<TextView>(R.id.internetStateValue)?.text =
                if (systemState.internetState) "Connected" else "Disconnected"
        findViewById<TextView>(R.id.doNotDisturbLabel)?.text = "Do Not Disturb"
        findViewById<TextView>(R.id.doNotDisturbValue)?.text =
                if (systemState.doNotDisturb) "On" else "Off"
        findViewById<TextView>(R.id.chargingLabel)?.text = "Charging"
        findViewById<TextView>(R.id.chargingValue)?.text = if (systemState.charging) "Yes" else "No"

        // Events Timeline
        val sortedEvents =
                events.sortedBy {
                    try {
                        java.time.Instant.parse(it.timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                }
        eventsTimelineTitle.text = "Events Timeline (${sortedEvents.size} events)"
        displayEvents(sortedEvents, sessionStartTime)
    }

    private fun displayEvents(events: List<BehaviorEvent>, sessionStartTime: Long) {
        if (events.isEmpty()) {
            eventsRecyclerView.visibility = View.GONE
            return
        }
        eventsRecyclerView.visibility = View.VISIBLE
        val adapter = EventsAdapter(events, sessionStartTime)
        eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        eventsRecyclerView.adapter = adapter
    }

    private fun formatDateTime(isoString: String): String {
        return try {
            val dateTime = java.time.Instant.parse(isoString)
            val localDateTime =
                    java.time.LocalDateTime.ofInstant(dateTime, java.time.ZoneId.systemDefault())
            String.format(
                    "%02d:%02d:%02d.%d",
                    localDateTime.hour,
                    localDateTime.minute,
                    localDateTime.second,
                    localDateTime.nano / 100_000_000
            )
        } catch (e: Exception) {
            isoString
        }
    }

    private fun formatMs(milliseconds: Long): String {
        return when {
            milliseconds < 1000 -> "${milliseconds}ms"
            milliseconds < 60000 -> String.format("%.1fs", milliseconds / 1000.0)
            else -> String.format("%.1fm", milliseconds / 60000.0)
        }
    }

    private fun pickStartTime() {
        val summary = sessionSummary ?: return
        val sessionStartUtc = Instant.parse(summary.startAt)
        val sessionEndUtc = Instant.parse(summary.endAt)
        val sessionStartLocal = sessionStartUtc.atZone(ZoneId.systemDefault())
        val sessionEndLocal = sessionEndUtc.atZone(ZoneId.systemDefault())

        val initialDateTime = selectedStartTime ?: sessionStartLocal

        showDateTimePicker(
                title = "Select Start Time",
                initialDateTime = initialDateTime,
                firstDate = sessionStartLocal.minusDays(1),
                lastDate = sessionEndLocal.plusDays(1)
        ) { selected ->
            if (selected != null) {
                selectedStartTime = selected
                updateTimeRangeButtons()
            }
        }
    }

    private fun pickEndTime() {
        val summary = sessionSummary ?: return
        val sessionStartUtc = Instant.parse(summary.startAt)
        val sessionEndUtc = Instant.parse(summary.endAt)
        val sessionStartLocal = sessionStartUtc.atZone(ZoneId.systemDefault())
        val sessionEndLocal = sessionEndUtc.atZone(ZoneId.systemDefault())

        val initialDateTime = selectedEndTime ?: (selectedStartTime ?: sessionStartLocal)

        showDateTimePicker(
                title = "Select End Time",
                initialDateTime = initialDateTime,
                firstDate = selectedStartTime ?: sessionStartLocal,
                lastDate = sessionEndLocal.plusDays(1)
        ) { selected ->
            if (selected != null) {
                selectedEndTime = selected
                updateTimeRangeButtons()
            }
        }
    }

    private fun showDateTimePicker(
            title: String,
            initialDateTime: ZonedDateTime,
            firstDate: ZonedDateTime,
            lastDate: ZonedDateTime,
            onResult: (ZonedDateTime?) -> Unit
    ) {
        try {
            var selectedDate = initialDateTime.toLocalDate()
            var selectedHour = initialDateTime.hour
            var selectedMinute = initialDateTime.minute
            var selectedSecond = initialDateTime.second

            val dialogView = layoutInflater.inflate(R.layout.dialog_datetime_picker, null)
            val dateText = dialogView.findViewById<TextView>(R.id.dateText)
            val hourText = dialogView.findViewById<TextView>(R.id.hourText)
            val minuteText = dialogView.findViewById<TextView>(R.id.minuteText)
            val secondText = dialogView.findViewById<TextView>(R.id.secondText)

            if (dateText == null || hourText == null || minuteText == null || secondText == null) {
                android.util.Log.e("SessionResults", "Failed to find views in dialog layout")
                AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("Failed to initialize date/time picker. Please try again.")
                        .setPositiveButton("OK", null)
                        .show()
                return
            }

            fun updateDisplay() {
                dateText.text =
                        String.format(
                                "%04d-%02d-%02d",
                                selectedDate.year,
                                selectedDate.monthValue,
                                selectedDate.dayOfMonth
                        )
                hourText.text = String.format("%02d", selectedHour)
                minuteText.text = String.format("%02d", selectedMinute)
                secondText.text = String.format("%02d", selectedSecond)
            }

            updateDisplay()

            // Date picker
            dateText.setOnClickListener {
                val calendar =
                        Calendar.getInstance().apply {
                            set(
                                    selectedDate.year,
                                    selectedDate.monthValue - 1,
                                    selectedDate.dayOfMonth
                            )
                        }
                DatePickerDialog(
                                this,
                                { _, year, month, dayOfMonth ->
                                    selectedDate =
                                            java.time.LocalDate.of(year, month + 1, dayOfMonth)
                                    updateDisplay()
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                        )
                        .apply {
                            datePicker.minDate = firstDate.toInstant().toEpochMilli()
                            datePicker.maxDate = lastDate.toInstant().toEpochMilli()
                        }
                        .show()
            }

            // Hour controls
            dialogView.findViewById<TextView>(R.id.hourUp)?.setOnClickListener {
                selectedHour = (selectedHour + 1) % 24
                updateDisplay()
            }
            dialogView.findViewById<TextView>(R.id.hourDown)?.setOnClickListener {
                selectedHour = (selectedHour - 1 + 24) % 24
                updateDisplay()
            }

            // Minute controls
            dialogView.findViewById<TextView>(R.id.minuteUp)?.setOnClickListener {
                selectedMinute = (selectedMinute + 1) % 60
                updateDisplay()
            }
            dialogView.findViewById<TextView>(R.id.minuteDown)?.setOnClickListener {
                selectedMinute = (selectedMinute - 1 + 60) % 60
                updateDisplay()
            }

            // Second controls
            dialogView.findViewById<TextView>(R.id.secondUp)?.setOnClickListener {
                selectedSecond = (selectedSecond + 1) % 60
                updateDisplay()
            }
            dialogView.findViewById<TextView>(R.id.secondDown)?.setOnClickListener {
                selectedSecond = (selectedSecond - 1 + 60) % 60
                updateDisplay()
            }

            AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(dialogView)
                    .setPositiveButton("OK") { _, _ ->
                        try {
                            val result =
                                    ZonedDateTime.of(
                                            selectedDate.year,
                                            selectedDate.monthValue,
                                            selectedDate.dayOfMonth,
                                            selectedHour,
                                            selectedMinute,
                                            selectedSecond,
                                            0,
                                            ZoneId.systemDefault()
                                    )
                            onResult(result)
                        } catch (e: Exception) {
                            android.util.Log.e(
                                    "SessionResults",
                                    "Error creating ZonedDateTime: ${e.message}",
                                    e
                            )
                            onResult(null)
                        }
                    }
                    .setNegativeButton("Cancel") { _, _ -> onResult(null) }
                    .show()
        } catch (e: Exception) {
            android.util.Log.e("SessionResults", "Error showing date/time picker: ${e.message}", e)
            AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage("Failed to show date/time picker: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
        }
    }

    private fun calculateAndLog() {
        if (selectedStartTime == null || selectedEndTime == null) {
            android.util.Log.e("SessionResults", "ERROR: Start time or end time is null")
            return
        }

        val summary = sessionSummary
        val behavior = this.behavior

        if (summary == null || behavior == null) {
            android.util.Log.e(
                    "SessionResults",
                    "ERROR: Session summary or behavior SDK is not available"
            )
            return
        }

        // Validate that start time is before end time
        if (selectedStartTime!!.isAfter(selectedEndTime!!)) {
            android.util.Log.e(
                    "SessionResults",
                    """
                ========================================
                ERROR: INVALID TIME RANGE
                ========================================
                Start time must be before end time!
                
                Selected Start (Local): ${selectedStartTime!!.toInstant()}
                Selected End (Local): ${selectedEndTime!!.toInstant()}
                Duration: ${formatMs(java.time.Duration.between(selectedStartTime!!, selectedEndTime!!).toMillis())}
                ========================================
            """.trimIndent()
            )
            AlertDialog.Builder(this)
                    .setTitle("Invalid Time Range")
                    .setMessage(
                            "Start time must be before end time!\n\n" +
                                    "Selected Start: ${formatDateTimeWithSeconds(selectedStartTime!!)}\n" +
                                    "Selected End: ${formatDateTimeWithSeconds(selectedEndTime!!)}"
                    )
                    .setPositiveButton("OK", null)
                    .show()
            return
        }

        // Validate time range is within session duration (with 1 second tolerance)
        val sessionStartUtc = Instant.parse(summary.startAt)
        val sessionEndUtc = Instant.parse(summary.endAt)
        val sessionStartMs = sessionStartUtc.toEpochMilli()
        val sessionEndMs = sessionEndUtc.toEpochMilli()
        val selectedStartMs = selectedStartTime!!.toInstant().toEpochMilli()
        val selectedEndMs = selectedEndTime!!.toInstant().toEpochMilli()
        val toleranceMs = 1000L // 1 second tolerance

        if (selectedStartMs < (sessionStartMs - toleranceMs) ||
                        selectedEndMs > (sessionEndMs + toleranceMs)
        ) {
            android.util.Log.e(
                    "SessionResults",
                    """
                ========================================
                ERROR: TIME RANGE OUT OF BOUNDS
                ========================================
                Session Start (UTC): ${sessionStartUtc}
                Session End (UTC): ${sessionEndUtc}
                Session Duration: ${formatMs(sessionEndMs - sessionStartMs)}
                
                Selected Start (UTC): ${selectedStartTime!!.toInstant()}
                Selected End (UTC): ${selectedEndTime!!.toInstant()}
                Selected Duration: ${formatMs(selectedEndMs - selectedStartMs)}
                ========================================
            """.trimIndent()
            )

            // Build error message
            val errorMessage = StringBuilder()
            errorMessage.append("Selected time range is outside the session duration!\n\n")
            errorMessage.append("Session Duration:\n")
            errorMessage.append(
                    "  Start: ${formatDateTimeWithSeconds(sessionStartUtc.atZone(ZoneId.systemDefault()))}\n"
            )
            errorMessage.append(
                    "  End: ${formatDateTimeWithSeconds(sessionEndUtc.atZone(ZoneId.systemDefault()))}\n"
            )
            errorMessage.append("  Duration: ${formatMs(sessionEndMs - sessionStartMs)}\n\n")
            errorMessage.append("Selected Range:\n")
            errorMessage.append("  Start: ${formatDateTimeWithSeconds(selectedStartTime!!)}\n")
            errorMessage.append("  End: ${formatDateTimeWithSeconds(selectedEndTime!!)}\n")
            errorMessage.append("  Duration: ${formatMs(selectedEndMs - selectedStartMs)}\n\n")

            if (selectedStartMs < (sessionStartMs - toleranceMs)) {
                val diffMs = (sessionStartMs - toleranceMs) - selectedStartMs
                errorMessage.append("⚠ Start time is ${formatMs(diffMs)} before session start\n")
            }
            if (selectedEndMs > (sessionEndMs + toleranceMs)) {
                val diffMs = selectedEndMs - (sessionEndMs + toleranceMs)
                errorMessage.append("⚠ End time is ${formatMs(diffMs)} after session end\n")
            }

            AlertDialog.Builder(this)
                    .setTitle("Time Range Out of Bounds")
                    .setMessage(errorMessage.toString())
                    .setPositiveButton("OK", null)
                    .show()
            return
        }

        val startTimestampSeconds = (selectedStartMs / 1000).toInt()
        val endTimestampSeconds = (selectedEndMs / 1000).toInt()

        android.util.Log.d(
                "SessionResults",
                """
            ========================================
            CALCULATE METRICS FOR TIME RANGE
            ========================================
            Session ID: ${summary.sessionId}
            Start Time (UTC): ${selectedStartTime!!.toInstant()}
            End Time (UTC): ${selectedEndTime!!.toInstant()}
            Start Time (Local): ${selectedStartTime!!}
            End Time (Local): ${selectedEndTime!!}
            Start Timestamp (seconds): $startTimestampSeconds
            End Timestamp (seconds): $endTimestampSeconds
            Duration: ${endTimestampSeconds - startTimestampSeconds} seconds
            Duration: ${formatMs((endTimestampSeconds - startTimestampSeconds) * 1000L)}
            ========================================
        """.trimIndent()
        )

        try {
            android.util.Log.d("SessionResults", "Calling calculateMetricsForTimeRange...")
            val result =
                    behavior.calculateMetricsForTimeRange(
                            startTimestampSeconds = startTimestampSeconds,
                            endTimestampSeconds = endTimestampSeconds,
                            sessionId = summary.sessionId
                    )

            // Log the results (matching Flutter's output format)
            android.util.Log.d(
                    "SessionResults",
                    """
                
                ========================================
                SESSION BEHAVIOR METRICS
                ========================================
                
                "session behavior" : {
                    "session_id": "${summary.sessionId}",
                    "start_at": "${selectedStartTime!!.toInstant()}",
                    "end_at": "${selectedEndTime!!.toInstant()}",
                    "micro_session": ${summary.microSession},
                    "OS": "${summary.os}",
                    ${if (summary.appId != null) "\"app_id\": \"${summary.appId}\"," else ""}
                    "session_spacing": ${summary.sessionSpacing},
                    
                    ${formatMetricsMap(result)}
                }
                
                ========================================
            """.trimIndent()
            )

            // Show results in a scrollable dialog
            showMetricsDialog(result, summary)
        } catch (e: Exception) {
            android.util.Log.e("SessionResults", "ERROR calculating metrics: ${e.message}", e)
            AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage("Failed to calculate metrics: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
        }
    }

    private fun showMetricsDialog(metrics: Map<String, Any?>, summary: BehaviorSessionSummary) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_metrics, null)
        val metricsText = dialogView.findViewById<TextView>(R.id.metricsText)

        // Format metrics for display
        val formattedText = formatMetricsForDialog(metrics, summary)
        metricsText.text = formattedText

        // Make text selectable for copying
        metricsText.setTextIsSelectable(true)

        AlertDialog.Builder(this)
                .setTitle("Calculated Metrics")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show()
    }

    private fun formatMetricsForDialog(
            metrics: Map<String, Any?>,
            summary: BehaviorSessionSummary
    ): String {
        val sb = StringBuilder()

        sb.append("SESSION INFO\n")
        sb.append("─────────────\n")
        sb.append("Session ID: ${summary.sessionId}\n")
        sb.append("Start: ${selectedStartTime!!.toInstant()}\n")
        sb.append("End: ${selectedEndTime!!.toInstant()}\n")
        sb.append(
                "Duration: ${formatMs((selectedEndTime!!.toInstant().toEpochMilli() - selectedStartTime!!.toInstant().toEpochMilli()))}\n\n"
        )

        // Behavioral Metrics
        (metrics["behavioral_metrics"] as? Map<*, *>)?.let { behavioralMetrics ->
            sb.append("BEHAVIORAL METRICS\n")
            sb.append("──────────────────\n")
            (behavioralMetrics["interaction_intensity"] as? Number)?.let {
                sb.append("Interaction Intensity: ${String.format("%.4f", it.toDouble())}\n")
            }
            (behavioralMetrics["task_switch_rate"] as? Number)?.let {
                sb.append("Task Switch Rate: ${String.format("%.4f", it.toDouble())}\n")
            }
            (behavioralMetrics["task_switch_cost"] as? Number)?.let {
                sb.append("Task Switch Cost: ${String.format("%.4f", it.toDouble())}\n")
            }
            (behavioralMetrics["idle_time_ratio"] as? Number)?.let {
                sb.append("Idle Time Ratio: ${String.format("%.4f", it.toDouble())}\n")
            }
            (behavioralMetrics["active_time_ratio"] as? Number)?.let {
                sb.append("Active Time Ratio: ${String.format("%.4f", it.toDouble())}\n")
            }
            (behavioralMetrics["notification_load"] as? Number)?.let {
                sb.append("Notification Load: ${String.format("%.4f", it.toDouble())}\n")
            }
            (behavioralMetrics["burstiness"] as? Number)?.let {
                sb.append("Burstiness: ${String.format("%.4f", it.toDouble())}\n")
            }
            (behavioralMetrics["behavioral_distraction_score"] as? Number)?.let {
                sb.append("Distraction Score: ${String.format("%.4f", it.toDouble())}\n")
            }
            (behavioralMetrics["focus_hint"] as? Number)?.let {
                sb.append("Focus Hint: ${String.format("%.4f", it.toDouble())}\n")
            }
            sb.append("\n")
        }

        // Activity Summary
        (metrics["activity_summary"] as? Map<*, *>)?.let { activitySummary ->
            sb.append("ACTIVITY SUMMARY\n")
            sb.append("────────────────\n")
            sb.append("Total Events: ${activitySummary["total_events"] ?: 0}\n")
            sb.append("App Switches: ${activitySummary["app_switch_count"] ?: 0}\n")
            sb.append("\n")
        }

        // Notification Summary
        (metrics["notification_summary"] as? Map<*, *>)?.let { notificationSummary ->
            sb.append("NOTIFICATION SUMMARY\n")
            sb.append("────────────────────\n")
            sb.append("Notifications: ${notificationSummary["notification_count"] ?: 0}\n")
            sb.append("Ignored: ${notificationSummary["notification_ignored"] ?: 0}\n")
            (notificationSummary["notification_ignore_rate"] as? Number)?.let {
                sb.append("Ignore Rate: ${String.format("%.2f%%", it.toDouble() * 100)}\n")
            }
            (notificationSummary["notification_clustering_index"] as? Number)?.let {
                sb.append("Clustering Index: ${String.format("%.4f", it.toDouble())}\n")
            }
            sb.append("Calls: ${notificationSummary["call_count"] ?: 0}\n")
            sb.append("Calls Ignored: ${notificationSummary["call_ignored"] ?: 0}\n")
            sb.append("\n")
        }

        // Device Context
        (metrics["device_context"] as? Map<*, *>)?.let { deviceContext ->
            sb.append("DEVICE CONTEXT\n")
            sb.append("──────────────\n")
            (deviceContext["avg_screen_brightness"] as? Number)?.let {
                sb.append("Avg Brightness: ${it.toInt()}\n")
            }
            sb.append("Orientation: ${deviceContext["start_orientation"] ?: "N/A"}\n")
            sb.append("Orientation Changes: ${deviceContext["orientation_changes"] ?: 0}\n")
            sb.append("\n")
        }

        // System State
        (metrics["system_state"] as? Map<*, *>)?.let { systemState ->
            sb.append("SYSTEM STATE\n")
            sb.append("────────────\n")
            sb.append(
                    "Internet: ${if (systemState["internet_state"] == true) "Connected" else "Disconnected"}\n"
            )
            sb.append(
                    "Do Not Disturb: ${if (systemState["do_not_disturb"] == true) "Enabled" else "Disabled"}\n"
            )
            sb.append("Charging: ${if (systemState["charging"] == true) "Yes" else "No"}\n")
            sb.append("\n")
        }

        // Motion State
        (metrics["motion_state"] as? Map<*, *>)?.let { motionState ->
            sb.append("MOTION STATE\n")
            sb.append("────────────\n")
            (motionState["major_state"] as? String)?.let { sb.append("State: $it\n") }
            (motionState["confidence"] as? Number)?.let {
                sb.append("Confidence: ${String.format("%.2f%%", it.toDouble() * 100)}\n")
            }
            (motionState["ml_model"] as? String)?.let { sb.append("ML Model: $it\n") }
            sb.append("\n")
        }

        // Typing Session Summary
        (metrics["typing_session_summary"] as? Map<*, *>)?.let { typingSummary ->
            sb.append("TYPING SUMMARY\n")
            sb.append("──────────────\n")
            sb.append("Typing Sessions: ${typingSummary["typing_session_count"] ?: 0}\n")
            (typingSummary["avg_typing_speed"] as? Number)?.let {
                sb.append("Avg Speed: ${String.format("%.2f", it.toDouble())} keys/sec\n")
            }
            (typingSummary["avg_typing_cadence_stability"] as? Number)?.let {
                sb.append("Avg Cadence Stability: ${String.format("%.4f", it.toDouble())}\n")
            }
            (typingSummary["avg_typing_interaction_intensity"] as? Number)?.let {
                sb.append("Avg Interaction Intensity: ${String.format("%.4f", it.toDouble())}\n")
            }
            sb.append("Total Keystrokes: ${typingSummary["total_keystrokes"] ?: 0}\n")
            sb.append("Deep Typing Sessions: ${typingSummary["deep_typing_session_count"] ?: 0}\n")
        }

        return sb.toString()
    }

    private fun formatMetricsMap(metrics: Map<String, Any?>): String {
        val sb = StringBuilder()

        // Motion State
        (metrics["motion_state"] as? Map<*, *>)?.let { motionState ->
            sb.append("    \"motion_state\": {\n")
            (motionState["major_state"] as? String)?.let {
                sb.append("        \"state\": \"$it\",\n")
            }
            (motionState["ml_model"] as? String)?.let {
                sb.append("        \"ml_model\": \"$it\",\n")
            }
            (motionState["confidence"] as? Number)?.let {
                sb.append("        \"confidence\": $it\n")
            }
            sb.append("    },\n")
        }

        // Device Context
        (metrics["device_context"] as? Map<*, *>)?.let { deviceContext ->
            sb.append("    \"device_context\": {\n")
            sb.append(
                    "      \"avg_screen_brightness\": ${deviceContext["avg_screen_brightness"] ?: 0},\n"
            )
            sb.append(
                    "      \"start_orientation\": \"${deviceContext["start_orientation"] ?: "N/A"}\",\n"
            )
            sb.append(
                    "      \"orientation_changes\": ${deviceContext["orientation_changes"] ?: 0}\n"
            )
            sb.append("    },\n")
        }

        // Activity Summary
        (metrics["activity_summary"] as? Map<*, *>)?.let { activitySummary ->
            sb.append("  \"activity_summary\": {\n")
            sb.append("    \"total_events\": ${activitySummary["total_events"] ?: 0},\n")
            sb.append("    \"app_switch_count\": ${activitySummary["app_switch_count"] ?: 0}\n")
            sb.append("  },\n")
        }

        // Behavioral Metrics
        (metrics["behavioral_metrics"] as? Map<*, *>)?.let { behavioralMetrics ->
            sb.append("  \"behavioral_metrics\": {\n")
            sb.append(
                    "      \"interaction_intensity\": ${behavioralMetrics["interaction_intensity"] ?: 0},\n"
            )
            sb.append(
                    "      \"task_switch_rate\": ${behavioralMetrics["task_switch_rate"] ?: 0},\n"
            )
            sb.append(
                    "      \"task_switch_cost\": ${behavioralMetrics["task_switch_cost"] ?: 0},\n"
            )
            sb.append("      \"idle_time_ratio\": ${behavioralMetrics["idle_time_ratio"] ?: 0},\n")
            sb.append(
                    "      \"active_time_ratio\": ${behavioralMetrics["active_time_ratio"] ?: 0},\n"
            )
            sb.append(
                    "      \"notification_load\": ${behavioralMetrics["notification_load"] ?: 0},\n"
            )
            sb.append("      \"burstiness\": ${behavioralMetrics["burstiness"] ?: 0},\n")
            sb.append(
                    "      \"behavioral_distraction_score\": ${behavioralMetrics["behavioral_distraction_score"] ?: 0},\n"
            )
            sb.append("      \"focus_hint\": ${behavioralMetrics["focus_hint"] ?: 0}\n")
            sb.append("  },\n")
        }

        // Notification Summary
        (metrics["notification_summary"] as? Map<*, *>)?.let { notificationSummary ->
            sb.append("  \"notification_summary\": {\n")
            sb.append(
                    "    \"notification_count\": ${notificationSummary["notification_count"] ?: 0},\n"
            )
            sb.append(
                    "    \"notification_ignored\": ${notificationSummary["notification_ignored"] ?: 0},\n"
            )
            sb.append(
                    "    \"notification_ignore_rate\": ${notificationSummary["notification_ignore_rate"] ?: 0},\n"
            )
            sb.append(
                    "    \"notification_clustering_index\": ${notificationSummary["notification_clustering_index"] ?: 0},\n"
            )
            sb.append("    \"call_count\": ${notificationSummary["call_count"] ?: 0},\n")
            sb.append("    \"call_ignored\": ${notificationSummary["call_ignored"] ?: 0}\n")
            sb.append("  },\n")
        }

        // System State
        (metrics["system_state"] as? Map<*, *>)?.let { systemState ->
            sb.append("  \"system_state\": {\n")
            sb.append("    \"internet_state\": ${systemState["internet_state"] ?: false},\n")
            sb.append("    \"do_not_disturb\": ${systemState["do_not_disturb"] ?: false},\n")
            sb.append("    \"charging\": ${systemState["charging"] ?: false}\n")
            sb.append("  }\n")
        }

        return sb.toString()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class EventsAdapter(private val events: List<BehaviorEvent>, private val sessionStartTime: Long) :
        RecyclerView.Adapter<EventsAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val eventTypeText: TextView = itemView.findViewById(R.id.eventTypeText)
        val relativeTimeText: TextView = itemView.findViewById(R.id.relativeTimeText)
        val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        val metricsText: TextView = itemView.findViewById(R.id.metricsText)
        val eventTypeContainer: android.widget.LinearLayout =
                itemView.findViewById(R.id.eventTypeContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        val eventTime =
                try {
                    java.time.Instant.parse(event.timestamp).toEpochMilli()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
        val relativeTime = eventTime - sessionStartTime

        holder.eventTypeText.text = event.eventType.name.uppercase()
        holder.relativeTimeText.text = formatRelativeTime(relativeTime)
        holder.timestampText.text = dateFormat.format(Date(eventTime))

        // Format metrics
        val metricsString =
                if (event.metrics.isNotEmpty()) {
                    event.metrics.entries.joinToString("\n") { (key, value) -> "$key: $value" }
                } else {
                    "No metrics"
                }
        holder.metricsText.text = metricsString

        // Set background color based on event type
        val color = getEventTypeColor(event.eventType)
        // Use GradientDrawable for rounded corners
        val drawable =
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 4f * holder.itemView.context.resources.displayMetrics.density
                }
        holder.eventTypeContainer.background = drawable
        holder.eventTypeContainer.visibility = View.VISIBLE
    }

    override fun getItemCount() = events.size

    private fun formatRelativeTime(ms: Long): String {
        return when {
            ms < 1000 -> "+${ms}ms"
            ms < 60000 -> String.format("+%.1fs", ms / 1000.0)
            else -> String.format("+%.1fm", ms / 60000.0)
        }
    }

    private fun getEventTypeColor(eventType: BehaviorEventType): Int {
        return when (eventType) {
            BehaviorEventType.SCROLL -> 0xFF2196F3.toInt() // Blue
            BehaviorEventType.TAP -> 0xFF4CAF50.toInt() // Green
            BehaviorEventType.SWIPE -> 0xFFFF9800.toInt() // Orange
            BehaviorEventType.NOTIFICATION -> 0xFF9C27B0.toInt() // Purple
            BehaviorEventType.CALL -> 0xFFF44336.toInt() // Red
            BehaviorEventType.TYPING -> 0xFF00BCD4.toInt() // Cyan
        }
    }
}
