package com.smashams92.todayreward

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smashams92.todayreward.base.BaseActivity
import com.smashams92.todayreward.data.DeedDatabase
import com.smashams92.todayreward.data.GoodDeedEntity
import com.smashams92.todayreward.data.seedInitialData
import com.smashams92.todayreward.databinding.ActivityMainBinding
import com.smashams92.todayreward.notification.NotificationReceiver
import com.smashams92.todayreward.ui.DeedListActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : BaseActivity() {

    companion object {
        private val API_Key_PRF = "ApiKey"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: DeedDatabase
    private var todayDeed: GoodDeedEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = DeedDatabase.getDatabase(this)

        val lang = getSelectedLang()
        val isRtl = lang == "fa" || lang == "ar"


        binding.lyTopMain.setBackgroundResource(
            if (isRtl) R.drawable.new_header_bg_rtl else R.drawable.new_header_bg_ltr
        )

        initLanguageSelector()
        initNotificationSwitch()
        setupButtonActions()
        setupApiKeyViews()

        lifecycleScope.launch {
            checkOrSeedDeeds()
            loadTodayDeed()
        }
    }

    private fun initLanguageSelector() {
        binding.languageSelector.setOnClickListener {
            val items = arrayOf("🇮🇷 فارسی", "🇺🇸 English", "🇸🇦 العربية")
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.language))
                .setItems(items) { _, which ->
                    val lang = when (which) {
                        0 -> "fa"
                        1 -> "en"
                        2 -> "ar"
                        else -> "fa"
                    }
                    saveSelectedLang(lang)
                    recreate()
                }
                .show()
        }
    }

    private fun initNotificationSwitch() {
        binding.notificationSwitch.isChecked = getNotificationPref()

        binding.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            setNotificationPref(isChecked)

            if (isChecked) {
                requestNotificationPermissionIfNeeded(this) {
                    scheduleDailyNotification()
                }
            } else {
                cancelDailyNotification()
            }
        }

        binding.setAlarmTimeButton.setOnClickListener {
            showTimePickerForAlarm()
        }
    }


    private suspend fun checkOrSeedDeeds() {
        val count = withContext(Dispatchers.IO) { database.deedDao().countDeeds() }
        if (count == 0) {
            database.seedInitialData(assets)
        }
    }

    private suspend fun loadTodayDeed() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val lastDate = prefs.getString("lastDate", null)
        val lastId = prefs.getInt("lastDeedId", -1)
        val todayStr = todayDate()

        todayDeed = if (lastDate == todayStr && lastId != -1) {
            withContext(Dispatchers.IO) { database.deedDao().getById(lastId) }
        } else {
            val all = withContext(Dispatchers.IO) { database.deedDao().getAllDeeds() }
            val random = all.randomOrNull()
            if (random != null) {
                prefs.edit().putString("lastDate", todayStr).putInt("lastDeedId", random.id).apply()
            }
            random
        }

        showDeed()
    }

    private fun showDeed() {
        val deed = todayDeed ?: return
        val lang = getSelectedLang()
        val text = when (lang) {
            "fa" -> deed.fa
            "ar" -> deed.ar
            else -> deed.en
        }
        binding.deedTextView.text = text


        if (deed.isCompleted) {
            binding.doneButton.isEnabled = false
            binding.doneButton.text = getString(R.string.done)
            binding.doneButton.setBackgroundColor(getColor(android.R.color.darker_gray))
        } else {
            binding.doneButton.isEnabled = true
            binding.doneButton.text = " ✔"
            binding.doneButton.setBackgroundColor(getColor(R.color.positiveGreen))
        }
    }

    private fun setupButtonActions() {

        binding.shareButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    getString(
                        R.string.share_deed_text,
                        binding.deedTextView.text.toString()
                    )
                )
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        }

        binding.doneButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.done), Toast.LENGTH_SHORT).show()
            binding.doneButton.apply {
                isEnabled = false
                text = getString(R.string.done)
                setBackgroundColor(getColor(android.R.color.darker_gray))
            }

            lifecycleScope.launch(Dispatchers.IO) {
                todayDeed?.let {
                    val updated = it.copy(isCompleted = true)
                    database.deedDao().updateDeed(updated)
                }
            }
        }

        binding.openDeedListButton.setOnClickListener {
            startActivity(Intent(this, DeedListActivity::class.java))
        }


        binding.questionInput.doAfterTextChanged {
            val input = it?.toString().orEmpty().trim()
            binding.sendQuestionBtn.isEnabled = input.isNotEmpty()
        }

        binding.sendQuestionBtn.setOnClickListener {
            val question = binding.questionInput.text.toString().trim()
            if (question.isNotBlank()) {

                // حالت لودینگ ساده
                binding.sendQuestionBtn.text = getString(R.string.loading_text)
                binding.sendQuestionBtn.isEnabled = false

                lifecycleScope.launch {
                    val answer = askGeminiQuestion(this@MainActivity, question)
                    binding.answerTextView.text = answer

                    // بازگشت دکمه به حالت عادی
                    binding.sendQuestionBtn.text = getString(R.string.send)
                    binding.sendQuestionBtn.isEnabled =
                        binding.questionInput.text.toString().trim().isNotEmpty()
                }

            } else {
                Toast.makeText(
                    this,
                    getString(R.string.question_required_toast),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


        binding.ivOpenDeedListButton.setOnClickListener { binding.openDeedListButton.callOnClick() }

        binding.ivSetAlarmTimeButton.setOnClickListener { binding.setAlarmTimeButton.callOnClick() }

        binding.ivNotificationSwitch.setOnClickListener { binding.notificationSwitch.toggle() }


    }


    private fun todayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun showTimePickerForAlarm() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val hour = prefs.getInt("alarmHour", 8)
        val min = prefs.getInt("alarmMin", 0)

        val themedContext = ContextThemeWrapper(this, R.style.TimePickerDialogTheme)

        TimePickerDialog(themedContext, { _, h, m ->
            prefs.edit().putInt("alarmHour", h).putInt("alarmMin", m).apply()
            scheduleDailyNotification()
            Toast.makeText(this, "$h:$m ${getString(R.string.set_alarm)}", Toast.LENGTH_SHORT)
                .show()
        }, hour, min, true).show()
    }


    private fun scheduleDailyNotification() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val h = prefs.getInt("alarmHour", 8)
        val m = prefs.getInt("alarmMin", 0)

        val intent = Intent(this, NotificationReceiver::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun cancelDailyNotification() {
        val intent = Intent(this, NotificationReceiver::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    private fun getNotificationPref(): Boolean =
        getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("notifEnabled", true)

    private fun setNotificationPref(value: Boolean) {
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("notifEnabled", value).apply()
    }

    suspend fun askGeminiQuestion(context: Context, question: String): String =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    .getString(API_Key_PRF, "")?.trim().orEmpty()

                if (apiKey.isBlank()) return@withContext getString(R.string.error_no_api_key)

                val lang = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    .getString("lang", "fa")?.takeIf { it in listOf("fa", "en", "ar") } ?: "en"

                val limiter = getLimiterText(lang)
                val fullPrompt = "$question. $limiter"

                val jsonBody = """
        {
          "contents": [
            {
              "parts": [
                {
                  "text": "${fullPrompt.replace("\"", "\\\"")}"
                }
              ]
            }
          ]
        }
        """.trimIndent()

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-goog-api-key", apiKey)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    Log.e("askGemini", "HTTP ${response.code}: ${body ?: "no body"}")

                    val backErrorShow = {
                        if (response.code == 403)
                            getString(R.string.error_response_ir)
                        else
                            getString(R.string.error_server_response)
                    }

                    return@withContext backErrorShow() + " : " + response.code


                }

                val json = JSONObject(body)
                return@withContext json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

            } catch (e: Exception) {
                Log.e("askGemini", "Exception: ${e.message}")
                return@withContext getString(R.string.error_server_response)
            }
        }

    fun getLimiterText(lang: String): String {
        return when (lang) {
            "fa" -> ".  پاسخ را به زبان فارسی و در حداکثر ۲۰۰ کاراکتر به صورت خلاصه و دقیق ارائه کن. اگر سؤال مرتبط با احکام، دین و شریعت یا کلا مذهبی نیست، اعلام کن که خارج از موضوع برنامه است."
            "ar" -> ". من فضلك، أجب باللغة العربية في حدود 200 حرف فقط. وإذا لم يكن السؤال متعلّقًا بالأحكام الشرعية، فاذكر أنّه خارج موضوع التطبيق الديني."
            "en" -> ". Please answer in English with a maximum of 200 characters. If the question is not related to  jurisprudence or religious matters, respond that it's beyond the app's scope."
            else -> ".  پاسخ را به زبانی که سوال پرسیده شده و در حداکثر ۲۰۰ کاراکتر ارائه کن."
        }
    }


    private fun setupApiKeyViews() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val savedKey = prefs.getString(API_Key_PRF, null)

        if (savedKey.isNullOrBlank()) {
            binding.layoutInfo.visibility = View.VISIBLE
            binding.askQuestionLayout.visibility = View.GONE
        } else {
            binding.layoutInfo.visibility = View.GONE
            binding.askQuestionLayout.visibility = View.VISIBLE
        }

        fun showApiDialog(title: String, onSave: (String) -> Unit) {
            val input = EditText(this)
            input.hint = getString(R.string.enter_api_key)
            input.setText(savedKey ?: "AIzaSyDtAmQqezvIA2AGjej7pGIqB4JVQMyjnJx")

            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(getString(R.string.save)) { _, _ ->
                    val key = input.text.toString().trim()
                    if (key.isNotEmpty()) {
                        prefs.edit().putString(API_Key_PRF, key).apply()
                        Toast.makeText(this, getString(R.string.api_key_saved), Toast.LENGTH_SHORT)
                            .show()
                        setupApiKeyViews()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        binding.OpenGoogleApi.setOnClickListener {
            val url = getString(R.string.open_google_api_site)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        binding.btnAddFirstApi.setOnClickListener {
            showApiDialog(getString(R.string.enter_api_key_dialog_title)) { }
        }



        binding.btnEditAPI.setOnClickListener {
            val options =
                arrayOf(getString(R.string.edit_api_key), getString(R.string.delete_api_key))
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.manage_api_key_title))
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showApiDialog(getString(R.string.edit_api_key)) { }
                        1 -> {

                            MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.remove_api_key_title))
                                .setMessage(getString(R.string.remove_api_key_message))
                                .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                                    getSharedPreferences("prefs", MODE_PRIVATE)
                                        .edit().remove(API_Key_PRF).apply()

                                    Toast.makeText(
                                        this,
                                        getString(R.string.api_key_removed),
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()


                                    binding.askQuestionLayout.visibility = View.GONE
                                    binding.layoutInfo.visibility = View.VISIBLE

                                    dialog.dismiss()
                                }
                                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->

                                    prefs.edit().remove(API_Key_PRF).apply()
                                    Toast.makeText(
                                        this,
                                        getString(R.string.api_key_deleted),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    setupApiKeyViews()

                                    dialog.dismiss()
                                }
                                .show()

                        }
                    }
                }
                .show()
        }
    }

    private fun requestNotificationPermissionIfNeeded(activity: Activity, onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                onGranted()
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(permission), 1001)
            }
        } else {
            onGranted()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scheduleDailyNotification()
            } else {
                Toast.makeText(this, getString(R.string.notif_permission_required), Toast.LENGTH_SHORT)
                    .show()
                binding.notificationSwitch.isChecked = false
            }
        }
    }


}
