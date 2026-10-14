package com.example.mizu

import android.app.Application
import android.content.Context
import com.example.mizu.data.MizuDatabase
import com.example.mizu.data.MizuRepository
import com.example.mizu.data.SettingsRepository
import com.example.mizu.reminder.NotificationHelper
import com.example.mizu.reminder.ReminderScheduler
import com.example.mizu.update.Updater

class AppContainer(context: Context) {
    val database = MizuDatabase.create(context)
    val dao = database.dao()
    val settings = SettingsRepository(context)
    val scheduler = ReminderScheduler(context, settings, dao)
    val repository = MizuRepository(database, settings, scheduler)
    val updater = Updater(context)
}

class MizuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as MizuApp).container
