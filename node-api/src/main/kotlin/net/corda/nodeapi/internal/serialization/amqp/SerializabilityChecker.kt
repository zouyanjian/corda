package net.corda.nodeapi.internal.serialization.amqp

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.amqp.custom.ThrowableSerializer
import java.io.File
import java.net.URLClassLoader
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "-h" || args[0] == "--help") {
        printHelp()
        exitProcess(1)
    }

    val classpath = args[0].split(':')
    if (classpath.isEmpty()) {
        println("The first argument should be a class path separated by : characters.")
        exitProcess(1)
    }

    val urls = classpath.map {
        val file = File(it)
        if (!file.exists()) {
            println("Could not locate: $it")
            exitProcess(1)
        }
        file.toURI().toURL()
    }
    val classloader = URLClassLoader(urls.toTypedArray(), null)

    // The exact values we use here doesn't really matter, because the only part we're going to use is the
    // fingerprinting path. That's the part that checks the class has the right shape.
    val factory = SerializerFactory(AllWhitelist, Thread.currentThread().contextClassLoader)
    AbstractAMQPSerializationScheme.registerJDKTypeSerializers(factory)
    val fingerPrinter = factory.fingerPrinter

    val scanner = FastClasspathScanner("net.corda")
    val scanResult = scanner.overrideClassLoaders(classloader).scan()
    // This picks up sub-classes of annotated classes automatically.
    val toScan = scanResult.getNamesOfClassesWithAnnotation(CordaSerializable::class.java)

    for (className in toScan) {
        try {
            val clazz = classloader.loadClass(className)
            if (Throwable::class.java.isAssignableFrom(clazz)) {
                ThrowableSerializer.propertiesForDeserialization(clazz as Class<Throwable>, factory)
            } else {
                fingerPrinter.fingerprint(clazz)
            }
        } catch (e: Exception) {
            println("Failed: $className: ${e.message}")
        }
    }

    val exception = TransactionVerificationException.MissingAttachmentRejection(SecureHash.allOnesHash, "Some contract class")
    val bytes: SerializedBytes<TransactionVerificationException.MissingAttachmentRejection> = SerializationOutput(factory).serialize(exception)
    val exception2 = DeserializationInput(factory).deserialize(bytes, Throwable::class.java)
    println(exception2)
}

fun printHelp() {
    println("""
        Serializability checker tool

        Specify a classpath as the first argument, separated by colons. The tool will scan it for serializable
        types and check all of them to see if they follow the serializability rules. Any classes that fail will
        result in an error message and a non-zero exit code.
    """.trimIndent()
    )
}
