package net.corda.perf

import net.corda.core.div
import net.corda.core.node.services.ServiceInfo
import net.corda.flows.TestIssueAndMove
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import java.nio.file.Paths

/** Creates and starts all nodes required for the demo. */
fun main(args: Array<String>) {
    val permissions = setOf(
            startFlowPermission<TestIssueAndMove>())
    val demoUser = listOf(User("demo", "demo", permissions))
    driver(isDebug = true, driverDirectory = Paths.get("build") / "perf-nodes") {
        startNode("Party", rpcUsers = demoUser)
        startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type)))
        waitForAllNodesToFinish()
    }
}