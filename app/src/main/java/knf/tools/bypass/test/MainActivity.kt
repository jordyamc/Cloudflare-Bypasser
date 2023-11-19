package knf.tools.bypass.test

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import knf.tools.bypass.DisplayType
import knf.tools.bypass.Request
import knf.tools.bypass.test.databinding.ActivityMainBinding
import knf.tools.bypass.startBypass

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.ckShowReload.isChecked = prefs.getBoolean("showReload", false)
        binding.ckSkipCaptcha.isChecked = prefs.getBoolean("skipCaptcha", false)
        binding.ckWaitCaptcha.isChecked = prefs.getBoolean("waitCaptcha", false)
        binding.ckClearCookies.isChecked = prefs.getBoolean("clearCookies", false)
        var selectedDisplay = prefs.getInt("displayType", 0)
        when(selectedDisplay) {
            DisplayType.ACTIVITY -> binding.toggleDisplayType.check(R.id.display_activity)
            DisplayType.DIALOG -> {
                binding.toggleDisplayType.check(R.id.display_dialog)
                binding.layDialog.isVisible = true
                binding.ckShowReload.isEnabled = false
            }
            DisplayType.BACKGROUND -> {
                binding.toggleDisplayType.check(R.id.display_background)
                binding.ckShowReload.isEnabled = false
                binding.ckWaitCaptcha.isEnabled = false
                binding.ckSkipCaptcha.isEnabled = false
            }
        }
        binding.toggleDisplayType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when(checkedId) {
                    R.id.display_activity -> selectedDisplay = DisplayType.ACTIVITY
                    R.id.display_dialog -> selectedDisplay = DisplayType.DIALOG
                    R.id.display_background -> selectedDisplay = DisplayType.BACKGROUND
                }
                prefs.edit {
                    putInt("displayType", selectedDisplay)
                }
                binding.ckSkipCaptcha.isEnabled = checkedId != R.id.display_background
                binding.ckWaitCaptcha.isEnabled = checkedId != R.id.display_background
                binding.ckShowReload.isEnabled = checkedId == R.id.display_activity
                binding.layDialog.isVisible = checkedId == R.id.display_dialog
            }
        }
        var selectedDialog = prefs.getInt("dialogStyle",0)
        binding.toggleDialogType.check(if (selectedDialog == 1) R.id.dialog_default else R.id.dialog_sheet)
        binding.toggleDialogType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked){
                selectedDialog = if (checkedId == R.id.dialog_default) 1 else 0
                prefs.edit {
                    putInt("dialogStyle", selectedDialog)
                }
            }
        }
        binding.ckShowReload.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean("showReload",isChecked)
            }
        }
        binding.ckSkipCaptcha.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean("skipCaptcha",isChecked)
            }
        }
        binding.ckWaitCaptcha.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean("waitCaptcha",isChecked)
            }
        }
        binding.ckClearCookies.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean("clearCookies",isChecked)
            }
        }
        binding.testButton.setOnClickListener {
            startBypass(666, Request(
                "https://www3.animeflv.net/", lastUA = null,
                showReload = binding.ckShowReload.isChecked,
                maxTryCount = 3,
                useLatestUA = true,
                reloadOnCaptcha = binding.ckSkipCaptcha.isChecked,
                waitCaptcha = binding.ckWaitCaptcha.isChecked,
                clearCookiesAtStart = binding.ckClearCookies.isChecked,
                displayType = selectedDisplay,
                dialogStyle = selectedDialog)
            )
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
                "Cookies: ${data?.getStringExtra("cookies")}\nUser agent: ${data?.getStringExtra("user_agent")}".also {
                    Toast.makeText(this,it,Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}