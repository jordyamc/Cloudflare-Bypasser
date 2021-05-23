package knf.tools.bypass

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        testButton.setOnClickListener {
            startBypass(666, "https://www3.animeflv.net/", lastUA = prefs.getString("ua",null),showReload = false, maxTryCount = 3, reloadOnCaptcha = true, clearCookiesAtStart = true, useDialog = true, dialogStyle = 1)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 666 && resultCode == Activity.RESULT_OK) {
            prefs.edit {
                putString("ua",data?.getStringExtra("user_agent"))
            }
            Log.e(
                "Bypass created",
                "\nCookies: ${data?.getStringExtra("cookies")}\nUser agent: ${data?.getStringExtra("user_agent")}".also {
                    Toast.makeText(this,it,Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}