package net.corda.blobinspector

import org.apache.commons.cli.*
import java.lang.IllegalArgumentException

/**
 * Mode isn't a required property as we default it to [Mode.file]
 */
private fun modeOption() = Option("m", "mode", true, "mode, file is the default").apply {
    isRequired = false
}



/**
 *
 */
fun getMode(args: Array<String>) : Config {
    // For now we only care what mode we're being put in, we can build the rest of the args and parse them
    // later
    val options = Options().apply {
        addOption(modeOption())
    }

    val cmd = try {
        DefaultParser().parse(options, args, true)
    } catch (e: org.apache.commons.cli.ParseException) {
        println (e)
        HelpFormatter().printHelp("blobinspector", options)
        throw IllegalArgumentException("OH NO!!!")
    }

    return try {
        Mode.valueOf(cmd.getParsedOptionValue("m") as? String ?: "file")
    } catch (e: IllegalArgumentException) {
        Mode.file
    }.make()
}

fun loadModeSpecificOptions(config: Config, args: Array<String>) {
    config.apply {
        // load that modes specific command line switches, needs to include the mode option
        val modeSpecificOptions = config.options().apply {
            addOption(modeOption())
        }

        println (modeSpecificOptions)

        println ("Loading options: ${args.joinToString(", ")}")

        populate (try {
            DefaultParser().parse(modeSpecificOptions, args, false)
        } catch (e: org.apache.commons.cli.ParseException) {
            println ("Error: ${e.message}")
            HelpFormatter().printHelp("blobinspector", modeSpecificOptions)
            System.exit(1)
            return
        })
    }
}

fun main(args: Array<String>) {
    getMode(args).apply {
        loadModeSpecificOptions(this, args)
        BlobHandler.make(this)
    }
}
