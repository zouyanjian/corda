package net.corda.node.services.messaging

import io.netty.channel.Channel
import io.netty.handler.ssl.SslHandler
import net.corda.core.crypto.AddressFormatException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.utilities.*
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.utilities.X509Utilities
import net.corda.nodeapi.ArtemisMessagingComponent
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.*
import org.apache.activemq.artemis.api.core.management.CoreNotificationType
import org.apache.activemq.artemis.api.core.management.ManagementHelper
import org.apache.activemq.artemis.jms.bridge.ConnectionFactoryFactory
import org.apache.activemq.artemis.jms.bridge.DestinationFactory
import org.apache.activemq.artemis.jms.bridge.QualityOfServiceMode
import org.apache.activemq.artemis.jms.bridge.impl.JMSBridgeImpl
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQQueue
import org.apache.qpid.jms.JmsConnectionFactory
import org.apache.qpid.jms.JmsTopic
import org.apache.qpid.jms.transports.TransportOptions
import org.apache.qpid.jms.transports.netty.NettySslTransportFactory
import org.apache.qpid.jms.transports.netty.NettyTcpTransport
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.security.auth.x500.X500Principal

class BridgeManager(private val serverLocator: ServerLocator,
                    private val serverAddress: NetworkHostAndPort,
                    private val username: String,
                    private val password: String,
                    private val networkMap: NetworkMapCache,
                    private val config: NodeConfiguration
) {
    companion object {
        val BRIDGE_MANAGER = "${ArtemisMessagingComponent.INTERNAL_PREFIX}bridge.manager"
        val BRIDGE_MANAGER_FILTER = "${ManagementHelper.HDR_NOTIFICATION_TYPE} = '${CoreNotificationType.BINDING_ADDED.name}' AND " +
                "${ManagementHelper.HDR_ROUTING_NAME} LIKE '${ArtemisMessagingComponent.INTERNAL_PREFIX}%'"
        private val log = loggerFor<BridgeManager>()

        private val expectedLegalNames = ConcurrentHashMap<String, Set<CordaX500Name>>()
    }

    private val bridges = mutableListOf<JMSBridgeImpl>()
    private lateinit var session: ClientSession
    private lateinit var consumer: ClientConsumer

    fun start() {
        log.trace { "Starting BridgeManager.." }
        val sessionFactory = serverLocator.createSessionFactory()
        session = sessionFactory.createSession(username, password, false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE)
        consumer = session.createConsumer(BRIDGE_MANAGER)
        consumer.setMessageHandler(this::bindingsAddedHandler)
        session.start()

        connectToNetworkMapService()
    }

    private fun connectToNetworkMapService() {
        config.networkMapService?.let { deployBridge(ArtemisMessagingComponent.NetworkMapAddress(it.address), setOf(it.legalName)) }
        networkMap.changed.subscribe { updateBridgesOnNetworkChange(it) }
    }

    private fun bindingsAddedHandler(artemisMessage: ClientMessage) {
        val notificationType = artemisMessage.getStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE)
        require(notificationType == CoreNotificationType.BINDING_ADDED.name)

        val clientAddress = SimpleString(artemisMessage.getStringProperty(ManagementHelper.HDR_ROUTING_NAME))
        log.debug { "Queue bindings added, deploying JMS bridge to $clientAddress" }
        deployBridgesFromNewQueue(clientAddress.toString())
    }

    private fun deployBridgesFromNewQueue(queueName: String) {
        log.debug { "Queue created: $queueName, deploying bridge(s)" }
        fun deployBridgeToPeer(nodeInfo: NodeInfo) {
            log.debug("Deploying bridge for $queueName to $nodeInfo")
            val address = nodeInfo.addresses.first()
            deployBridge(queueName, address, nodeInfo.legalIdentitiesAndCerts.map { it.name }.toSet())
        }

        if (queueName.startsWith(ArtemisMessagingComponent.PEERS_PREFIX)) {
            try {
                val identity = parsePublicKeyBase58(queueName.substring(ArtemisMessagingComponent.PEERS_PREFIX.length))
                val nodeInfos = networkMap.getNodesByLegalIdentityKey(identity)
                if (nodeInfos.isNotEmpty()) {
                    nodeInfos.forEach { deployBridgeToPeer(it) }
                } else {
                    log.error("Queue created for a peer that we don't know from the network map: $queueName")
                }
            } catch (e: AddressFormatException) {
                log.error("Flow violation: Could not parse peer queue name as Base 58: $queueName")
            }
        }
    }

    private fun deployBridge(address: ArtemisMessagingComponent.ArtemisPeerAddress, legalNames: Set<CordaX500Name>) {
        deployBridge(address.queueName, address.hostAndPort, legalNames)
    }

    /**
     * All nodes are expected to have a public facing address called [ArtemisMessagingComponent.P2P_QUEUE] for receiving
     * messages from other nodes. When we want to send a message to a node we send it to our internal address/queue for it,
     * as defined by ArtemisAddress.queueName. A bridge is then created to forward messages from this queue to the node's
     * P2P address.
     */
    private fun deployBridge(queueName: String, target: NetworkHostAndPort, legalNames: Set<CordaX500Name>) {
        expectedLegalNames[target.toString()] = legalNames

        val sourceConnectionFactoryFactory = buildSourceConnectionFactoryFactory()
        val destinationConnectionFactoryFactory = buildTargetConnectionFactoryFactory(target)

        val sourceQueue = DestinationFactory { ActiveMQQueue(queueName) }
        val destinationTopic = DestinationFactory { JmsTopic(ArtemisMessagingComponent.P2P_QUEUE) }

        val jmsBridge = JMSBridgeImpl(
                sourceConnectionFactoryFactory,
                destinationConnectionFactoryFactory,
                sourceQueue,
                destinationTopic,
                username,
                password,
                ArtemisMessagingComponent.PEER_USER,
                ArtemisMessagingComponent.PEER_USER,
                null,
                5000,
                10,
                QualityOfServiceMode.DUPLICATES_OK,
                1,
                -1,
                null,
                null,
                true)

        jmsBridge.bridgeName = getBridgeName(queueName, target)

        jmsBridge.start()
        if (!jmsBridge.isFailed) bridges.add(jmsBridge)
    }

    private fun buildTargetConnectionFactoryFactory(target: NetworkHostAndPort): ConnectionFactoryFactory {
        val targetTransportOptions = mapOf(
                "keyStoreLocation" to config.sslKeystore.toString(),
                "keyStorePassword" to config.keyStorePassword,
                "trustStoreLocation" to config.trustStoreFile.toString(),
                "trustStorePassword" to config.trustStorePassword,
                "enabledCipherSuites" to ArtemisTcpTransport.CIPHER_SUITES.joinToString(","),
                "verifyHost" to "false"
        )
        val targetQueryString = targetTransportOptions.map { (k, v) -> "transport.$k=$v" }.joinToString("&")
        val targetServerURL = "amqps://${target.host}:${target.port}?$targetQueryString"

        return ConnectionFactoryFactory {
            JmsConnectionFactory(targetServerURL)
        }
    }

    private fun buildSourceConnectionFactoryFactory(): ConnectionFactoryFactory {
        val sourceTransportConfiguration = ArtemisTcpTransport.tcpTransport(
                ConnectionDirection.Outbound(),
                serverAddress,
                config
        )
        return ConnectionFactoryFactory {
            ActiveMQConnectionFactory(false, sourceTransportConfiguration)
        }
    }

    fun stop() {
        consumer.close()
        session.close()
        bridges.forEach { it.stop() }
    }

    private fun bridgeExists(bridgeName: String): Boolean {
        return bridges.any { it.bridgeName == bridgeName }
    }

    private fun queueExists(queueName: String) = session.queueQuery(SimpleString(queueName)).isExists


    private val ArtemisMessagingComponent.ArtemisPeerAddress.bridgeName: String get() = getBridgeName(queueName, hostAndPort)

    private fun getBridgeName(queueName: String, hostAndPort: NetworkHostAndPort): String = "$queueName -> $hostAndPort"

    private fun updateBridgesOnNetworkChange(change: NetworkMapCache.MapChange) {
        log.debug { "Updating bridges on network change: $change" }

        fun gatherAddresses(node: NodeInfo): Sequence<ArtemisMessagingComponent.ArtemisPeerAddress> {
            val address = node.addresses.first()
            return node.legalIdentitiesAndCerts.map { getArtemisPeerAddress(it.party, address, config.networkMapService?.legalName) }.asSequence()
        }


        fun deployBridges(node: NodeInfo) {
            gatherAddresses(node)
                    .filter { queueExists(it.queueName) && !bridgeExists(it.bridgeName) }
                    .forEach { deployBridge(it, node.legalIdentitiesAndCerts.map { it.name }.toSet()) }
        }

        fun destroyBridges(node: NodeInfo) {
            gatherAddresses(node).forEach { addr ->
                val br = bridges.singleOrNull { it.bridgeName == addr.bridgeName }
                br?.let {
                    bridges.remove(it)
                    it.destroy()
                }
            }
        }

        when (change) {
            is NetworkMapCache.MapChange.Added -> {
                deployBridges(change.node)
            }
            is NetworkMapCache.MapChange.Removed -> {
                destroyBridges(change.node)
            }
            is NetworkMapCache.MapChange.Modified -> {
                // TODO Figure out what has actually changed and only destroy those bridges that need to be.
                destroyBridges(change.previousNode)
                deployBridges(change.node)
            }
        }
    }

    private fun getArtemisPeerAddress(party: Party, address: NetworkHostAndPort, netMapName: CordaX500Name? = null): ArtemisMessagingComponent.ArtemisPeerAddress {
        return if (party.name == netMapName) {
            ArtemisMessagingComponent.NetworkMapAddress(address)
        } else {
            ArtemisMessagingComponent.NodeAddress.asSingleNode(party.owningKey, address) // It also takes care of services nodes treated as peer nodes
        }
    }

    class VerifyingNettyTransport(remoteLocation: URI, options: TransportOptions) : NettyTcpTransport(remoteLocation, options) {
        override fun handleConnected(channel: Channel) {
            val expectedLegalNames = expectedLegalNames["$remoteHost:$remotePort"]
            try {
                val session = channel
                        .pipeline()
                        .get(SslHandler::class.java)
                        .engine()
                        .session
                // Checks the peer name is the one we are expecting.
                // TODO Some problems here: after introduction of multiple legal identities on the node and removal of the main one,
                //  we run into the issue, who are we connecting to. There are some solutions to that: advertise `network identity`;
                //  have mapping port -> identity (but, design doc says about removing SingleMessageRecipient and having just NetworkHostAndPort,
                //  it was convenient to store that this way); SNI.
                val peerLegalName = CordaX500Name.parse(session.peerPrincipal.name)
                val expectedLegalName = expectedLegalNames!!.singleOrNull { it == peerLegalName }
                require(expectedLegalName != null) {
                    "Peer has wrong CN - expected $expectedLegalNames but got $peerLegalName. This is either a fatal " +
                            "misconfiguration by the remote peer or an SSL man-in-the-middle attack!"
                }
                // Make sure certificate has the same name.
                val peerCertificateName = CordaX500Name.build(X500Principal(session.peerCertificateChain[0].subjectDN.name))
                require(peerCertificateName == expectedLegalName) {
                    "Peer has wrong subject name in the certificate - expected $expectedLegalNames but got $peerCertificateName. This is either a fatal " +
                            "misconfiguration by the remote peer or an SSL man-in-the-middle attack!"
                }
                X509Utilities.validateCertificateChain(session.localCertificates.last() as java.security.cert.X509Certificate, *session.peerCertificates)
            } catch (e: IllegalArgumentException) {
                log.warn(e.message)
                return
            }
        }
    }

    class VerifyingNettyTransportFactory : NettySslTransportFactory() {
        override fun doCreateTransport(remoteURI: URI, transportOptions: TransportOptions): NettyTcpTransport {
            return VerifyingNettyTransport(remoteURI, transportOptions)
        }
    }
}

