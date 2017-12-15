package net.corda.core.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.naming.OperationNotSupportedException

/**
 * Constructs a secure random number generator (RNG) implementing the default random number algorithm SHA256.
 * The DeterministicSecureRandom instance is seeded with the specified seed bytes.
 * This PRNG should be used for deterministic key derivation, thus a constructor without an input seed is not implemented.
 * @param seed the seed.
 */
class DeterministicSecureRandom(seed: ByteArray) : SecureRandom() {
    /** SHA256 PRNG implementation.  */
    private val secureRandomSpi = SHA256PRNG()
    override fun getAlgorithm(): String = "SHA256PRNG"

    init {
        this.secureRandomSpi.engineSetSeed(seed)
    }

    /**
     * Reseeds this random object. The given seed supplements, rather than
     * replaces, the existing seed. Thus, repeated calls are guaranteed
     * never to reduce randomness.
     *
     * @param seed the seed.
     * @see .getSeed
     */
    @Synchronized override fun setSeed(seed: ByteArray) {
        throw OperationNotSupportedException("DeterministicSecureRandom does not allow seed updates")
    }

    /**
     * Reseeds this random object, using the eight bytes contained
     * in the given `long seed`. The given seed supplements,
     * rather than replaces, the existing seed. Thus, repeated calls
     * are guaranteed never to reduce randomness.
     *
     * This method is defined for compatibility with
     * `java.util.Random`.
     *
     * @param seed the seed.
     * @see .getSeed
     */
    override fun setSeed(seed: Long) {
        /*
         * Ignore call from super constructor (as well as any other calls
         * unfortunate enough to be passing 0).  It's critical that we
         * ignore call from superclass constructor, as digest has not
         * yet been initialized at that point.
         */
        if (seed != 0L) {
            secureRandomSpi.engineSetSeed(seed.toByteArray())
        }
    }

    /**
     * Generates a user-specified number of random bytes.
     *
     * If a call to `setSeed` had not occurred previously,
     * the first call to this method forces this SecureRandom object
     * to seed itself.  This self-seeding will not occur if
     * `setSeed` was previously called.
     *
     * @param bytes the array to be filled in with random bytes.
     */
    override fun nextBytes(bytes: ByteArray) {
        secureRandomSpi.engineNextBytes(bytes)
    }

    /**
     * Returns the given number of seed bytes, computed using the seed
     * generation algorithm that this class uses to seed itself.  This
     * call may be used to seed other random number generators.
     *
     * @param numBytes the number of seed bytes to generate.
     * @return the seed bytes.
     */
    override fun generateSeed(numBytes: Int): ByteArray {
        return secureRandomSpi.engineGenerateSeed(numBytes)
    }

    /** Helper function to convert a long into a byte array (least significant byte first). */
    fun Long.toByteArray() = ByteBuffer.allocate(java.lang.Long.BYTES).putLong(this).array()
}