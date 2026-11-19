package com.ford.escape2009.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                100
            )
        }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            setGeolocationEnabled(true)
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, true, false)
            }
        }

        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        webView.evaluateJavascript("showApps(false)", null)
    }

    inner class AndroidBridge(private val context: Context) {

        @JavascriptInterface
        fun getSystemTime(): String {
            val sdf = SimpleDateFormat("hh:mm a", Locale.US)
            return sdf.format(Date())
        }

        @JavascriptInterface
        fun launchApp(appKey: String) {
            runOnUiThread {
                when (appKey) {
                    "settings" -> startActivity(Intent(Settings.ACTION_SETTINGS))
                    "maps" -> openByPackagesOrKeyword(listOf("com.google.android.apps.maps"), listOf("maps"))
                    "waze" -> openByPackagesOrKeyword(listOf("com.waze"), listOf("waze"))
                    "spotify" -> openByPackagesOrKeyword(listOf("com.spotify.music"), listOf("spotify"))
                    "youtube" -> openByPackagesOrKeyword(listOf("com.google.android.youtube", "com.vanced.android.youtube"), listOf("youtube"))
                    "radio" -> openByPackagesOrKeyword(emptyList(), listOf("radio", "fm"))
                    "bt" -> openByPackagesOrKeyword(emptyList(), listOf("bt", "bluetooth", "avrcp"))
                    "phone" -> openByPackagesOrKeyword(emptyList(), listOf("dialer", "phone", "btphone"))
                    "music" -> openByPackagesOrKeyword(emptyList(), listOf("music", "audioplayer", "media"))
                    "camera" -> openByPackagesOrKeyword(emptyList(), listOf("aux", "camera", "avin", "dvr", "reverse"))
                    else -> openByPackagesOrKeyword(emptyList(), listOf(appKey))
                }
            }
        }

        @JavascriptInterface
        fun mediaControl(command: String) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val keyCode = when (command) {
                "playpause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> return
            }
            val eventTime = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
        }

        private fun openByPackagesOrKeyword(exactPackages: List<String>, keywords: List<String>) {
            val pm = context.packageManager
            for (pkg in exactPackages) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    context.startActivity(intent)
                    return
                }
            }
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val pkgLower = app.packageName.lowercase()
                val labelLower = pm.getApplicationLabel(app).toString().lowercase()
                if (pkgLower == context.packageName) continue
                for (kw in keywords) {
                    if (pkgLower.contains(kw) || labelLower.contains(kw)) {
                        val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                            return
                        }
                    }
                }
            }
            Toast.makeText(context, "App no instalada en el estéreo", Toast.LENGTH_SHORT).show()
        }
    }
}