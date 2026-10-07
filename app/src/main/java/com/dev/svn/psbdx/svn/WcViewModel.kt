package com.dev.svn.psbdx.svn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.svn.psbdx.PsbdxApp
import com.dev.svn.psbdx.data.SvnRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileRow(val file: File, val isDir: Boolean, val state: ChangeState?)

/** Files waiting to be pasted. [cut] = move, otherwise copy. */
data class FileClip(val files: List<File>, val cut: Boolean)

class WcViewModel(val repo: SvnRepo) : ViewModel() {
    val svn = SvnService(repo, File(PsbdxApp.instance.workingCopiesDir, repo.id))

    var checkedOut by mutableStateOf(svn.isCheckedOut)
    var currentDir by mutableStateOf(svn.wcDir)
    var entries by mutableStateOf<List<FileRow>>(emptyList())
    var changes by mutableStateOf<List<WcChange>>(emptyList())
    var log by mutableStateOf<List<LogItem>>(emptyList())
    var busy by mutableStateOf(false)
    var progress by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    /** Multi-select in the file explorer (absolute paths, survives folder navigation). */
    val selected = mutableStateListOf<String>()
    var clip by mutableStateOf<FileClip?>(null)

    init {
        svn.onProgress = { progress = it }
        reload()
    }

    val atRoot: Boolean get() = currentDir == svn.wcDir

    fun reload() {
        viewModelScope.launch { refreshInternal() }
    }

    private suspend fun refreshInternal() = withContext(Dispatchers.IO) {
        checkedOut = svn.isCheckedOut
        val st = if (checkedOut) runCatching { svn.status() }.getOrDefault(emptyList()) else emptyList()
        val map = st.associate { it.file.absolutePath to it.state }
        val list = (currentDir.listFiles() ?: emptyArray())
            .filter { it.name != ".svn" }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { FileRow(it, it.isDirectory, map[it.absolutePath]) }
        // Status entries for deleted / missing files should still be visible in the explorer.
        val ghosts = st.filter { it.file.parentFile == currentDir && !it.file.exists() }
            .map { FileRow(it.file, false, it.state) }
        withContext(Dispatchers.Main) {
            changes = st
            entries = list + ghosts
        }
    }

