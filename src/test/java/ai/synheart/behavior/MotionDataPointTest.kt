package ai.synheart.behavior

import org.junit.Test
import org.junit.Assert.*

class MotionDataPointTest {

    @Test
    fun `motion data point creation works`() {
        val features = mapOf(
            "tBodyAcc-mean()-X" to 0.25,
            "tBodyAcc-mean()-Y" to -0.1,
            "tBodyAcc-mean()-Z" to 0.05
        )

        val dataPoint = MotionDataPoint(
            timestamp = "2023-01-01T10:00:00Z",
            features = features
        )

        assertEquals("2023-01-01T10:00:00Z", dataPoint.timestamp)
        assertEquals(3, dataPoint.features.size)
        assertEquals(0.25, dataPoint.features["tBodyAcc-mean()-X"]!!, 0.001)
    }

    @Test
    fun `motion data point fromJson works`() {
        val json = mapOf(
            "timestamp" to "2023-01-01T10:00:00Z",
            "features" to mapOf(
                "tBodyAcc-std()-X" to 0.15,
                "tBodyAcc-std()-Y" to 0.12
            )
        )

        val dataPoint = MotionDataPoint.fromJson(json)

        assertEquals("2023-01-01T10:00:00Z", dataPoint.timestamp)
        assertEquals(2, dataPoint.features.size)
        assertEquals(0.15, dataPoint.features["tBodyAcc-std()-X"]!!, 0.001)
    }

    @Test
    fun `motion data point toJson works`() {
        val features = mapOf(
            "fBodyAccMag-mean()" to 0.3,
            "fBodyAccMag-std()" to 0.2
        )

        val dataPoint = MotionDataPoint(
            timestamp = "2023-01-01T10:00:00Z",
            features = features
        )

        val json = dataPoint.toJson()

        assertEquals("2023-01-01T10:00:00Z", json["timestamp"])
        val jsonFeatures = json["features"] as Map<*, *>
        assertEquals(2, jsonFeatures.size)
    }
}

