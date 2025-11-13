package ai.synheart.behavior.internal

import ai.synheart.behavior.BehaviorEvent

/**
 * Base interface for all signal collectors in the SDK.
 * Each collector is responsible for monitoring specific behavioral signals.
 */
internal interface SignalCollector {
    /**
     * Start collecting behavioral signals.
     */
    fun start()

    /**
     * Stop collecting behavioral signals.
     */
    fun stop()

    /**
     * Set the callback for emitting behavioral events.
     */
    fun setEventCallback(callback: (BehaviorEvent) -> Unit)

    /**
     * Get current statistics from this collector.
     */
    fun getCurrentStats(): Map<String, Any?>

    /**
     * Get the session statistics summary.
     */
    fun getSessionSummary(): Map<String, Any?>

    /**
     * Reset all statistics for a new session.
     */
    fun resetSession()
}
