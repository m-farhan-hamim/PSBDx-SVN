package com.dev.svn.psbdx.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

data class RemoteBackup(val id: String, val modifiedAtMs: Long)

/** Minimal Google Drive REST v3 client (appDataFolder) built on plain OkHttp. */
class DriveClient(private val auth: GoogleAuth, private val http: OkHttpClient) {

    suspend fun upload(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        val existing = listBackups(token).firstOrNull()?.id
        val metadata = JSONObject().put("name", FILE_NAME)
        if (existing == null) metadata.put("parents", org.json.JSONArray().put("appDataFolder"))
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val url = if (existing == null) "$UPLOAD/files?uploadType=multipart"
        else "$UPLOAD/files/$existing?uploadType=multipart"
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .let { if (existing == null) it.post(body) else it.patch(body) }
            .build()
        http.newCall(req).execute().use { checkOk(it.code, it.body?.string()) }
    }

    /** Metadata of the newest backup, or null when none exists yet. */
    suspend fun findBackup(): RemoteBackup? = withContext(Dispatchers.IO) {
        listBackups(auth.accessToken()).firstOrNull()
    }

    /** Returns the encrypted backup blob, or null when no backup exists yet. */
    suspend fun download(): ByteArray? = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        val id = listBackups(token).firstOrNull()?.id ?: return@withContext null
        val req = Request.Builder().url("$API/files/$id?alt=media")
            .header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) checkOk(resp.code, resp.body?.string())
            resp.body?.bytes()
        }
    }

    /** Deletes every backup file of this app from Drive ("I forgot my passphrase"). */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        listBackups(token).forEach { f ->
            val req = Request.Builder().url("$API/files/${f.id}")
                .header("Authorization", "Bearer $token").delete().build()
            http.newCall(req).execute().use { if (it.code != 404) checkOk(it.code, it.body?.string()) }
        }
    }

    private fun listBackups(token: String): List<RemoteBackup> {
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name = '$FILE_NAME' and trashed = false")
            .addQueryParameter("fields", "files(id,name,modifiedTime)")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("pageSize", "10")
            .build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            checkOk(resp.code, body)
            val files = JSONObject(body).optJSONArray("files") ?: return emptyList()
            return (0 until files.length()).map { i ->
                val o = files.getJSONObject(i)
                RemoteBackup(
                    id = o.getString("id"),
                    modifiedAtMs = runCatching { java.time.Instant.parse(o.optString("modifiedTime")).toEpochMilli() }
                        .getOrDefault(0L),
                )
            }
        }
    }

    private fun checkOk(code: Int, body: String?) {
        if (code !in 200..299) throw IOException("Google Drive HTTP $code ${body?.take(200).orEmpty()}")
    }

    companion object {
        const val FILE_NAME = "psbdx-svn-backup.enc"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}
