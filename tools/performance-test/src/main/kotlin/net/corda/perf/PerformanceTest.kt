package net.corda.perf

import com.google.common.net.HostAndPort
import net.corda.core.div
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.flows.TestIssueAndMove
import net.corda.node.services.config.NodeSSLConfiguration
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.node.utilities.timed
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
    val host = HostAndPort.fromString("localhost:10008")
    println("Connecting to the recipient node ($host)")
    CordaRPCClient(host, sslConfigFor("nodeb", "certs")).use("demo", "demo") {
        val api = ClientApi(this)
        while (true) {
            val t = timed {
                api.startTest(500)
            }
            println("Total duration: ${t.second}ms")
        }
    }
}


/** Interface for using the notary demo API from a client. */
private class ClientApi(val rpc: CordaRPCOps) {

    private val notary by lazy {
        rpc.networkMapUpdates().first.first { it.advertisedServices.any { it.info.type.isNotary() } }.notaryIdentity
    }

    private companion object {
        private val TRANSACTION_COUNT = 10
    }

    fun startTest(transactionCount: Int = TRANSACTION_COUNT) {
        (1..transactionCount)
                .forEach {
                    val retValObs = rpc.startFlow(::TestIssueAndMove, notary)
                    retValObs.progress.subscribe().unsubscribe()
                    retValObs.returnValue.toBlocking().first()
                }
    }
}

// TODO: Take this out once we have a dedicated RPC port and allow SSL on it to be optional.
private fun sslConfigFor(nodename: String, certsPath: String?): NodeSSLConfiguration {
    return object : NodeSSLConfiguration {
        override val keyStorePassword: String = "cordacadevpass"
        override val trustStorePassword: String = "trustpass"
        override val certificatesDirectory: Path = if (certsPath != null) Paths.get(certsPath) else Paths.get("build") / "nodes" / nodename / "certificates"
    }
}
