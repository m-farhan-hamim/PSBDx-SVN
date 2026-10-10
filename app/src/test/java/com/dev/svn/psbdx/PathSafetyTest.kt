package com.dev.svn.psbdx

import com.dev.svn.psbdx.backup.PathSafety
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PathSafetyTest {
    private val root: File = Files.createTempDirectory("wc").toFile()

    @Test
    fun acceptsNormalPaths() {
        assertNotNull(PathSafety.resolve(root, "src/main/App.kt"))
        assertEquals(File(root, "a.txt").path, PathSafety.resolve(root, "a.txt")?.path)
    }

    @Test
    fun rejectsTraversalAbsoluteAndMetadata() {
        assertNull(PathSafety.resolve(root, "../evil.txt"))
        assertNull(PathSafety.resolve(root, "a/../../evil.txt"))
        assertNull(PathSafety.resolve(root, "/etc/passwd"))
        assertNull(PathSafety.resolve(root, ".svn/wc.db"))
        assertNull(PathSafety.resolve(root, "dir/.svn/entries"))
        assertNull(PathSafety.resolve(root, ""))
    }
}
