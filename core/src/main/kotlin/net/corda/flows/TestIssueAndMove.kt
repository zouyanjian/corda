package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.DummyContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.recordTransactions
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_PUBKEY_1
import java.util.*

class TestIssueAndMove(private val notary: Party) : FlowLogic<SignedTransaction>() {
    val counterPartyKey = DUMMY_PUBKEY_1

    @Suspendable
    override fun call():SignedTransaction {
        val asset = issue()

        // Move ownership of the asset to the counterparty
        val moveTx = DummyContract.move(asset, counterPartyKey).apply {
            signWith(serviceHub.legalIdentityKey)
        }

        // We don't check signatures because we know that the notary's signature is missing
        val stx = moveTx.toSignedTransaction(checkSufficientSignatures = false)
        subFlow(FinalityFlow(stx, setOf(serviceHub.myInfo.legalIdentity)))
        return stx
    }

    private fun issue(): StateAndRef<DummyContract.SingleOwnerState> {
        val random = Random()
        // Self issue an asset
        val issueTx = DummyContract.generateInitial(serviceHub.myInfo.legalIdentity.ref(0), random.nextInt(), notary).apply {
            signWith(serviceHub.legalIdentityKey)
        }

        serviceHub.recordTransactions(issueTx.toSignedTransaction())
        return issueTx.toWireTransaction().outRef<DummyContract.SingleOwnerState>(0)
    }
}