package net.corda.node.modes.draining

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.chooseIdentity
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class P2PFlowsDrainingModeTest {

    private val portAllocation = PortAllocation.Incremental(10000)
    private val user = User("mark", "dadada", setOf(Permissions.all()))
    private val users = listOf(user)

    private var executor: ExecutorService? = null

    companion object {
        private val logger = loggerFor<P2PFlowsDrainingModeTest>()
        private val sendCompletes = HashMap<Party, OpenFuture<Unit>>()
    }

    @Before
    fun setup() {
        executor = Executors.newSingleThreadExecutor()
    }

    @After
    fun cleanUp() {
        executor!!.shutdown()
    }

    @Test
    fun `flows draining mode suspends consumption of initial session messages`() {

        driver(isDebug = true, startNodesInProcess = true, portAllocation = portAllocation) {
            val initiatedNode = startNode().getOrThrow()
            val initiating = startNode(rpcUsers = users).getOrThrow().rpc
            val counterParty = initiatedNode.nodeInfo.chooseIdentity()
            val initiated = initiatedNode.rpc

            initiated.setFlowsDrainingModeEnabled(true)

            initiating.apply {
                val sendComplete = openFuture<Unit>()
                sendCompletes[counterParty] = sendComplete
                val flow = startFlow(::InitiateSessionFlow, counterParty)
                sendComplete.get()
                require(!flow.returnValue.isDone) { "Flow should not be finished" }
                logger.info("Now disabling flows draining mode for $counterParty.")
                initiated.setFlowsDrainingModeEnabled(false)
                val result = flow.returnValue.getOrThrow()
                assertThat(result).isEqualTo("Hi there answer")
            }
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class InitiateSessionFlow(private val counterParty: Party) : FlowLogic<String>() {

        @Suspendable
        override fun call(): String {

            val session = initiateFlow(counterParty)
            session.send("Hi there")
            sendCompletes[counterParty]?.set(Unit)
            return session.receive<String>().unwrap { it }
        }
    }

    @InitiatedBy(InitiateSessionFlow::class)
    class InitiatedFlow(private val initiatingSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            val message = initiatingSession.receive<String>().unwrap { it }
            initiatingSession.send("$message answer")
        }
    }
}