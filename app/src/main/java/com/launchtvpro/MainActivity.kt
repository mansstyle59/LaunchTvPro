package com.launchtvpro

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.core.view.isVisible

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "LaunchTVPro"
        private const val DEFAULT_HOME = "https://www.google.com"
        private const val PREFS_NAME = "LaunchTVProPrefs"
        private const val PREF_LAST_URL = "last_url"
    }

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var overlayMessage: TextView

    private var tvNavScript: String = ""
    private var isInputMode = false
    private var toolbarVisible = true
    private val handler = Handler(Looper.getMainLooper())
    private var toolbarHideRunnable: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen immersive
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        setContentView(R.layout.activity_main)
        initViews()
        loadTVNavScript()
        setupWebView()
        setupToolbar()
        loadLastUrl()
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        progressBar = findViewById(R.id.progressBar)
        toolbar = findViewById(R.id.toolbar)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnHome = findViewById(R.id.btnHome)
        btnMenu = findViewById(R.id.btnMenu)
        overlayMessage = findViewById(R.id.overlayMessage)
    }

    private fun loadTVNavScript() {
        try {
            tvNavScript = assets.open("tv_navigation.js").bufferedReader().readText()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement script TVNav", e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                userAgentString = "Mozilla/5.0 (Linux; Android 11; Chromecast with Google TV) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Mobile Safari/537.36"
                allowContentAccess = true
                allowFileAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            // JavaScript Bridge
            addJavascriptInterface(TVBridge(), "TVBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar.isVisible = true
                    urlBar.setText(url)
                    isInputMode = false
                    Log.d(TAG, "Chargement: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar.isVisible = false
                    url?.let {
                        urlBar.setText(it)
                        saveLastUrl(it)
                    }
                    injectTVNav()
                    btnBack.isEnabled = view?.canGoBack() == true
                    btnForward.isEnabled = view?.canGoForward() == true
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false // Laisse la WebView gérer toutes les URLs
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Log.e(TAG, "Erreur WebView: ${error?.description}")
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    if (newProgress == 100) {
                        progressBar.isVisible = false
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    title?.let { Log.d(TAG, "Titre: $it") }
                }
            }

            // Empêche le scroll système d'interférer
            setOnTouchListener { _, event ->
                performClick()
                false
            }
        }
    }

    private fun injectTVNav() {
        if (tvNavScript.isNotEmpty()) {
            webView.evaluateJavascript(tvNavScript, null)
        }
    }

    private fun setupToolbar() {
        btnBack.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        btnForward.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        btnRefresh.setOnClickListener {
            webView.reload()
        }
        btnHome.setOnClickListener {
            webView.loadUrl(DEFAULT_HOME)
        }
        btnMenu.setOnClickListener {
            toggleToolbar()
        }

        urlBar.setOnEditorActionListener { _, _, _ ->
            navigateToUrl(urlBar.text.toString())
            hideKeyboard()
            webView.requestFocus()
            true
        }

        // Cacher la toolbar après 4 secondes
        scheduleToolbarHide()
    }

    private fun toggleToolbar() {
        if (toolbarVisible) {
            toolbar.animate().translationY(-toolbar.height.toFloat()).setDuration(250).start()
            toolbarVisible = false
        } else {
            toolbar.animate().translationY(0f).setDuration(250).start()
            toolbarVisible = true
            scheduleToolbarHide()
        }
    }

    private fun scheduleToolbarHide() {
        toolbarHideRunnable?.let { handler.removeCallbacks(it) }
        toolbarHideRunnable = Runnable {
            if (toolbarVisible) toggleToolbar()
        }
        handler.postDelayed(toolbarHideRunnable!!, 4000)
    }

    private fun showToolbar() {
        if (!toolbarVisible) {
            toolbar.animate().translationY(0f).setDuration(200).start()
            toolbarVisible = true
        }
        scheduleToolbarHide()
    }

    private fun navigateToUrl(input: String) {
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(input, "UTF-8")}"
        }
        webView.loadUrl(url)
    }

    private fun loadLastUrl() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUrl = prefs.getString(PREF_LAST_URL, DEFAULT_HOME) ?: DEFAULT_HOME
        webView.loadUrl(lastUrl)
    }

    private fun saveLastUrl(url: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_LAST_URL, url).apply()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    private fun showOverlayMessage(msg: String, durationMs: Long = 2000) {
        runOnUiThread {
            overlayMessage.text = msg
            overlayMessage.isVisible = true
            handler.postDelayed({ overlayMessage.isVisible = false }, durationMs)
        }
    }

    /* ─── Gestion des touches télécommande ─── */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "KeyDown: $keyCode")

        when (keyCode) {
            // Navigation D-pad → délégué au JavaScript TVNav
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!isInputMode) {
                    webView.evaluateJavascript("window.TVNav && window.TVNav.navigate('up')", null)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!isInputMode) {
                    webView.evaluateJavascript("window.TVNav && window.TVNav.navigate('down')", null)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isInputMode) {
                    webView.evaluateJavascript("window.TVNav && window.TVNav.navigate('left')", null)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isInputMode) {
                    webView.evaluateJavascript("window.TVNav && window.TVNav.navigate('right')", null)
                    return true
                }
            }

            // OK / Entrée → activer l'élément focalisé
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!isInputMode) {
                    webView.evaluateJavascript("window.TVNav && window.TVNav.activate()", null)
                    return true
                }
            }

            // Bouton BACK → retour ou réduction WebView
            KeyEvent.KEYCODE_BACK -> {
                if (isInputMode) {
                    webView.evaluateJavascript("window.TVNav && window.TVNav.exitInputMode()", null)
                    return true
                }
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
            }

            // Menu → afficher/cacher la toolbar
            KeyEvent.KEYCODE_MENU -> {
                toggleToolbar()
                return true
            }

            // Bouton rouge → recharger
            KeyEvent.KEYCODE_PROG_RED -> {
                webView.reload()
                showOverlayMessage("🔄 Rechargement...")
                return true
            }

            // Bouton vert → retour
            KeyEvent.KEYCODE_PROG_GREEN -> {
                if (webView.canGoBack()) webView.goBack()
                return true
            }

            // Bouton jaune → URL bar
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                showToolbar()
                urlBar.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(urlBar, InputMethodManager.SHOW_IMPLICIT)
                return true
            }

            // Bouton bleu → domicile
            KeyEvent.KEYCODE_PROG_BLUE -> {
                webView.loadUrl(DEFAULT_HOME)
                return true
            }

            // Page Up/Down → scroll
            KeyEvent.KEYCODE_PAGE_UP -> {
                webView.evaluateJavascript("window.TVNav && window.TVNav.scrollUp()", null)
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                webView.evaluateJavascript("window.TVNav && window.TVNav.scrollDown()", null)
                return true
            }

            // Chaîne +/- → navigation dans l'historique
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (webView.canGoForward()) {
                    webView.goForward()
                    return true
                }
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER -> true
            else -> super.onKeyUp(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        // Réinjecter le script au retour
        webView.evaluateJavascript("if (!window.__TVNavLoaded) { ${tvNavScript} }", null)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
        handler.removeCallbacksAndMessages(null)
    }

    /* ─── Bridge JavaScript ↔ Android ─── */
    inner class TVBridge {
        @JavascriptInterface
        fun onFocusChanged(tagName: String, label: String) {
            Log.d(TAG, "Focus: $tagName → $label")
        }

        @JavascriptInterface
        fun onInputModeChanged(entering: Boolean) {
            isInputMode = entering
            if (entering) {
                runOnUiThread {
                    showOverlayMessage("✏️ Mode saisie texte\nAppuyez sur OK pour terminer", 3000)
                }
            }
        }

        @JavascriptInterface
        fun navigateTo(url: String) {
            runOnUiThread { navigateToUrl(url) }
        }

        @JavascriptInterface
        fun showMessage(msg: String) {
            showOverlayMessage(msg)
        }
    }
}
