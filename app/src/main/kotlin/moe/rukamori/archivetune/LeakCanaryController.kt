/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune

import android.app.Application
import android.content.Context

object LeakCanaryController {
    private const val VARIANT_CLASS = "moe.rukamori.archivetune.LeakCanaryVariant"

    fun initialize(application: Application) {
        invoke(application, "initialize", application)
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        invoke(context, "setEnabled", context, enabled)
    }

    private fun invoke(
        context: Context,
        methodName: String,
        vararg arguments: Any,
    ) {
        runCatching {
            val variantClass = Class.forName(VARIANT_CLASS)
            val parameterTypes =
                when (methodName) {
                    "initialize" -> arrayOf(Application::class.java)
                    "setEnabled" -> arrayOf(Context::class.java, Boolean::class.javaPrimitiveType!!)
                    else -> return
                }
            variantClass.getMethod(methodName, *parameterTypes).invoke(null, *arguments)
        }.onFailure { throwable ->
            if (throwable !is ClassNotFoundException) {
                android.util.Log.w("LeakCanaryController", "Unable to invoke LeakCanary variant", throwable)
            }
        }
    }
}

internal object LeakCanaryToggle {
    const val PREFERENCE_KEY = "dev_enable_leak_canary"
}
