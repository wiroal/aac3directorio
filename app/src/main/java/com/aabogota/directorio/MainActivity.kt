package com.aabogota.directorio

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.aabogota.directorio.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding

    // Página del directorio (solo esta)
    private val HOME_URL = "https://www.aabogota.com/p/reuniones-virtuales-grupos-aa-bogota.html"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val wv = binding.webview
        val swipe = binding.swipe
        val progress = binding.progress

        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            setSupportMultipleWindows(false)
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv.webChromeClient = object : WebChromeClient() {}

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                swipe.isRefreshing = false
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                swipe.isRefreshing = false
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val host = request.url.host ?: ""

                // Teléfonos, mail, WhatsApp, Maps, Zoom/Meet/Youtube -> abrir fuera de la app
                val specialSchemes = listOf("tel:", "mailto:", "sms:", "geo:", "intent:")
                if (specialSchemes.any { url.startsWith(it) }) { openExternal(url); return true }

                val externalHosts = listOf(
                    "maps.app.goo.gl", "www.google.com", "maps.google.com",
                    "wa.me", "api.whatsapp.com",
                    "zoom.us", "meet.google.com", "us02web.zoom.us",
                    "www.youtube.com", "youtu.be"
                )
                if (externalHosts.any { host.contains(it, ignoreCase = true) }) { openExternal(url); return true }

                // Solo permitimos navegar dentro del mismo dominio aabogota.com
                val allowed = "www.aabogota.com"
                if (!host.equals(allowed, ignoreCase = true)) { openExternal(url); return true }

                return false // cargar en WebView
            }
        }

        swipe.setOnRefreshListener { wv.reload() }

        onBackPressedDispatcher.addCallback(this) {
            if (wv.canGoBack()) wv.goBack() else finish()
        }

        if (savedInstanceState == null) {
            wv.loadUrl(HOME_URL)
        } else {
            wv.restoreState(savedInstanceState)
        }
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) { }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webview.saveState(outState)
    }
}