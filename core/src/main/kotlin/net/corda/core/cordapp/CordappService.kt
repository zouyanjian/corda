package net.corda.core.cordapp

import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentId

/**
 * Cordapp services are to allow access to necessary meta information about the cordapp to allow for attachment loading
 * configuration of cordapps, etc
 */
interface CordappService {
    /**
     * Exposes the current CorDapp context which will contain information and configuration of the CorDapp that
     * is currently running.
     * @throws IllegalStateException When called from a non-app context
     */
    fun getAppContext(): CordappContext

    /**
     * Resolve an attachment ID for a given contract name
     *
     * @param contractClassName The contract to find the attachment for
     * @return An attachment ID if it exists
     */
    fun getContractAttachmentID(contractClassName: ContractClassName): AttachmentId?
}