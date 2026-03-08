package com.xiaomo.androidforclaw.accessibility

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream

object MediaProjectionHelper {
    private const val TAG = "MediaProjectionHelper"
    private const val REQUEST_CODE = 10086

    // 录屏权限状态常量
    const val STATUS_AUTHORIZED = "已授权"
    const val STATUS_OBJECT_NULL = "权限已获取但对象为空"
    const val STATUS_NOT_AUTHORIZED = "未授权"

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var isPermissionGranted = false

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    // 用于保存截图的目录 (由外部设置)
    private var screenshotDir: File? = null
    private var appContext: Context? = null

    fun setScreenshotDirectory(dir: File) {
        screenshotDir = dir
        if (!dir.exists()) dir.mkdirs()
    }

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    fun requestMediaProjection(activity: Activity): Boolean {
        Log.d(TAG, "requestMediaProjection: isPermissionGranted=$isPermissionGranted, mediaProjection=$mediaProjection")
        if (isPermissionGranted && mediaProjection != null) {
            Log.d(TAG, "Already granted, returning true")
            return true
        }

        // 启动前台服务 (MediaProjection 需要前台服务)
        val foregroundServiceIntent = Intent(activity, ForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(foregroundServiceIntent)
        } else {
            activity.startService(foregroundServiceIntent)
        }
        Log.d(TAG, "Foreground service started")

        // 请求 MediaProjection 权限
        val mediaProjectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        Log.d(TAG, "Starting activity for result with REQUEST_CODE=$REQUEST_CODE")
        activity.startActivityForResult(intent, REQUEST_CODE)
        Log.d(TAG, "startActivityForResult called")

        return false
    }
    
    /**
     * 检查录屏权限是否已授权
     */
    fun isMediaProjectionGranted(): Boolean {
        return isPermissionGranted && mediaProjection != null
    }
    
    /**
     * 获取录屏权限状态
     */
    fun getPermissionStatus(): String {
        val status = when {
            isPermissionGranted && mediaProjection != null -> STATUS_AUTHORIZED
            isPermissionGranted -> STATUS_OBJECT_NULL
            else -> STATUS_NOT_AUTHORIZED
        }
        Log.d(TAG, "getPermissionStatus: $status (isPermissionGranted=$isPermissionGranted, mediaProjection=$mediaProjection, imageReader=$imageReader)")
        return status
    }
    
    /**
     * 重置录屏权限状态
     *
     * 注意: 这会停止 MediaProjection 并清理资源,
     * 但不会停止前台服务。前台服务需要持续运行以保持权限。
     */
    fun resetPermission() {
        isPermissionGranted = false
        mediaProjection?.stop()
        mediaProjection = null
        imageReader?.close()
        imageReader = null
        Log.d(TAG, "📌 MediaProjection 权限已重置")
    }

    /**
     * 完全释放录屏权限 (包括停止前台服务)
     *
     * ⚠️ 只有在用户明确要求释放权限时才调用此方法!
     * 一般情况下应该让前台服务保持运行,这样权限不会失效。
     */
    fun releasePermissionCompletely(context: Context) {
        resetPermission()

        // 停止前台服务
        try {
            val intent = Intent(context, ForegroundService::class.java)
            context.stopService(intent)
            Log.i(TAG, "✅ 前台服务已停止 - 录屏权限完全释放")
        } catch (e: Exception) {
            Log.e(TAG, "停止前台服务失败", e)
        }
    }

    /**
     * 强制重新申请录屏权限
     */
    fun forceRequestPermission(activity: Activity) {
        resetPermission()
        requestMediaProjection(activity)
    }

    fun handleActivityResult(
        context: Context,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            initScreenCapture(context, resultCode, data)
            isPermissionGranted = true
            return true
        } else if (requestCode == REQUEST_CODE) {
            // 用户拒绝了权限
            isPermissionGranted = false
            mediaProjection = null
            imageReader = null
        }
        return false
    }

    // 初始化截屏
    private fun initScreenCapture(context: Context, resultCode: Int, data: Intent) {
        val mediaProjectionManager =
            context.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Android 14+ 需要先注册 callback
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : android.media.projection.MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    mediaProjection?.stop()
                    mediaProjection = null
                    imageReader?.close()
                    imageReader = null
                }
            }, null)
        } else {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        }

        // 获取屏幕信息
        // 使用真实物理分辨率避免虚拟显示缩放导致坐标错位
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val realMetrics = DisplayMetrics()
        display.getRealMetrics(realMetrics)
        screenWidth = realMetrics.widthPixels
        screenHeight = realMetrics.heightPixels
        screenDensity = realMetrics.densityDpi

        // 创建 ImageReader
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 1
        )

        mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    fun captureScreen(): Pair<Bitmap, String>? {
        return try {
            Log.d(TAG, "captureScreen called")
            Log.d(TAG, "imageReader: $imageReader")
            Log.d(TAG, "mediaProjection: $mediaProjection")

            val image = imageReader?.acquireLatestImage() ?: run {
                Log.e(TAG, "acquireLatestImage returned null")
                return null
            }
            Log.d(TAG, "Image acquired: ${image.width}x${image.height}")
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = createBitmap(screenWidth + rowPadding / pixelStride, screenHeight)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            // 裁剪为实际屏幕大小
            val bitmap2 = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            if (bitmap2 == null) {
                Log.e(TAG, "Failed to create cropped bitmap")
                return null
            }

            Log.d(TAG, "Bitmap created, saving...")
            val path = saveBitmap(bitmap2) ?: ""
            Log.d(TAG, "Bitmap saved to: $path")

            if (path.isEmpty()) {
                Log.e(TAG, "Failed to save bitmap")
                return null
            }

            Log.d(TAG, "Returning result: path=$path, size=${bitmap2.width}x${bitmap2.height}")
            Pair(bitmap2, path)
        } catch (e: Exception) {
            Log.e(TAG, "截图失败", e)
            null
        }
    }

    private fun saveBitmap(bitmap: Bitmap): String? {
        val context = appContext ?: run {
            Log.e(TAG, "Context not set")
            return null
        }

        val dir = screenshotDir ?: run {
            Log.e(TAG, "截图目录未设置")
            return null
        }

        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
        return try {
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            Log.d(TAG, "Saved to file: ${file.absolutePath}")

            // 使用 FileProvider 获取 Content URI
            val uri = try {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "com.xiaomo.androidforclaw.accessibility.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get URI from FileProvider", e)
                return null
            }

            // 授予主应用读取权限
            try {
                context.grantUriPermission(
                    "com.xiaomo.androidforclaw",
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d(TAG, "Granted URI permission to main app: $uri")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to grant URI permission", e)
            }

            uri.toString()  // 返回 Content URI
        } catch (e: Exception) {
            Log.e(TAG, "保存截图失败", e)
            null
        }
    }

}