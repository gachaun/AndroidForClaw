package com.xiaomo.androidforclaw.agent.tools

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.providers.ToolDefinition
import java.io.File

/**
 * Tool Registry - 管理通用底层 Tools
 * 参考 OpenClaw 的 pi-tools (来自 Pi Coding Agent)
 *
 * Tools 是跨平台的通用能力：
 * - read_file, write_file, edit_file: 文件操作
 * - list_dir: 目录列表
 * - exec: 执行 shell 命令
 * - web_fetch: 网页获取
 * - javascript: JavaScript 执行
 *
 * 注意：Android 特定能力在 AndroidToolRegistry 中管理
 */
class ToolRegistry(
    private val context: Context,
    private val taskDataManager: TaskDataManager
) {
    companion object {
        private const val TAG = "ToolRegistry"
    }

    private val tools = mutableMapOf<String, Tool>()

    init {
        registerDefaultTools()
    }

    /**
     * 注册通用 tools（跨平台能力）
     */
    private fun registerDefaultTools() {
        // 使用外部存储的工作空间 (对齐 OpenClaw ~/.openclaw/workspace/)
        val workspace = File("/sdcard/.androidforclaw/workspace")
        workspace.mkdirs()

        // === 文件系统工具 (来自 Pi Coding Agent) ===
        register(ReadFileTool(workspace = workspace))
        register(WriteFileTool(workspace = workspace))
        register(EditFileTool(workspace = workspace))
        register(ListDirTool(workspace = workspace))

        // === 记忆工具 (Memory Recall) ===
        // TODO: Fix Memory tools compilation errors
        // register(MemorySearchTool(workspace = workspace))
        // register(MemoryGetTool(workspace = workspace))

        // === Shell 工具 ===
        register(ExecTool(workingDir = workspace.absolutePath))

        // === 网络工具 ===
        register(WebFetchTool())

        // === JavaScript 执行工具 ===
        register(JavaScriptTool(context))

        Log.d(TAG, "✅ Registered ${tools.size} universal tools (incl. memory_search, memory_get)")
    }

    /**
     * 注册一个 tool
     */
    fun register(tool: Tool) {
        tools[tool.name] = tool
        Log.d(TAG, "Registered tool: ${tool.name}")
    }

    /**
     * 检查是否包含指定 tool
     */
    fun contains(name: String): Boolean = tools.containsKey(name)

    /**
     * 执行 tool
     */
    suspend fun execute(name: String, args: Map<String, Any?>): ToolResult {
        val tool = tools[name]
        if (tool == null) {
            Log.e(TAG, "Unknown tool: $name")
            return ToolResult.error("Unknown tool: $name")
        }

        Log.d(TAG, "Executing tool: $name with args: $args")
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: $name", e)
            ToolResult.error("Execution failed: ${e.message}")
        }
    }

    /**
     * 获取所有 Tool Definitions（用于 LLM function calling）
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return tools.values.map { it.getToolDefinition() }
    }

    /**
     * 获取所有 tools 的描述（用于构建 system prompt）
     */
    fun getToolsDescription(): String {
        return buildString {
            appendLine("## Universal Tools")
            appendLine()
            appendLine("跨平台通用工具，来自 Pi Coding Agent 和 OpenClaw：")
            appendLine()
            tools.values.forEach { tool ->
                appendLine("### ${tool.name}")
                appendLine(tool.description)
                appendLine()
            }
        }
    }

    /**
     * 获取 tool 数量
     */
    fun getToolCount(): Int = tools.size
}
