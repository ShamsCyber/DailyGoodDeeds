package com.smashams92.todayreward.base

import android.app.Application
import com.smashams92.todayreward.ui.TypefaceUtil

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TypefaceUtil.overrideFont(this, "MONOSPACE", "fonts/iransans_light_fa.ttf")
    }
}
