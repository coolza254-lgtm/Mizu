package com.example.mizu.util

import android.content.Context
import android.content.res.Configuration
import com.example.mizu.core.AppLanguage
import java.util.Locale

fun AppLanguage.toLocale(): Locale = Locale.forLanguageTag(tag)

/** A context whose resources use [language], so the UI and notifications switch without a restart. */
fun Context.localized(language: AppLanguage): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(language.toLocale())
    return createConfigurationContext(config)
}
