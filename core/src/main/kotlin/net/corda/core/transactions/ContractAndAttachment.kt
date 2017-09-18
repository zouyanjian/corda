package net.corda.core.transactions

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractClassName

/**
 * A contract and attachment container. In proper usage the contract class that named by the contractName parameter
 * will exist as a .class file within the JAR file represented by the attachment property.
 *
 * @param contractName A contract class name that is contained within the JAR represented by the attachment
 * @param attachment An attachment that contained a class of the same name as the contractName
 */
data class ContractAndAttachment(val contractName: ContractClassName, val attachment: Attachment)