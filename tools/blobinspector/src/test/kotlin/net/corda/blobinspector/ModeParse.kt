package net.corda.blobinspector

import org.junit.Test
import org.junit.Assert.assertEquals

class ModeParse {
    @Test
    fun fileIsSetToFile() {

        val opts1 = Array<String>(2) { when (it) {
            0 -> "-m"
            1 -> "file"
            else -> "error"
        }}

        assertEquals(Mode.file, getMode(opts1).mode)
    }
}