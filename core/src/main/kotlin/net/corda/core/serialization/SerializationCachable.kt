package net.corda.core.serialization

import java.lang.annotation.Inherited

/**
 * This annotation is a marker to indicate that caching is utilized for the serialized form of the members of this class.
 *
 * TODO: This annotation will only be applied on classes that serialization of the same objects is highly possible to be re-used, such as [PublicKey].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
annotation class SerializationCachable
