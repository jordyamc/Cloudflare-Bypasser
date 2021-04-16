package knf.tools.bypass

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.Fuel
import knf.kuma.uagen.randomUA
import kotlinx.android.synthetic.main.lay_web.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BypassActivity : AppCompatActivity() {

    private val url by lazy { intent.getStringExtra("url") ?: "about:blank" }
    private val showReload by lazy { intent.getBooleanExtra("showReload", false) }
    private val useFocus by lazy { intent.getBooleanExtra("useFocus", false) }
    private val reloadCountdown = Handler(Looper.getMainLooper())
    private val reloadRun = Runnable {
        lifecycleScope.launch(Dispatchers.Main) {
            forceReload()
        }
    }
    private var tryCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lay_web)
        if (!showReload)
            reload.hide()
        else if (useFocus)
            reload.requestFocus()
        webview.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setAppCacheEnabled(false)
        }
        webview.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.url?.toString()?.let { url ->
                    if (url.matches(".*\\?__cf_chl_\\w+_tk__=.*".toRegex())) {
                        lifecycleScope.launch(Dispatchers.Main){
                            delay(3000)
                            Fuel.get(this@BypassActivity.url)
                                .header("User-Agent", webview.settings.userAgentString)
                                .header("Cookie", currentCookies())
                                .response { _, response, _ ->
                                    Log.e("Test UA bypass", "Response code: ${response.statusCode}")
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        if (response.statusCode == 200) {
                                            setResult(Activity.RESULT_CANCELED, Intent().apply {
                                                putExtra(
                                                    "user_agent",
                                                    webview.settings.userAgentString
                                                )
                                                putExtra("cookies", currentCookies())
                                            })
                                            reloadCountdown.removeCallbacks(reloadRun)
                                            this@BypassActivity.finish()
                                        }
                                    }
                                }
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
                    Fuel.get(this@BypassActivity.url)
                        .header("User-Agent", webview.settings.userAgentString)
                        .header("Cookie", cookies)
                        .response { _, response, _ ->
                            Log.e("Test UA bypass", "Response code: ${response.statusCode}")
                            lifecycleScope.launch(Dispatchers.Main) {
                                if (response.statusCode == 200) {
                                    setResult(Activity.RESULT_OK, Intent().apply {
                                        putExtra(
                                            "user_agent",
                                            webview.settings.userAgentString
                                        )
                                        putExtra("cookies", cookies)
                                    })
                                    reloadCountdown.removeCallbacks(reloadRun)
                                    this@BypassActivity.finish()
                                } else {
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
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    tryCount++
                    Log.e("Reload","Tries: $tryCount")
                    if (tryCount >= 3) {
                        tryCount = 0
                        webview.settings.userAgentString = randomUA()
                    }
                    view?.loadUrl(url)
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
        //clearCookies()
        webview.settings.userAgentString = randomUA()
        webview.loadUrl(url)
        reload.setOnClickListener {
            forceReload()
        }
    }

    private fun forceReload() {
        tryCount = 0
        webview.settings.userAgentString = randomUA()
        webview.loadUrl(url)
    }

    private fun currentCookies() = try {
        CookieManager.getInstance().getCookie(url)!!
    } catch (e: Exception) {
        e.printStackTrace()
        "Null"
    }

    private fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
    }

    override fun onBackPressed() {
        if (useFocus && webview.hasFocus()) {
            reload.requestFocus()
            return
        }
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
    useFocus: Boolean = false
) {
    startActivityForResult(Intent(this, BypassActivity::class.java).apply {
        putExtra("url", url)
        putExtra("showReload", showReload)
        putExtra("useFocus", useFocus)
    }, code)
}

fun Fragment.startBypass(code: Int, url: String, showReload: Boolean, useFocus: Boolean = false) {
    startActivityForResult(Intent(requireContext(), BypassActivity::class.java).apply {
        putExtra("url", url)
        putExtra("showReload", showReload)
        putExtra("useFocus", useFocus)
    }, code)
}

fun startBypass(
    activity: Activity,
    code: Int,
    url: String,
    showReload: Boolean,
    useFocus: Boolean = false
) {
    activity.startActivityForResult(Intent(activity, BypassActivity::class.java).apply {
        putExtra("url", url)
        putExtra("showReload", showReload)
        putExtra("useFocus", useFocus)
    }, code)
}
