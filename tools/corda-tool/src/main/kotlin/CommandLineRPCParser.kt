package net.corda.rpc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.core.messaging.RPCOps
import net.corda.node.utilities.JsonSupport
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.concurrent.Callable
import javax.annotation.concurrent.ThreadSafe
import kotlin.reflect.jvm.kotlinFunction

/**
 * Creates objects that hold an RPC waiting to be executed, constructed from a string in YAML format.
 *
 * Example strings that can be accepted:
 *
 * nodeIdentity
 *
 * attachmentExists id: b6d7e826e8739ab2eb6e077fc4fba9b04fb880bb4cbd09bc618d30234a8827a4
 *
 * addVaultTransactionNote id: b6d7e826e8739ab2eb6e077fc4fba9b04fb880bb4cbd09bc618d30234a8827a4, note: "Some note"
 */
@ThreadSafe
class CommandLineRPCParser<in T : RPCOps>(targetType: Class<out T>) {
    companion object {
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private val ignoredNames = java.lang.Object::class.java.methods.map { it.name }
        fun methodsFromType(clazz: Class<*>) = clazz.methods.map { it.name to it }.toMap().filterKeys { it !in ignoredNames }
        val log = LoggerFactory.getLogger(CommandLineRPCParser::class.java)!!

        fun paramNamesFromMethod(method: Method): List<String> {
            val kf = method.kotlinFunction
            return method.parameters.mapIndexed { index, param ->
                when {
                    param.isNamePresent -> param.name
                    // index + 1 because the first Kotlin reflection param is 'this', but that doesn't match Java reflection.
                    kf != null -> kf.parameters[index + 1].name ?: throw UnparseableRPCException.ReflectionDataMissing(method.name, index)
                    else -> throw UnparseableRPCException.ReflectionDataMissing(method.name, index)
                }
            }
        }
    }

    /** The methods that can be invoked via this parser. */
    val methodMap = methodsFromType(targetType)
    /** A map of method name to parameter names for the target type. */
    val methodParamNames: Map<String, List<String>> = targetType.declaredMethods.map { it.name to paramNamesFromMethod(it) }.toMap()

    // For deserialization of the input.
    private val om = JsonSupport.createDefaultMapper(null, YAMLFactory())

    inner class ParsedRPC(private val target: T?, val methodName: String, val args: Array<Any?>) : Callable<Any?> {
        operator fun invoke(): Any? = call()
        override fun call(): Any? {
            if (target == null)
                throw IllegalStateException("No target object was specified")
            if (log.isDebugEnabled)
                log.debug("Invoking RPC $methodName($args)")
            return methodMap[methodName]!!.invoke(target, *args)
        }
    }

    open class UnparseableRPCException(command: String) : Exception("Could not parse as a command: $command") {
        class UnknownMethod(val methodName: String) : UnparseableRPCException("Unknown command name: $methodName")
        class MissingParameter(methodName: String, paramName: String, command: String) : UnparseableRPCException("Parameter $paramName missing from attempt to invoke $methodName in command: $command")
        class ReflectionDataMissing(methodName: String, argIndex: Int) : UnparseableRPCException("Method $methodName missing parameter name at index $argIndex")
    }

    /**
     * Parses the given command as a call on the target type. The target should be specified, if it's null then
     * the resulting [ParsedRPC] can't be invoked, just inspected.
     */
    @Throws(UnparseableRPCException::class)
    fun parse(command: String, target: T?): ParsedRPC {
        log.debug("Parsing RPC command from string: {}", command)
        val spaceIndex = command.indexOf(' ')
        val name = if (spaceIndex != -1) command.substring(0, spaceIndex) else command
        // If we have parameters, wrap them in {} to allow the Yaml parser to eat them on a single line.
        val parameterString = if (spaceIndex != -1) "{ " + command.substring(spaceIndex) + " }" else null
        val method = methodMap[name] ?: throw UnparseableRPCException.UnknownMethod(name)
        log.debug("Parsing RPC for method {}", name)

        if (parameterString == null) {
            if (method.parameterCount == 0)
                return ParsedRPC(target, name, emptyArray())
            else
                throw UnparseableRPCException.MissingParameter(name, methodParamNames[name]!![0], command)
        } else {
            val tree: JsonNode = om.readTree(parameterString) ?: throw UnparseableRPCException(command)
            val inOrderParams: List<Any?> = methodParamNames[name]!!.mapIndexed { index, argName ->
                val entry = tree[argName] ?: throw UnparseableRPCException.MissingParameter(name, argName, command)
                om.readValue(entry.traverse(), method.parameters[index].type)
            }
            if (log.isDebugEnabled) {
                inOrderParams.forEachIndexed { i, param ->
                    val tmp = if (param != null) "${param.javaClass.name} -> $param" else "(null)"
                    log.debug("Parameter $i $tmp")
                }
            }
            return ParsedRPC(target, name, inOrderParams.toTypedArray())
        }
    }
}