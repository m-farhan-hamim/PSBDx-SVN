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

/** Minimal Google Drive REST v3 client (appDataFolder) built on plain OkHttp. */
class DriveClient(private val auth: GoogleAuth, private val http: OkHttpClient) {

    suspend fun upload(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        val existing = findFileId(token)
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

    /** Returns the encrypted backup blob, or null when no backup exists yet. */
    suspend fun download(): ByteArray? = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        val id = findFileId(token) ?: return@withContext null
        val req = Request.Builder().url("$API/files/$id?alt=media")
            .header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) checkOk(resp.code, resp.body?.string())
            resp.body?.bytes()
        }
    }

    private fun findFileId(token: String): String? {
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name = '$FILE_NAME' and trashed = false")
            .addQueryParameter("fields", "files(id,name)")
            .addQueryParameter("pageSize", "5")
            .build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            checkOk(resp.code, body)
            val files = JSONObject(body).optJSONArray("files") ?: return null
            return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
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
