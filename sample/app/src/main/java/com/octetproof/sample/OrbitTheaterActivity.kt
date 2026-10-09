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

/**
 * GNSS · Orbits (3D) — the WebGL orbit globe, hosted in a dedicated Activity
 * rather than a Compose `AndroidView`. WebGL renders on a hardware surface that a
 * Compose `AndroidView` fails to composite (the scene draws on the GPU but never
 * reaches the screen — verified black despite a healthy Mali-G715 WebGL2 context);
 * a plain Activity view hierarchy composites it correctly.
 *
 * The scene (three.js + satellite.js + bundled Celestrak TLEs) lives in
 * assets/orbit/. This Activity feeds it the live GnssStatus + device position.
 */
class OrbitTheaterActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var monitor: SensorMonitor
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = SensorMonitor(this)
        web = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(object {
                @JavascriptInterface
                fun getTle(): String = runCatching {
                    assets.open("orbit/gnss.tle").bufferedReader().use { it.readText() }
                }.getOrDefault("")

                @JavascriptInterface
                fun getLand(): String = runCatching {
                    assets.open("orbit/land.geojson").bufferedReader().use { it.readText() }
                }.getOrDefault("")

                @JavascriptInterface
                fun close() { runOnUiThread { finish() } }
            }, "AndroidBridge")
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    Log.i("OrbitWeb", "${cm.message()} @${cm.lineNumber()}")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { ready = true }
            }
            loadUrl("file:///android_asset/orbit/index.html")
        }
        setContentView(web)
        monitor.start()
        handler.post(pushLoop)
    }

    /** Push the device position + live satellite state into the scene every 2s. */
    private val pushLoop = object : Runnable {
        override fun run() {
            if (ready) {
                monitor.coordinate?.let { (lat, lon) ->
                    web.evaluateJavascript("window.setObserver($lat,$lon)", null)
                }
                val prns = monitor.satellites.filter { it.usedInFix }
                    .joinToString(",") { "\"${it.constellation}${it.svid}\"" }
                val json = "{\"inView\":${monitor.satellites.size}," +
                    "\"usedInFix\":${monitor.satellitesUsedInFix},\"prns\":[$prns]}"
                web.evaluateJavascript("window.setLive('$json')", null)
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        monitor.stop()
        web.destroy()
        super.onDestroy()
    }
}
