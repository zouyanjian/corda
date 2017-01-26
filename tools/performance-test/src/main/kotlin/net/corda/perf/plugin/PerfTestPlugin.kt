package net.corda.perf.plugin

import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.flows.TestIssueAndMove

class PerfTestPlugin : CordaPluginRegistry() {
    // A list of protocols that are required for this cordapp
    override val requiredFlows = mapOf(
            TestIssueAndMove::class.java.name to setOf(Party::class.java.name)
    )
}
