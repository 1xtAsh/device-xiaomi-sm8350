/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.telephony

import android.app.ActivityThread
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemProperties
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.text.TextUtils
import android.util.Log
import dalvik.system.DexClassLoader
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class EsimController private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EsimController"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        private const val DEVICE_ESIM_SUPPORT_LIST = "fuxi,nuwa"
        private const val PROPERTY_DEVICE_INFO_HW = "ro.boot.hwc"
        private const val PROPERTY_DEVICE_NAME = "ro.product.device"

        private const val MIRILHOOK_CLASS_NAME = "com.xiaomi.mirilhook.MiRilHook"
        private const val MIRILHOOK_JAR_PATH = "/system_ext/framework/mirilhook.jar"
        private const val QCRILHOOKCALLBACK_CLASS_NAME = "com.qualcomm.qcrilhook.QcRilHookCallback"
        private const val QCRILHOOK_JAR_PATH = "/system_ext/framework/qcrilhook.jar"

        @Volatile
        private var instance: EsimController? = null

        fun getInstance(context: Context): EsimController {
            return instance ?: synchronized(this) {
                instance ?: EsimController(context.applicationContext).also { instance = it }
            }
        }
    }

    private var miRilJarLoader: DexClassLoader? = null
    private var miRilHookClass: Class<*>? = null
    private var miRilHookObj: Any? = null
    private var qcRilJarLoader: DexClassLoader? = null
    private var qcRilHookCallbackClass: Class<*>? = null
    private var qcRilHookCallbackObj: Any? = null

    fun onBootCompleted() {
        if (DEBUG) Log.d(TAG, "onBootCompleted")
        setupHook()
        updateEuicc()
    }

    fun getEsimActive(): Boolean {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val subscriptionInfoList = subscriptionManager?.activeSubscriptionInfoList ?: return false

        for (subscriptionInfo in subscriptionInfoList) {
            if (subscriptionInfo.isEmbedded) {
                if (DEBUG) Log.d(TAG, "Found eSIM profile: ${subscriptionInfo.displayName}, ${subscriptionInfo.carrierName}")
                return true
            }
        }
        if (DEBUG) Log.d(TAG, "No eSIM profiles found.")
        return false
    }

    fun getEsimSupported(): Boolean {
        return DEVICE_ESIM_SUPPORT_LIST.contains(SystemProperties.get(PROPERTY_DEVICE_NAME)) &&
                TextUtils.equals(SystemProperties.get(PROPERTY_DEVICE_INFO_HW), "GL")
    }

    fun getEsimEnabled(): Boolean {
        return getEsimSupported() &&
                (callMiRilHookMethod("onGetEsimStatus", -1) as? Int ?: -1) == 0
    }

    fun setEsimEnabled(isEnabled: Boolean) {
        if (DEBUG) Log.d(TAG, "setEsimEnabled, isEnabled = $isEnabled")
        callMiRilHookMethod("onHookUimPowerReqEx", false, 0, 2, -1)
        callMiRilHookMethod("onSetEsimStatus", -1, if (isEnabled) 0 else 1, true)
        callMiRilHookMethod("onHookUimPowerReqEx", false, 1, 2, if (isEnabled) 1 else 0)
        updateEuicc()
    }

    private fun setupHook() {
        if (DEBUG) Log.d(TAG, "setupHook, context = $context")
        if (!getEsimSupported()) {
            if (DEBUG) Log.d(TAG, "getEsimSupported is false")
            return
        }

        miRilJarLoader = miRilJarLoader ?: runCatching {
            DexClassLoader(
                MIRILHOOK_JAR_PATH,
                context.getDir("jar", 0).absolutePath,
                null,
                context.classLoader
            )
        }.onFailure { e ->
            if (DEBUG) Log.d(TAG, "Failed to initialize miRilJarLoader: $e")
        }.getOrNull()

        miRilHookClass = miRilHookClass ?: runCatching {
            miRilJarLoader?.loadClass(MIRILHOOK_CLASS_NAME)
        }.onFailure { e ->
            if (DEBUG) Log.d(TAG, "Failed to load miRilHookClass: $e")
        }.getOrNull()

        qcRilJarLoader = qcRilJarLoader ?: runCatching {
            DexClassLoader(
                QCRILHOOK_JAR_PATH,
                context.getDir("jar", 0).absolutePath,
                null,
                context.classLoader
            )
        }.onFailure { e ->
            if (DEBUG) Log.d(TAG, "Failed to initialize qcRilJarLoader: $e")
        }.getOrNull()

        qcRilHookCallbackClass = qcRilHookCallbackClass ?: runCatching {
            qcRilJarLoader?.loadClass(QCRILHOOKCALLBACK_CLASS_NAME)
        }.onFailure { e ->
            if (DEBUG) Log.d(TAG, "Failed to load qcRilHookCallbackClass: $e")
        }.getOrNull()

        qcRilHookCallbackObj = qcRilHookCallbackObj ?: runCatching {
            Proxy.newProxyInstance(
                context.classLoader,
                arrayOf(qcRilHookCallbackClass),
                QcRilHookCbMethodProxy()
            )
        }.onFailure { e ->
            if (DEBUG) Log.d(TAG, "Failed to initialize qcRilHookCallbackObj: $e")
        }.getOrNull()

        miRilHookObj = miRilHookObj ?: miRilHookClass?.getConstructor(
            Context::class.java,
            qcRilHookCallbackClass,
            String::class.java
        )?.let { constructor ->
            runCatching {
                constructor.newInstance(
                    context,
                    qcRilHookCallbackObj,
                    ActivityThread.currentPackageName()
                )
            }.onFailure { e ->
                if (DEBUG) Log.d(TAG, "Failed to initialize miRilHookObj: $e")
            }.getOrNull()
        }
    }

    private fun callMiRilHookMethod(methodName: String, defObj: Any?, vararg args: Any?): Any? {
        return try {
            val parameterTypes = args.map { it?.javaClass?.kotlin?.javaPrimitiveType ?: it?.javaClass }.toTypedArray()
            miRilHookClass?.getMethod(methodName, *parameterTypes)?.invoke(miRilHookObj, *args)
        } catch (e: Exception) {
            if (DEBUG) Log.d(TAG, "callMiRilHookMethod failed: $methodName, error: $e")
            defObj
        }
    }

    private fun updateEuicc() {
        context.packageManager?.setApplicationEnabledSetting(
            "com.google.android.euicc",
            if (getEsimEnabled()) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            0
        )
    }

    fun dispose() {
        if (DEBUG) Log.d(TAG, "dispose")
        try {
            miRilHookClass?.getMethod("dispose")?.invoke(miRilHookObj)
        } catch (e: Exception) {
            if (DEBUG) Log.d(TAG, "dispose method failed, error: $e")
        }
    }

    inner class QcRilHookCbMethodProxy : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            val methodName = method.name
            if (DEBUG) Log.d(TAG, "QcRilHookCbMethodProxy callbackMethod name: $methodName")
            when (methodName) {
                "onQcRilHookReady" -> updateEuicc()
            }
            return null
        }
    }
}
