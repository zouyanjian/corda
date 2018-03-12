package net.corda.node.services.network

import net.corda.core.crypto.Crypto
import net.corda.core.internal.sign
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.createNodeInfoAndSigned
import net.corda.testing.internal.signWith
import net.corda.testing.node.internal.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

class NetworkMapClientTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheTimeout = 100000.seconds

    private lateinit var server: NetworkMapServer
    private lateinit var networkMapClient: NetworkMapClient

    @Before
    fun setUp() {
        server = NetworkMapServer(cacheTimeout, NetworkHostAndPort("localhost", 10000))
        val hostAndPort = server.start()
        networkMapClient = NetworkMapClient(URL("http://$hostAndPort"), DEV_ROOT_CA.certificate)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `registered node is added to the network map`() {
        val (aliceNodeInfo, aliceSignedNodeInfo) = createNodeInfoAndSigned(ALICE_NAME)
        val aliceNodeInfoHash = aliceNodeInfo.serialize().hash

        networkMapClient.publish(aliceSignedNodeInfo)

        assertThat(networkMapClient.getNetworkMap().payload.nodeInfoHashes).containsExactly(aliceNodeInfoHash)
        assertEquals(aliceNodeInfo, networkMapClient.getNodeInfo(aliceNodeInfoHash))

        val (bobNodeInfo, bobSignedNodeInfo) = createNodeInfoAndSigned(BOB_NAME)
        val bobNodeInfoHash = bobNodeInfo.serialize().hash

        networkMapClient.publish(bobSignedNodeInfo)

        assertThat(networkMapClient.getNetworkMap().payload.nodeInfoHashes).containsOnly(aliceNodeInfoHash, bobNodeInfoHash)
        assertEquals(cacheTimeout, networkMapClient.getNetworkMap().cacheMaxAge)
        assertEquals(bobNodeInfo, networkMapClient.getNodeInfo(bobNodeInfoHash))
    }

    @Test
    fun `errors return a meaningful error message`() {
        val nodeInfoBuilder = TestNodeInfoBuilder()
        val (_, aliceKey) = nodeInfoBuilder.addIdentity(ALICE_NAME)
        nodeInfoBuilder.addIdentity(BOB_NAME)
        val nodeInfo3 = nodeInfoBuilder.build()
        val signedNodeInfo3 = nodeInfo3.signWith(listOf(aliceKey))

        assertThatThrownBy { networkMapClient.publish(signedNodeInfo3) }
                .isInstanceOf(IOException::class.java)
                .hasMessage("Response Code 403: Missing signatures. Found 1 expected 2")
    }

    @Test
    fun `download NetworkParameter correctly`() {
        // The test server returns same network parameter for any hash.
        val parametersHash = server.networkParameters.serialize().hash
        val networkParameter = networkMapClient.getNetworkParameters(parametersHash).verified()
        assertEquals(server.networkParameters, networkParameter)
    }

    @Test
    fun `get hostname string from http response correctly`() {
        assertEquals("test.host.name", networkMapClient.myPublicHostname())
    }

    @Test
    fun `handle parameters update`() {
        val nextParameters = testNetworkParameters(epoch = 2)
        val originalNetworkParameterHash = server.networkParameters.serialize().hash
        val nextNetworkParameterHash = nextParameters.serialize().hash
        val description = "Test parameters"
        server.scheduleParametersUpdate(nextParameters, description, Instant.now().plus(1, ChronoUnit.DAYS))
        val (networkMap) = networkMapClient.getNetworkMap()
        assertEquals(networkMap.networkParameterHash, originalNetworkParameterHash)
        assertEquals(networkMap.parametersUpdate?.description, description)
        assertEquals(networkMap.parametersUpdate?.newParametersHash, nextNetworkParameterHash)
        assertEquals(networkMapClient.getNetworkParameters(originalNetworkParameterHash).verified(), server.networkParameters)
        assertEquals(networkMapClient.getNetworkParameters(nextNetworkParameterHash).verified(), nextParameters)
        val keyPair = Crypto.generateKeyPair()
        val signedHash = nextNetworkParameterHash.serialize().sign(keyPair)
        networkMapClient.ackNetworkParametersUpdate(signedHash)
        assertEquals(nextNetworkParameterHash, server.latestParametersAccepted(keyPair.public))
    }
}
