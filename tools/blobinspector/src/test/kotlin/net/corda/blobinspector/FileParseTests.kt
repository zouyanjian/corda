package net.corda.blobinspector

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import net.corda.testing.common.internal.ProjectStructure.projectRootDir


class FileParseTests {
    @Suppress("UNUSED")
    var localPath = projectRootDir.toUri().resolve(
            "tools/blobinspector/src/test/resources/net/corda/blobinspector")

    fun setupArgsWithFile(path: String)  = Array<String>(4) {
        when (it) {
            0 -> "-m"
            1 -> "file"
            2 -> "-f"
            3 -> path
            else -> "error"
        }
    }

    val filesToTest = listOf (
            "FileParseTests.1Int",
            "FileParseTests.2Int",
            "FileParseTests.3Int",
            "FileParseTests.1String",
            "FileParseTests.1Composite",
            "FileParseTests.2Composite",
            "FileParseTests.IntList",
            "FileParseTests.StringList",
            "FileParseTests.MapIntString",
            "FileParseTests.MapIntClass"
            )

    fun testFile(file : String) {
        val path = FileParseTests::class.java.getResource(file)
        val args = setupArgsWithFile(path.toString())

        val handler = getMode(args).let { mode ->
            loadModeSpecificOptions(mode, args)
            BlobHandler.make(mode)
        }

        inspectBlob(handler.config, handler.getBytes())
    }

    @Test
    fun simpleFiles() {
        filesToTest.forEach { testFile(it) }
    }

    @Test
    fun specificTest() {
        testFile(filesToTest[4])
        testFile(filesToTest[5])
        testFile(filesToTest[6])
    }

}
