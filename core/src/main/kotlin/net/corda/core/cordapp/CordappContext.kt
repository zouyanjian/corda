package net.corda.core.cordapp

import net.corda.core.crypto.SecureHash

// TODO: Add per app config

/**
 * Every loaded cordapp has a context which contains any context about the cordapp that doesn't belong as part of it's
 * definition.
 *
 * A CordappContext is obtained from [CordappService.getAppContext] which resides on a [ServiceHub]. This will be
 * used primarily from within flows.
 *
 * @property cordapp The cordapp this context is about
 * @property attachmentId For CorDapps containing [Contract] or [UpgradedContract] implementations this will be populated
 * with the attachment containing those class files
 * @property classLoader the classloader used to load this cordapp's classes
 */
data class CordappContext(val cordapp: Cordapp, val attachmentId: SecureHash?, val classLoader: ClassLoader)