package com.muhasib.soft

import android.app.Application
import com.muhasib.soft.data.db.AppDatabase
import com.muhasib.soft.data.repo.Repository

class App : Application() {
    val db: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repo: Repository by lazy { Repository(db) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
