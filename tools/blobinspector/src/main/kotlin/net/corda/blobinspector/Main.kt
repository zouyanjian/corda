package net.corda.blobinspector

import org.apache.commons.cli.*
import java.lang.IllegalArgumentException

/**
 *
 */
fun getMode(args: Array<String>) : Config {
    // For now we only care what mode we're being put in, we can build the rest of the args and parse them
    // later
    val options = Options().apply {
        addOption(
                Option("m", "mode", true, "mode, file is the default").apply {
                    isRequired = false
                })
    }

    val cmd = try {
        DefaultParser().parse(options, args)
    } catch (e: org.apache.commons.cli.ParseException) {
        HelpFormatter().printHelp("blobinspector", options)
        throw IllegalArgumentException("OH NO!!!")
    }

    return try {
        Mode.valueOf(cmd.getParsedOptionValue("m") as? String ?: "file")
    } catch (e: IllegalArgumentException) {
        Mode.file
    }.make()
}


fun main(args: Array<String>) {
    // work out what mode we're operating in
    getMode(args).apply {
        // load that modes specific command line switches
        val modeSpecificOptions = mode.options()

        populate (try {
            DefaultParser().parse(modeSpecificOptions, args)
        } catch (e: org.apache.commons.cli.ParseException) {
            HelpFormatter().printHelp("blobinspector", modeSpecificOptions)
            System.exit(1)
            return
        })


        BlobHandler.make(this)
    }
}
