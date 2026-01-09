package ai.synheart.behavior.internal

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class AttentionSignalCollectorTest {

    private lateinit var collector: AttentionSignalCollector
    private val sessionId = "test-session"
    private val maxIdleGapSeconds = 30.0

    @Mock lateinit var application: Application

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Mock getSystemService for TelephonyManager to avoid NPE or errors
        `when`(application.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(null)
        
        collector = AttentionSignalCollector(sessionId, application, maxIdleGapSeconds)
    }

    @Test
    fun `start registers lifecycle callbacks`() {
        collector.start()
        verify(application).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `onActivityResumed increments taskSwitchCount`() {
        collector.start()

        // Capture the registered callback
        val captor = ArgumentCaptor.forClass(Application.ActivityLifecycleCallbacks::class.java)
        verify(application).registerActivityLifecycleCallbacks(captor.capture())
        val callback = captor.value

        assertEquals(0, collector.getAppSwitchCount())

        // Simulate resume (app foreground logic calls onAppForegrounded)
        callback.onActivityResumed(mock(android.app.Activity::class.java))
        
        // Should increment to 1
        assertEquals(1, collector.getAppSwitchCount())
        
        // Simulate pause/resume
        callback.onActivityPaused(mock(android.app.Activity::class.java))
        callback.onActivityResumed(mock(android.app.Activity::class.java))
        
        assertEquals(2, collector.getAppSwitchCount())
    }
    
    @Test
    fun `stop unregisters lifecycle callbacks`() {
        collector.start()
        collector.stop()
        verify(application).unregisterActivityLifecycleCallbacks(any())
    }
}
