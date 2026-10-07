package com.dev.svn.psbdx.svn

import com.dev.svn.psbdx.PsbdxApp
import com.dev.svn.psbdx.data.SvnRepo
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.wc.ISVNEventHandler
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNConflictChoice
import org.tmatesoft.svn.core.wc.SVNEvent
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc.SVNStatusType
import org.tmatesoft.svn.core.wc.SVNWCUtil
import org.tmatesoft.svn.core.wc.ISVNStatusHandler
import org.tmatesoft.svn.core.ISVNLogEntryHandler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Date

enum class ChangeState(val letter: String, val label: String) {
    MODIFIED("M", "Modified"),
    ADDED("A", "Added"),
    DELETED("D", "Deleted"),
    UNVERSIONED("?", "Unversioned"),
    MISSING("!", "Missing"),
    CONFLICTED("C", "Conflicted"),
    REPLACED("R", "Replaced"),
}

enum class ResolveChoice { MINE, THEIRS, MERGED }

data class WcChange(val file: File, val relPath: String, val state: ChangeState)

data class LogItem(
    val revision: Long,
    val author: String,
    val date: Date?,
    val message: String,
    val paths: List<String>,
)

/** Thin, blocking wrapper around SVNKit. Call from a background dispatcher. */
class SvnService(private val repo: SvnRepo, val wcDir: File) {
    var onProgress: ((String) -> Unit)? = null

    val isCheckedOut: Boolean get() = File(wcDir, ".svn").isDirectory

    private fun newManager(): SVNClientManager {
        val configDir = File(PsbdxApp.instance.filesDir, "svn-config").apply { mkdirs() }
        val options = SVNWCUtil.createDefaultOptions(configDir, true)
        val auth = BasicAuthenticationManager.newInstance(repo.username, repo.password.toCharArray())
        val manager = SVNClientManager.newInstance(options, auth)
        manager.setEventHandler(object : ISVNEventHandler {
            override fun handleEvent(event: SVNEvent, progress: Double) {
                val name = event.file?.name ?: event.url?.toString().orEmpty()
                if (name.isNotEmpty()) onProgress?.invoke(name)
            }

            override fun checkCancelled() {}
        })
        return manager
    }

    private inline fun <T> withClient(block: (SVNClientManager) -> T): T {
        val manager = newManager()
        try {
            return block(manager)
        } catch (e: SVNException) {
            throw IOException(e.errorMessage.fullMessage, e)
        } finally {
            manager.dispose()
        }
    }

    fun checkout(): Long = withClient {
        wcDir.mkdirs()
        it.updateClient.doCheckout(
            SVNURL.parseURIEncoded(repo.url.trim()), wcDir,
            SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, true,
        )
    }

    fun update(): Long = withClient {
        it.updateClient.doUpdate(wcDir, SVNRevision.HEAD, SVNDepth.INFINITY, false, false)
    }

    fun commit(message: String) = withClient {
        it.commitClient.doCommit(
            arrayOf(wcDir), false, message, null, null, false, false, SVNDepth.INFINITY,
        )
    }

    fun revert(files: List<File>) = withClient {
        it.wcClient.doRevert(files.toTypedArray(), SVNDepth.INFINITY, null)
    }

    fun cleanup() = withClient { it.wcClient.doCleanup(wcDir) }

    fun resolve(file: File, choice: ResolveChoice) = withClient {
        val c = when (choice) {
            ResolveChoice.MINE -> SVNConflictChoice.MINE_FULL
            ResolveChoice.THEIRS -> SVNConflictChoice.THEIRS_FULL
            ResolveChoice.MERGED -> SVNConflictChoice.MERGED
        }
        it.wcClient.doResolve(file, SVNDepth.EMPTY, true, true, true, c)
    }

    fun add(file: File) = withClient {
        it.wcClient.doAdd(file, true, false, false, SVNDepth.INFINITY, false, true)
    }

    fun delete(file: File) = withClient {
        it.wcClient.doDelete(file, true, true, false)
    }

    fun rename(src: File, dst: File, versioned: Boolean) {
        if (versioned) withClient { it.moveClient.doMove(src, dst) }
        else if (!src.renameTo(dst)) throw IOException("Could not rename ${src.name}")
    }

    fun isVersioned(file: File): Boolean = withClient {
        runCatching { it.statusClient.doStatus(file, false).isVersioned }.getOrDefault(false)
    }

    fun status(): List<WcChange> = withClient { m ->
        val out = mutableListOf<WcChange>()
        m.statusClient.doStatus(
            wcDir, SVNRevision.UNDEFINED, SVNDepth.INFINITY, false, false, false, false,
            ISVNStatusHandler { st ->
                val f = st.file ?: return@ISVNStatusHandler
                val node = st.nodeStatus
                val contents = st.contentsStatus
                val state = when {
                    node == SVNStatusType.STATUS_CONFLICTED || contents == SVNStatusType.STATUS_CONFLICTED ->
                        ChangeState.CONFLICTED
                    node == SVNStatusType.STATUS_ADDED -> ChangeState.ADDED
                    node == SVNStatusType.STATUS_DELETED -> ChangeState.DELETED
                    node == SVNStatusType.STATUS_REPLACED -> ChangeState.REPLACED
                    node == SVNStatusType.STATUS_MISSING -> ChangeState.MISSING
                    node == SVNStatusType.STATUS_UNVERSIONED -> ChangeState.UNVERSIONED
                    node == SVNStatusType.STATUS_MODIFIED || contents == SVNStatusType.STATUS_MODIFIED ->
                        ChangeState.MODIFIED
                    else -> null
                }
                if (state != null) {
                    out += WcChange(f, f.relativeTo(wcDir).path.ifEmpty { "." }, state)
                }
            },
            null,
        )
        out.sortedBy { it.relPath.lowercase() }
    }

    fun log(limit: Long = 100): List<LogItem> = withClient { m ->
        val items = mutableListOf<LogItem>()
        m.logClient.doLog(
            arrayOf(wcDir), SVNRevision.HEAD, SVNRevision.create(1L), false, true, limit,
            ISVNLogEntryHandler { e ->
                items += LogItem(
                    revision = e.revision,
                    author = e.author ?: "",
                    date = e.date,
                    message = e.message ?: "",
                    paths = e.changedPaths?.values?.map { "${it.type} ${it.path}" }?.sorted() ?: emptyList(),
                )
            },
        )
        items
    }

    /** Unified diff of the working copy vs. BASE for one path. */
    fun diff(file: File): String = withClient {
        val out = ByteArrayOutputStream()
        val depth = if (file.isDirectory) SVNDepth.INFINITY else SVNDepth.EMPTY
        it.diffClient.doDiff(file, SVNRevision.BASE, file, SVNRevision.WORKING, depth, true, out, null)
        out.toString("UTF-8")
    }
}
