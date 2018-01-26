package net.corda.node.azure

import com.microsoft.azure.keyvault.KeyVaultClient
import com.microsoft.azure.keyvault.models.KeyBundle
import com.microsoft.azure.keyvault.requests.CreateKeyRequest
import com.microsoft.azure.keyvault.webkey.JsonWebKeyOperation
import com.microsoft.azure.keyvault.webkey.JsonWebKeySignatureAlgorithm
import com.microsoft.azure.keyvault.webkey.JsonWebKeyType
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey
import java.util.*

const val TWO_YEARS = 1000L * 60L * 60L * 24L * 365L * 2L

/**
 * AzureKeyVaultHandler handles all Azure Key Vault related requests.
 */
class AzureKeyVaultHandler(val azureClient: KeyVaultClient, val vaultBaseUrl: String, val rootKeyName: String, val trustStore: KeyStore) {

    companion object {
        private val log = LoggerFactory.getLogger(AzureKeyVaultHandler::class.java)
    }


    private var nodeRootCert: X509CertificateHolder
    private var rootKeyPair: KeyPair

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        val keyIdentifier = createAzureKeyIdentifier(vaultBaseUrl, rootKeyName)
        val keyBundle = azureClient.getKey(keyIdentifier) ?: throw AzureKeyNotFoundException("Azure key '$keyIdentifier' not found. Root node key must be present in Azure Key Vault.")
        if (!isRSAKey(keyBundle)) {
            throw InvalidAzureKeyException("Invalid Azure key '$keyIdentifier' type")
        }
        if (!isSigningKey(keyBundle)) {
            throw InvalidAzureKeyException("Azure key '$keyIdentifier' can not be used for signing. Missing signing operation")
        }
        rootKeyPair = keyBundle.key().toRSA()
        var certificate = trustStore.getCertificate(rootKeyName)
        nodeRootCert = X509CertificateHolder(certificate.encoded)
    }

    /**
     * getKeyPair loads a key from Azure Key Vault. Only the public part of the key is returned.
     */
    fun getKeyPair(key: String): KeyPair {
        val keyPair = azureClient.getKey(vaultBaseUrl, key).key().toRSA()
        val rsaPublicKey = (keyPair.public as RSAPublicKey)
        val factory = KeyFactory.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        var byKey = factory.translateKey(rsaPublicKey) as PublicKey
        return KeyPair(byKey, AzurePrivateKey(key))
    }

    /**
     * createKeyPair method creates a new RSA key pair
     */
    fun createKeyPair(name: String): KeyPair {
        val normalizedKeyName = normalizeKey(name)
        var builder = CreateKeyRequest.Builder(vaultBaseUrl, normalizedKeyName, JsonWebKeyType.RSA)
        builder.withKeyOperations(mutableListOf(JsonWebKeyOperation.SIGN, JsonWebKeyOperation.VERIFY))
        var response = azureClient.createKey(builder.build())
        val keyPair = response.key().toRSA()!!
        val factory = KeyFactory.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        var byKey = factory.translateKey(keyPair.public) as PublicKey
        return KeyPair(byKey, AzurePrivateKey(normalizedKeyName))
    }

    /**
     * createKeyPairAndCertificate method creates a new RSA key pair and a new X509 certificate. New X509 certificate is singed by the node intermediate CA.
     */
    fun createKeyPairAndCertificate(name: String, subjectDn :X500Name, keyUsage: KeyUsage = KeyUsage(KeyUsage.digitalSignature)): Triple<KeyPair, X509CertificateHolder, CertPath> {
        var normalizedName = normalizeKey(name)
        var keyPair = createKeyPair(normalizedName)

        var notAfter = Date(System.currentTimeMillis() + TWO_YEARS) // ~2 years

        val pk = keyPair.public.encoded
        val bcPk = SubjectPublicKeyInfo.getInstance(pk)

        val certBuilder = X509v3CertificateBuilder(nodeRootCert.subject, BigInteger.ONE, Date(), notAfter, subjectDn, bcPk)
        certBuilder.addExtension(Extension.keyUsage, false, keyUsage)

        val purposes = ASN1EncodableVector()
        purposes.add(KeyPurposeId.id_kp_clientAuth)
        purposes.add(KeyPurposeId.anyExtendedKeyUsage)
        certBuilder.addExtension(Extension.extendedKeyUsage, false, DERSequence(purposes))

        var certificate = certBuilder.build(AzureContentSigner(this, rootKeyName))

        val factory = CertificateFactory.getInstance("X509")
        val jdkCert = factory.generateCertificate(ByteArrayInputStream(certificate.encoded))

        //sanity check
        jdkCert.verify(rootKeyPair.public)

        var path = buildCertificatePath(jdkCert, trustStore)

        return Triple(keyPair, certificate, path)
    }

    /**
     * Sign method is used for signing
     */
    fun sign(algorithm: JsonWebKeySignatureAlgorithm, digest: ByteArray, keyName: String): ByteArray {
        log.info("Key name is $keyName, URL ${createAzureKeyIdentifier(vaultBaseUrl, keyName)}")

        val response = azureClient.sign(createAzureKeyIdentifier(vaultBaseUrl, keyName), algorithm, digest)
        return response.result()
    }

    private fun isSigningKey(keyBundle: KeyBundle) = keyBundle.key().keyOps().contains(JsonWebKeyOperation.SIGN)

    private fun isRSAKey(keyBundle: KeyBundle): Boolean = keyBundle.key().kty() == JsonWebKeyType.RSA || keyBundle.key().kty() == JsonWebKeyType.RSA_HSM

}

class AzurePrivateKey(val normalizedKeyName: String) : PrivateKey {

    override fun getAlgorithm(): String {
        throw IllegalStateException("Azure private key shouldn't be used for direct signing")
    }

    override fun getEncoded(): ByteArray {
        throw IllegalStateException("Azure private key shouldn't be used for direct signing")
    }

    override fun getFormat(): String {
        throw IllegalStateException("Azure private key shouldn't be used for direct signing")
    }

}
