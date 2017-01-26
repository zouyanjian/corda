package net.corda

import com.google.common.util.concurrent.Futures
import com.yourkit.api.Controller
import net.corda.core.contracts.DummyContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.Party
import net.corda.core.crypto.newSecureRandom
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.loggerFor
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.node.utilities.databaseTransaction
import net.corda.node.utilities.timed
import net.corda.testing.node.NodeBasedTest
import org.h2.mvstore.MVStore
import org.h2.util.Profiler
import org.junit.Ignore
import org.junit.Test
import java.security.KeyPair
import java.util.*


class PerformanceTest : NodeBasedTest() {
    val log = loggerFor<PerformanceTest>()

    //    @Test @Ignore
//    fun `simpleTest`() {
//        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)
//
//        val (masterNode, alice) = Futures.allAsList(
//                startNode("Notary", advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type))),
//                startNode("Alice")
//        ).getOrThrow()
//
//
//        masterNode.services.registerFlowInitiator(DummyFlow::class, ::DummyFlowReceive)
//
//        val notaryParty = alice.netMapCache.getAnyNotary()!!
//        val notaryNodeKeyPair = databaseTransaction(masterNode.database) { masterNode.services.notaryIdentityKey }
//        val aliceKey = databaseTransaction(alice.database) { alice.services.legalIdentityKey }
//
//        for (i in 1..10000) {
//
//            val toSend = i
//            val t = timed {
//                val firstSpend = alice.services.startFlow(DummyFlow(toSend, notaryParty))
//                val res = firstSpend.resultFuture.getOrThrow()
//                println(res)
//            }
//            val duration = t.second.toInt()
//            log.info("$duration")
//            println("$i: $duration ms")
//        }
//    }


    @Test @Ignore
    fun mvStorePerfTest() {
        val rnd = newSecureRandom()
        val s = MVStore.Builder().fileName("testStore${rnd.nextInt()}.mv.db").autoCommitDisabled().open()

        val map = s.openMap<Int, Int>("data")
        val durations = mutableListOf<Double>()

        val t1 = timed {
            for (i in 1..100000) {
                val t = timed {
                    map.put(rnd.nextInt(), rnd.nextInt())
                    s.commit()
                }
                durations.add(t.second)
            }
            println(durations.joinToString())
        }.second

        println("Total time: $t1")


//        Thread.sleep(50000)

//        s.compact()

//        map.put(rnd.nextInt(), rnd.nextFloat().toString())
//        s.commit()

//        println(s.currentVersion)


    }

    @Test @Ignore
    fun mvstoreTest() {
        val s = MVStore.open("testStore.mv.db")
        val map = s.openMap<Int, String>("data")

        // add some data
        map.put(1, "Hello")
        map.put(2, "World")

        // get the current version, for later use
        val oldVersion = s.currentVersion
        println("Version: " + s.currentVersion)
        // from now on, the old version is read-only
        s.commit()

        // more changes, in the new version
        // changes can be rolled back if required
        // changes always go into "head" (the newest version)
        map.put(1, "Hi")
        map.remove(2)
        println("Version: " + s.currentVersion)
        s.commit()

        map.put(1, "Hi 3")
        println("Version: " + s.currentVersion)
        s.commit()
        println("Version: " + s.currentVersion)


        // access the old data (before the commit)
        val oldMap = map.openVersion(oldVersion)

        // print the old version (can be done
        // concurrently with further modifications)
        // this will print "Hello" and "World":
        println(oldMap.get(1))
        println(oldMap.get(2))

        Thread.sleep(50000)

        // access the old data (before the commit)
        val oldMap2 = map.openVersion(oldVersion)

        // print the old version (can be done
        // concurrently with further modifications)
        // this will print "Hello" and "World":
        println(oldMap2.get(1))
        println(oldMap2.get(2))

        // print the newest version ("Hi")
        println(map.get(1))
    }

    @Test @Ignore
    fun `detect double spend`() {
        val (masterNode, alice) = Futures.allAsList(
                startNode("Notary", advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type))),
                startNode("Alice")
        ).getOrThrow()

        val notaryParty = alice.netMapCache.getAnyNotary()!!
        val notaryNodeKeyPair = databaseTransaction(masterNode.database) { masterNode.services.notaryIdentityKey }
        val aliceKey = databaseTransaction(alice.database) { alice.services.legalIdentityKey }

        var durations = mutableListOf<Int>()

//        val prof = Profiler()

        val controller = Controller()
//        controller.startCPUSampling(null)


        var i = 0
        while (true) {
            i++
            val t = timed {
                val inputState = issueState(alice, notaryParty, notaryNodeKeyPair)

                val firstSpendTx = TransactionType.General.Builder(notaryParty).withItems(inputState).run {
                    signWith(aliceKey)
                    toSignedTransaction(false)
                }
                val firstSpend = alice.services.startFlow(NotaryFlow.Client(firstSpendTx))
                firstSpend.resultFuture.getOrThrow()
            }
            val duration = t.second.toInt()
            durations.add(duration)

            if (i == 1000) controller.startCPUSampling(null)

            if (i > 1000 && i % 1000 == 0) {
                println(controller.capturePerformanceSnapshot())
//
                println("$i = " + durations.joinToString())
                println("Average: " + durations.average())
                Thread.sleep(1000)
                durations = mutableListOf<Int>()
//                prof.startCollecting()
            }
        }
    }

    private fun issueState(node: AbstractNode, notary: Party, notaryKey: KeyPair): StateAndRef<*> {
        return databaseTransaction(node.database) {
            val tx = DummyContract.generateInitial(node.info.legalIdentity.ref(0), Random().nextInt(), notary)
            tx.signWith(node.services.legalIdentityKey)
            tx.signWith(notaryKey)
            val stx = tx.toSignedTransaction()
            node.services.recordTransactions(listOf(stx))
            StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
        }
    }
}
//
//class DummyFlow(private val value: Int, private val otherParty: Party) : FlowLogic<Int>() {
//    @Suspendable
//    override fun call(): Int {
//        val res = sendAndReceive<Int>(otherParty, value).unwrap { it }
//        return res
//    }
//}
//
//class DummyFlowReceive(private val otherParty: Party) : FlowLogic<Unit>() {
//    @Suspendable
//    override fun call() {
//        val a = receive<Int>(otherParty).unwrap { it }
//        send(otherParty, a * 2)
//    }
//}
