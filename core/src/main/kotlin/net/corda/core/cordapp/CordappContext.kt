package net.corda.core.cordapp

import net.corda.core.crypto.SecureHash

// TODO: Add per app config

/**
 * Every loaded cordapp has a context which contains any context about the cordapp that doesn't belong as part of it's
 * definition.
 *
 * @property cordapp The cordapp this context is about
 * @property attachmentId If the attachment has a contract this will be the attachment of this cordapp
 * @property classLoader the classloader used to load this cordapp's classes
 */
data class CordappContext(val cordapp: Cordapp, val attachmentId: SecureHash?, val classLoader: ClassLoader)