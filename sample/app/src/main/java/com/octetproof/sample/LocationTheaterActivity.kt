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
 * octet-location — the combined demo: a satellite (GNSS) fix and a cell-tower
 * triangulation brought together on one canvas. Hosted in a dedicated Activity
 * (the WebView compositing lesson from [OrbitTheaterActivity]).
 *
 * The scene (assets/location/) starts on the 3D globe (reusing the orbit assets:
 * three.js + satellite.js + bundled TLEs), zooms in as the satellite fix narrows,
 * hands off to a Leaflet map, drops the observed cell towers and triangulates,
 * then shows the OS **fused** fix alongside the corroborated **octet-location**
 * fix — with high-level signal chips only, no SDK internals.
 *
 * Real signals: GNSS + fused fixes, satellites in view/used, observed cells (+
 * OpenCelliD positions when a key is configured — see [CellTheaterActivity]).
 */
class LocationTheaterActivity : ComponentActivity() {

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
                @JavascriptInterface fun getTle(): String = runCatching {
                    assets.open("orbit/gnss.tle").bufferedReader().use { it.readText() }
                }.getOrDefault("")
                @JavascriptInterface fun getLand(): String = runCatching {
                    assets.open("orbit/land.geojson").bufferedReader().use { it.readText() }
                }.getOrDefault("")
                @JavascriptInterface fun close() { runOnUiThread { finish() } }
            }, "AndroidBridge")
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    Log.i("LocWeb", "${cm.message()} @${cm.lineNumber()}"); return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { ready = true }
            }
            loadUrl("file:///android_asset/location/index.html")
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
                    web.evaluateJavascript("window.setGnss($lat,$lon,${monitor.horizontalAccuracyM ?: 0f})", null)
                }
                monitor.fusedCoordinate?.let { (lat, lon) ->
                    web.evaluateJavascript(
                        "window.setFused($lat,$lon,${monitor.fusedAccuracyM ?: 0f},'${monitor.fusedSource}')", null)
                }
                val used = monitor.satellites.count { it.usedInFix }
                web.evaluateJavascript("window.setLive(${monitor.satellites.size},$used)", null)
                // Same accumulate-across-scans + OpenCelliD path as the Cell theater.
                acc.observe(monitor.cellTowers)
                web.evaluateJavascript("window.setTowers('${acc.towersJson()}')", null)
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        io.shutdownNow(); monitor.stop(); web.destroy()
        super.onDestroy()
    }
}
