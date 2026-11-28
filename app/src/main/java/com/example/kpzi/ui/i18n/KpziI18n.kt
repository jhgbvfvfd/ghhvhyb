package com.example.kpzi.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
    val shortCode: String,
) {
    THAI(code = "th", nativeLabel = "ภาษาไทย (Thai)", shortCode = "TH"),
    ENGLISH(code = "en", nativeLabel = "English (อังกฤษ)", shortCode = "EN"),
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.THAI }

@Composable
@ReadOnlyComposable
fun isEnglishLanguage(): Boolean = LocalAppLanguage.current == AppLanguage.ENGLISH

@Composable
@ReadOnlyComposable
fun tr(th: String, en: String): String =
    if (LocalAppLanguage.current == AppLanguage.ENGLISH) en else th

fun localize(language: AppLanguage, th: String, en: String): String =
    if (language == AppLanguage.ENGLISH) en else th

fun localize(isEnglish: Boolean, th: String, en: String): String =
    if (isEnglish) en else th
