package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionForContract
import net.corda.core.contracts.clauses.Clause
import java.util.*

/**
 * Wrapper around a clause, which filters and groups the states before passing them to the delegate clause.
 *
 * @see [TransactionForContract.groupStates]
 */
class GroupBy<S : ContractState, C : CommandData, K : Any>(val clause: Clause<S, C, K>,
                                                           val groupStates: TransactionForContract.() -> List<TransactionForContract.InOutGroup<S, K>>) : Clause<ContractState, C, Unit>() {
    override fun getExecutionPath(commands: List<AuthenticatedObject<C>>): List<Clause<*, *, *>>
            = clause.getExecutionPath(commands)

    override val requiredCommands: Set<Class<out CommandData>> = clause.requiredCommands

    override fun verify(tx: TransactionForContract,
                        inputs: List<ContractState>,
                        outputs: List<ContractState>,
                        commands: List<AuthenticatedObject<C>>,
                        groupingKey: Unit?): Set<C> {
        val groups = groupStates(tx)
        val matchedCommands = HashSet<C>()

        for ((groupInputs, groupOutputs, groupToken) in groups) {
            matchedCommands.addAll(clause.verify(tx, groupInputs, groupOutputs, commands, groupToken))
        }

        return matchedCommands
    }
}