package net.corda.core.serialization

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import java.util.concurrent.ExecutionException

class KryoCache {

    // TODO: use a TypedSerializedBytes<T>
    // TODO: annotate and whitelist classes that should be cached.
    object SerialisationCache {
        private val cache = loadingCache<Any>()

        /**
         * Returns the value associated with {@code key} in this cache, first loading that value if
         * necessary. No observable state associated with this cache is modified until loading completes.
         */
        @Throws(ExecutionException::class)
        fun <T: Any> get(key: T): SerializedBytes<T> {
            // getStats()
            @Suppress("UNCHECKED_CAST")
            return cache.get(key) as SerializedBytes<T>
        }

        // Required for measurements while experimenting on the performance
        fun getStats() {
            println("hitrate:" + cache.stats().hitRate())
            println("size:" + cache.size())
            println("hitcount:" + cache.stats().hitCount())
            println("loadcount:" + cache.stats().loadCount())
            println("misscount:" + cache.stats().missCount())
        }

        private fun <T: Any> loadingCache() = CacheBuilder.newBuilder()
                .maximumWeight(4_000_000) // 4MiB, TODO: this is a guessed value.
                .weigher { _: T, value: SerializedBytes<T> -> value.bytes.size } // it could actually be 2 x value.bytes.size
                .build(object : CacheLoader<T, SerializedBytes<T>>() {
                    @Throws(Exception::class)
                    override fun load(key: T): SerializedBytes<T> {
                        val kryo = p2PKryo().borrow()
                        try {
                            // TODO: use  a custom Object Vs false as a flag
                            kryo.context.put(false, false) // a custom flag stating that caching won't be used during serialisation to avoid loops.
                            return key.serialize(kryo)
                        } catch (e: Exception) {
                            throw Exception(e)
                        } finally {
                            kryo.context.remove(false)
                            p2PKryo().release(kryo)
                        }
                    }
                })

    }

    // Deserialization cache was never checked if it actually works as expected with generics, tried to port it from PublicKeyCache.
    // TODO: use a TypedSerializedBytes<T>
    object DeserialisationCache {
        private val cache = DeserialisationCache.loadingCache<Any>()

        /**
         * Returns the value associated with {@code key} in this cache, first loading that value if
         * necessary. No observable state associated with this cache is modified until loading completes.
         */
        @Throws(ExecutionException::class)
        fun <T: Any> get(key: SerializedBytes<T>): T {
            // getStats()
            @Suppress("UNCHECKED_CAST")
            return cache.get(key as SerializedBytes<Any>) as T
        }

        // Required for measurements while experimenting on the performance
        fun getStats() {
            println("hitrate:" + cache.stats().hitRate())
            println("size:" + cache.size())
            println("hitcount:" + cache.stats().hitCount())
            println("loadcount:" + cache.stats().loadCount())
            println("misscount:" + cache.stats().missCount())
        }

        private fun <T: Any> loadingCache() = CacheBuilder.newBuilder()
                .maximumWeight(4_000_000) // 4MiB
                .weigher { key: SerializedBytes<T>, _: T -> key.bytes.size } // it could actually be 2 x value.bytes.size
                .recordStats()
                .build(object : CacheLoader<SerializedBytes<T>, T>() {
                    @Throws(Exception::class)
                    override fun load(key: SerializedBytes<T>): T {
                        val kryo = p2PKryo().borrow()
                        try {
                            // TODO: use  a custom Object Vs false as a flag
                            kryo.context.put(false, false) // a custom flag stating that caching won't be used during deserialisation to avoid loops.
                            return key.deserialize(kryo)
                        } catch (e: Exception) {
                            throw Exception(e)
                        } finally {
                            kryo.context.remove(false)
                            p2PKryo().release(kryo)
                        }
                    }
                })
    }
}

