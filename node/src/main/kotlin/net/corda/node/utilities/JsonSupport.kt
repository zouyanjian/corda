package net.corda.node.utilities

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers
import com.fasterxml.jackson.databind.deser.std.StringArrayDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.core.contracts.BusinessCalendar
import net.corda.core.crypto.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.i2p.crypto.eddsa.EdDSAPublicKey
import java.math.BigDecimal

// TODO: JSON/YAML/XML/misc-non-native-format serialization is generally useful and should be refactored out of node into a separate module.

/**
 * Utilities and serialisers for working with JSON representations of basic types. This adds Jackson support for
 * the java.time API, some core types, and Kotlin data classes.
 */
object JsonSupport {
    fun createDefaultMapper(identities: IdentityService?, factory: JsonFactory = JsonFactory()): ObjectMapper {
        val mapper = ServiceHubObjectMapper(identities, factory)
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)


        val cordaModule = SimpleModule("core")
        cordaModule.addSerializer(Party::class.java, PartySerializer)
        if (identities != null)
            cordaModule.addDeserializer(Party::class.java, PartyDeserializer)
        cordaModule.addSerializer(BigDecimal::class.java, ToStringSerializer)
        cordaModule.addDeserializer(BigDecimal::class.java, NumberDeserializers.BigDecimalDeserializer())
        cordaModule.addSerializer(SecureHash::class.java, SecureHashSerializer)
        cordaModule.addDeserializer(SecureHash::class.java, SecureHashDeserializer())
        cordaModule.addDeserializer(BusinessCalendar::class.java, CalendarDeserializer)

        // For ed25519 pubkeys
        cordaModule.addSerializer(EdDSAPublicKey::class.java, PublicKeySerializer)
        cordaModule.addDeserializer(EdDSAPublicKey::class.java, PublicKeyDeserializer)

        // For composite keys
        cordaModule.addSerializer(CompositeKey::class.java, CompositeKeySerializer)
        cordaModule.addDeserializer(CompositeKey::class.java, CompositeKeyDeserializer)

        // For NodeInfo
        // TODO this tunnels the Kryo representation as a Base58 encoded string. Replace when RPC supports this.
        cordaModule.addSerializer(NodeInfo::class.java, NodeInfoSerializer)
        cordaModule.addDeserializer(NodeInfo::class.java, NodeInfoDeserializer)

        mapper.registerModule(cordaModule)
        mapper.registerModule(JavaTimeModule())
        mapper.registerModule(KotlinModule())
        return mapper
    }

    class ServiceHubObjectMapper(val identities: IdentityService?, factory: JsonFactory) : ObjectMapper(factory)

    object ToStringSerializer : JsonSerializer<Any>() {
        override fun serialize(obj: Any, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }


    object PartySerializer : JsonSerializer<Party>() {
        override fun serialize(obj: Party, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.name)
        }
    }

    object PartyDeserializer : JsonDeserializer<Party>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): Party {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }
            val mapper = parser.codec as ServiceHubObjectMapper
            // TODO this needs to use some industry identifier(s) not just these human readable names
            return mapper.identities!!.partyFromName(parser.text) ?: throw JsonParseException(parser, "Could not find a Party with name: ${parser.text}")
        }
    }

    object NodeInfoSerializer : JsonSerializer<NodeInfo>() {
        override fun serialize(value: NodeInfo, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(Base58.encode(value.serialize().bytes))
        }
    }

    object NodeInfoDeserializer : JsonDeserializer<NodeInfo>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): NodeInfo {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }
            try {
                return Base58.decode(parser.text).deserialize<NodeInfo>()
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid NodeInfo ${parser.text}: ${e.message}")
            }
        }
    }

    object SecureHashSerializer : JsonSerializer<SecureHash>() {
        override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    /**
     * Implemented as a class so that we can instantiate for T.
     */
    class SecureHashDeserializer<T : SecureHash> : JsonDeserializer<T>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): T {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }
            try {
                @Suppress("UNCHECKED_CAST")
                return SecureHash.parse(parser.text) as T
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid hash ${parser.text}: ${e.message}")
            }
        }
    }

    object CalendarDeserializer : JsonDeserializer<BusinessCalendar>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): BusinessCalendar {
            return try {
                val array = StringArrayDeserializer.instance.deserialize(parser, context)
                BusinessCalendar.getInstance(*array)
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid calendar(s) ${parser.text}: ${e.message}")
            }
        }
    }

    object PublicKeySerializer : JsonSerializer<EdDSAPublicKey>() {
        override fun serialize(obj: EdDSAPublicKey, generator: JsonGenerator, provider: SerializerProvider) {
            check(obj.params == ed25519Curve)
            generator.writeString(obj.toBase58String())
        }
    }

    object PublicKeyDeserializer : JsonDeserializer<EdDSAPublicKey>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): EdDSAPublicKey {
            return try {
                parsePublicKeyBase58(parser.text)
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid public key ${parser.text}: ${e.message}")
            }
        }
    }

    object CompositeKeySerializer : JsonSerializer<CompositeKey>() {
        override fun serialize(obj: CompositeKey, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toBase58String())
        }
    }

    object CompositeKeyDeserializer : JsonDeserializer<CompositeKey>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): CompositeKey {
            return try {
                CompositeKey.parseFromBase58(parser.text)
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid composite key ${parser.text}: ${e.message}")
            }
        }
    }
}
