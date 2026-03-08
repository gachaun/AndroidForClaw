package com.xiaomo.androidforclaw.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.content.ComponentName
import com.xiaomo.androidforclaw.accessibility.AccessibilityProxy
import com.draco.ladb.R
import com.draco.ladb.databinding.ActivityPermissionsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 权限管理页面
 * 逐个检查和开启应用所需权限
 */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    private val handler = Handler(Looper.getMainLooper())
    private var statusCheckRunnable: Runnable? = null
    private val CHECK_INTERVAL = 1000L  // 每秒检查一次

    companion object {
        private const val TAG = "PermissionsActivity"
        private const val REQUEST_ACCESSIBILITY = 1001
        private const val REQUEST_OVERLAY = 1002
        private const val REQUEST_SCREEN_CAPTURE = 10086  // 必须与 MediaProjectionHelper.REQUEST_CODE 一致
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d(TAG, "onCreate called")
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "权限管理"
        }

        setupViews()
        // 使用协程异步检查权限，避免阻塞主线程
        lifecycleScope.launch {
            updatePermissionStatus()
        }
        android.util.Log.d(TAG, "onCreate completed")
    }

    override fun onResume() {
        super.onResume()
        // 启动定时检查
        startStatusCheck()
    }

    override fun onPause() {
        super.onPause()
        // 停止定时检查
        stopStatusCheck()
    }

    /**
     * 启动权限状态定时检查
     */
    private fun startStatusCheck() {
        stopStatusCheck()  // 先停止之前的检查

        statusCheckRunnable = object : Runnable {
            override fun run() {
                // 使用协程异步检查
                lifecycleScope.launch {
                    updatePermissionStatus()
                }
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        }

        // 立即执行一次，然后开始定时检查
        lifecycleScope.launch {
            updatePermissionStatus()
        }
        handler.postDelayed(statusCheckRunnable!!, CHECK_INTERVAL)

        android.util.Log.d(TAG, "Started permission status check (interval: ${CHECK_INTERVAL}ms)")
    }

    /**
     * 停止权限状态定时检查
     */
    private fun stopStatusCheck() {
        statusCheckRunnable?.let {
            handler.removeCallbacks(it)
            statusCheckRunnable = null
        }
        android.util.Log.d(TAG, "Stopped permission status check")
    }

    private fun setupViews() {
        android.util.Log.d(TAG, "setupViews called")
        binding.apply {
            // 无障碍服务
            btnAccessibility.setOnClickListener {
                android.util.Log.d(TAG, "btnAccessibility clicked")
                requestAccessibilityPermission()
            }

            // 悬浮窗权限
            btnOverlay.setOnClickListener {
                android.util.Log.d(TAG, "btnOverlay clicked")
                requestOverlayPermission()
            }

            // 录屏权限
            btnScreenCapture.setOnClickListener {
                android.util.Log.d(TAG, "btnScreenCapture clicked")
                requestScreenCapturePermission()
            }

            // 全部授权按钮
            btnGrantAll.setOnClickListener {
                android.util.Log.d(TAG, "btnGrantAll clicked")
                grantAllPermissions()
            }
        }
        android.util.Log.d(TAG, "setupViews completed")
    }

    /**
     * 更新所有权限状态（协程异步）
     */
    private suspend fun updatePermissionStatus() {
        updateAccessibilityStatus()
        updateOverlayStatus()
        updateScreenCaptureStatus()
        updateAllGrantedStatus()
    }

    /**
     * 更新无障碍服务状态（协程异步）
     */
    private suspend fun updateAccessibilityStatus() {
        val isConnected = AccessibilityProxy.isConnected.value == true
        val isReady = AccessibilityProxy.isServiceReadyAsync()

        binding.apply {
            // 隐藏状态标签
            tvAccessibilityStatus.visibility = android.view.View.GONE

            if (isConnected && isReady) {
                btnAccessibility.isEnabled = false
                btnAccessibility.text = "已连接"
                android.util.Log.d(TAG, "无障碍服务状态变更: 已连接并就绪")
            } else {
                btnAccessibility.isEnabled = true
                btnAccessibility.text = "检查服务"
                android.util.Log.d(TAG, "无障碍服务状态变更: 未授权")
            }

            tvAccessibilityDesc.text = "用于：点击、滑动、输入文本、获取界面信息"
        }
    }

    /**
     * 更新悬浮窗权限状态
     */
    private fun updateOverlayStatus() {
        val isGranted = Settings.canDrawOverlays(this)

        binding.apply {
            // 隐藏状态标签
            tvOverlayStatus.visibility = android.view.View.GONE

            if (isGranted) {
                btnOverlay.isEnabled = false
                btnOverlay.text = "已授权"
                android.util.Log.d(TAG, "悬浮窗权限状态变更: 已授权")
            } else {
                btnOverlay.isEnabled = true
                btnOverlay.text = "去授权"
                android.util.Log.d(TAG, "悬浮窗权限状态变更: 未授权")
            }

            tvOverlayDesc.text = "用于：显示 Agent 执行状态悬浮窗"
        }
    }

    /**
     * 更新录屏权限状态
     */
    private fun updateScreenCaptureStatus() {
        val isGranted = AccessibilityProxy.isMediaProjectionGranted()

        binding.apply {
            // 隐藏状态标签
            tvScreenCaptureStatus.visibility = android.view.View.GONE

            if (isGranted) {
                btnScreenCapture.isEnabled = false
                btnScreenCapture.text = "已授权"
                android.util.Log.d(TAG, "录屏权限状态变更: 已授权")
            } else {
                btnScreenCapture.isEnabled = true
                btnScreenCapture.text = "去授权"
                android.util.Log.d(TAG, "录屏权限状态变更: 未授权")
            }

            tvScreenCaptureDesc.text = "用于：截图观察界面、分析 UI 元素"
        }
    }

    /**
     * 更新全部授权状态（协程异步）
     */
    private suspend fun updateAllGrantedStatus() {
        val accessibility = AccessibilityProxy.isConnected.value == true && AccessibilityProxy.isServiceReadyAsync()
        val overlay = Settings.canDrawOverlays(this)
        val screenCapture = AccessibilityProxy.isMediaProjectionGranted()

        val allGranted = accessibility && overlay && screenCapture

        binding.apply {
            if (allGranted) {
                tvAllStatus.text = "✅ 所有权限已授予"
                tvAllStatus.setTextColor(getColor(R.color.status_ok))
                btnGrantAll.isEnabled = false
                btnGrantAll.text = "全部已授权"
            } else {
                val grantedCount = listOf(accessibility, overlay, screenCapture).count { it }
                tvAllStatus.text = "⚠️ 已授予 $grantedCount/3 个权限"
                tvAllStatus.setTextColor(getColor(R.color.status_warning))
                btnGrantAll.isEnabled = true
                btnGrantAll.text = "一键授权"
            }
        }
    }

    /**
     * 请求无障碍服务权限
     */
    private fun requestAccessibilityPermission() {
        // 提示用户在无障碍服务 APK 中启用
        android.app.AlertDialog.Builder(this)
            .setTitle("无障碍服务")
            .setMessage("请在系统设置中启用 \"AndroidForClaw Accessibility\" 服务。\n\n注意：该服务由独立的 APK 提供，需要单独安装。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivityForResult(intent, REQUEST_ACCESSIBILITY)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQUEST_OVERLAY)
    }

    /**
     * 请求录屏权限
     */
    private fun requestScreenCapturePermission() {
        android.util.Log.d(TAG, "requestScreenCapturePermission called")

        // 跳转到无障碍服务 APK 的权限请求 Activity
        android.app.AlertDialog.Builder(this)
            .setTitle("录屏权限")
            .setMessage("录屏权限由独立的无障碍服务 APK 管理。\n\n点击下方按钮将打开权限管理界面。")
            .setPositiveButton("去授权") { _, _ ->
                try {
                    val intent = Intent().apply {
                        component = ComponentName(
                            "com.xiaomo.androidforclaw.accessibility",
                            "com.xiaomo.androidforclaw.accessibility.PermissionActivity"
                        )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    android.util.Log.d(TAG, "Launched PermissionActivity")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to launch PermissionActivity", e)
                    android.widget.Toast.makeText(
                        this,
                        "无法打开权限管理界面，请确保已安装无障碍服务 APK",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 一键授权所有权限
     */
    private fun grantAllPermissions() {
        // 按顺序请求权限
        val isAccessibilityReady = AccessibilityProxy.isConnected.value == true && AccessibilityProxy.isServiceReady()
        if (!isAccessibilityReady) {
            requestAccessibilityPermission()
        } else if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else if (!AccessibilityProxy.isMediaProjectionGranted()) {
            requestScreenCapturePermission()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_ACCESSIBILITY, REQUEST_OVERLAY -> {
                // 权限设置返回，刷新状态
                lifecycleScope.launch {
                    delay(500)
                    updatePermissionStatus()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
