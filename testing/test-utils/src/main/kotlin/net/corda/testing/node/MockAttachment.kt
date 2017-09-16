package net.corda.testing.node

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.AbstractAttachment

/**
 * An attachment with only an ID and an empty data array
 */
class MockAttachment(override val id: SecureHash) : AbstractAttachment({ ByteArray(0) })