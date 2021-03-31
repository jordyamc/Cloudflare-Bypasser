package knf.tools.bypass

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.github.kittinunf.fuel.Fuel
import knf.kuma.uagen.randomUA
import kotlinx.android.synthetic.main.lay_web.*

class BypassActivity: AppCompatActivity() {

    private val url by lazy { intent.getStringExtra("url")?:"about:blank" }
    private val showReload by lazy { intent.getBooleanExtra("showReload",false) }
    private var tryCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lay_web)
        if (!showReload)
            reload.hide()
        else
            reload.requestFocus()
        webview.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setAppCacheEnabled(false)
        }
        webview.webViewClient = object : WebViewClient(){

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                request?.url?.toString()?.let {
                    if (!it.contains("captcha"))
                        return null
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    Log.e("Finish",it)
                    val cookies = currentCookies()
                    Log.e("Cookies",cookies)
                    if (cookies.contains("cf_clearance")){
                        setResult(Activity.RESULT_OK,Intent().apply {
                            putExtra("user_agent",webview.settings.userAgentString)
                            putExtra("cookies",cookies)
                        })
                        finish()
                    }else{
                       /* Handler(Looper.getMainLooper()).postDelayed({

                        },2000)*/
                        Fuel.get(this@BypassActivity.url).header("User-Agent",webview.settings.userAgentString)
                                .response { _, response, _ ->
                                    Log.e("Test UA bypass","Response code: ${response.statusCode}")
                                    if (response.statusCode == 200){
                                        runOnUiThread {
                                            setResult(Activity.RESULT_CANCELED,Intent().apply {
                                                putExtra("user_agent",webview.settings.userAgentString)
                                            })
                                            this@BypassActivity.finish()
                                        }
                                    }
                                }
                    }
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                request?.url?.toString()?.let {
                    tryCount++
                    if (tryCount >= 3){
                        tryCount = 0
                        webview.settings.userAgentString = randomUA()
                    }
                    view?.loadUrl(it)
                }
                return false
            }
        }
        clearCookies()
        webview.settings.userAgentString = randomUA()
        webview.loadUrl(url)
        reload.setOnClickListener {
            tryCount = 0
            webview.settings.userAgentString = randomUA()
            webview.loadUrl(url)
        }
    }

    private fun currentCookies() = try {
        CookieManager.getInstance().getCookie(url)!!
    }catch (e:Exception){
        e.printStackTrace()
        "Null"
    }

    private fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED,Intent().apply {
            putExtra("user_agent",webview.settings.userAgentString)
        })
        super.onBackPressed()
    }

    fun String.containsAny(vararg terms: String): Boolean{
        terms.forEach {
            if (contains(it))
                return true
        }
        return false
    }
}

fun AppCompatActivity.startBypass(code: Int,url:String, showReload: Boolean){
    startActivityForResult(Intent(this,BypassActivity::class.java).apply {
        putExtra("url",url)
        putExtra("showReload",showReload)
    },code)
}

fun Fragment.startBypass(code: Int,url:String, showReload: Boolean){
    startActivityForResult(Intent(requireContext(),BypassActivity::class.java).apply {
        putExtra("url",url)
        putExtra("showReload",showReload)
    },code)
}

fun startBypass(activity: Activity,code: Int,url:String, showReload: Boolean){
    activity.startActivityForResult(Intent(activity,BypassActivity::class.java).apply {
        putExtra("url",url)
        putExtra("showReload",showReload)
    },code)
}
