package net.corda.blobinspector

import org.apache.commons.cli.*;

fun main(args: Array<String>) {
    val options = Options()

    val a = Option("a", "aaa", true, "test1")
    a.isRequired = true
    options.addOption(a)

    val b = Option("b", "bbb", true, "test 2")
    b.isRequired = false
    options.addOption(b)

    val parser = DefaultParser()
    val formatter = HelpFormatter()
    val cmd: CommandLine

    try {
        cmd = parser.parse(options, args)
    } catch (e: org.apache.commons.cli.ParseException) {
        formatter.printHelp("blobinspector", options)

        System.exit(1)
        return
    }

}
