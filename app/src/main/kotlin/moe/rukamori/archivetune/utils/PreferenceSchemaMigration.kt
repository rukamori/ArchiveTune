/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import moe.rukamori.archivetune.constants.DeclaredPreferenceTypes
import moe.rukamori.archivetune.constants.PreferenceValueType

internal object PreferenceSchemaMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val declaredTypes = DeclaredPreferenceTypes
        return currentData.asMap().any { (key, value) ->
            declaredTypes[key.name]?.accepts(value) != true
        }
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val declaredTypes = DeclaredPreferenceTypes
        val migrated = currentData.toMutablePreferences()
        currentData.asMap().forEach { (key, value) ->
            val declaredType = declaredTypes[key.name]
            if (declaredType?.accepts(value) == true) return@forEach
            migrated -= key
            if (declaredType != null) {
                migrated.putConverted(key.name, declaredType, value)
            }
        }
        return migrated
    }

    override suspend fun cleanUp() = Unit
}

private fun PreferenceValueType.accepts(value: Any): Boolean =
    when (this) {
        PreferenceValueType.BOOLEAN -> value is Boolean
        PreferenceValueType.INT -> value is Int
        PreferenceValueType.LONG -> value is Long
        PreferenceValueType.FLOAT -> value is Float
        PreferenceValueType.STRING -> value is String
        PreferenceValueType.STRING_SET -> value is Set<*>
    }

private fun MutablePreferences.putConverted(
    name: String,
    type: PreferenceValueType,
    value: Any,
) {
    when (type) {
        PreferenceValueType.BOOLEAN -> value.convertToBoolean()?.let { this[booleanPreferencesKey(name)] = it }
        PreferenceValueType.INT -> value.convertToInt()?.let { this[intPreferencesKey(name)] = it }
        PreferenceValueType.LONG -> value.convertToLong()?.let { this[longPreferencesKey(name)] = it }
        PreferenceValueType.FLOAT -> value.convertToFloat()?.let { this[floatPreferencesKey(name)] = it }
        PreferenceValueType.STRING -> value.convertToString()?.let { this[stringPreferencesKey(name)] = it }
        PreferenceValueType.STRING_SET -> Unit
    }
}

private fun Any.convertToBoolean(): Boolean? = (this as? String)?.trim()?.toBooleanStrictOrNull()

private fun Any.convertToInt(): Int? =
    when (this) {
        is Int -> this
        is Long -> takeIf { it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
        is Float -> toDouble().finiteWithin(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())?.toInt()
        is Double -> finiteWithin(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())?.toInt()
        is String -> trim().toIntOrNull()
        else -> null
    }

private fun Any.convertToLong(): Long? =
    when (this) {
        is Long -> this
        is Int -> toLong()
        is Float -> toDouble().finiteWithin(Long.MIN_VALUE.toDouble(), Long.MAX_VALUE.toDouble())?.toLong()
        is Double -> finiteWithin(Long.MIN_VALUE.toDouble(), Long.MAX_VALUE.toDouble())?.toLong()
        is String -> trim().toLongOrNull()
        else -> null
    }

private fun Any.convertToFloat(): Float? =
    when (this) {
        is Int -> toFloat()
        is Long -> toFloat()
        is Double -> toFloat()
        is String -> trim().toFloatOrNull()
        else -> null
    }?.takeIf { it.isFinite() }

private fun Any.convertToString(): String? =
    when (this) {
        is Boolean, is Int, is Long, is Float, is Double -> toString()
        else -> null
    }

private fun Double.finiteWithin(
    min: Double,
    max: Double,
): Double? = takeIf { it.isFinite() && it >= min && it <= max }
