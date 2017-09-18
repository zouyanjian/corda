package net.corda.core.contracts

/**
 * Wrap an attachment in this if it is to be used as an executable contract attachment
 *
 * @property attachment The attachment representing the contract JAR
 * @property contract The contract name contained within the JAR
 */
class ContractAttachment(private val attachment: Attachment, val contract: ContractClassName) : Attachment {
    override fun open() = attachment.open()
    override val signers = attachment.signers
    override val id = attachment.id
}