package com.dev.svn.psbdx.backup

import com.dev.svn.psbdx.PsbdxApp
import com.dev.svn.psbdx.data.SvnRepo
import com.dev.svn.psbdx.svn.ChangeState
import com.dev.svn.psbdx.svn.SvnService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Uncommitted work (modified, added and unversioned files, plus pending deletions) travels inside
 * every backup, so a restored repository can be checked out again with its local edits intact.
 */
object WorkingCopyBackup {
    private const val MAX_FILE = 5L * 1024 * 1024
    private const val MAX_TOTAL = 25L * 1024 * 1024

    fun collect(app: PsbdxApp, repos: List<SvnRepo>): JSONArray {
        val out = JSONArray()
        var total = 0L
        repos.forEach { repo ->
            // a broken working copy must never block the credential backup
            runCatching {
                val wc = File(app.workingCopiesDir, repo.id)
                val svn = SvnService(repo, wc)
                if (!svn.isCheckedOut) return@runCatching
                val changes = svn.status()
                if (changes.isEmpty()) return@runCatching

                val files = JSONArray()
                val deleted = JSONArray()
                val skipped = JSONArray()

                fun addFile(f: File, state: ChangeState) {
                    val rel = f.relativeTo(wc).path.replace(File.separatorChar, '/')
                    if (f.length() > MAX_FILE || total + f.length() > MAX_TOTAL) { skipped.put(rel); return }
                    total += f.length()
                    files.put(
                        JSONObject().put("path", rel).put("state", state.name)
                            .put("data", Base64.getEncoder().encodeToString(f.readBytes())),
                    )
                }

                changes.forEach { c ->
                    when (c.state) {
                        ChangeState.DELETED, ChangeState.MISSING ->
                            deleted.put(c.file.relativeTo(wc).path.replace(File.separatorChar, '/'))
                        else -> when {
                            c.file.isDirectory ->
                                c.file.walkTopDown().onEnter { it.name != ".svn" }.filter { it.isFile }
                                    .forEach { addFile(it, c.state) }
                            c.file.isFile -> addFile(c.file, c.state)
                        }
                    }
                }
                out.put(
                    JSONObject().put("repoId", repo.id).put("baseRevision", svn.baseRevision())
                        .put("files", files).put("deleted", deleted).put("skipped", skipped),
                )
            }
        }
        return out
    }
}

/** Snapshots from a restored backup, waiting for the repository to be checked out again. */
object PendingChanges {
    private fun dir(app: PsbdxApp) = File(app.filesDir, "pending-wc").apply { mkdirs() }
    private fun file(app: PsbdxApp, repoId: String): File? =
        if (Regex("[A-Za-z0-9-]{1,64}").matches(repoId)) File(dir(app), "$repoId.json.gz") else null

    fun save(app: PsbdxApp, repoId: String, snapshot: JSONObject) {
        val f = file(app, repoId) ?: return
        GZIPOutputStream(f.outputStream()).use { it.write(snapshot.toString().toByteArray(Charsets.UTF_8)) }
    }

    fun load(app: PsbdxApp, repoId: String): JSONObject? {
        val f = file(app, repoId)?.takeIf { it.isFile } ?: return null
        return runCatching {
            JSONObject(GZIPInputStream(f.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) })
        }.getOrNull()
    }

    fun delete(app: PsbdxApp, repoId: String) { file(app, repoId)?.delete() }

    fun count(app: PsbdxApp): Int = dir(app).listFiles()?.size ?: 0

    /** Writes the saved edits back into a fresh checkout. Returns how many items were re-applied. */
    fun apply(svn: SvnService, wc: File, snapshot: JSONObject): Int {
        var n = 0
        val files = snapshot.optJSONArray("files") ?: JSONArray()
        for (i in 0 until files.length()) {
            val o = files.getJSONObject(i)
            val target = PathSafety.resolve(wc, o.optString("path")) ?: continue
            runCatching {
                target.parentFile?.mkdirs()
                target.writeBytes(Base64.getDecoder().decode(o.optString("data")))
                if (o.optString("state") == ChangeState.ADDED.name) svn.add(target)
                n++
            }
        }
        val deleted = snapshot.optJSONArray("deleted") ?: JSONArray()
        for (i in 0 until deleted.length()) {
            val target = PathSafety.resolve(wc, deleted.optString(i)) ?: continue
            if (target.exists()) runCatching { svn.delete(target); n++ }
        }
        return n
    }
}
