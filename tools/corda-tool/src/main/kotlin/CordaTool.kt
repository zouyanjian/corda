@file:JvmName("CordaTool")
package net.corda.rpc

import com.google.common.net.HostAndPort
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import joptsimple.OptionSet
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.Emoji
import net.corda.node.drawCordaLogoBanner
import net.corda.node.services.config.NodeSSLConfiguration
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.node.services.messaging.RPCException
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.fusesource.jansi.Ansi
import org.jline.reader.*
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import printAndFollowRPCResponse
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

enum class Format {
    yaml,
    json,
    tostring
}

/**
 * This program lets you send RPCs to a server given simple textual descriptions on the command line.
 *
 * TODO: Support reading multiple commands from standard input, for more complex scripting uses.
 */
fun main(args: Array<String>) {
    val parser = OptionParser()
    val rpcParser = CommandLineRPCParser(CordaRPCOps::class.java)

    val helpArg = parser.accepts("help").forHelp()
    val nodeArg = parser.accepts("node", "The hostname:port of the server to connect to. Taken from \$CORDA_NODE if not specified.").withRequiredArg()
    val certsDirArg = parser.accepts("certs-dir", "The directory where the server certificates can be found. Taken from \$CORDA_CERTS_DIR if not specified.").withRequiredArg()
    val userArg = parser.accepts("user", "The username to log into the node as. Taken from \$CORDA_USER if not specified.").withRequiredArg()
    val passwordArg = parser.accepts("password", "The password for the given user. Taken from \$CORDA_PASSWORD if not specified, or else prompted for at the console.").withRequiredArg()
    val timeoutArg = parser.accepts("timeout", "How long to wait for an RPC to complete, in seconds.").withRequiredArg().ofType(Double::class.java).defaultsTo(10.0)
    val outputFormatArg = parser.accepts("format", "What format to print the result in.").withRequiredArg().ofType(Format::class.java).defaultsTo(Format.yaml)
    val consoleArg = parser.accepts("console", "If specified, opens up an interactive REPL where you can dispatch commands one after the other.")

    val options = try {
        parser.parse(*args)
    } catch (ex: Exception) {
        println("Unknown command line arguments: ${ex.message}.")
        println()
        help(parser, rpcParser)
        exitProcess(1)
    }

    if ((!options.has(consoleArg) && options.nonOptionArguments().isEmpty()) || options.has(helpArg)) {
        help(parser, rpcParser)
    }

    fun fromArgOrEnv(arg: ArgumentAcceptingOptionSpec<String>, env: String, required: Boolean = true): String? {
        if (options.has(arg)) {
            return options.valueOf(arg)
        } else {
            val r = System.getenv(env)
            if (r == null && required)
                error("You must set either the --${arg.options().first()} command line flag or set the \$$env environment variable.")
            else
                return r
        }
    }

    val nodeAddressStr = fromArgOrEnv(nodeArg, "CORDA_NODE")!!
    val nodeAddress = try {
        HostAndPort.fromString(nodeAddressStr)
    } catch (e: IllegalArgumentException) {
        error("Could not understand or parse the node address: $nodeAddressStr")
    }
    val certsDirStr = fromArgOrEnv(certsDirArg, "CORDA_CERTS_DIR")!!
    val userStr = fromArgOrEnv(userArg, "CORDA_USER")!!
    val passwordStr = fromArgOrEnv(passwordArg, "CORDA_PASSWORD", false) ?: readPasswordFromTerminal()
    val timeout: Duration = Duration.ofMillis((options.valueOf(timeoutArg) * 1000).toLong())
    val outputFormat: Format = options.valueOf(outputFormatArg)

    CordaRPCClient(nodeAddress, object : NodeSSLConfiguration {
        override val keyStorePassword: String = "cordacadevpass"
        override val trustStorePassword: String = "trustpass"
        override val certificatesPath: Path = Paths.get(certsDirStr)
    }).use {
        try {
            it.start(userStr, passwordStr)
        } catch (e: ActiveMQNotConnectedException) {
            // Artemis already splatted an exception to the screen by this point :(
            error("Failed to connect to $nodeAddress - are you trying to connect to an HTTP port instead of an AMQP port by mistake?")
        } catch (e: ActiveMQSecurityException) {
            error("Failed to authenticate to server: check your password and SSL certificates.")
        }

        val proxy = it.proxy(timeout)

        if (options.has(consoleArg)) {
            runConsole(outputFormat, proxy, rpcParser)
        } else {
            runOneShotCommand(options, outputFormat, proxy, rpcParser)
        }
    }
}

fun readPasswordFromTerminal(): String {
    TerminalBuilder.terminal().use { term ->
        var result: String? = null
        while (result == null) {
            result = LineReaderBuilder.builder().terminal(term).build().readLine("Password: ", '•')
        }
        return result
    }
}

private fun runOneShotCommand(options: OptionSet, outputFormat: Format, proxy: CordaRPCOps, rpcParser: CommandLineRPCParser<CordaRPCOps>) {
    val command = options.nonOptionArguments().joinToString(" ")
    val parsedRPC = parseCommand(command, proxy, rpcParser) ?: return
    execute(outputFormat, parsedRPC)?.get()
}

