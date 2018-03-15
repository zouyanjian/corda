package net.corda.core.internal.notary

import com.google.common.primitives.Booleans
import net.corda.core.contracts.TimeWindow
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import java.security.PublicKey
import java.time.Clock

abstract class NotaryService : SingletonSerializeAsToken() {
    companion object {
        @Deprecated("No longer used")
        const val ID_PREFIX = "corda.notary."
        @Deprecated("No longer used")
        fun constructId(validating: Boolean, raft: Boolean = false, bft: Boolean = false, custom: Boolean = false): String {
            require(Booleans.countTrue(raft, bft, custom) <= 1) { "At most one of raft, bft or custom may be true" }
            return StringBuffer(ID_PREFIX).apply {
                append(if (validating) "validating" else "simple")
                if (raft) append(".raft")
                if (bft) append(".bft")
                if (custom) append(".custom")
            }.toString()
        }

        /**
         * Checks if the current instant provided by the clock falls within the specified time window. Should only be
         * used by a notary service flow.
         *
         * @throws NotaryInternalException if current time is outside the specified time window. The exception contains
         *                         the [NotaryError.TimeWindowInvalid] error.
         */
        @JvmStatic
        @Throws(NotaryInternalException::class)
        fun validateTimeWindow(clock: Clock, timeWindow: TimeWindow?) {
            if (timeWindow == null) return
            val currentTime = clock.instant()
            if (currentTime !in timeWindow) {
                throw NotaryInternalException(
                        NotaryError.TimeWindowInvalid(currentTime, timeWindow)
                )
            }
        }
    }

    abstract val services: ServiceHub
    abstract val notaryIdentityKey: PublicKey

    abstract fun start()
    abstract fun stop()

    /**
     * Produces a notary service flow which has the corresponding sends and receives as [NotaryFlow.Client].
     * @param otherPartySession client [Party] making the request
     */
    abstract fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?>
}