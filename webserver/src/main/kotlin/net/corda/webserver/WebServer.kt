@file:JvmName("WebServer")

package net.corda.webserver

import com.google.common.base.Stopwatch
import com.typesafe.config.ConfigException
import net.corda.core.internal.div
import net.corda.core.internal.rootCause
import net.corda.webserver.internal.NodeWebServer
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val stopwatch = Stopwatch.createStarted()
    val argsParser = ArgsParser()

    val cmdlineOptions = try {
        argsParser.parse(*args)
    } catch (ex: Exception) {
        println("Unknown command line arguments: ${ex.message}")
        exitProcess(1)
    }

    // Maybe render command line help.
    if (cmdlineOptions.help) {
        argsParser.printHelp(System.out)
        exitProcess(0)
    }

    // Set up logging.
    if (cmdlineOptions.logToConsole) {
        // This property is referenced from the XML config file.
        System.setProperty("consoleLogLevel", "info")
    }

    System.setProperty("log-path", (cmdlineOptions.baseDirectory / "logs/web").toString())
    val log = LoggerFactory.getLogger("Main")
    log.warn("This Corda-specific web server is deprecated and will be removed in future.")
    log.warn("Please switch to a regular web framework like Spring, J2EE or Play Framework.")
    log.warn("Logs can be found in ${System.getProperty("log-path")}")

    val conf = try {
        WebServerConfig(cmdlineOptions.baseDirectory, cmdlineOptions.loadConfig())
    } catch (e: ConfigException) {
        println("Unable to load the configuration file: ${e.rootCause.message}")
        exitProcess(2)
    }

    val info = ManagementFactory.getRuntimeMXBean()
    log.trace("Main class: ${WebServerConfig::class.java.protectionDomain.codeSource.location.toURI().path}")
    log.trace("CommandLine Args: ${info.inputArguments.joinToString(" ")}")
    log.trace("Application Args: ${args.joinToString(" ")}")
    log.trace("bootclasspath: ${info.bootClassPath}")
    log.trace("classpath: ${info.classPath}")
    log.trace("VM ${info.vmName} ${info.vmVendor} ${info.vmVersion}")
    log.trace("Machine: ${InetAddress.getLocalHost().hostName}")
    log.trace("Working Directory: ${cmdlineOptions.baseDirectory}")
    log.info("Starting as webserver on ${conf.webAddress}")

    try {
        val server = NodeWebServer(conf)
        server.start()
        log.info("Webserver started up in ${stopwatch.elapsed(TimeUnit.SECONDS)} sec")
        server.run()
    } catch (e: Exception) {
        log.error("Exception during node startup", e)
        exitProcess(1)
    }
}