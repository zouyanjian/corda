package net.corda.blobinspector

import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.amqp.AmqpHeaderV1_0
import net.corda.nodeapi.internal.serialization.amqp.Envelope
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.Data
import java.nio.ByteBuffer

fun inspectBlob(config: Config, blob: ByteArray) {
    // This sucks, but that's because the main code doesn't deal well with the multiple versions so for now
    // we're going to just bodge around that

    val bytes = ByteSequence.of (blob)

    val headerSize = AmqpHeaderV1_0.size
    println (headerSize)
    val headers = listOf (ByteSequence.of(AmqpHeaderV1_0.bytes))

    val blobHeader = bytes.take(headerSize)

    if (blobHeader !in headers) {
        println ("nooo")
        return
    }


    val data = Data.Factory.create()

    val size = data.decode(ByteBuffer.wrap(bytes.bytes, bytes.offset + headerSize, bytes.size - headerSize))

    /*
    if (size.toInt() != blob.size - /*headerSize*/2) {
        throw NotSerializableException("Unexpected size of data")
    }
    */

    val e = Envelope.get(data)

    println(e.schema)
    println (e.transformsSchema)

    println ((e.obj as DescribedType).described)
    println ((e.obj as DescribedType).descriptor)
}

