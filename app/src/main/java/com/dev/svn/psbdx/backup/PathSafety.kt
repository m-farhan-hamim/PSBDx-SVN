package com.dev.svn.psbdx.backup

import java.io.File

/** Resolves relative paths read from a backup file, refusing anything that escapes the working copy. */
object PathSafety {
    fun resolve(root: File, rel: String): File? {
        if (rel.isBlank() || rel.startsWith("/") || rel.startsWith("\\")) return null
        if (rel.split('/', '\\').any { it == ".." || it == ".svn" }) return null
        val target = File(root, rel)
        val rootPath = root.canonicalPath
        return if (target.canonicalPath.startsWith(rootPath + File.separator)) target else null
    }
}
