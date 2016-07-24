package net.corda.flows.twoparty

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.random63BitValue
import net.corda.core.testing.DUMMY_NOTARY
import net.corda.core.testing.MEGA_CORP
import com.typesafe.config.ConfigFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.*

class TwoPartyFlow {

    class TraderInitialArgs(
            val assetToSell: StateAndRef<OwnableState>,
            val price: Amount<Issued<Currency>>,
            val myKeyPair: KeyPair,
            val sessionID: Long
    )

    // Alice is the seller of USD and is the proposer
    val ALICE_KEY = KeyPairGenerator.getInstance("EC").genKeyPair()
    val ALICE_PUBKEY = ALICE_KEY.public
    val ALICE = Party("Alice", ALICE_PUBKEY)

    // Bob is the buyer of USD (for GBP) and is the acceptor
    val BOB_KEY = KeyPairGenerator.getInstance("EC").genKeyPair()
    val BOB_PUBKEY = BOB_KEY.public
    val BOB = Party("Bob", BOB_PUBKEY)

    // Operate flow
    fun operateFlow() {
        // create an Akka uniqueness
        val system = ActorSystem.create("TwoPartySystem", ConfigFactory.load("two-party"))

        // these services are actors that are supervised by the uniqueness
        val coordinator = system.actorOf(Props.create(Coordinator::class.java), "coordinator")

        val sessionID = random63BitValue()

        // Alice is going to sell USD
        val aliceCash = StateAndRef(
                TransactionState(Cash.State(ALICE.ref(1), 1000.DOLLARS, ALICE_PUBKEY), DUMMY_NOTARY),
                StateRef(SecureHash.randomSHA256(), Random().nextInt(32))
        )
        // Alice is going to accept pounds
        val aliceProposerInitialArgs = TraderInitialArgs(
                aliceCash,
                500.POUNDS `issued by` MEGA_CORP.ref(1),
                ALICE_KEY,
                sessionID
        )

        // Bob is going to sell GBP
        val bobCash = StateAndRef(
                TransactionState(Cash.State(BOB.ref(1), 500.POUNDS, BOB_PUBKEY), DUMMY_NOTARY),
                StateRef(SecureHash.randomSHA256(), Random().nextInt(32))
        )
        // Bob is going to accept dollars
        val bobAcceptorInitialArgs = TraderInitialArgs(
                bobCash,
                1000.DOLLARS `issued by` MEGA_CORP.ref(1),
                BOB_KEY,
                sessionID
        )

        coordinator.tell(Coordinator.TwoPartyTradeMsg(aliceProposerInitialArgs, bobAcceptorInitialArgs), ActorRef.noSender())

    }
}