private fun runConsole(outputFormat: Format, proxy: CordaRPCOps, rpcParser: CommandLineRPCParser<CordaRPCOps>) {
    drawCordaLogoBanner("RPC CONSOLE")

    Emoji.renderIfSupported {
        println(Ansi.ansi().bgRed().fg(Ansi.Color.WHITE).bold().a("${Emoji.skullAndCrossBones} EXPERIMENTAL ".repeat(5).trim()).reset())
        println()
    }

    val availableCommands: TreeSet<String> = sortedSetOf(
            "help",
            "use yaml",
            "use json",
            "use tostring",
            "quit",
            *rpcParser.methodMap.keys.toTypedArray()
    )

    val terminal = TerminalBuilder.terminal()

    // If the user presses Ctrl-C whilst following a future, just stop caring and go back to the console.
    val futureToFinish = AtomicReference<CompletableFuture<Unit>?>()
    terminal.handle(Terminal.Signal.INT) {
        futureToFinish.getAndSet(null)?.complete(Unit)
    }

    terminal.use {
        val lineReader = LineReaderBuilder.builder()
                .appName("Corda")
                .terminal(terminal)
                .completer { lineReader, parsedLine: ParsedLine, resultsList: MutableList<Candidate> ->
                    // Allow tab-completion for commands.
                    // TODO: Support tab completion of parameters and object fields too.
                    val text = parsedLine.line().trim()
                    availableCommands.subSet(text, true, text + Character.MAX_VALUE, false).map(::Candidate).toCollection(resultsList)
                }
                .build()
        lineReader.unsetOpt(LineReader.Option.INSERT_TAB)
        var format = outputFormat
        while (true) {
            val input = try {
                lineReader.readLine(">>> ").trim()
            } catch (e: UserInterruptException) {
                println("Bye bye!")
                break
            } catch (e: EndOfFileException) {
                println("Bye bye!")
                break
            }

            if (input.isEmpty()) continue

            val loweredInput = input.toLowerCase()
            if (loweredInput == "help" || loweredInput == "?" || loweredInput == "/help") {
                printCommandHelp(rpcParser)
                continue
            } else if (loweredInput == "use yaml") {
                format = Format.yaml
                continue
            } else if (loweredInput == "use json") {
                format = Format.json
                continue
            } else if (loweredInput == "use tostring") {
                format = Format.tostring
                continue
            } else if (loweredInput == "quit" || loweredInput == "exit") {
                println("Bye bye")
                break
            }

            val pendingRPC = parseCommand(input, proxy, rpcParser) ?: continue
            val future = execute(format, pendingRPC)
            if (future != null) {
                // Following an observable: wait either for it to complete, or the user to press Ctrl-C.
                futureToFinish.set(future)
                future.get()
            }
        }
    }
}

private fun parseCommand(command: String, proxy: CordaRPCOps, rpcParser: CommandLineRPCParser<CordaRPCOps>): CommandLineRPCParser<CordaRPCOps>.ParsedRPC? {
    return try {
        rpcParser.parse(command, proxy)
    } catch (e: CommandLineRPCParser.UnparseableRPCException) {
        println(e.message)
        null
    }
}

private fun execute(outputFormat: Format, pendingRPC: CommandLineRPCParser<CordaRPCOps>.ParsedRPC): CompletableFuture<Unit>? {
    try {
        val response = pendingRPC()
        return printAndFollowRPCResponse(outputFormat, response)
    } catch (e: RPCException) {
        error("RPC failed: $e")
    } catch (e: Exception) {
        println(e)
        e.printStackTrace()
        exitProcess(1)
    }
}

private fun error(msg: String): Nothing {
    println(msg)
    exitProcess(1)
}

private fun help(optionParser: OptionParser, rpcParser: CommandLineRPCParser<*>) {
    println("""corda-tool: Send commands to a server and view the responses.

This is a low level administration and development tool that lets you send RPCs directly to the node and
view the responses.

Examples:

   corda-tool nodeIdentity
   corda-tool --username=bob partyFromName name: Big Bank
""")
    optionParser.printHelpOn(System.out)
    println()
    printCommandHelp(rpcParser)
    exitProcess(0)
}

private fun printCommandHelp(rpcParser: CommandLineRPCParser<*>) {
    val padding = 50
    val header = "Commands available".padEnd(padding) + "Parameter types"
    println(header)
    println("-".repeat(80))
    println()
    for ((name, args) in rpcParser.methodMap) {
        print(name.padEnd(padding))
        if (args.parameterCount > 0) {
            val paramNames = rpcParser.methodParamNames[name]!!
            val typeNames = args.parameters.map { it.type.simpleName }
            val paramTypes = paramNames.zip(typeNames)
            println(paramTypes.map { "${it.first}: ${it.second}" }.joinToString(", "))
        } else {
            println()
        }
    }
}
