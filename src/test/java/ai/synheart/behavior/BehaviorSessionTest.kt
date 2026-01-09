package ai.synheart.behavior

import org.junit.Test
import org.junit.Assert.*

class BehaviorSessionTest {

    @Test
    fun `session summary creation works`() {
        // Create nested objects
        val deviceContext = DeviceContext(0.5, "portrait", 0)
        val activitySummary = ActivitySummary(100, 3)
        val deepFocusBlock = DeepFocusBlock("2023-01-01T12:00:00Z", "2023-01-01T12:30:00Z", 1800000)
        val behavioralMetrics = BehavioralMetrics(
            interactionIntensity = 0.5,
            taskSwitchRate = 0.1,
            taskSwitchCost = 500,
            idleTimeRatio = 0.2,
            activeTimeRatio = 0.8,
            notificationLoad = 0.05,
            burstiness = 0.3,
            behavioralDistractionScore = 0.2,
            focusHint = 0.8,
            fragmentedIdleRatio = 0.1,
            scrollJitterRate = 0.05,
            deepFocusBlocks = listOf(deepFocusBlock)
        )
        val notificationSummary = NotificationSummary(10, 2, 0.2, 0.5, 5, 1)
        val systemState = SystemState(true, false, true)

        val summary = BehaviorSessionSummary(
            sessionId = "test-session",
            startAt = "2023-01-01T10:00:00Z",
            endAt = "2023-01-01T11:00:00Z",
            microSession = false,
            os = "Android 14",
            appId = "com.example.app",
            appName = "Example App",
            sessionSpacing = 300,
            motionState = null,
            deviceContext = deviceContext,
            activitySummary = activitySummary,
            behavioralMetrics = behavioralMetrics,
            notificationSummary = notificationSummary,
            systemState = systemState
        )

        assertEquals("test-session", summary.sessionId)
        assertEquals("2023-01-01T10:00:00Z", summary.startAt)
        assertEquals("Android 14", summary.os)
        assertEquals(0.5, summary.deviceContext.avgScreenBrightness, 0.001)
        assertEquals(100, summary.activitySummary.totalEvents)
        assertEquals(0.2, summary.behavioralMetrics.behavioralDistractionScore, 0.001)
    }

    @Test
    fun `durationMs calculation is correct`() {
        // 1 hour difference
        val summary = createDummySummary(
            "2023-01-01T10:00:00Z",
            "2023-01-01T11:00:00Z"
        )
        assertEquals(3600000, summary.durationMs)
    }
    
    @Test
    fun `durationMs handles invalid dates gracefully`() {
        val summary = createDummySummary("invalid", "invalid")
        assertEquals(0, summary.durationMs)
    }

    @Test
    fun `session summary with typing and motion data works`() {
        val typingMetrics = TypingMetrics(
            startAt = "2023-01-01T10:00:00Z",
            endAt = "2023-01-01T10:00:10Z",
            duration = 10,
            deepTyping = true,
            typingTapCount = 50,
            typingSpeed = 5.0,
            meanInterTapIntervalMs = 200.0,
            typingCadenceVariability = 0.1,
            typingCadenceStability = 0.9,
            typingGapCount = 2,
            typingGapRatio = 0.05,
            typingBurstiness = 0.2,
            typingActivityRatio = 0.95,
            typingInteractionIntensity = 0.85
        )

        val typingSummary = TypingSessionSummary(
            typingSessionCount = 1,
            averageKeystrokesPerSession = 50.0,
            averageTypingSessionDuration = 10.0,
            averageTypingSpeed = 5.0,
            averageTypingGap = 200.0,
            averageInterTapInterval = 200.0,
            typingCadenceStability = 0.9,
            burstinessOfTyping = 0.2,
            totalTypingDuration = 10,
            activeTypingRatio = 0.1,
            typingContributionToInteractionIntensity = 0.05,
            deepTypingBlocks = 1,
            typingFragmentation = 0.05,
            individualTypingSessions = listOf(typingMetrics)
        )

        val motionState = MotionState(
            state = listOf("sitting", "standing"),
            majorState = "sitting",
            majorStatePct = 0.6,
            mlModel = "motion_state_svc_classifier_v0.1",
            confidence = 0.9
        )

        val motionData = listOf(
            MotionDataPoint(
                timestamp = "2023-01-01T10:00:00Z",
                features = mapOf("tBodyAcc-mean()-X" to 0.25)
            )
        )

        val summary = BehaviorSessionSummary(
            sessionId = "test-session",
            startAt = "2023-01-01T10:00:00Z",
            endAt = "2023-01-01T11:00:00Z",
            microSession = false,
            os = "Android 14",
            sessionSpacing = 0,
            motionState = motionState,
            deviceContext = DeviceContext(0.5, "portrait", 0),
            activitySummary = ActivitySummary(100, 3),
            behavioralMetrics = BehavioralMetrics(
                0.5, 0.1, 500, 0.2, 0.8, 0.05, 0.3, 0.2, 0.8, 0.1, 0.05, emptyList()
            ),
            notificationSummary = NotificationSummary(10, 2, 0.2, 0.5, 5, 1),
            systemState = SystemState(true, false, true),
            typingSessionSummary = typingSummary,
            motionData = motionData
        )

        assertNotNull(summary.typingSessionSummary)
        assertEquals(1, summary.typingSessionSummary!!.typingSessionCount)
        assertNotNull(summary.motionState)
        assertEquals("sitting", summary.motionState!!.majorState)
        assertNotNull(summary.motionData)
        assertEquals(1, summary.motionData!!.size)
    }

    private fun createDummySummary(start: String, end: String): BehaviorSessionSummary {
         return BehaviorSessionSummary(
            sessionId = "test",
            startAt = start,
            endAt = end,
            microSession = false,
            os = "Android",
            sessionSpacing = 0,
            deviceContext = DeviceContext(0.0, "portrait", 0),
            activitySummary = ActivitySummary(0, 0),
            behavioralMetrics = BehavioralMetrics(0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList()),
            notificationSummary = NotificationSummary(0, 0, 0.0, 0.0, 0, 0),
            systemState = SystemState(false, false, false)
        )
    }
}
