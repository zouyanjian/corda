package net.corda.core.contracts

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable

/** Constrain which contract-code-containing attachment can be used with a [ContractState]. */
@CordaSerializable
interface AttachmentConstraint {
    /** Returns whether the given contract attachment can be used with the [ContractState] associated with this constraint object. */
    fun isSatisfiedBy(attachment: Attachment): Boolean
}

/** An [AttachmentConstraint] where [isSatisfiedBy] always returns true. */
object AlwaysAcceptAttachmentConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment) = true
}

/** An [AttachmentConstraint] that verifies by hash */
data class HashAttachmentConstraint(val attachmentId: SecureHash) : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment) = attachment.id == attachmentId
}

