package net.corda.node.azure

import com.microsoft.azure.keyvault.webkey.JsonWebKeySignatureAlgorithm
import org.apache.commons.codec.digest.DigestUtils
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * AzureContentSigner is an implementation of BC's ContentSigner that uses Azure Key Vault key for signing.
 */
class AzureContentSigner(val client: AzureKeyVaultHandler, val keyName: String) : ContentSigner {

    val sha256WithRSA = DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WithRSA")!!
    val outputStream = ByteArrayOutputStream()

    override fun getAlgorithmIdentifier(): AlgorithmIdentifier {
        return sha256WithRSA
    }

    override fun getOutputStream(): OutputStream {
        return outputStream
    }

    override fun getSignature(): ByteArray {
        val digest = DigestUtils.sha256(outputStream.toByteArray())
        return client.sign(JsonWebKeySignatureAlgorithm.RS256, digest, keyName)
    }

}
