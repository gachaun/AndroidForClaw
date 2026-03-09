package com.xiaomo.androidforclaw.accessibility

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import com.xiaomo.androidforclaw.accessibility.databinding.ActivityPermissionsBinding

class PermissionActivity : Activity() {
    companion object {
        private const val TAG = "PermissionActivity"
        private const val REQUEST_CODE = 10086
        private const val REQUEST_ACCESSIBILITY = 1001
        private const val CHECK_INTERVAL = 1000L  // 每秒检查一次
    }

    private lateinit var binding: ActivityPermissionsBinding
    private val handler = Handler(Looper.getMainLooper())
    private var statusCheckRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        binding = ActivityPermissionsBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        setupViews()
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        startStatusCheck()
    }

    override fun onPause() {
        super.onPause()
        stopStatusCheck()
    }

    private fun startStatusCheck() {
        stopStatusCheck()

        statusCheckRunnable = object : Runnable {
            override fun run() {
                updatePermissionStatus()
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        }

        updatePermissionStatus()
        handler.postDelayed(statusCheckRunnable!!, CHECK_INTERVAL)
        Log.d(TAG, "Started permission status check (interval: ${CHECK_INTERVAL}ms)")
    }

    private fun stopStatusCheck() {
        statusCheckRunnable?.let {
            handler.removeCallbacks(it)
            statusCheckRunnable = null
        }
        Log.d(TAG, "Stopped permission status check")
    }

    private fun setupViews() {
        Log.d(TAG, "setupViews called")
        binding.apply {
            // 无障碍服务按钮
            btnAccessibility.setOnClickListener {
                Log.d(TAG, "btnAccessibility clicked")
                requestAccessibilityPermission()
            }

            // 录屏权限按钮
            btnScreenCapture.setOnClickListener {
                Log.d(TAG, "btnScreenCapture clicked")
                requestScreenCapturePermission()
            }

            // 一键授权按钮
            btnGrantAll.setOnClickListener {
                Log.d(TAG, "btnGrantAll clicked")
                grantAllPermissions()
            }
        }
    }

    private fun updatePermissionStatus() {
        updateAccessibilityStatus()
        updateScreenCaptureStatus()
        updateAllGrantedStatus()
    }

    private fun updateAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled()

        binding.apply {
            val currentStatus = tvAccessibilityStatus.text.toString()
            val newStatus = if (isEnabled) "✅ 已启用" else "❌ 未启用"

            if (currentStatus != newStatus) {
                if (isEnabled) {
                    tvAccessibilityStatus.text = "✅ 已启用"
                    tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                    btnAccessibility.isEnabled = false
                    btnAccessibility.text = "已启用"
                    Log.d(TAG, "无障碍服务状态变更: 已启用")
                } else {
                    tvAccessibilityStatus.text = "❌ 未启用"
                    tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    btnAccessibility.isEnabled = true
                    btnAccessibility.text = "去设置"
                    Log.d(TAG, "无障碍服务状态变更: 未启用")
                }
            }

            tvAccessibilityDesc.text = """
                用于：点击、滑动、输入文本、获取界面信息

                状态详情：$newStatus
            """.trimIndent()
        }
    }

    private fun updateScreenCaptureStatus() {
        val isGranted = MediaProjectionHelper.isMediaProjectionGranted()
        val statusText = MediaProjectionHelper.getPermissionStatus()

        binding.apply {
            val currentStatus = tvScreenCaptureStatus.text.toString()
            val newStatus = if (isGranted) "✅ 已授权" else "❌ 未授权"

            if (currentStatus != newStatus) {
                if (isGranted) {
                    tvScreenCaptureStatus.text = "✅ 已授权"
                    tvScreenCaptureStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                    btnScreenCapture.isEnabled = false
                    btnScreenCapture.text = "已授权"
                    Log.d(TAG, "录屏权限状态变更: 已授权")
                } else {
                    tvScreenCaptureStatus.text = "❌ 未授权"
                    tvScreenCaptureStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    btnScreenCapture.isEnabled = true
                    btnScreenCapture.text = "授予录屏权限"
                    Log.d(TAG, "录屏权限状态变更: 未授权")
                }
            }

            tvScreenCaptureDesc.text = """
                用于：截图观察界面、分析 UI 元素

                状态详情：$statusText
            """.trimIndent()
        }
    }

    private fun updateAllGrantedStatus() {
        val accessibility = isAccessibilityServiceEnabled()
        val screenCapture = MediaProjectionHelper.isMediaProjectionGranted()

        val allGranted = accessibility && screenCapture

        binding.apply {
            if (allGranted) {
                tvAllStatus.text = "✅ 所有权限已授予"
                tvAllStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                btnGrantAll.isEnabled = false
                btnGrantAll.text = "全部已授权"
            } else {
                val grantedCount = listOf(accessibility, screenCapture).count { it }
                tvAllStatus.text = "⚠️ 已授予 $grantedCount/2 个权限"
                tvAllStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
                btnGrantAll.isEnabled = true
                btnGrantAll.text = "一键授权"
            }
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivityForResult(intent, REQUEST_ACCESSIBILITY)
    }

    private fun requestScreenCapturePermission() {
        Log.d(TAG, "Requesting MediaProjection permission")
        val granted = MediaProjectionHelper.requestMediaProjection(this)
        if (granted) {
            Toast.makeText(this, R.string.toast_permission_granted, Toast.LENGTH_SHORT).show()
            updatePermissionStatus()
        }
    }

    private fun grantAllPermissions() {
        // 按顺序请求权限
        if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityPermission()
        } else if (!MediaProjectionHelper.isMediaProjectionGranted()) {
            requestScreenCapturePermission()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        // 检查两种格式: 短格式 (.service.PhoneAccessibilityService) 和完整格式
        val serviceShort = "${packageName}/.service.PhoneAccessibilityService"
        val serviceFull = "${packageName}/com.xiaomo.androidforclaw.accessibility.service.PhoneAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        val isEnabled = enabledServices?.let {
            it.contains(serviceShort) || it.contains(serviceFull)
        } ?: false

        return isEnabled
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        when (requestCode) {
            REQUEST_CODE -> {
                val handled = MediaProjectionHelper.handleActivityResult(this, requestCode, resultCode, data)
                if (handled) {
                    Toast.makeText(this, R.string.toast_screen_capture_granted, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.toast_screen_capture_denied, Toast.LENGTH_SHORT).show()
                }

                // Delay update to ensure permission is registered
                handler.postDelayed({
                    updatePermissionStatus()
                }, 500)
            }
            REQUEST_ACCESSIBILITY -> {
                // 权限设置返回，刷新状态
                handler.postDelayed({
                    updatePermissionStatus()
                }, 500)
            }
        }
    }
}
