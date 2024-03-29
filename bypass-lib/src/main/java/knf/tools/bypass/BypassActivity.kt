package knf.tools.bypass

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.httpGet
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import knf.kuma.uagen.randomUA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class BypassActivity : AppCompatActivity() {

    private val layBinding by lazy { layoutInflater.inflate(R.layout.lay_web, null) }
    private val reload by lazy<ExtendedFloatingActionButton> { layBinding.findViewById(R.id.reload) }
    private val layBindingShort by lazy { layoutInflater.inflate(R.layout.lay_web_short, null) }
    private val url by lazy { intent.getStringExtra("url") ?: "about:blank" }
    private val lastUA by lazy { intent.getStringExtra("lastUA") ?: System.getProperty("http.agent") }
    private val showReload by lazy { intent.getBooleanExtra("showReload", false) }
    private val displayType by lazy { intent.getIntExtra("displayType", 0) }
    private val useFocus by lazy { intent.getBooleanExtra("useFocus", false) }
    private val timeout by lazy { intent.getLongExtra("timeout", 6000) }
    private val maxTryCount by lazy { intent.getIntExtra("maxTryCount", 3) }
    private val useLatestUA by lazy { intent.getBooleanExtra("useLatestUA", false) }
    private var reloadOnCaptcha by lazyMutable { intent.getBooleanExtra("reloadOnCaptcha", false) }
    private val waitCaptcha by lazy { intent.getBooleanExtra("waitCaptcha", false) }
    private val clearCookiesAtStart by lazy { intent.getBooleanExtra("clearCookiesAtStart", false) }
    private val dialogStyle by lazy { intent.getIntExtra("dialogStyle", 0) }
    private val reloadCountdown = Handler(Looper.getMainLooper())
    private var dialog: AppCompatDialog? = null
    private lateinit var webview: WebView
    private val latestUA = mutableListOf<String>()
    private var tryCount = 0
    private val reloadRun = Runnable {
        lifecycleScope.launch(Dispatchers.Main) {
            forceReload(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (displayType == 0)
            setTheme(R.style.Theme_BypassTester)
        super.onCreate(savedInstanceState)
        if (displayType == DisplayType.ACTIVITY) {
            setContentView(layBinding)
            if (showReload) {
                if (useFocus)
                    reload.requestFocus()
                reload.setOnClickListener {
                    forceReload()
                }
            } else {
                reload.hide()
            }
            webview = layBinding.findViewById(R.id.webview)
        } else {
            setContentView(R.layout.activity_translucent)
            title = " "
            //window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            webview = layBindingShort.findViewById(R.id.webview)
            if (displayType == DisplayType.BACKGROUND || dialogStyle == 0) {
                dialog = BottomSheetDialog(this@BypassActivity).apply {
                    setContentView(layBindingShort)
                    setCanceledOnTouchOutside(false)
                    behavior.apply {
                        //expandedOffset = 400
                        if (displayType == DisplayType.BACKGROUND) {
                            peekHeight = 0
                            state = BottomSheetBehavior.STATE_COLLAPSED
                        } else {
                            state = BottomSheetBehavior.STATE_EXPANDED
                        }
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
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webview, true);
        webview.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        val startTime = System.currentTimeMillis()
        webview.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.url?.toString()?.let { url ->
                    //Log.e("Intercept", url)
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
                    Log.e("User Agent", webview.settings.userAgentString)
                    Log.e("Cookies", cookies)
                    val requestHeaders = mapOf(
                        "User-Agent" to webview.settings.userAgentString,
                        "Cookie" to cookies
                    )
                    lifecycleScope.launch {
                        val (_, response) = withContext(Dispatchers.IO) {
                            this@BypassActivity.url.httpGet().header(requestHeaders)
                                .responseString()
                        }
                        Log.e("Test UA bypass", "Response code: ${response.statusCode}")
                        if (response.statusCode == 200) {
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
                        } else if (waitCaptcha) {
                            Log.e("Bypass", "Waiting captcha")
                        } else {
                            if (view?.title?.containsAny(
                                    "Just a moment...",
                                    "Verifica que no eres un bot"
                                ) == false
                            ) {
                                Log.e("Bypass", "Reload")
                                if (timeout > 5000) {
                                    reloadCountdown.postDelayed(reloadRun, timeout)
                                }
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
                        webview.settings.userAgentString = createRandomUA()
                        clearCookies()
                        Log.e("Reload", "Using new identity: ${webview.settings.userAgentString}")
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
        webview.settings.userAgentString = lastUA
        lifecycleScope.launch {
            if (useLatestUA) {
                populateUA()
            }
            webview.loadUrl(url)
        }
    }

    private fun createRandomUA(): String {
        return if (useLatestUA) {
            latestUA.random()
        } else {
            randomUA()
        }
    }

    private suspend fun populateUA() {
        withContext(Dispatchers.IO) {
            val base = "https://www.whatismybrowser.com/guides/the-latest-user-agent"
            latestUA.add(System.getProperty("http.agent"))
            latestUA.add(WebSettings.getDefaultUserAgent(this@BypassActivity))
            try {
                Jsoup.connect("$base/safari").get().select("span.code:contains(Macintosh)").first()?.text()?.ifBlank { null }?.also { latestUA.add(it) }
            } catch (e: Exception) {
                //
            }
            try {
                Jsoup.connect("$base/windows").get().select("span.code").mapNotNull { it.text().ifBlank { null } }.forEach {
                    latestUA.add(it)
                }
            } catch (e: Exception) {
                //
            }
        }
    }

    private fun forceReload(clearCookies: Boolean = false) {
        tryCount = 0
        webview.settings.userAgentString = createRandomUA()
        if (clearCookies) {
            clearCookies()
        }
        webview.loadUrl(url)
    }

    private fun currentCookies(current: String = url) = try {
        CookieManager.getInstance().getCookie(current)!!
    } catch (e: Exception) {
        //e.printStackTrace()
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
        finish()
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

data class Result(val userAgent: String, val cookies: String)

data class Request(
    val url: String,
    val lastUA: String? = System.getProperty("http.agent"),
    val showReload: Boolean,
    val useFocus: Boolean = false,
    val timeout: Long = 6000,
    val maxTryCount: Int = 3,
    val useLatestUA: Boolean = false,
    val reloadOnCaptcha: Boolean = false,
    val waitCaptcha: Boolean = false,
    val clearCookiesAtStart: Boolean = false,
    val displayType: Int = 0,
    val dialogStyle: Int = 0
)

class BypassContract : ActivityResultContract<Request, Result>() {
    override fun createIntent(context: Context, input: Request): Intent {
        val intent = Intent(context, BypassActivity::class.java)
        return with(input) {
            intent.apply {
                putExtra("url", url)
                putExtra("lastUA", lastUA)
                putExtra("showReload", showReload)
                putExtra("useFocus", useFocus)
                putExtra("timeout", timeout)
                putExtra("maxTryCount", maxTryCount)
                putExtra("useLatestUA", useLatestUA)
                putExtra("reloadOnCaptcha", reloadOnCaptcha)
                putExtra("waitCaptcha", waitCaptcha)
                putExtra("clearCookiesAtStart", clearCookiesAtStart)
                putExtra("displayType", displayType)
                putExtra("dialogStyle", dialogStyle)
            }
        } ?: intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        return Result(
            intent?.getStringExtra("user_agent") ?: randomUA(),
            intent?.getStringExtra("cookies") ?: ""
        )
    }
}


object DisplayType {
    const val ACTIVITY = 0
    const val DIALOG = 1
    const val BACKGROUND = 2
}


fun FragmentActivity.startBypass(
    code: Int,
    request: Request
) {
    startActivityForResult(Intent(this, BypassActivity::class.java).apply {
        putExtra("url", request.url)
        putExtra("lastUA", request.lastUA)
        putExtra("showReload", request.showReload)
        putExtra("useFocus", request.useFocus)
        putExtra("timeout", request.timeout)
        putExtra("maxTryCount", request.maxTryCount)
        putExtra("useLatestUA", request.useLatestUA)
        putExtra("reloadOnCaptcha", request.reloadOnCaptcha)
        putExtra("waitCaptcha", request.waitCaptcha)
        putExtra("clearCookiesAtStart", request.clearCookiesAtStart)
        putExtra("displayType", request.displayType)
        putExtra("dialogStyle", request.dialogStyle)
    }, code)
}

fun Fragment.startBypass(
    code: Int,
    request: Request
) {
    startActivityForResult(Intent(requireContext(), BypassActivity::class.java).apply {
        putExtra("url", request.url)
        putExtra("lastUA", request.lastUA)
        putExtra("showReload", request.showReload)
        putExtra("useFocus", request.useFocus)
        putExtra("timeout", request.timeout)
        putExtra("maxTryCount", request.maxTryCount)
        putExtra("useLatestUA", request.useLatestUA)
        putExtra("reloadOnCaptcha", request.reloadOnCaptcha)
        putExtra("waitCaptcha", request.waitCaptcha)
        putExtra("clearCookiesAtStart", request.clearCookiesAtStart)
        putExtra("displayType", request.displayType)
        putExtra("dialogStyle", request.dialogStyle)
    }, code)
}

class lazyMutable<T>(
    val initializer: () -> T,
) : ReadWriteProperty<Any?, T> {
    private val lazyValue by lazy { initializer() }
    private var newValue: T? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        newValue ?: lazyValue

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        newValue = value
    }
}
