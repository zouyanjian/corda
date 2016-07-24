package net.corda.flows.twoparty

import akka.actor.Props
import akka.actor.UntypedActor
import net.corda.uniqueness.UniquenessValidatorAkkaPersistence
import net.corda.core.contracts.SignedTransaction
import net.corda.core.contracts.TransactionBuilder
import java.io.Serializable


class Coordinator : UntypedActor() {

    // Coordinator does not create and supervise the uniqueness service actor, but needs to communicate
    internal class TwoPartyTradeMsg(val proposerArgs: TwoPartyFlow.TraderInitialArgs, val acceptorArgs: TwoPartyFlow.TraderInitialArgs)

    internal class CompletedTxnMsg(val swt: SignedTransaction)

    internal class UniqueTxnResCmd(val uniquenessRef: Int) : Serializable {
        private val serialVersionUID = 1L;
    }

    // these actors are supervised by the Coordinator actor, and contain state specific to this trade
    val proposer = context.actorOf(Props.create(Proposer::class.java), "proposer")
    val acceptor = context.actorOf(Props.create(Acceptor::class.java), "acceptor")
    // this actor is global and might be clustered
    val uniquenessValidator = context.actorSelection("akka.tcp://UniquenessSystem@127.0.0.1:2553/user/uniqueness-validator")

    override fun onReceive(message: Any) {
        if (message is TwoPartyTradeMsg) {

            println("Coordinator, TwoPartyTradeMsg, thread " + Thread.currentThread())
            val tx = TransactionBuilder()
            proposer.tell(Proposer.ProposerMsg(tx, acceptor, message.proposerArgs, message.acceptorArgs), self)

        } else if (message is CompletedTxnMsg) {

            println("Coordinator, CompletedTxnMsg, thread " + Thread.currentThread())
            uniquenessValidator.tell(UniquenessValidatorAkkaPersistence.UniqueTxnReqCmd(message.swt.hashCode(),
                    message.swt.tx.inputs.map { it.hashCode() }), self)
            println(message.swt)

         } else if (message is UniqueTxnResCmd) {

            println("Coordinator, UniqueTxnMsg, thread " + Thread.currentThread())
            println(message.uniquenessRef)
            // Stops this actor and all its supervised children
            context.system().terminate()

        } else {
            unhandled(message)
        }
    }

}
