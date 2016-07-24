package net.corda.flows.twoparty

import akka.actor.UntypedActor
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionBuilder
import net.corda.core.contracts.sumOrThrow
import java.util.*

class Acceptor : UntypedActor() {
    internal class AcceptorMsg(val tx: TransactionBuilder, val proposerArgs: TwoPartyFlow.TraderInitialArgs, val acceptorArgs: TwoPartyFlow.TraderInitialArgs)

    override fun onReceive(message: Any) {
        if (message is AcceptorMsg) {

            println("Buyer, BuyerMsg, thread " + Thread.currentThread())
            val acceptorCash = ArrayList<StateAndRef<Cash.State>>()
            if (message.acceptorArgs.assetToSell.state.data is Cash.State) {
                acceptorCash.add(message.acceptorArgs.assetToSell as StateAndRef<Cash.State>)
            }
            // Acceptor assigns cash to the proposer, and signs
            val amount = acceptorCash.map { it.state.data.amount }.sumOrThrow()
            Cash().generateSpend(message.tx,
                    Amount(amount.quantity, amount.token.product),
                    message.proposerArgs.myKeyPair.public,
                    acceptorCash)
            message.tx.signWith(message.acceptorArgs.myKeyPair)
            // B sends to S a SignedWireTransaction that includes the state as input, B’s cash as input, the state with
            // the new owner key as output, and any change cash as output. It contains a single signature
            sender.tell(Proposer.PartialTxnMsg(message.tx, message.proposerArgs), self)

        } else {
            unhandled(message)
        }
    }

}
