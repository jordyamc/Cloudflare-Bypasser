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
import knf.tools.bypass.R
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ck_show_reload.isChecked = prefs.getBoolean("showReload", false)
        ck_skip_captcha.isChecked = prefs.getBoolean("skipCaptcha", false)
        ck_clear_cookies.isChecked = prefs.getBoolean("clearCookies", false)
        ck_use_dialog.isChecked = prefs.getBoolean("useDialog", false)
        ck_show_reload.isEnabled = !ck_use_dialog.isChecked
        toggle_dialog_type.isEnabled = ck_use_dialog.isChecked
        var selectedDialog = prefs.getInt("dialogStyle",0)
        toggle_dialog_type.check(if (selectedDialog == 1) R.id.dialog_default else R.id.dialog_sheet)
        toggle_dialog_type.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked){
                selectedDialog = if (checkedId == R.id.dialog_default) 1 else 0
                prefs.edit {
                    putInt("dialogStyle", selectedDialog)
                }
            }
        }
        ck_show_reload.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean("showReload",isChecked)
            }
        }
        ck_skip_captcha.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean("skipCaptcha",isChecked)
            }
        }
        ck_clear_cookies.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean("clearCookies",isChecked)
            }
        }
        ck_use_dialog.setOnCheckedChangeListener { _, isChecked ->
            toggle_dialog_type.isEnabled = isChecked
            ck_show_reload.isEnabled = !isChecked
            prefs.edit {
                putBoolean("useDialog",isChecked)
            }
        }
        testButton.setOnClickListener {
            startBypass(666, "https://www3.animeflv.net/", lastUA = prefs.getString("ua",null),
                showReload = ck_show_reload.isChecked,
                maxTryCount = 3,
                reloadOnCaptcha = ck_skip_captcha.isChecked,
                clearCookiesAtStart = ck_clear_cookies.isChecked,
                useDialog = ck_use_dialog.isChecked,
                dialogStyle = selectedDialog)
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