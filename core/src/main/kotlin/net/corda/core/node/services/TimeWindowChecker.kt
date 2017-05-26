package net.corda.core.node.services

import net.corda.core.contracts.TimeWindow
import net.corda.core.seconds
import java.time.Clock
import java.time.Duration

/**
 * Checks if the given time-window falls within the allowed tolerance interval.
 */
class TimeWindowChecker(val clock: Clock = Clock.systemUTC(),
                        val tolerance: Duration = 30.seconds) {
    fun isValid(timeWindow: TimeWindow) = timeWindow.isValid(clock, tolerance)
}
