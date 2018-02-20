package net.corda.blobinspector

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ModeParse {
    @Test
    fun fileIsSetToFile() {
        val opts1 = Array<String>(2) {
            when (it) {
                0 -> "-m"
                1 -> "file"
                else -> "error"
            }
        }

        assertEquals(Mode.file, getMode(opts1).mode)
    }

    @Test
    fun nothingIsSetToFile() {
        val opts1 = Array<String>(0) { "" }

        assertEquals(Mode.file, getMode(opts1).mode)
    }

    @Test
    fun filePathIsSet() {
        val opts1 = Array<String>(4) {
            when (it) {
                0 -> "-m"
                1 -> "file"
                2 -> "-f"
                3 -> "path/to/file"
                else -> "error"
            }
        }

        val config = getMode(opts1)
        assertTrue (config is FileConfig)
        assertEquals(Mode.file, config.mode)
        assertEquals("unset", (config as FileConfig).file)

        loadModeSpecificOptions(config, opts1)

        assertEquals("path/to/file", config.file)
    }


}