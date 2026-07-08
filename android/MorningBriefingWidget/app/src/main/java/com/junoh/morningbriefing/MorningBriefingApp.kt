package com.junoh.morningbriefing

import android.app.Application

class MorningBriefingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BriefingUpdateWorker.schedulePeriodic(this)
        BriefingUpdateWorker.refreshNow(this)
    }
}
