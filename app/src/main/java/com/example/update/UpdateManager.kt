package com.example.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val version: String, val downloadUrl: String)

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    
    // TODO: Change this to your actual GitHub username and repository name
    private val githubRepo = "shamimahmedrobin/mfs-automator"

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$githubRepo/releases/latest")
                .header("User-Agent", "MFS-Automator-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@withContext null
                    val json = JSONObject(bodyString)
                    val tagName = json.getString("tag_name")
                    val assets = json.getJSONArray("assets")
                    if (assets.length() > 0) {
                        val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
                        
                        val currentVersion = "v${BuildConfig.VERSION_NAME}"
                        
                        Log.d("UpdateManager", "Current: $currentVersion, Latest: $tagName")
                        if (tagName != currentVersion && tagName.isNotBlank()) {
                            return@withContext UpdateInfo(tagName, downloadUrl)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Update check failed: ${e.message}")
        }
        return@withContext null
    }

    fun downloadAndInstall(updateInfo: UpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(updateInfo.downloadUrl)
        val fileName = "mfs_automator_update_${updateInfo.version}.apk"
        
        val request = DownloadManager.Request(uri).apply {
            setTitle("Downloading Update")
            setDescription("Downloading version ${updateInfo.version}")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }

        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId && ctx != null) {
                    val apkFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                    // Never install an update that isn't signed with the same
                    // certificate as the currently installed app.
                    if (isSignatureMatching(ctx, apkFile)) {
                        installApk(ctx, fileName)
                    } else {
                        Log.e("UpdateManager", "Downloaded APK signature does not match installed app — refusing to install.")
                        Toast.makeText(ctx, "Update blocked: signature mismatch", Toast.LENGTH_LONG).show()
                        try { apkFile.delete() } catch (_: Exception) {}
                    }
                    ctx.unregisterReceiver(this)
                }
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    /**
     * Compares the signing certificate(s) of a downloaded APK against the
     * installed app. Android itself also enforces same-signer updates, but an
     * explicit check lets us refuse (and delete) a tampered file with a clear
     * message instead of a cryptic install failure.
     */
    @Suppress("DEPRECATION")
    private fun isSignatureMatching(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) return false
            val pm = context.packageManager
            val apkSigs = pm.getPackageArchiveInfo(
                apkFile.absolutePath, PackageManager.GET_SIGNATURES
            )?.signatures?.map { it.toByteArray().contentHashCode() }?.toSet()
            val installedSigs = pm.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNATURES
            ).signatures?.map { it.toByteArray().contentHashCode() }?.toSet()
            !apkSigs.isNullOrEmpty() && apkSigs == installedSigs
        } catch (e: Exception) {
            Log.e("UpdateManager", "Signature check failed: ${e.message}")
            false
        }
    }

    private fun installApk(context: Context, fileName: String) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
