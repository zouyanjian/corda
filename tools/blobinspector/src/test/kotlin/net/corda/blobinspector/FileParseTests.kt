package net.corda.blobinspector

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import net.corda.testing.common.internal.ProjectStructure.projectRootDir


class FileParseTests {
    @Suppress("UNUSED")
    var localPath = projectRootDir.toUri().resolve(
            "tools/blobinspector/src/test/resources/net/corda/blobinspector")

    @Test
    fun singleInt() {
        val path = FileParseTests::class.java.getResource("FileParseTests.1Int")
        println (path)

        val args = Array<String>(4) {
            when (it) {
                0 -> "-m"
                1 -> "file"
                2 -> "-f"
                3 -> path.toString()
                else -> "error"
            }
        }

        val handler = getMode(args).let { mode ->
            loadModeSpecificOptions(mode, args)
            BlobHandler.make(mode)
        }

        inspectBlob(handler.config, handler.getBytes())
    }
}
