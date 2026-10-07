package com.dev.svn.psbdx.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "svn export": copies a working copy without its .svn metadata into the public Download folder. */
object Exporter {
    /** @return human readable destination, e.g. "Download/PSBDx-SVN/my-repo-20261006-103400" */
    fun exportToDownloads(context: Context, wc: File, alias: String, onProgress: (String) -> Unit): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safe = alias.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "repository" }
        val folder = "PSBDx-SVN/$safe-$stamp"

        val files = wc.walkTopDown().onEnter { it.name != ".svn" }.filter { it.isFile }.toList()
        if (files.isEmpty()) throw IOException("Nothing to export — the working copy is empty")

        if (Build.VERSION.SDK_INT >= 29) {
            // MediaStore needs no storage permission for files this app creates.
            val resolver = context.contentResolver
            files.forEach { f ->
                onProgress(f.name)
                val sub = f.parentFile!!.relativeTo(wc).path
                val rel = "${Environment.DIRECTORY_DOWNLOADS}/$folder" + if (sub.isEmpty()) "" else "/$sub"
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())
                    ?: "application/octet-stream"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Could not create ${f.name} in Downloads")
                resolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("Could not write ${f.name}")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
        } else {
            val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folder)
            files.forEach { f ->
                onProgress(f.name)
                val dst = File(root, f.relativeTo(wc).path)
                dst.parentFile?.mkdirs()
                f.copyTo(dst)
            }
        }
        return "Download/$folder"
    }
}
