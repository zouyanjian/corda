package net.corda.blobinspector

import net.corda.nodeapi.internal.serialization.amqp.AmqpHeaderV1_0
import net.corda.nodeapi.internal.serialization.amqp.Envelope
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.nio.ByteBuffer

fun inspectBlob(config: Config, blob: ByteArray) {
    // This sucks, but that's because the main code doesn't deal well with the multiple versions so for now
    // we're going to just bodge around that
    val headerSize = AmqpHeaderV1_0.size
    val headers = listOf (AmqpHeaderV1_0.bytes.toList())

    val blobHeader = blob.take(headerSize)

    if (blobHeader !in headers) {
        println ("nooo")
        return
    }

    println ("HEADER: ${blobHeader.joinToString { "" }}")


    val data = Data.Factory.create()
    val size = data.decode(ByteBuffer.wrap(blob, headerSize, blob.size - headerSize))
    /*
    if (size.toInt() != blob.size - /*headerSize*/2) {
        throw NotSerializableException("Unexpected size of data")
    }
    */

    val e = Envelope.get(data)
}

