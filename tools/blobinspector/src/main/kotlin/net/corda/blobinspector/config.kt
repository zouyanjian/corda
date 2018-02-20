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
        val options : () -> Options) {
    file(
            {
                FileConfig(Mode.file)
            },
            {
                Options().apply{
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
 *
 */
abstract class Config (
        val mode: Mode
) {
    abstract fun populate(cmdLine: CommandLine)
}

/**
 *
 */
class FileConfig (
        mode: Mode
        ) : Config(mode) {

    var file: String = "unset"

    override fun populate(cmdLine : CommandLine) {
        file = cmdLine.getParsedOptionValue("f") as String
    }
}
