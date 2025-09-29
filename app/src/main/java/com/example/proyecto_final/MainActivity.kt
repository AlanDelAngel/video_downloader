package com.example.proyecto_final

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.text.TextUtils
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.SparseArray
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import at.huber.youtubeExtractor.VideoMeta
import at.huber.youtubeExtractor.YouTubeExtractor
import at.huber.youtubeExtractor.YtFile
import com.example.proyecto_final.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder

import android.content.ContentValues
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlin.text.clear
import kotlin.toString


class MainActivity : AppCompatActivity() {
    private val http = OkHttpClient()
    private lateinit var auth: FirebaseAuth
    private lateinit var button: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate ONCE and set content view ONCE (use binding.root)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // If not logged in, go to Login and stop
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, Login::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        // Wire the click on the ACTUAL on-screen button
        binding.logOutButton.setOnClickListener {
            // Sign out first
            auth.signOut()

            // Then navigate; clear back stack so Back won't reopen Main
            startActivity(Intent(this, Login::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        // (Keep your other listeners AFTER this single setContentView)
        binding.downloadButton.setOnClickListener {
            val youtubeUrl = binding.urlEditText.text?.toString()?.trim().orEmpty()
            if (youtubeUrl.isBlank() || !youtubeUrl.startsWith("http")) {
                Toast.makeText(this, "Please enter a valid YouTube URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            extractAndDownloadVideo(youtubeUrl)
        }
    }


    private fun isYouTubeUrl(u: String) =
        u.matches(Regex("(?i)^https?://(www\\.)?(youtube\\.com|youtu\\.be)/.+"))

    private fun openYouTube(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        // Try the YouTube app first; fall back to browser if not installed
        intent.setPackage("com.google.android.youtube")
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }


    private fun isDirectMediaUrl(u: String): Boolean {
        return u.matches(Regex("(?i)^https?://.+\\.(mp4|m4v|mov)(\\?.*)?$"))
    }

    private lateinit var binding: ActivityMainBinding

    // Optional: Android 13+ notification permission request (nice to have)
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op; user choice respected */ }

    /**
     * Saves the OkHttp response body into the public Downloads collection on API 29+,
     * and into the app's external files (Download/) on older devices.
     * Returns a content Uri you can open/share.
     */
    private fun saveToDownloads(body: okhttp3.ResponseBody, title: String): Uri {
        return if (Build.VERSION.SDK_INT >= 29) {
            // Public Downloads via MediaStore (visible in Files/Downloads app)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$title.mp4")
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to create media entry")

            try {
                resolver.openOutputStream(uri, "w").use { out ->
                    if (out == null) throw IllegalStateException("No output stream")
                    body.byteStream().copyTo(out, 8 * 1024)
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null) // cleanup on failure
                throw e
            }
            uri
        } else {
            // API 28 and below: write to app-specific external "Download" folder
            val outFile = java.io.File(
                getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
                "$title.mp4"
            )
            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output, 8 * 1024)
                    output.flush()
                }
            }
            // Convert to a content Uri via FileProvider so we can open/share it
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", outFile)
        }
    }

    /** Opens the saved video Uri in any video player the user has. */
    private fun openSavedVideo(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun uiFail(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            resetUI()
        }
    }

    private fun pickPreferredItagUrl(ytFiles: android.util.SparseArray<at.huber.youtubeExtractor.YtFile>): String? {
        // 1) Try 720p MP4+audio (itag 22)
        ytFiles.get(22)?.url?.let { return it }
        // 2) Then 360p MP4+audio (itag 18)
        ytFiles.get(18)?.url?.let { return it }

        // 3) Fallback: best MP4 with audio available
        var bestUrl: String? = null
        var bestHeight = -1
        for (i in 0 until ytFiles.size()) {
            val file = ytFiles.valueAt(i)
            val f = file.format
            if (f.ext == "mp4" && f.audioBitrate > 0 && f.height > bestHeight) {
                bestHeight = f.height
                bestUrl = file.url
            }
        }
        return bestUrl
    }


    private fun extractAndDownloadVideo(youtubeUrl: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.downloadButton.isEnabled = false

        if (isYouTubeUrl(youtubeUrl)) {
            // Respect TOS: don’t download; just play it
            openYouTube(youtubeUrl)
            resetUI()
            return
        }

        // ✅ If the user pasted a direct .mp4/.mov URL, skip the extractor and download directly
        if (isDirectMediaUrl(youtubeUrl)) {
            val title = "video_" + System.currentTimeMillis()
            lifecycleScope.launch(Dispatchers.IO) {
                startDownload(youtubeUrl, title)
            }
            return
        }

        // 🔑 Create and run the extractor on the MAIN thread
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val extractor = @SuppressLint("StaticFieldLeak")
                object : YouTubeExtractor(this@MainActivity) {
                    override fun onExtractionComplete(
                        ytFiles: android.util.SparseArray<at.huber.youtubeExtractor.YtFile>?,
                        videoMeta: at.huber.youtubeExtractor.VideoMeta?
                    ) {
                        if (ytFiles == null) {
                            uiFail("Failed to extract video.")
                            return
                        }

                        val downloadUrl = pickPreferredItagUrl(ytFiles)
                        if (downloadUrl.isNullOrBlank()) {
                            uiFail("No MP4 stream found (itag 22/18 missing).")
                            return
                        }

                        val videoTitle = (videoMeta?.title ?: "youtube_video")
                            .replace("[^a-zA-Z0-9.-]".toRegex(), "_")

                        // Start the OkHttp download off the main thread
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            startDownload(downloadUrl, videoTitle)
                        }
                    }


                }
                extractor.extract(youtubeUrl)
            } catch (e: Exception) {
                uiFail("Error: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun startDownload(url: String, title: String) {
        // Build a browser-like request (Range is important for YouTube CDN)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("Referer", "https://www.youtube.com/")
            .header("Origin", "https://www.youtube.com")
            .header("Range", "bytes=0-")
            .build()

        try {
            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) {
                uiFail("HTTP ${resp.code} while downloading.")
                resp.close()
                return
            }
            val body = resp.body ?: run {
                uiFail("Empty response body.")
                resp.close()
                return
            }

            // 🔽 Save to public Downloads (API 29+) or app folder (older)
            val savedUri = saveToDownloads(body, title)
            resp.close()

            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Saved to Downloads", Toast.LENGTH_LONG).show()
                binding.urlEditText.text?.clear()
                resetUI()
                // Optional: open it immediately
                openSavedVideo(savedUri)
            }
        } catch (e: Exception) {
            uiFail("Download error: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private fun resetUI() {
        binding.progressBar.visibility = View.GONE
        binding.downloadButton.isEnabled = true
    }
}
