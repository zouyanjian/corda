package net.corda.node.azure

import com.microsoft.azure.keyvault.webkey.JsonWebKeySignatureAlgorithm
import net.corda.core.crypto.DigitalSignature
import net.corda.core.identity.AnonymousPartyAndPath
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.ThreadBox
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.security.KeyPair
import java.security.PublicKey
import java.util.*

class AzureKeyManagementService(val handler: AzureKeyVaultHandler, val identityService: IdentityService,
                                initialKeys: Set<KeyPair>) : SingletonSerializeAsToken(), KeyManagementService {

    companion object {
        private val log = loggerFor<AzureKeyManagementService>()
    }

    private class InnerState {
        val keys = HashMap<PublicKey, String>()
    }

    private val mutex = ThreadBox(InnerState())

    init {
        mutex.locked {
            for (key in initialKeys) {
                if (key.private is AzurePrivateKey) {
                    log.info("Registering Azure key ${(key.private as AzurePrivateKey).normalizedKeyName}. Public key: ${Base64.getEncoder().encodeToString(key.public.encoded)}")
                    keys[key.public] = (key.private as AzurePrivateKey).normalizedKeyName
                } else {
                    throw IllegalStateException("Unknown private key. Expected Azure private key.")
                }
            }
        }
    }

    // Accessing this map clones it.
    override val keys: Set<PublicKey> get() = mutex.locked { keys.keys }

    override fun freshKey(): PublicKey {
        val keyPair = handler.createKeyPair(UUID.randomUUID().toString())
        mutex.locked {
            keys[keyPair.public] = UUID.randomUUID().toString()
        }
        return keyPair.public
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): AnonymousPartyAndPath {
        throw IllegalStateException("issuing new anonymous certificates aren't supported")
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> {
        return mutex.locked { candidateKeys.filter { it in this.keys } }
    }

    override fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
        log.info("Signing with public key: ${Base64.getEncoder().encodeToString(publicKey.encoded)}")
        val keyName = getSigningKeyName(publicKey) ?: throw IllegalStateException("key not found for public key $publicKey")
        log.info("Azure key name $keyName, bytes are: ${Hex.encodeHexString(bytes)}")

        val signature = handler.sign(JsonWebKeySignatureAlgorithm.RS256, DigestUtils.sha256(bytes), keyName)
        return DigitalSignature.WithKey(publicKey, signature)
    }

    private fun getSigningKeyName(publicKey: PublicKey): String? {
        return mutex.locked {
            return keys[publicKey]
        }
    }
}
