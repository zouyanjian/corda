package net.corda.nodeapi.internal

import net.corda.core.crypto.Crypto
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.ALICE_NAME
import net.corda.testing.BOB_NAME
import net.corda.testing.DUMMY_NOTARY_NAME
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.signWith
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import java.security.SignatureException

class SignedNodeInfoTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val nodeInfoBuilder = TestNodeInfoBuilder()

    @Test
    fun `verifying single identity`() {
        nodeInfoBuilder.withIdentity(ALICE_NAME)
        val (nodeInfo, signedNodeInfo) = nodeInfoBuilder.buildWithSigned()
        assertThat(signedNodeInfo.verified()).isEqualTo(nodeInfo)
    }

    @Test
    fun `verifying multiple identities`() {
        nodeInfoBuilder.withIdentity(ALICE_NAME)
        nodeInfoBuilder.withIdentity(BOB_NAME)
        val (nodeInfo, signedNodeInfo) = nodeInfoBuilder.buildWithSigned()
        assertThat(signedNodeInfo.verified()).isEqualTo(nodeInfo)
    }

    @Test
    fun `verifying with composite identity`() {
        val (aliceIdentity) = nodeInfoBuilder.withIdentity(ALICE_NAME)
        val bobPubKey = generateKeyPair().public
        nodeInfoBuilder.withCompositeIdentity(DUMMY_NOTARY_NAME, aliceIdentity.owningKey, bobPubKey)
        val (nodeInfo, signedNodeInfo) = nodeInfoBuilder.buildWithSigned()
        assertThat(signedNodeInfo.verified()).isEqualTo(nodeInfo)
    }

    @Test
    fun `verifying missing signature`() {
        val (_, aliceKey) = nodeInfoBuilder.withIdentity(ALICE_NAME)
        nodeInfoBuilder.withIdentity(BOB_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(aliceKey))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining("Missing signatures")
    }

    @Test
    fun `verifying extra signature`() {
        val (_, aliceKey) = nodeInfoBuilder.withIdentity(ALICE_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(aliceKey, generateKeyPair().private))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining("Extra signatures")
    }

    @Test
    fun `verifying incorrect signature`() {
        nodeInfoBuilder.withIdentity(ALICE_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(generateKeyPair().private))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining(ALICE_NAME.toString())
    }

    @Test
    fun `verifying with signatures in wrong order`() {
        val (_, aliceKey) = nodeInfoBuilder.withIdentity(ALICE_NAME)
        val (_, bobKey) = nodeInfoBuilder.withIdentity(BOB_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(bobKey, aliceKey))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining(ALICE_NAME.toString())
    }

    private fun generateKeyPair() = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
}
