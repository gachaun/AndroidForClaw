package com.xiaomo.androidforclaw.accessibility.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.xiaomo.androidforclaw.accessibility.MediaProjectionHelper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Separate service for AIDL binding
 * This allows external apps to bind and control the accessibility service
 */
class AccessibilityBinderService : Service() {
    companion object {
        private const val TAG = "AccessibilityBinderService"
        var serviceInstance: PhoneAccessibilityService? = null

        // CountDownLatch 用于同步等待 serviceInstance 初始化
        private var readyLatch: CountDownLatch? = null

        /**
         * 通知 serviceInstance 已准备好
         */
        fun notifyServiceReady() {
            Log.d(TAG, "notifyServiceReady called, latch=${readyLatch != null}")
            readyLatch?.countDown()
            Log.d(TAG, "countDown completed")
        }
    }

    private lateinit var binder: AccessibilityBinder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AccessibilityBinderService created")

        // 设置 Context 用于 FileProvider
        MediaProjectionHelper.setContext(this)
        Log.d(TAG, "MediaProjectionHelper context set")

        // 使用外部存储的公共Download目录，确保主应用可以访问
        // Download 目录是所有应用都可以读写的公共目录
        val screenshotDir = File("/sdcard/Download/AndroidForClaw")
        screenshotDir.mkdirs() // 创建目录

        // 设置目录权限，确保其他应用可以读取
        try {
            screenshotDir.setReadable(true, false)
            screenshotDir.setWritable(true, false)
            screenshotDir.setExecutable(true, false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set directory permissions", e)
        }

        MediaProjectionHelper.setScreenshotDirectory(screenshotDir)
        Log.d(TAG, "MediaProjectionHelper screenshot directory: ${screenshotDir.absolutePath}")
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called")

        // 创建新的 CountDownLatch（每次 bind 都创建新的）
        readyLatch = CountDownLatch(1)

        // 检查是否已经 ready
        var accessibilityService = serviceInstance
        if (accessibilityService != null) {
            Log.i(TAG, "✅ Accessibility service already ready")
            binder = AccessibilityBinder(accessibilityService)
            return binder
        }

        // 等待 serviceInstance 初始化（最多 5 秒）
        Log.d(TAG, "Waiting for accessibility service to be ready...")
        try {
            val success = readyLatch!!.await(5, TimeUnit.SECONDS)
            if (!success) {
                Log.w(TAG, "❌ Accessibility service not ready after 5 seconds, returning null")
                return null
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for accessibility service", e)
            return null
        }

        // 再次检查 serviceInstance
        accessibilityService = serviceInstance
        if (accessibilityService == null) {
            Log.w(TAG, "❌ serviceInstance is still null after latch released")
            return null
        }

        Log.i(TAG, "✅ Accessibility service ready, returning binder")
        binder = AccessibilityBinder(accessibilityService)
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AccessibilityBinderService destroyed")
    }
}
