package net.corda.node.azure

import com.microsoft.aad.adal4j.AuthenticationContext
import com.microsoft.aad.adal4j.ClientCredential
import com.microsoft.azure.keyvault.authentication.KeyVaultCredentials
import java.util.concurrent.Executors

/**
 * Credentials for Azure Key Vault
 */
class AzureKeyVaultCredentials(val applicationId : String, val applicationSecret : String) : KeyVaultCredentials() {

    override fun doAuthenticate(authorization: String?, resource: String?, scope: String?): String {
        val ctx = AuthenticationContext(authorization, false, Executors.newCachedThreadPool())
        val response = ctx.acquireToken(resource, ClientCredential(applicationId, applicationSecret), null)
        val result = response.get()
        return result.accessToken
    }
}
