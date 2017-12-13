package net.corda.certissuer

/**
 * Main class for the certificate issuer tool
 */
fun main(args: Array<String>) {
    try {
        run(parseParameters(*args))
    } catch (e: ShowHelpException) {
        e.errorMessage?.let(::println)
        e.parser.printHelpOn(System.out)
    }
}

fun run(parameters: Parameters) {
    parameters.run {
        // Do stuff here
    }
}