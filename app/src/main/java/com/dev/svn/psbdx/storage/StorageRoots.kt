package com.dev.svn.psbdx.storage

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

data class StorageRoot(val name: String, val dir: File, val removable: Boolean)

/** Internal storage plus every mounted SD card / USB drive that the app can see. */
object StorageRoots {
    fun list(context: Context): List<StorageRoot> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val found = LinkedHashMap<String, StorageRoot>()

        fun add(dir: File, removable: Boolean) {
            val key = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (key in found || !dir.isDirectory) return
            val label = runCatching { sm.getStorageVolume(dir)?.getDescription(context) }.getOrNull()
                ?: if (removable) dir.name else "Internal storage"
            found[key] = StorageRoot(label, dir, removable)
        }

        add(Environment.getExternalStorageDirectory(), removable = false)

        // Secondary volumes show up as /storage/XXXX-XXXX/Android/data/<pkg>/files
        context.getExternalFilesDirs(null).filterNotNull().forEach { f ->
            val idx = f.absolutePath.indexOf("/Android/data")
            if (idx > 0) add(File(f.absolutePath.substring(0, idx)), removable = true)
        }
        // Anything else mounted under /storage (USB OTG, adopted media)
        File("/storage").listFiles()?.forEach { f ->
            if (f.name != "emulated" && f.name != "self" && f.isDirectory && f.canRead()) add(f, removable = true)
        }
        return found.values.toList()
    }
}
