package com.octetproof.sample

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import java.util.concurrent.Executors

/**
 * Cell · Triangulation — the "police triangulation" cinematic, hosted in a
 * dedicated Activity (a plain view hierarchy composites the WebView correctly;
 * a Compose `AndroidView` does not — see [OrbitTheaterActivity]).
 *
 * The scene (a Leaflet map in assets/cell/) is fed the observed cells that a
 * [CellAccumulator] gathers across scans (identity + signal from
 * `TelephonyManager`, positions resolved via OpenCelliD when a key is set). Cells
 * the DB knows are drawn at their real coordinates and badged REAL; signal-only
 * neighbours are placed at estimated bearings and badged "estimated"; and — only
 * when the user opts in — synthetic DEMO towers top up a scene with too few real
 * cells to triangulate. Either way it is observed, not integrity-proven — a cell
 * signal is not a location proof.
 */
class CellTheaterActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var monitor: SensorMonitor
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var ready = false

    private val key = BuildConfig.OPENCELLID_API_KEY
    private val acc by lazy { CellAccumulator(io, key) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = SensorMonitor(this)
        web = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(object {
                @JavascriptInterface fun close() { runOnUiThread { finish() } }
            }, "AndroidBridge")
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    Log.i("CellWeb", "${cm.message()} @${cm.lineNumber()}"); return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { ready = true }
            }
            loadUrl("file:///android_asset/cell/index.html")
        }
        setContentView(web)
        monitor.start()
        handler.post(pushLoop)
    }

    private val pushLoop = object : Runnable {
        override fun run() {
            if (ready) {
                monitor.refreshCellular()
                monitor.coordinate?.let { (lat, lon) ->
                    web.evaluateJavascript("window.setFix($lat,$lon,${monitor.horizontalAccuracyM ?: 0f})", null)
                }
                // Union this scan into the running set, then push the accumulated
                // towers. The scene decides when there are enough to triangulate.
                acc.observe(monitor.cellTowers)
                web.evaluateJavascript("window.setTowers('${acc.towersJson()}')", null)
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        io.shutdownNow()
        monitor.stop()
        web.destroy()
        super.onDestroy()
    }
}
