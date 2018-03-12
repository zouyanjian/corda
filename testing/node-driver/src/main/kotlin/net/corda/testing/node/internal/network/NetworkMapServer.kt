package net.corda.testing.node.internal.network

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.readObject
import net.corda.core.internal.signWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.ParametersUpdate
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import java.io.Closeable
import java.io.InputStream
import java.net.InetSocketAddress
import java.security.PublicKey
import java.security.SignatureException
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.ok
import javax.ws.rs.core.Response.status
import kotlin.collections.HashMap

class NetworkMapServer(private val cacheTimeout: Duration,
                       hostAndPort: NetworkHostAndPort,
                       private val networkMapCa: CertificateAndKeyPair = createDevNetworkMapCa(),
                       private val myHostNameValue: String = "test.host.name",
                       vararg additionalServices: Any) : Closeable {
    companion object {
        private val stubNetworkParameters = NetworkParameters(1, emptyList(), 10485760, Int.MAX_VALUE, Instant.now(), 10, emptyMap())
    }

    private val server: Server
    var networkParameters: NetworkParameters = stubNetworkParameters
        set(networkParameters) {
            check(field == stubNetworkParameters) { "Network parameters can be set only once" }
            field = networkParameters
        }
    private val service = InMemoryNetworkMapService()
    private var parametersUpdate: ParametersUpdate? = null
    private var nextNetworkParameters: NetworkParameters? = null

    init {
        server = Server(InetSocketAddress(hostAndPort.host, hostAndPort.port)).apply {
            handler = HandlerCollection().apply {
                addHandler(ServletContextHandler().apply {
                    contextPath = "/"
                    val resourceConfig = ResourceConfig().apply {
                        // Add your API provider classes (annotated for JAX-RS) here
                        register(service)
                        additionalServices.forEach { register(it) }
                    }
                    val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply { initOrder = 0 } // Initialise at server start
                    addServlet(jerseyServlet, "/*")
                })
            }
        }
    }

    fun start(): NetworkHostAndPort {
        server.start()
        // Wait until server is up to obtain the host and port.
        while (!server.isStarted) {
            Thread.sleep(500)
        }
        return server.connectors
                .mapNotNull { it as? ServerConnector }
                .first()
                .let { NetworkHostAndPort(it.host, it.localPort) }
    }

    fun removeNodeInfo(nodeInfo: NodeInfo) {
        service.nodeInfos -= nodeInfo.legalIdentities[0].name
    }

    fun scheduleParametersUpdate(nextParameters: NetworkParameters, description: String, updateDeadline: Instant) {
        nextNetworkParameters = nextParameters
        parametersUpdate = ParametersUpdate(nextParameters.serialize().hash, description, updateDeadline)
    }

    fun advertiseNewParameters() {
        networkParameters = checkNotNull(nextNetworkParameters) { "Schedule parameters update first" }
        nextNetworkParameters = null
        parametersUpdate = null
    }

    fun latestParametersAccepted(publicKey: PublicKey): SecureHash? {
        return service.latestAcceptedParametersMap[publicKey]
    }

    fun registerAsPrivate(nodeName: CordaX500Name, privateNetworkMapKey: UUID) {
        service.privateNames[nodeName] = privateNetworkMapKey
    }

    override fun close() {
        server.stop()
    }

    @Path("network-map")
    inner class InMemoryNetworkMapService {
        val nodeInfos = HashMap<CordaX500Name, NodeInfoHolder>()
        val latestAcceptedParametersMap = HashMap<PublicKey, SecureHash>()
        private val signedNetParams by lazy {
            networkParameters.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
        }
        val privateNames = HashMap<CordaX500Name, UUID>()

        @POST
        @Path("publish")
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        fun publishNodeInfo(input: InputStream): Response {
            return try {
                val nodeInfoAndSigned = NodeInfoAndSigned(input.readObject())
                val nodeName = nodeInfoAndSigned.nodeInfo.legalIdentities[0].name
                val privateNetworkMapKey = privateNames[nodeName]
                nodeInfos[nodeName] = NodeInfoHolder(nodeInfoAndSigned, privateNetworkMapKey)
                ok()
            } catch (e: Exception) {
                when (e) {
                    is SignatureException -> status(Response.Status.FORBIDDEN).entity(e.message)
                    else -> status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.message)
                }
            }.build()
        }

        @POST
        @Path("ack-parameters")
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        fun ackNetworkParameters(input: InputStream): Response {
            val signedParametersHash = input.readObject<SignedData<SecureHash>>()
            val hash = signedParametersHash.verified()
            latestAcceptedParametersMap[signedParametersHash.sig.by] = hash
            return ok().build()
        }

        @GET
        @Path("{var}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getNetworkMap(@PathParam("var") privNetMapKeyString: String): Response {
            val privateNetworkMapKey = UUID.fromString(privNetMapKeyString)
            val nodeInfoHashes = nodeInfos.values.mapNotNull { if (it.privateNetworkMapKey == privateNetworkMapKey) it.nodeInfoHash else null }
            val networkMap = NetworkMap(nodeInfoHashes, signedNetParams.raw.hash, parametersUpdate)
            val signedNetworkMap = networkMap.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
            return Response.ok(signedNetworkMap.serialize().bytes).header("Cache-Control", "max-age=${cacheTimeout.seconds}").build()
        }

        @GET
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getNetworkMap(): Response = getNetworkMap(null)

        private fun getNetworkMap(privateNetworkMapKey: UUID?): Response {
            val nodeInfoHashes = nodeInfos.values.mapNotNull { if (it.privateNetworkMapKey == privateNetworkMapKey) it.nodeInfoHash else null }
            val networkMap = NetworkMap(nodeInfoHashes, signedNetParams.raw.hash, parametersUpdate)
            val signedNetworkMap = networkMap.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
            return Response.ok(signedNetworkMap.serialize().bytes).header("Cache-Control", "max-age=${cacheTimeout.seconds}").build()
        }

        @GET
        @Path("node-info/{var}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getNodeInfo(@PathParam("var") nodeInfoHashString: String): Response {
            val nodeInfoHash = SecureHash.parse(nodeInfoHashString)
            val signedNodeInfo = nodeInfos.values.find { it.nodeInfoHash == nodeInfoHash }?.nodeInfoAndSigned?.signed
            return if (signedNodeInfo != null) {
                Response.ok(signedNodeInfo.serialize().bytes)
            } else {
                Response.status(Response.Status.NOT_FOUND)
            }.build()
        }

        @GET
        @Path("network-parameters/{var}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getNetworkParameter(@PathParam("var") hash: String): Response {
            val requestedHash = SecureHash.parse(hash)
            val requestedParameters = if (requestedHash == signedNetParams.raw.hash) {
                signedNetParams
            } else if (requestedHash == nextNetworkParameters?.serialize()?.hash) {
                nextNetworkParameters?.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
            } else null
            requireNotNull(requestedParameters)
            return Response.ok(requestedParameters!!.serialize().bytes).build()
        }

        @GET
        @Path("my-hostname")
        fun getHostName(): Response = Response.ok(myHostNameValue).build()
    }

    data class NodeInfoHolder(val nodeInfoAndSigned: NodeInfoAndSigned, val privateNetworkMapKey: UUID?) {
        val nodeInfoHash: SecureHash get() = nodeInfoAndSigned.signed.raw.hash
    }
}
