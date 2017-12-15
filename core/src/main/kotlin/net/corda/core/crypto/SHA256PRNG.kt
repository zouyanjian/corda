package net.corda.core.crypto

import java.io.Serializable
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandomSpi
import java.util.Random
import kotlin.experimental.xor

/**
 * SHA256PRNG based on Java's default SHA1PRNG implementation (Secure Random SPI SHA1PRNG).
 */
class SHA256PRNG : SecureRandomSpi(), Serializable {
    private var digest: MessageDigest
    private var seed: ByteArray
    private var data: ByteArray
    private var seedpos: Int = 0
    private var datapos: Int = 0
    private var seeded = true // Set to true when we seed this.

    init {
        try {
            digest = MessageDigest.getInstance("SHA-256")
        } catch (nsae: NoSuchAlgorithmException) {
            throw InternalError("no SHA-256 implementation found")
        }

        seed = ByteArray(32)
        seedpos = 0
        data = ByteArray(64)
        datapos = 32  // try to force hashing a first block
    }

    public override fun engineSetSeed(seed: ByteArray) {
        for (i in seed.indices)
            this.seed[seedpos++ % 32] = this.seed[seedpos++ % 32] xor seed[i]
        seedpos %= 32
    }

    public override fun engineNextBytes(bytes: ByteArray) {
        ensureIsSeeded()
        var loc = 0
        while (loc < bytes.size) {
            val copy = Math.min(bytes.size - loc, 32 - datapos)
            if (copy > 0) {
                System.arraycopy(data, datapos, bytes, loc, copy)
                datapos += copy
                loc += copy
            } else {
                // No data ready for copying, so refill our buffer.
                System.arraycopy(seed, 0, data, 32, 32)
                val digestdata = digest.digest(data)
                System.arraycopy(digestdata, 0, data, 0, 32)
                datapos = 0
            }
        }
    }

    public override fun engineGenerateSeed(numBytes: Int): ByteArray {
        val tmp = ByteArray(numBytes)
        engineNextBytes(tmp)
        return tmp
    }

    private fun ensureIsSeeded() {
        if (!seeded) {
            Random(0L).nextBytes(seed)
            val digestdata = digest.digest(data)
            System.arraycopy(digestdata, 0, data, 0, 32)
            seeded = true
        }
    }
}