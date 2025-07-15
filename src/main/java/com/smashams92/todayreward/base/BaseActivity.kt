package com.smashams92.todayreward.base

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("lang", Locale.getDefault().language) ?: "fa"

        val wrappedContext = wrapLanguageContext(newBase, lang)
        super.attachBaseContext(wrappedContext)
    }

    private fun wrapLanguageContext(context: Context, langCode: String): Context {
        val locale = Locale.forLanguageTag(langCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    fun getSelectedLang(): String =
        getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("lang", Locale.getDefault().language) ?: "fa"

    fun saveSelectedLang(lang: String) {
        getSharedPreferences("prefs", MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
    }
}