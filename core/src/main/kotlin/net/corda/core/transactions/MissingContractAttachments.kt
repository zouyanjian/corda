package net.corda.core.transactions

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState

/**
 * A contract attachment was missing when trying to automatically attach all known contract attachments
 */
class MissingContractAttachments(val states: List<TransactionState<ContractState>>) : Exception()