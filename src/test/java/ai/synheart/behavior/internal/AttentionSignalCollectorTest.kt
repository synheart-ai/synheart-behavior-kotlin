package ai.synheart.behavior.internal

import android.app.Activity
import android.app.Application
import android.content.Context
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AttentionSignalCollectorTest {

    private lateinit var collector: AttentionSignalCollector
    private val sessionId = "test-session"
    private val maxIdleGapSeconds = 30.0

    private lateinit var application: Application

    @Before
    fun setup() {
        application = mockk(relaxed = true)
        every { application.getSystemService(Context.TELEPHONY_SERVICE) } returns null

        collector = AttentionSignalCollector(sessionId, application, maxIdleGapSeconds)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `start registers lifecycle callbacks`() {
        collector.start()
        verify { application.registerActivityLifecycleCallbacks(any()) }
    }

    @Test
    fun `onActivityPaused increments taskSwitchCount`() {
        collector.start()

        // Capture the registered callback
        val callbackSlot = slot<Application.ActivityLifecycleCallbacks>()
        verify { application.registerActivityLifecycleCallbacks(capture(callbackSlot)) }
        val callback = callbackSlot.captured

        assertEquals(0, collector.getAppSwitchCount())

        // App switch is counted when going to background (onActivityPaused -> onAppBackgrounded)
        callback.onActivityResumed(mockk<Activity>(relaxed = true)) // foreground
        callback.onActivityPaused(mockk<Activity>(relaxed = true))   // background -> count 1
        assertEquals(1, collector.getAppSwitchCount())

        callback.onActivityResumed(mockk<Activity>(relaxed = true))
        callback.onActivityPaused(mockk<Activity>(relaxed = true))   // background -> count 2
        assertEquals(2, collector.getAppSwitchCount())
    }

    @Test
    fun `stop unregisters lifecycle callbacks`() {
        collector.start()
        collector.stop()
        verify { application.unregisterActivityLifecycleCallbacks(any()) }
    }
}
