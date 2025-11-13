package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent
import ai.synheart.behavior.BehaviorEventType
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * Collector for motion-lite signals: device orientation, shake patterns, micro-movement.
 * Monitors device motion to extract behavioral markers related to attention and fatigue.
 */
internal class MotionSignalCollector(
    private val sessionId: String,
    private val context: Context
) : SignalCollector, SensorEventListener {

    private var eventCallback: ((BehaviorEvent) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isCollecting = false

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Orientation tracking
    private val orientationEvents = ConcurrentLinkedQueue<OrientationEvent>()
    private var lastOrientation: FloatArray? = null
    private val orientationChangeThreshold = 30f // degrees

    // Shake detection
    private val accelerationEvents = ConcurrentLinkedQueue<AccelerationEvent>()
    private val shakeThreshold = 15f // m/s^2
    private var lastShakeTime: Long = 0
    private val shakeCooldown = 1000L // 1 second between shake detections

    // Micro-movement tracking
    private val microMovements = ConcurrentLinkedQueue<MicroMovementEvent>()
    private var lastAcceleration: FloatArray? = null

    // Statistics
    private var totalOrientationShifts = 0
    private var totalShakes = 0
    private var totalMicroMovements = 0

    override fun start() {
        isCollecting = true

        // Register sensor listeners with low sampling rate to save battery
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL // ~200ms delay = 5Hz
            )
        }

        gyroscope?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        // Start periodic event emission
        scope.launch {
            while (isCollecting) {
                delay(10000) // Emit every 10 seconds
                emitOrientationShiftSummary()
                emitMicroMovementSummary()
            }
        }
    }

    override fun stop() {
        isCollecting = false
        sensorManager.unregisterListener(this)
        scope.cancel()
    }

    override fun setEventCallback(callback: (BehaviorEvent) -> Unit) {
        this.eventCallback = callback
    }

    override fun getCurrentStats(): Map<String, Any?> {
        return mapOf(
            "totalOrientationShifts" to totalOrientationShifts,
            "totalShakes" to totalShakes,
            "totalMicroMovements" to totalMicroMovements,
            "recentOrientationShifts" to getRecentOrientationShifts(),
            "microMovementIntensity" to calculateMicroMovementIntensity()
        )
    }

    override fun getSessionSummary(): Map<String, Any?> {
        return mapOf(
            "totalOrientationShifts" to totalOrientationShifts,
            "totalShakes" to totalShakes,
            "totalMicroMovements" to totalMicroMovements,
            "averageMicroMovementIntensity" to calculateAverageMicroMovementIntensity()
        )
    }

    override fun resetSession() {
        orientationEvents.clear()
        accelerationEvents.clear()
        microMovements.clear()
        lastOrientation = null
        lastAcceleration = null
        lastShakeTime = 0
        totalOrientationShifts = 0
        totalShakes = 0
        totalMicroMovements = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isCollecting || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometerEvent(event)
            Sensor.TYPE_GYROSCOPE -> handleGyroscopeEvent(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this use case
    }

    private fun handleAccelerometerEvent(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val timestamp = System.currentTimeMillis()

        // Calculate total acceleration magnitude (remove gravity)
        val magnitude = sqrt(x * x + y * y + z * z)
        val acceleration = abs(magnitude - SensorManager.GRAVITY_EARTH)

        accelerationEvents.offer(AccelerationEvent(timestamp, acceleration))

        // Detect shake
        if (acceleration > shakeThreshold && timestamp - lastShakeTime > shakeCooldown) {
            lastShakeTime = timestamp
            totalShakes++
            emitShakePatternEvent(acceleration)
        }

        // Detect micro-movements (subtle device movements)
        lastAcceleration?.let { last ->
            val deltaX = abs(x - last[0])
            val deltaY = abs(y - last[1])
            val deltaZ = abs(z - last[2])
            val movementIntensity = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

            if (movementIntensity > 0.5f && movementIntensity < shakeThreshold) {
                microMovements.offer(MicroMovementEvent(timestamp, movementIntensity))
                totalMicroMovements++
            }
        }

        lastAcceleration = floatArrayOf(x, y, z)

        // Clean old events (keep last 60 seconds)
        cleanOldAccelerationEvents(timestamp, 60000)
        cleanOldMicroMovements(timestamp, 60000)
    }

    private fun handleGyroscopeEvent(event: SensorEvent) {
        val x = event.values[0] // Rotation around x-axis (pitch)
        val y = event.values[1] // Rotation around y-axis (roll)
        val z = event.values[2] // Rotation around z-axis (yaw)
        val timestamp = System.currentTimeMillis()

        val currentOrientation = floatArrayOf(x, y, z)

        // Detect significant orientation changes
        lastOrientation?.let { last ->
            val deltaX = abs(x - last[0])
            val deltaY = abs(y - last[1])
            val deltaZ = abs(z - last[2])
            val totalChange = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

            if (totalChange > orientationChangeThreshold) {
                totalOrientationShifts++
                orientationEvents.offer(OrientationEvent(timestamp, totalChange))
                emitOrientationShiftEvent(totalChange)
            }
        }

        lastOrientation = currentOrientation

        // Clean old events (keep last 60 seconds)
        cleanOldOrientationEvents(timestamp, 60000)
    }

    // Event emission methods
    private fun emitOrientationShiftEvent(magnitude: Float) {
        emitEvent(
            BehaviorEventType.ORIENTATION_SHIFT,
            mapOf(
                "magnitude_degrees" to magnitude,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    private fun emitOrientationShiftSummary() {
        val recentShifts = getRecentOrientationShifts()
        if (recentShifts > 0) {
            emitEvent(
                BehaviorEventType.ORIENTATION_SHIFT,
                mapOf("recent_shifts_count" to recentShifts)
            )
        }
    }

    private fun emitShakePatternEvent(intensity: Float) {
        emitEvent(
            BehaviorEventType.SHAKE_PATTERN,
            mapOf(
                "intensity_m_per_s2" to intensity,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    private fun emitMicroMovementSummary() {
        val intensity = calculateMicroMovementIntensity()
        if (intensity != null && intensity > 0) {
            emitEvent(
                BehaviorEventType.MICRO_MOVEMENT,
                mapOf(
                    "intensity" to intensity,
                    "count" to microMovements.count { it.timestamp > System.currentTimeMillis() - 60000 }
                )
            )
        }
    }

    // Calculation methods
    private fun getRecentOrientationShifts(): Int {
        val currentTime = System.currentTimeMillis()
        return orientationEvents.count { it.timestamp > currentTime - 60000 }
    }

    private fun calculateMicroMovementIntensity(): Double? {
        val recent = microMovements.filter { it.timestamp > System.currentTimeMillis() - 10000 }
        if (recent.isEmpty()) return null
        return recent.map { it.intensity.toDouble() }.average()
    }

    private fun calculateAverageMicroMovementIntensity(): Double? {
        if (microMovements.isEmpty()) return null
        return microMovements.map { it.intensity.toDouble() }.average()
    }

    private fun emitEvent(type: BehaviorEventType, payload: Map<String, Any?>) {
        eventCallback?.invoke(
            BehaviorEvent(
                sessionId = sessionId,
                timestamp = System.currentTimeMillis(),
                type = type,
                payload = payload.filterValues { it != null }.mapValues { it.value as Any }
            )
        )
    }

    private fun cleanOldOrientationEvents(currentTime: Long, maxAgeMs: Long) {
        while (orientationEvents.peek()?.let { currentTime - it.timestamp > maxAgeMs } == true) {
            orientationEvents.poll()
        }
    }

    private fun cleanOldAccelerationEvents(currentTime: Long, maxAgeMs: Long) {
        while (accelerationEvents.peek()?.let { currentTime - it.timestamp > maxAgeMs } == true) {
            accelerationEvents.poll()
        }
    }

    private fun cleanOldMicroMovements(currentTime: Long, maxAgeMs: Long) {
        while (microMovements.peek()?.let { currentTime - it.timestamp > maxAgeMs } == true) {
            microMovements.poll()
        }
    }

    private data class OrientationEvent(val timestamp: Long, val magnitude: Float)
    private data class AccelerationEvent(val timestamp: Long, val acceleration: Float)
    private data class MicroMovementEvent(val timestamp: Long, val intensity: Float)
}
