package net.corda.core.contracts

class ContractAttachment(private val attachment: Attachment, val contracts: List<ContractClassName>) : Attachment {
    override fun open() = attachment.open()
    override val signers = attachment.signers
    override val id = attachment.id
}