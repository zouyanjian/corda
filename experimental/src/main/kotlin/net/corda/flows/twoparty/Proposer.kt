package net.corda.flows.twoparty

import akka.actor.ActorRef
import akka.actor.UntypedActor
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionBuilder
import net.corda.core.contracts.sumOrThrow
import java.util.*

class Proposer : UntypedActor() {
    internal class ProposerMsg(val tx: TransactionBuilder, val acceptor: ActorRef, val proposerArgs: TwoPartyFlow.TraderInitialArgs, val acceptorArgs: TwoPartyFlow.TraderInitialArgs)
    internal class PartialTxnMsg(val tx: TransactionBuilder, val proposerArgs: TwoPartyFlow.TraderInitialArgs)

    override fun onReceive(message: Any) {
        if (message is ProposerMsg) {

            println("Proposer, ProposerMsg, thread " + Thread.currentThread())
            val proposerCash = ArrayList<StateAndRef<Cash.State>>()
            val ref = message.proposerArgs.assetToSell
            val state = ref.state.data
            if (state is Cash.State) {
                proposerCash.add(ref as StateAndRef<Cash.State>)
            }
            val amount = proposerCash.map { it.state.data.amount }.sumOrThrow()
            // S sends a StateAndRef pointing to the state they want to sell to B, along with info about the price they require B to pay.
            Cash().generateSpend(message.tx,
                    Amount(amount.quantity, amount.token.product),
                    message.acceptorArgs.myKeyPair.public,
                    proposerCash)
            message.acceptor.tell(Acceptor.AcceptorMsg(message.tx, message.proposerArgs, message.acceptorArgs), self)

        } else if (message is PartialTxnMsg) {

            println("Seller, PartialTxnMsg, thread " + Thread.currentThread())
            // S signs it and hands the now finalised SignedWireTransaction back to the Coordinator
            message.tx.signWith(message.proposerArgs.myKeyPair)
            context.parent().tell(Coordinator.CompletedTxnMsg(message.tx.toSignedTransaction(true)), self)

        } else {
            unhandled(message)
        }
    }

}
