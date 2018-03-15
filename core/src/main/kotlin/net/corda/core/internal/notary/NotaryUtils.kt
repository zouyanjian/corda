package net.corda.core.internal.notary

import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.NotarisationRequest
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotarisationResponse
import net.corda.core.flows.NotaryError
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.serialize
import java.security.InvalidKeyException
import java.security.SignatureException

/**
 * Checks that there are sufficient signatures to satisfy the notary signing requirement and validates the signatures
 * against the given transaction id.
 */
fun NotarisationResponse.validateSignatures(txId: SecureHash, notary: Party) {
    val signingKeys = signatures.map { it.by }
    require(notary.owningKey.isFulfilledBy(signingKeys)) { "Insufficient signatures to fulfill the notary signing requirement for $notary" }
    signatures.forEach { it.verify(txId) }
}

/** Verifies that the correct notarisation request was signed by the counterparty. */
fun NotaryServiceFlow.validateRequestSignature(request: NotarisationRequest, signature: NotarisationRequestSignature) {
    val requestingParty = otherSideSession.counterparty
    request.verifySignature(signature, requestingParty)
}

/** Creates a signature over the notarisation request using the legal identity key. */
fun NotarisationRequest.generateSignature(serviceHub: ServiceHub): NotarisationRequestSignature {
    val serializedRequest = this.serialize().bytes
    val signature = with(serviceHub) {
        val myLegalIdentity = myInfo.legalIdentitiesAndCerts.first().owningKey
        keyManagementService.sign(serializedRequest, myLegalIdentity)
    }
    return NotarisationRequestSignature(signature, serviceHub.myInfo.platformVersion)
}


/** Verifies the signature against this notarisation request. Checks that the signature is issued by the right party. */
fun NotarisationRequest.verifySignature(requestSignature: NotarisationRequestSignature, intendedSigner: Party) {
    val signature = requestSignature.digitalSignature
    if (intendedSigner.owningKey != signature.by) {
        val errorMessage = "Expected a signature by ${intendedSigner.owningKey}, but received by ${signature.by}}"
        throw NotaryInternalException(NotaryError.RequestSignatureInvalid(IllegalArgumentException(errorMessage)))
    }
    // TODO: if requestSignature was generated over an old version of NotarisationRequest, we need to be able to
    // reserialize it in that version to get the exact same bytes. Modify the serialization logic once that's
    // available.
    val expectedSignedBytes = this.serialize().bytes
    verifyCorrectBytesSigned(signature, expectedSignedBytes)
}

private fun verifyCorrectBytesSigned(signature: DigitalSignature.WithKey, bytes: ByteArray) {
    try {
        signature.verify(bytes)
    } catch (e: Exception) {
        when (e) {
            is InvalidKeyException, is SignatureException -> {
                val error = NotaryError.RequestSignatureInvalid(e)
                throw NotaryInternalException(error)
            }
            else -> throw e
        }
    }
}