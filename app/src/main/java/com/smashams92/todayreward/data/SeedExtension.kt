package com.smashams92.todayreward.data

import android.content.res.AssetManager
import org.json.JSONArray

suspend fun DeedDatabase.seedInitialData(assets: AssetManager) {
    val json = assets.open("good_deeds.json").bufferedReader().use { it.readText() }
    val array = JSONArray(json)
    val list = mutableListOf<GoodDeedEntity>()

    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(
            GoodDeedEntity(
                id = obj.getInt("id"),
                fa = obj.getString("fa"),
                en = obj.getString("en"),
                ar = obj.getString("ar")
            )
        )
    }

    this.deedDao().insertAll(list)
}
