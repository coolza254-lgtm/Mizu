package com.example.mizu.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import com.example.mizu.core.AppLanguage
import java.util.Locale

fun AppLanguage.toLocale(): Locale = Locale.forLanguageTag(tag)

/** A context whose resources use [language]. For non-UI use (notifications, workers). */
fun Context.localized(language: AppLanguage): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(language.toLocale())
    return createConfigurationContext(config)
}

/**
 * Wraps this (Activity) context but answers resources in [language], so the UI switches language without a restart.
 * Unlike [localized], it is still a ContextWrapper around the Activity, so Compose can find the Activity
 * (permission launchers, back handling, dialogs) through it.
 */
fun Context.localizedWrapper(language: AppLanguage): Context = LocalizedContext(this, localized(language).resources)

private class LocalizedContext(base: Context, private val res: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = res
}
