package ai.synheart.behavior

import org.junit.Test
import org.junit.Assert.*

class MotionStateTest {

    @Test
    fun `motion state creation works`() {
        val motionState = MotionState(
            state = listOf("sitting", "standing", "sitting"),
            majorState = "sitting",
            majorStatePct = 0.67,
            mlModel = "motion_state_svc_classifier_v0.1",
            confidence = 0.95
        )

        assertEquals(3, motionState.state.size)
        assertEquals("sitting", motionState.majorState)
        assertEquals(0.67, motionState.majorStatePct, 0.01)
        assertEquals("motion_state_svc_classifier_v0.1", motionState.mlModel)
        assertEquals(0.95, motionState.confidence, 0.01)
    }

    @Test
    fun `motion state fromJson works`() {
        val json = mapOf(
            "state" to listOf("laying", "moving", "sitting"),
            "major_state" to "moving",
            "major_state_pct" to 0.5,
            "ml_model" to "motion_state_svc_classifier_v0.1",
            "confidence" to 0.88
        )

        val motionState = MotionState.fromJson(json)

        assertEquals(3, motionState.state.size)
        assertEquals("moving", motionState.majorState)
        assertEquals(0.5, motionState.majorStatePct, 0.01)
        assertEquals(0.88, motionState.confidence, 0.01)
    }

    @Test
    fun `motion state toJson works`() {
        val motionState = MotionState(
            state = listOf("standing", "sitting"),
            majorState = "standing",
            majorStatePct = 0.6,
            mlModel = "motion_state_svc_classifier_v0.1",
            confidence = 0.92
        )

        val json = motionState.toJson()

        assertEquals(2, (json["state"] as List<*>).size)
        assertEquals("standing", json["major_state"])
        assertEquals(0.6, (json["major_state_pct"] as Number).toDouble(), 0.01)
    }
}

