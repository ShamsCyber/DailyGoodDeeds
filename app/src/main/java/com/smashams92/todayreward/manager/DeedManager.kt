package com.smashams92.todayreward.manager

import android.content.Context
import android.content.SharedPreferences
import com.smashams92.todayreward.model.GoodDeed
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DeedManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("GoodDeedPrefs", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun getAllDeeds(): List<GoodDeed> {
        return getCustomDeeds() + parseGoodDeeds()
    }

    fun getDeedById(id: Int): GoodDeed? {
        return getAllDeeds().find { it.id == id }
    }


    fun getTodayDate(): String {
        val today = Calendar.getInstance().time
        return dateFormat.format(today)
    }

    fun getLastShownDate(): String? = prefs.getString("lastShownDate", null)

    fun getLastShownDeedId(): Int = prefs.getInt("lastShownDeedId", -1)

    fun getCompletedDeedIds(): Set<Int> {
        val json = prefs.getString("completedDeeds", "[]") ?: "[]"
        val result = mutableSetOf<Int>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                result.add(array.getInt(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun markAsCompleted(id: Int) {
        val current = getCompletedDeedIds().toMutableSet()
        current.add(id)
        val jsonArray = JSONArray()
        current.forEach { jsonArray.put(it) }
        prefs.edit().putString("completedDeeds", jsonArray.toString()).apply()
    }

    fun saveTodayDeed(id: Int) {
        prefs.edit()
            .putString("lastShownDate", getTodayDate())
            .putInt("lastShownDeedId", id)
            .apply()
    }

    fun shouldShowSameDeed(): Boolean = getLastShownDate() == getTodayDate()

    fun hasCompleted(id: Int): Boolean = getCompletedDeedIds().contains(id)

    fun clearAllData() {
        prefs.edit().clear().apply()
    }

    fun isNotificationEnabled(): Boolean = prefs.getBoolean("notificationsEnabled", true)

    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notificationsEnabled", enabled).apply()
    }

    fun getCustomDeeds(): List<GoodDeed> {
        val list = mutableListOf<GoodDeed>()
        val json = getPrefs().getString("customDeeds", "[]") ?: "[]"
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                GoodDeed(
                    id = obj.getInt("id"),
                    fa = obj.getString("fa"),
                    en = obj.getString("en"),
                    ar = obj.getString("ar")
                )
            )
        }
        return list
    }

    fun saveCustomDeed(deed: GoodDeed) {
        val json = getPrefs().getString("customDeeds", "[]") ?: "[]"
        val array = JSONArray(json)
        val obj = JSONObject().apply {
            put("id", deed.id)
            put("fa", deed.fa)
            put("en", deed.en)
            put("ar", deed.ar)
        }
        array.put(obj)
        getPrefs().edit().putString("customDeeds", array.toString()).apply()
    }

    fun editCustomDeed(id: Int, newText: String, lang: String) {
        val array = JSONArray(getPrefs().getString("customDeeds", "[]") ?: "[]")
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getInt("id") == id) {
                obj.put(lang, newText)
                break
            }
        }
        getPrefs().edit().putString("customDeeds", array.toString()).apply()
    }

    fun removeCustomDeed(id: Int) {
        val array = JSONArray(getPrefs().getString("customDeeds", "[]") ?: "[]")
        val newArray = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getInt("id") != id) newArray.put(obj)
        }
        getPrefs().edit().putString("customDeeds", newArray.toString()).apply()
    }

    fun parseGoodDeeds(): List<GoodDeed> {
        val list = mutableListOf<GoodDeed>()
        val json = loadJSONFromAsset("good_deeds.json") ?: return list
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    GoodDeed(
                        id = obj.getInt("id"),
                        fa = obj.getString("fa"),
                        en = obj.getString("en"),
                        ar = obj.getString("ar")
                    )
                )
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return list
    }

    private fun getPrefs(): SharedPreferences =
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private fun loadJSONFromAsset(filename: String): String? {
        return try {
            val stream = context.assets.open(filename)
            val size = stream.available()
            val buffer = ByteArray(size)
            stream.read(buffer)
            stream.close()
            String(buffer, Charset.forName("UTF-8"))
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    fun saveAlarmTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt("alarm_hour", hour)
            .putInt("alarm_minute", minute)
            .apply()
    }

    fun getAlarmTime(): Pair<Int, Int> {
        val hour = prefs.getInt("alarm_hour", 8)
        val minute = prefs.getInt("alarm_minute", 0)
        return Pair(hour, minute)
    }

    fun saveTodayDeedFull(deed: GoodDeed) {
        val obj = JSONObject().apply {
            put("id", deed.id)
            put("fa", deed.fa)
            put("en", deed.en)
            put("ar", deed.ar)
        }
        prefs.edit()
            .putString("lastShownDeedFull", obj.toString())
            .putString("lastShownDate", getTodayDate())
            .putInt("lastShownDeedId", deed.id) // (برای سازگاری قدیمی)
            .apply()
    }

    fun getTodayDeedFull(): GoodDeed? {
        val json = prefs.getString("lastShownDeedFull", null) ?: return null
        return try {
            val obj = JSONObject(json)
            GoodDeed(
                id = obj.getInt("id"),
                fa = obj.getString("fa"),
                en = obj.getString("en"),
                ar = obj.getString("ar")
            )
        } catch (e: Exception) {
            null
        }
    }


}