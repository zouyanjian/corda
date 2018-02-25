package net.corda.blobinspector

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.HelpFormatter
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
 * Configuration data class for  the Blob Inspector.
 *
 * @property mode
 */
abstract class Config (val mode: Mode) {
    var schema: Boolean = false
    var transforms: Boolean = false
    var data: Boolean = false

    abstract fun populateSpecific(cmdLine: CommandLine)

    fun populate(cmdLine: CommandLine) {
        schema = cmdLine.hasOption('s')
        transforms = cmdLine.hasOption('t')
        data = cmdLine.hasOption('d')
    }

    /**
     *
     */
    fun options() = Options().apply {
        // install generic options
        addOption(Option("s", "schema", false, "print the blob's schema").apply {
            isRequired = false
        })

        addOption(Option("t", "transforms", false, "print the blob's transforms schema").apply {
            isRequired = false
        })

        addOption(Option("d", "data", false, "Display the serialised data").apply {
            isRequired = false
        })

        // install the mode specific options
        mode.options(this)
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
