package moe.rukamori.archivetune.utils.oem

/**
 * Media Kit
 * Copyright (C) 2025 Moriafly
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 小米妙播适配
 */
object MiPlayAudioSupport {
    private const val ACTION_MIPLAY_DETAIL = "miui.intent.action.ACTIVITY_MIPLAY_DETAIL"
    private const val AUDIO_RECORD_CLASS = "miui.media.MiuiAudioPlaybackRecorder"
    private const val PACKAGE_NAME = "com.milink.service"
    private const val SERVICE_NAME = "com.miui.miplay.audio.service.CoreService"
    private const val WHITE_TARGET = "com.milink.service:hide_foreground"

    /**
     * 查询是否支持妙播服务
     *
     * https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1944
     */
    fun supportMiPlay(context: Context): Boolean {
        try {
            // 未找到抛出 PackageManager.NameNotFoundException
            context.packageManager.getServiceInfo(
                ComponentName(PACKAGE_NAME, SERVICE_NAME),
                PackageManager.MATCH_ALL
            )
            // 未找到抛出 ClassNotFoundException
            context.classLoader.loadClass(AUDIO_RECORD_CLASS)

            val isInternationalBuild = isInternationalBuild()
            val systemUIReady = systemUIReady(context)
            val notificationReady = notificationReady(context)
            return !isInternationalBuild && systemUIReady && notificationReady
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * 是否为国际版
     */
    private fun isInternationalBuild(): Boolean =
        try {
            val clazz = Class.forName("miui.os.Build")
            val field = clazz.getField("IS_INTERNATIONAL_BUILD")
            field.isAccessible = true
            field.getBoolean(null)
        } catch (_: Exception) {
            false
        }

    /**
     * 检查 SystemUI 是否包含处理妙播意图的 Activity
     */
    private fun systemUIReady(context: Context): Boolean {
        val intent =
            Intent(ACTION_MIPLAY_DETAIL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        // TODO 是否需要 try catch？
        return try {
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /**
     * 检查妙播服务是否在 SystemUI 的前台服务通知白名单中
     */
    private fun notificationReady(context: Context): Boolean =
        try {
            val systemUiAppInfo =
                context.packageManager.getApplicationInfo(
                    "com.android.systemui",
                    0
                )
            val resources = context.packageManager.getResourcesForApplication(systemUiAppInfo)
            val identifier =
                @SuppressLint("DiscouragedApi")
                resources.getIdentifier(
                    "system_foreground_notification_whitelist",
                    "array",
                    "com.android.systemui"
                )

            if (identifier > 0) {
                val whiteList = resources.getStringArray(identifier)
                val contains = whiteList.contains(WHITE_TARGET)
                contains
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
}