    /** Runs a blocking SVN / file operation with busy + error handling, then refreshes. */
    private fun op(success: String? = null, block: () -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { block() }
                if (success != null) message = success
            } catch (e: Exception) {
                message = e.message ?: e.toString()
            } finally {
                busy = false
                progress = ""
            }
            refreshInternal()
        }
    }

    fun openDir(dir: File) { currentDir = dir; reload() }
    fun up() { if (!atRoot) currentDir.parentFile?.let { openDir(it) } }

    fun checkout() = op("Checkout complete") { svn.checkout() }
    fun update() = op("Updated") { svn.update().let { } }
    fun cleanup() = op("Cleanup complete") { svn.cleanup() }
    fun commit(msg: String) = op("Committed") { svn.commit(msg) }
    fun revert(files: List<File>) = op("Reverted ${files.size} item(s)") { svn.revert(files) }
    fun resolve(file: File, c: ResolveChoice) = op("Conflict resolved (${c.name.lowercase()})") { svn.resolve(file, c) }
    fun addToSvn(file: File) = op("Added ${file.name}") { svn.add(file) }

    fun addAllUnversioned() = op("Added unversioned files") {
        changes.filter { it.state == ChangeState.UNVERSIONED }.forEach { svn.add(it.file) }
    }

    fun delete(row: FileRow) = op("Deleted ${row.file.name}") {
        if (row.state == ChangeState.UNVERSIONED || !svn.isCheckedOut) row.file.deleteRecursively()
        else svn.delete(row.file)
    }

    fun rename(row: FileRow, newName: String) = op("Renamed to $newName") {
        val dst = File(row.file.parentFile, newName)
        if (dst.exists()) throw java.io.IOException("$newName already exists")
        svn.rename(row.file, dst, versioned = row.state != ChangeState.UNVERSIONED)
    }

    fun newFolder(name: String) = op("Folder created") {
        val d = File(currentDir, name)
        if (!d.mkdirs()) throw java.io.IOException("Could not create $name")
        if (svn.isCheckedOut) svn.add(d)
    }

    fun newFile(name: String) = op("File created") {
        val f = File(currentDir, name)
        if (f.exists() || !f.createNewFile()) throw java.io.IOException("Could not create $name")
        if (svn.isCheckedOut) svn.add(f)
    }

    /** Copies files / folders from anywhere on the device into the open folder and schedules them for add. */
    fun upload(sources: List<File>) = op {
        val added = mutableListOf<File>()
        val destDir = currentDir.canonicalFile
        sources.forEach { src ->
            val canon = src.canonicalFile
            if (destDir == canon || destDir.path.startsWith(canon.path + File.separator)) {
                throw java.io.IOException("Can't copy ${src.name} into itself")
            }
            val dst = uniqueTarget(currentDir, src.name)
            copyTree(src, dst)
            added += dst
        }
        if (svn.isCheckedOut) added.forEach { svn.add(it) }
        message = "Added ${added.size} item(s) to the repository"
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || base == name) "" else ".$it" }
        var i = 1
        while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
        return f
    }

    private fun copyTree(src: File, dst: File) {
        if (java.nio.file.Files.isSymbolicLink(src.toPath())) return
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { if (it.name != ".svn") copyTree(it, File(dst, it.name)) }
        } else {
            progress = src.name
            src.copyTo(dst)
        }
    }

    /** svn export into Download/PSBDx-SVN/<alias>-<timestamp>. */
    fun exportToDownloads() = op {
        val where = com.dev.svn.psbdx.storage.Exporter.exportToDownloads(
            PsbdxApp.instance, svn.wcDir, repo.alias,
        ) { progress = it }
        message = "Exported to $where"
    }

    // ---------- selection, copy / cut / paste ----------
    fun toggleSelect(file: File) {
        val p = file.absolutePath
        if (!selected.remove(p)) selected.add(p)
    }

    fun clearSelection() = selected.clear()

    fun selectAll() {
        entries.forEach { if (it.file.absolutePath !in selected) selected.add(it.file.absolutePath) }
    }

    private fun setClip(files: List<File>, cut: Boolean) {
        if (files.isEmpty()) return
        clip = FileClip(files, cut)
        selected.clear()
        message = "${files.size} item(s) ${if (cut) "cut" else "copied"} — open a folder and tap Paste"
    }

    fun copySelected() = setClip(selected.map { File(it) }, cut = false)
    fun cutSelected() = setClip(selected.map { File(it) }, cut = true)
    fun copyOne(file: File) = setClip(listOf(file), cut = false)
    fun cutOne(file: File) = setClip(listOf(file), cut = true)
    fun clearClip() { clip = null }

    fun paste() {
        val c = clip ?: return
        val target = currentDir
        op {
            val done = mutableListOf<File>()
            val destCanon = target.canonicalFile
            c.files.forEach { src ->
                if (!src.exists()) return@forEach
                val canon = src.canonicalFile
                if (destCanon == canon || destCanon.path.startsWith(canon.path + File.separator)) {
                    throw java.io.IOException("Can't paste ${src.name} into itself")
                }
                if (c.cut && src.parentFile?.canonicalFile == destCanon) return@forEach // already here
                val dst = uniqueTarget(target, src.name)
                if (c.cut) {
                    if (svn.isCheckedOut && svn.isVersioned(src)) svn.rename(src, dst, versioned = true)
                    else if (!src.renameTo(dst)) { copyTree(src, dst); src.deleteRecursively() }
                } else {
                    copyTree(src, dst)
                }
                done += dst
            }
            if (!c.cut && svn.isCheckedOut) done.forEach { svn.add(it) }
            if (c.cut) clip = null
            message = "${if (c.cut) "Moved" else "Pasted"} ${done.size} item(s)"
        }
    }

    fun deleteSelected() {
        val files = selected.map { File(it) }
        selected.clear()
        op("Deleted ${files.size} item(s)") {
            files.forEach { f ->
                if (svn.isCheckedOut && svn.isVersioned(f)) svn.delete(f) else f.deleteRecursively()
            }
        }
    }

    fun loadLog() {
        viewModelScope.launch {
            busy = true
            try {
                log = withContext(Dispatchers.IO) { svn.log() }
            } catch (e: Exception) {
                message = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }

    suspend fun diff(file: File): String = withContext(Dispatchers.IO) {
        try {
            svn.diff(file).ifBlank { "(no differences against BASE)" }
        } catch (e: Exception) {
            "Cannot diff: ${e.message}"
        }
    }

    suspend fun readText(file: File): String? = withContext(Dispatchers.IO) {
        if (file.length() > 1_000_000) return@withContext null
        val bytes = file.readBytes()
        if (bytes.any { it == 0.toByte() }) null else String(bytes, Charsets.UTF_8)
    }

    suspend fun writeText(file: File, text: String) = withContext(Dispatchers.IO) {
        file.writeText(text, Charsets.UTF_8)
        refreshInternal()
    }
}
