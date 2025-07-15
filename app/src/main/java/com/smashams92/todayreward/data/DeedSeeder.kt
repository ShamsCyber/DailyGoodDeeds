package com.smashams92.todayreward.data

import android.content.Context
import org.json.JSONArray

class DeedSeeder {
    suspend fun seedFromJson(context: Context) {
        val json = context.assets.open("good_deeds.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val deeds = mutableListOf<GoodDeedEntity>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            deeds.add(
                GoodDeedEntity(
                    id = obj.getInt("id"),
                    fa = obj.getString("fa"),
                    en = obj.getString("en"),
                    ar = obj.getString("ar")
                )
            )
        }
        DeedDatabase.getDatabase(context).deedDao().insertAll(deeds)
    }

}