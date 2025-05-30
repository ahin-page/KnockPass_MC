package com.ahin.knockpass

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

object KnockPatternManager {
    private const val PREF_NAME = "knock_prefs"
    private const val KEY_PATTERN = "knock_pattern"

    fun savePattern(context: Context, pattern: List<Float>) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        pattern.forEach { jsonArray.put(it) }
        prefs.edit().putString(KEY_PATTERN, jsonArray.toString()).apply()
    }

    fun getPattern(context: Context): List<Float> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PATTERN, null) ?: return emptyList()
        val jsonArray = JSONArray(jsonString)
        val patternList = mutableListOf<Float>()
        for (i in 0 until jsonArray.length()) {
            patternList.add(jsonArray.getDouble(i).toFloat())
        }
        return patternList
    }

    fun clearPattern(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PATTERN).apply()
    }

    fun hasPattern(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_PATTERN)
    }
}