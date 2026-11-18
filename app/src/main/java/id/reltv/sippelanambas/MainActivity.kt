package id.reltv.sippelanambas

import android.app.Activity
import android.print.PrintManager
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.print.PrintAttributes
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.util.Base64
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingSaveBytes: ByteArray? = null
    private val fileChooserRequest = 4102
    private val createDocumentRequest = 4103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 91, 73)
        window.navigationBarColor = android.graphics.Color.rgb(7, 65, 55)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = true
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) =
                request?.url?.let { assetLoader.shouldInterceptRequest(it) }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    startActivityForResult(Intent.createChooser(intent, "Pilih foto pelabuhan"), fileChooserRequest)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }
        if (savedInstanceState == null) webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        else webView.restoreState(savedInstanceState)
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun printPage() {
            runOnUiThread {
                try {
                    val manager = getSystemService(PRINT_SERVICE) as PrintManager
                    val adapter = webView.createPrintDocumentAdapter("SIPPEL_ANAMBAS_Laporan")
                    manager.print("Laporan SIPPEL ANAMBAS", adapter,
                        PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build())
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Fitur cetak belum dapat dibuka: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun saveExcel(filename: String, encoded: String) {
            try {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                runOnUiThread {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.ms-excel")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            }
                            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                ?: throw IllegalStateException("Tidak dapat membuat file unduhan")
                            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                                ?: throw IllegalStateException("Tidak dapat menulis file")
                            Toast.makeText(this@MainActivity, "Excel tersimpan di folder Download", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Gagal menyimpan Excel: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        pendingSaveBytes = bytes
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/vnd.ms-excel"
                            putExtra(Intent.EXTRA_TITLE, filename)
                        }
                        startActivityForResult(intent, createDocumentRequest)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Data Excel tidak dapat diproses", Toast.LENGTH_LONG).show() }
            }
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserRequest) {
            val result = if (resultCode == RESULT_OK) {
                val clip = data?.clipData
                when {
                    clip != null -> Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    data?.data != null -> arrayOf(data.data!!)
                    else -> null
                }
            } else null
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        } else if (requestCode == createDocumentRequest) {
            if (resultCode == RESULT_OK && data?.data != null) {
                try {
                    contentResolver.openOutputStream(data.data!!)?.use { it.write(pendingSaveBytes ?: ByteArray(0)) }
                    Toast.makeText(this, "Excel berhasil disimpan", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal menyimpan Excel: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            pendingSaveBytes = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
