@file:JvmName("CordaJarLoader")

package net.corda.node

import net.corda.node.internal.NodeStartup
import java.io.File

// TODO: Convert to JAva, this needs the kotlin stdlib
fun main(args: Array<String>) {
    println(File(NodeStartup::class.java.protectionDomain.codeSource.location.toURI()))
}