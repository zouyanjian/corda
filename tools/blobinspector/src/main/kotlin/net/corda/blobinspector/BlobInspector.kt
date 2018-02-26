package net.corda.blobinspector

import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.nio.ByteBuffer

/**
 * Print a string to the console only if the verbose config option is set.
 */
fun String.debug(config: Config) {
    if (config.verbose) {
        println (this)
    }
}

/**
 *
 */
open class Property (
        val name: String,
        val type: String)

class PrimProperty (
        name: String,
        type: String,
        val value: String) : Property(name, type)
{
    override fun toString(): String = "$name : $type : $value"
}

class BinaryProperty (
        name: String,
        type: String,
        val value: ByteArray) : Property(name, type)
{
    override fun toString(): String = "$name : $type : <<<BINARY BLOB>>>"
}

class PrimListProperty (
        name: String,
        type: String,
        private val values: MutableList<String> = mutableListOf()) : Property (name, type)
{
    override fun toString(): String {
        return "$name : $type : [ ${values.joinToString(", ")} ]"
    }
}

class InstanceProperty (
        name: String,
        type: String,
        val value: Instance) : Property(name, type)
{
    override fun toString(): String = "$name : $value"
}

class Instance (
        val name: String,
        val type: String,
        val fields: MutableList<Property> = mutableListOf(),
        var offset: Int = 0)
{
    override fun toString(): String {
        val pad = "".padStart(offset, ' ')
        return StringBuilder("")
                .appendln("$name {")
                .apply {
                    fields.forEach {
                        appendln("$pad    $it")
                    }
                }.append("$pad}")
                .toString()
    }
}

fun inspectComposite(
        config: Config,
        typeMap : Map<Symbol?, TypeNotation>,
        obj: DescribedType) : Instance
{
    if (obj.described !is List<*>) throw MalformedBlob("")

    "composite: ${(typeMap[obj.descriptor] as CompositeType).name}".debug(config)


    val inst = Instance(
            typeMap[obj.descriptor]?.name ?: "",
            typeMap[obj.descriptor]?.label ?: "")

    (typeMap[obj.descriptor] as CompositeType).fields.zip(obj.described as List<*>).forEach {
        "  field: ${it.first.name}".debug(config)
        inst.fields.add (
        if (it.second is DescribedType) {
            "    - is described".debug(config)
            val d = inspectDescribed(config, typeMap, it.second as DescribedType)

            when (d) {
                is Instance ->
                    InstanceProperty(
                            it.first.name,
                            it.first.type,
                            d.apply {
                                offset = inst.offset + 4
                            })
                is List<*> -> {
                    "      - List".debug(config)
                    PrimListProperty (
                            it.first.name,
                            it.first.type,
                            d as MutableList<String>)
                }
                else -> {
                    "    skip it".debug(config)
                    return@forEach
                }
            }

        } else {
            "    - is prim".debug(config)
            when (it.first.type) {
                "binary" -> BinaryProperty(it.first.name, it.first.type, (it.second as Binary).array)
                else -> PrimProperty(it.first.name, it.first.type, it.second.toString())
            }
        })
    }

    return inst
}

fun inspectRestricted(
        config: Config,
        typeMap : Map<Symbol?, TypeNotation>,
        obj: DescribedType) : Any
{
    return when ((typeMap[obj.descriptor] as RestrictedType).source) {
        "list" -> inspectRestrictedList(config, typeMap, obj)
        "map"  -> inspectRestrictedMap(config, typeMap, obj)
        else -> throw NotImplementedError()
    }
}

fun inspectRestrictedList(
        config: Config,
        typeMap : Map<Symbol?, TypeNotation>,
        obj: DescribedType) : List<Any>
{
    if (obj.described !is List<*>) throw MalformedBlob("")

    typeMap[obj.descriptor]?.name?.debug(config)


    return mutableListOf<String>().apply {
        (obj.described as List<*>).forEach {
            try {
                "List of described".debug(config)
                inspectDescribed(config, typeMap, it as DescribedType)
            } catch (e: ClassCastException) {
                add(it.toString())
            }
        }
    }
}

fun inspectRestrictedMap(
        config: Config,
        typeMap : Map<Symbol?, TypeNotation>,
        obj: DescribedType) : Instance
{
    throw NotImplementedError()
}


fun inspectDescribed(
        config: Config,
        typeMap : Map<Symbol?, TypeNotation>,
        obj: DescribedType) : Any
{
    "${obj.descriptor} in typeMap? = ${obj.descriptor in typeMap}".debug(config)

    return when (typeMap[obj.descriptor]) {
        is CompositeType -> {
            "* It's composite".debug(config)
            inspectComposite(config, typeMap, obj)
        }
        is RestrictedType -> {
            "* It's restricted".debug(config)
            inspectRestricted(config, typeMap, obj)
        }
        else -> {
            "${typeMap[obj.descriptor]?.name} is neither Composite or Restricted".debug(config)
        }
    }

}

fun inspectBlob(config: Config, blob: ByteArray) {
    // This sucks, but that's because the main code doesn't deal well with the multiple versions so for now
    // we're going to just bodge around that

    val bytes = ByteSequence.of (blob)

    val headerSize = AmqpHeaderV1_0.size
    val headers = listOf (ByteSequence.of(AmqpHeaderV1_0.bytes))

    val blobHeader = bytes.take(headerSize)

    if (blobHeader !in headers) {
        throw MalformedBlob ("Blob is not a Corda AMQP serialised object graph")
    }


    val data = Data.Factory.create()
    val size = data.decode(ByteBuffer.wrap(bytes.bytes, bytes.offset + headerSize, bytes.size - headerSize))

    if (size.toInt() != blob.size - headerSize) {
        throw MalformedBlob ("Blob size does not match internal size")
    }

    val e = Envelope.get(data)

    if (config.schema) {
        println(e.schema)
    }

    if (config.transforms) {
        println(e.transformsSchema)
    }

    val typeMap = e.schema.types.associateBy( {it.descriptor.name }, { it })

    if (config.data) {
        val inspected = inspectDescribed(config, typeMap, e.obj as DescribedType)
        println ("\n$inspected")

        (inspected as Instance).fields.find { it.type == "net.corda.core.serialization.SerializedBytes<?>" }?.let {
            "Found field of SerializedBytes".debug(config)
            (it as InstanceProperty).value.fields.find { it.name == "bytes" }?.let { raw ->
                inspectBlob(config, (raw as BinaryProperty).value)
            }
        }
    }
}

