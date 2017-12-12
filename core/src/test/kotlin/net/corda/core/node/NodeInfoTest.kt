package net.corda.core.node

import net.corda.core.crypto.Crypto
import net.corda.testing.ALICE_NAME
import net.corda.testing.DUMMY_NOTARY_NAME
import net.corda.testing.internal.TestNodeInfoBuilder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class NodeInfoTest {
    private val nodeInfoBuilder = TestNodeInfoBuilder()

    @Test
    fun `with composite identity`() {
        val (aliceIdentity) = nodeInfoBuilder.withIdentity(ALICE_NAME)
        val bobPubKey = generateKeyPair().public
        val compositeIdentity = nodeInfoBuilder.withCompositeIdentity(DUMMY_NOTARY_NAME, aliceIdentity.owningKey, bobPubKey)
        val nodeInfo = nodeInfoBuilder.build()
        assertThat(nodeInfo.legalIdentitiesAndCerts).containsExactly(aliceIdentity, compositeIdentity)
    }

    @Test
    fun `composite identity which does not include one of the other identities`() {
        nodeInfoBuilder.withIdentity(ALICE_NAME)
        nodeInfoBuilder.withCompositeIdentity(DUMMY_NOTARY_NAME, generateKeyPair().public, generateKeyPair().public)
        assertThatThrownBy { nodeInfoBuilder.build() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Composite identity does not contain one of the other identities")
    }

    private fun generateKeyPair() = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
}