package net.corda.node.azure

/**
 * AzureKeyNotFoundException is thrown when Azure Key isn't found
 */
class AzureKeyNotFoundException(message: String): Exception(message)

/**
 * InvalidAzureKeyException is thrown when Azure key can not be used for signing
 */
class InvalidAzureKeyException(message: String): Exception(message)



