package knf.tools.bypass

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.webkit.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.httpGet
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import knf.kuma.uagen.randomUA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BypassActivity : AppCompatActivity() {

    private val layBinding by lazy { layoutInflater.inflate(R.layout.lay_web, null) }
    private val reload by lazy<ExtendedFloatingActionButton> { layBinding.findViewById(R.id.reload) }
    private val layBindingShort by lazy { layoutInflater.inflate(R.layout.lay_web_short, null) }
    private val url by lazy { intent.getStringExtra("url") ?: "about:blank" }
    private val showReload by lazy { intent.getBooleanExtra("showReload", false) }
    private val useDialog by lazy { intent.getBooleanExtra("useDialog", false) }
    private val useFocus by lazy { intent.getBooleanExtra("useFocus", false) }
    private val maxTryCount by lazy { intent.getIntExtra("maxTryCount", 3) }
    private val reloadOnCaptcha by lazy { intent.getBooleanExtra("reloadOnCaptcha", false) }
    private val clearCookiesAtStart by lazy { intent.getBooleanExtra("clearCookiesAtStart", false) }
    private val dialogStyle by lazy { intent.getIntExtra("dialogStyle", 0) }
    private val reloadCountdown = Handler(Looper.getMainLooper())
    private var dialog: AppCompatDialog? = null
    private lateinit var webview: WebView
    private var tryCount = 0
    private val reloadRun = Runnable {
        lifecycleScope.launch(Dispatchers.Main) {
            forceReload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (useDialog)
            setTheme(R.style.Theme_Transparent)
        super.onCreate(savedInstanceState)
        if (!useDialog) {
            setContentView(layBinding)
            if (showReload){
                if (useFocus)
                    reload.requestFocus()
                reload.setOnClickListener {
                    forceReload()
                }
            }else{
                reload.hide()
            }
            webview = layBinding.findViewById(R.id.webview)
        } else {
            title = " "
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            webview = layBindingShort.findViewById(R.id.webview)
            if (dialogStyle == 0) {
                dialog = BottomSheetDialog(this@BypassActivity).apply {
                    setContentView(layBindingShort)
                    setCanceledOnTouchOutside(false)
                    behavior.apply {
                        expandedOffset = 400
                        isDraggable = false
                    }
                    show()
                }
            } else {
                dialog = AlertDialog.Builder(this@BypassActivity).apply {
                    setContentView(layBindingShort)
                }.create().also {
                    it.setCanceledOnTouchOutside(false)
                    it.show()
                }
            }
        }
        webview.settings.apply {
            javaScriptEnabled = true
        }
        val startTime = System.currentTimeMillis()
        webview.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.url?.toString()?.let { url ->
                    if (url.contains("captcha") && reloadOnCaptcha) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            forceReload()
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                reloadCountdown.removeCallbacks(reloadRun)
                url?.let {
                    Log.e("Finish", it)
                    val cookies = currentCookies()
                    Log.e("Cookies", cookies)
                    val requestHeaders = mapOf(
                        "User-Agent" to webview.settings.userAgentString,
                        "Cookie" to cookies
                    )
                    lifecycleScope.launch {
                        val (_,response) = withContext(Dispatchers.IO) { this@BypassActivity.url.httpGet().header(requestHeaders).responseString() }
                        Log.e("Test UA bypass", "Response code: ${response.statusCode}")
                        if (response.statusCode == 200){
                            setResult(Activity.RESULT_OK, Intent().apply {
                                putExtra(
                                    "user_agent",
                                    webview.settings.userAgentString
                                )
                                putExtra("cookies", cookies)
                                putExtra("finishTime", System.currentTimeMillis() - startTime)
                            })
                            reloadCountdown.removeCallbacks(reloadRun)
                            dialog?.dismiss()
                            this@BypassActivity.finish()
                        }else{
                            if (view?.title?.containsAny(
                                    "Just a moment...",
                                    "Verifica que no eres un bot"
                                ) == false
                            ) {
                                Log.e("Bypass", "Reload")
                                reloadCountdown.postDelayed(reloadRun, 6000)
                                forceReload()
                            }
                        }
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    tryCount++
                    Log.e("Reload", "Tries: $tryCount")
                    if (tryCount > maxTryCount) {
                        tryCount = 0
                        webview.settings.userAgentString = randomUA()
                        Log.e("Reload", "Using new UA: ${webview.settings.userAgentString}")
                    }
                    ///view?.loadUrl(url)
                }
                return super.shouldOverrideUrlLoading(view, url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return shouldOverrideUrlLoading(view, request?.url?.toString())
            }
        }
        if (clearCookiesAtStart)
            clearCookies()
        webview.settings.userAgentString = randomUA()
        webview.loadUrl(url)
    }

    private fun forceReload() {
        tryCount = 0
        webview.settings.userAgentString = randomUA()
        webview.loadUrl(url)
    }

    private fun currentCookies(current: String = url) = try {
        CookieManager.getInstance().getCookie(current)!!
    } catch (e: Exception) {
        e.printStackTrace()
        "Null"
    }

    private fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    override fun onBackPressed() {
        if (useFocus && webview.hasFocus() && showReload) {
            reload.requestFocus()
            return
        }
        dialog?.dismiss()
        setResult(Activity.RESULT_CANCELED, Intent().apply {
            putExtra("user_agent", webview.settings.userAgentString)
            putExtra("cookies", currentCookies())
        })
        super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (useFocus)
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> webview.requestFocus()
            }
        return super.onKeyDown(keyCode, event)
    }
}

fun String.containsAny(vararg terms: String): Boolean {
    for (term in terms) {
        if (this.contains(term, true))
            return true
    }
    return false
}

fun AppCompatActivity.startBypass(
    code: Int,
    url: String,
    showReload: Boolean,
    useFocus: Boolean = false,
    maxTryCount: Int = 3,
    reloadOnCaptcha: Boolean = false,
    clearCookiesAtStart: Boolean = false,
    useDialog: Boolean = false,
    dialogStyle: Int = 0
) {
    startActivityForResult(Intent(this, BypassActivity::class.java).apply {
        putExtra("url", url)
        putExtra("showReload", showReload)
        putExtra("useFocus", useFocus)
        putExtra("maxTryCount", maxTryCount)
        putExtra("reloadOnCaptcha", reloadOnCaptcha)
        putExtra("clearCookiesAtStart", clearCookiesAtStart)
        putExtra("useDialog", useDialog)
        putExtra("dialogStyle", dialogStyle)
    }, code)
}

fun Fragment.startBypass(
    code: Int,
    url: String,
    showReload: Boolean,
    useFocus: Boolean = false,
    maxTryCount: Int = 3,
    reloadOnCaptcha: Boolean = false,
    clearCookiesAtStart: Boolean = false,
    useDialog: Boolean = false,
    dialogStyle: Int = 0
) {
    startActivityForResult(Intent(requireContext(), BypassActivity::class.java).apply {
        putExtra("url", url)
        putExtra("showReload", showReload)
        putExtra("useFocus", useFocus)
        putExtra("maxTryCount", maxTryCount)
        putExtra("reloadOnCaptcha", reloadOnCaptcha)
        putExtra("clearCookiesAtStart", clearCookiesAtStart)
        putExtra("useDialog", useDialog)
        putExtra("dialogStyle", dialogStyle)
    }, code)
}

fun startBypass(
    activity: Activity,
    code: Int,
    url: String,
    showReload: Boolean,
    useFocus: Boolean = false,
    maxTryCount: Int = 3,
    reloadOnCaptcha: Boolean = false,
    clearCookiesAtStart: Boolean = false,
    useDialog: Boolean = false,
    dialogStyle: Int = 0
) {
    activity.startActivityForResult(Intent(activity, BypassActivity::class.java).apply {
        putExtra("url", url)
        putExtra("showReload", showReload)
        putExtra("useFocus", useFocus)
        putExtra("maxTryCount", maxTryCount)
        putExtra("reloadOnCaptcha", reloadOnCaptcha)
        putExtra("clearCookiesAtStart", clearCookiesAtStart)
        putExtra("useDialog", useDialog)
        putExtra("dialogStyle", dialogStyle)
    }, code)
}
