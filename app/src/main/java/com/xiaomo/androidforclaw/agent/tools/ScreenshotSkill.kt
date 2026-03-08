package com.xiaomo.androidforclaw.agent.tools

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.DeviceController
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import kotlinx.coroutines.delay

/**
 * Screenshot Skill
 * 截取当前屏幕 + UI 树（完整信息）
 *
 * 注意：此工具开销较大（需要截图 + UI 树），请优先使用 get_view_tree。
 * 仅在以下情况使用：
 * - 需要查看视觉信息（颜色、图标、图片）
 * - 操作失败需要视觉确认
 * - UI 树信息不足
 */
class ScreenshotSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "ScreenshotSkill"
    }

    override val name = "screenshot"
    override val description = """
        截取当前屏幕截图（可选：附带 UI 树信息）。

        **开销较大，请优先使用 get_view_tree**。

        返回内容：
        1. 屏幕截图（用于视觉分析）
        2. UI 树信息（如果无障碍服务可用，用于定位元素）

        适用场景：
        - 需要查看颜色、图标、图片等视觉信息
        - 操作失败，需要视觉确认当前状态
        - UI 树无法提供足够信息
        - 需要 OCR 识别文本

        普通情况请用 get_view_tree（更快、更轻量）。
    """.trimIndent()

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = emptyMap(),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        Log.d(TAG, "Taking screenshot with UI tree...")

        // 截图功能现在总是启用，由 MediaProjection 权限控制

        return try {
            // 1. 获取 UI 树（总是启用）
            val (originalNodes, processedNodes) = run {
                val result = DeviceController.detectIcons(context)
                if (result == null) {
                    Log.w(TAG, "无法获取 UI 树（无障碍服务未启用或失败），继续截图")
                    Pair(emptyList(), emptyList())
                } else {
                    result
                }
            }
            Log.d(TAG, "UI tree captured: ${processedNodes.size} nodes")

            // 2. 短暂延迟，确保 UI 稳定
            // ⚡ 优化：减少到 50ms
            delay(50)

            // 3. 截图
            val screenshotResult = DeviceController.getScreenshot(context)
            if (screenshotResult == null) {
                return SkillResult.error("Screenshot failed: result is null")
            }

            val (bitmap, path) = screenshotResult
            Log.d(TAG, "Screenshot captured: ${bitmap.width}x${bitmap.height}, path: $path")

            // 4. 组合输出
            val output = buildString {
                appendLine("【截图信息】")
                appendLine("分辨率: ${bitmap.width}x${bitmap.height}")
                appendLine("路径: $path")
                appendLine()

                appendLine("【屏幕 UI 元素】（共 ${processedNodes.size} 个）")
                appendLine()

                processedNodes.forEachIndexed { index, node ->
                    val text = node.text?.takeIf { it.isNotBlank() }
                        ?: node.contentDesc?.takeIf { it.isNotBlank() }
                        ?: "[无文本]"

                    append("[$index] \"$text\" (${node.point.x}, ${node.point.y})")

                    if (node.clickable) {
                        append(" [可点击]")
                    }

                    appendLine()
                }

                appendLine()
                appendLine("提示：使用坐标 (x,y) 进行 tap 操作")
            }

            SkillResult.success(
                output,
                mapOf(
                    "screenshot_path" to path,
                    "width" to bitmap.width,
                    "height" to bitmap.height,
                    "view_count" to processedNodes.size,
                    "original_count" to originalNodes.size
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot with UI tree failed", e)
            SkillResult.error("Screenshot with UI tree failed: ${e.message}")
        }
    }
}
