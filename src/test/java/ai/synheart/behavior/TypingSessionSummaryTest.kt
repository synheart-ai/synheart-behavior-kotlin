package ai.synheart.behavior

import org.junit.Test
import org.junit.Assert.*

class TypingSessionSummaryTest {

    @Test
    fun `typing session summary creation works`() {
        val typingMetrics1 = TypingMetrics(
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

        val typingMetrics2 = TypingMetrics(
            startAt = "2023-01-01T10:01:00Z",
            endAt = "2023-01-01T10:01:15Z",
            duration = 15,
            deepTyping = false,
            typingTapCount = 60,
            typingSpeed = 4.0,
            meanInterTapIntervalMs = 250.0,
            typingCadenceVariability = 0.15,
            typingCadenceStability = 0.85,
            typingGapCount = 3,
            typingGapRatio = 0.1,
            typingBurstiness = 0.3,
            typingActivityRatio = 0.9,
            typingInteractionIntensity = 0.8
        )

        val summary = TypingSessionSummary(
            typingSessionCount = 2,
            averageKeystrokesPerSession = 55.0,
            averageTypingSessionDuration = 12.5,
            averageTypingSpeed = 4.5,
            averageTypingGap = 225.0,
            averageInterTapInterval = 225.0,
            typingCadenceStability = 0.875,
            burstinessOfTyping = 0.25,
            totalTypingDuration = 25,
            activeTypingRatio = 0.5,
            typingContributionToInteractionIntensity = 0.1,
            deepTypingBlocks = 1,
            typingFragmentation = 0.075,
            individualTypingSessions = listOf(typingMetrics1, typingMetrics2)
        )

        assertEquals(2, summary.typingSessionCount)
        assertEquals(55.0, summary.averageKeystrokesPerSession, 0.1)
        assertEquals(2, summary.individualTypingSessions.size)
        assertEquals(1, summary.deepTypingBlocks)
    }

    @Test
    fun `typing session summary fromJson works`() {
        val json = mapOf(
            "typing_session_count" to 1,
            "average_keystrokes_per_session" to 40.0,
            "average_typing_session_duration" to 8.0,
            "average_typing_speed" to 5.0,
            "average_typing_gap" to 200.0,
            "average_inter_tap_interval" to 200.0,
            "typing_cadence_stability" to 0.9,
            "burstiness_of_typing" to 0.2,
            "total_typing_duration" to 8,
            "active_typing_ratio" to 0.4,
            "typing_contribution_to_interaction_intensity" to 0.05,
            "deep_typing_blocks" to 1,
            "typing_fragmentation" to 0.05,
            "typing_metrics" to listOf(
                mapOf(
                    "start_at" to "2023-01-01T10:00:00Z",
                    "end_at" to "2023-01-01T10:00:08Z",
                    "duration" to 8,
                    "deep_typing" to true,
                    "typing_tap_count" to 40,
                    "typing_speed" to 5.0,
                    "mean_inter_tap_interval_ms" to 200.0,
                    "typing_cadence_variability" to 0.1,
                    "typing_cadence_stability" to 0.9,
                    "typing_gap_count" to 1,
                    "typing_gap_ratio" to 0.05,
                    "typing_burstiness" to 0.2,
                    "typing_activity_ratio" to 0.95,
                    "typing_interaction_intensity" to 0.85
                )
            )
        )

        val summary = TypingSessionSummary.fromJson(json)

        assertEquals(1, summary.typingSessionCount)
        assertEquals(40.0, summary.averageKeystrokesPerSession, 0.1)
        assertEquals(1, summary.individualTypingSessions.size)
        assertEquals(true, summary.individualTypingSessions[0].deepTyping)
    }

    @Test
    fun `typing session summary toJson works`() {
        val typingMetrics = TypingMetrics(
            startAt = "2023-01-01T10:00:00Z",
            endAt = "2023-01-01T10:00:05Z",
            duration = 5,
            deepTyping = false,
            typingTapCount = 25,
            typingSpeed = 5.0,
            meanInterTapIntervalMs = 200.0,
            typingCadenceVariability = 0.1,
            typingCadenceStability = 0.9,
            typingGapCount = 1,
            typingGapRatio = 0.05,
            typingBurstiness = 0.2,
            typingActivityRatio = 0.95,
            typingInteractionIntensity = 0.85
        )

        val summary = TypingSessionSummary(
            typingSessionCount = 1,
            averageKeystrokesPerSession = 25.0,
            averageTypingSessionDuration = 5.0,
            averageTypingSpeed = 5.0,
            averageTypingGap = 200.0,
            averageInterTapInterval = 200.0,
            typingCadenceStability = 0.9,
            burstinessOfTyping = 0.2,
            totalTypingDuration = 5,
            activeTypingRatio = 0.1,
            typingContributionToInteractionIntensity = 0.05,
            deepTypingBlocks = 0,
            typingFragmentation = 0.05,
            individualTypingSessions = listOf(typingMetrics)
        )

        val json = summary.toJson()

        assertEquals(1, json["typing_session_count"])
        assertEquals(25.0, (json["average_keystrokes_per_session"] as Number).toDouble(), 0.1)
        val metrics = json["typing_metrics"] as List<*>
        assertEquals(1, metrics.size)
    }
}

