package net.corda.node.azure

import org.bouncycastle.cert.X509CertificateHolder
import java.security.KeyStore
import java.security.cert.*
import java.util.*

fun normalizeKey(keyName: String): String {
    var name = ""
    for (ch: Char in keyName.trim().toCharArray().toList()) {
        if (ch.isLetterOrDigit() || ch.toString() == "-") {
            name += ch
        } else {
            name += "-"
        }
    }
    return name

}

fun createAzureKeyIdentifier(baseUrl: String, keyName: String): String {
    var keyId = baseUrl
    if (keyId.endsWith("/")) {
        keyId = keyId.removeSuffix("/")
    }
    return "$keyId/keys/$keyName"
}

fun buildCertificatePath(cert: Certificate, trustStore: KeyStore): CertPath {
    val trustAnchors = HashSet<TrustAnchor>()
    val intermediateCerts = HashSet<X509Certificate>()
    for (a: String in trustStore.aliases()) {
        if (trustStore.isCertificateEntry(a)) {
            val jdkCert = trustStore.getCertificate(a)
            val c = X509CertificateHolder(jdkCert.encoded)
            if (c.issuer == c.subject) {
                trustAnchors.add(TrustAnchor(jdkCert as X509Certificate, null))
            } else {
                intermediateCerts.add(jdkCert as X509Certificate)
            }

        }
    }
    intermediateCerts.add(cert as X509Certificate)

    val selector = X509CertSelector()
    selector.certificate = cert

    val parameters = PKIXBuilderParameters(trustAnchors, selector)
    parameters.isRevocationEnabled = false
    val intermediateCertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(intermediateCerts), "BC")
    parameters.addCertStore(intermediateCertStore)

    val certPathBuilder = CertPathBuilder.getInstance("PKIX")
    var result = certPathBuilder.build(parameters)
    return result.certPath
}
