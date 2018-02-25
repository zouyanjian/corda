package net.corda.blobinspector

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options

/**
 * Enumeration of the modes in which the blob inspector can be run.
 *
 * @property make lambda function that takes no parameters and returns a specific instance of the configuration
 * opbject for that mode.
 *
 * @property options A lambda functio that takes no parameters and returns am [Options] instance that define
 * the command line flags related to this mode. For example ``file`` mode would have an option to pass in
 * the name of the file to read.
 *
 */
enum class Mode(
        val make : () -> Config,
        val options : (Options) -> Unit) {
    file(
            {
                FileConfig(Mode.file)
            },
            { o ->
                o.apply{
                    addOption(
                            Option ("f", "file", true, "path to file").apply {
                                isRequired = true
                            }
                    )
                }
            }
    )
}

/**
 * Configuration data class for
 */
abstract class Config (
        val mode: Mode
) {
    var schema: Boolean = false

    abstract fun populateSpecific(cmdLine: CommandLine)

    fun populate(cmdLine: CommandLine) {
        populateSpecific(cmdLine)
    }
}


/**
 *
 */
class FileConfig (
        mode: Mode
        ) : Config(mode) {

    var file: String = "unset"

    override fun populateSpecific(cmdLine : CommandLine) {
        file = cmdLine.getParsedOptionValue("f") as String
    }
}
