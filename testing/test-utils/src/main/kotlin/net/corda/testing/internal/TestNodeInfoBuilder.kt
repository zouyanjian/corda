package net.corda.testing.internal

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.cert
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.DEV_CA
import net.corda.testing.DEV_TRUST_ROOT
import java.security.PrivateKey
import java.security.PublicKey

class TestNodeInfoBuilder {
    private val identities = ArrayList<PartyAndCertificate>()
    private val privateKeys = ArrayList<PrivateKey>()

    private fun addIdentity(name: CordaX500Name, publicKey: PublicKey): PartyAndCertificate {
        val nodeCaCert = X509Utilities.createCertificate(CertificateType.NODE_CA, DEV_CA.certificate, DEV_CA.keyPair, name, publicKey)
        val certPath = X509CertificateFactory().generateCertPath(nodeCaCert.cert, DEV_CA.certificate.cert, DEV_TRUST_ROOT.cert)
        val identity = PartyAndCertificate(certPath)
        identities += identity
        return identity
    }

    fun withIdentity(name: CordaX500Name): Pair<PartyAndCertificate, PrivateKey> {
        val nodeCaKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        privateKeys += nodeCaKeyPair.private
        return Pair(addIdentity(name, nodeCaKeyPair.public), nodeCaKeyPair.private)
    }

    fun withCompositeIdentity(name: CordaX500Name, vararg keys: PublicKey): PartyAndCertificate {
        val compositeKey = CompositeKey.Builder().addKeys(*keys).build()
        return addIdentity(name, compositeKey)
    }

    fun build(serial: Long = 1): NodeInfo {
        return NodeInfo(
                listOf(NetworkHostAndPort("my.${identities[0].party.name.organisation}.com", 1234)),
                ArrayList(identities),  // We must copy the identity list otherwise subsequent changes to it will reflect in previously built NodeInfos
                1,
                serial
        )
    }

    fun buildWithSigned(serial: Long = 1): Pair<NodeInfo, SignedNodeInfo> {
        val nodeInfo = build(serial)
        return Pair(nodeInfo, nodeInfo.signWith(privateKeys))
    }

    fun reset() {
        identities.clear()
        privateKeys.clear()
    }
}

fun createNodeInfoAndSigned(vararg names: CordaX500Name, serial: Long = 1): Pair<NodeInfo, SignedNodeInfo> {
    val nodeInfoBuilder = TestNodeInfoBuilder()
    names.forEach { nodeInfoBuilder.withIdentity(it) }
    return nodeInfoBuilder.buildWithSigned(serial)
}

fun NodeInfo.signWith(keys: List<PrivateKey>): SignedNodeInfo {
    val serialized = serialize()
    val signatures = keys.map { it.sign(serialized.bytes) }
    return SignedNodeInfo(serialized, signatures)
}
