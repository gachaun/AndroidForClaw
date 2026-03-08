package com.xiaomo.androidforclaw.agent.context

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.agent.skills.RequirementsCheckResult
import com.xiaomo.androidforclaw.agent.skills.SkillsLoader
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.channel.ChannelManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Context Builder - 按照 OpenClaw 架构构建 Agent 的上下文
 *
 * OpenClaw 系统提示词的 22 个部分（按构建顺序）:
 * 1. ✅ Identity - 核心身份
 * 2. ✅ Tooling - 工具列表（预排序）
 * 3. ✅ Tool Call Style - 何时叙述工具调用
 * 4. ✅ Safety - 安全保障
 * 5. ✅ Channel Hints - message tool 提示（对应 OpenClaw CLI Quick Reference）
 * 6. ✅ Skills (mandatory) - 技能列表（对齐 OpenClaw 格式）
 * 7. ✅ Memory Recall - memory_search/memory_get（已实现）
 * 8. ✅ User Identity - 用户信息（已实现，基于设备信息）
 * 9. ✅ Current Date & Time - 时区
 * 10. ✅ Workspace - 工作目录
 * 11. ⏸️ Documentation - 文档路径（Android 环境不需要）
 * 12. ✅ Workspace Files (injected) - Bootstrap 注入标记
 * 13. ⏸️ Reply Tags - [[reply_to_current]]（Android App 不需要）
 * 14. ✅ Messaging - Channel hints（部分实现，通过 ChannelManager）
 * 15. ⏸️ Voice (TTS) - 语音输出（暂不需要）
 * 16. ✅ Group Chat / Subagent Context - 额外上下文（已实现，支持 extraSystemPrompt）
 * 17. ⏸️ Reactions Guidance - 反应指南（Android App 不需要）
 * 18. ✅ Reasoning Format - 推理标记（已实现，<think>/<final> tags）
 * 19. ✅ Project Context - Bootstrap Files (SOUL, AGENTS, TOOLS, MEMORY, etc.)
 * 20. ✅ Silent Replies - 静默回复（已实现）
 * 21. ✅ Heartbeats - 心跳（已实现）
 * 22. ✅ Runtime - 运行时信息
 *
 * 总结: 22 个部分中，16 个已实现 ✅，6 个不需要 ⏸️
 */
class ContextBuilder(
    private val context: Context,
    private val toolRegistry: ToolRegistry,
    private val androidToolRegistry: AndroidToolRegistry
) {
    companion object {
        private const val TAG = "ContextBuilder"

        // Bootstrap 文件列表（完整 OpenClaw 9 个文件）
        private val BOOTSTRAP_FILES = listOf(
            "IDENTITY.md",      // 身份定义
            "AGENTS.md",        // Agent 列表
            "SOUL.md",          // 个性和语气
            "TOOLS.md",         // 工具使用指南
            "USER.md",          // 用户信息
            "HEARTBEAT.md",     // 心跳配置
            "BOOTSTRAP.md",     // 新工作区初始化
            "MEMORY.md"         // 长期记忆
        )

        // Prompt Mode (参考 OpenClaw)
        enum class PromptMode {
            FULL,      // 主 Agent - 所有 22 部分
            MINIMAL,   // 子 Agent - 仅核心部分
            NONE       // 最小模式 - 仅基础身份
        }
    }

    // 对齐 OpenClaw: workspace 在外部存储,用户可访问
    // OpenClaw: ~/.openclaw/workspace
    // AndroidForClaw: /sdcard/.androidforclaw/workspace
    private val workspaceDir = File("/sdcard/.androidforclaw/workspace")
    private val skillsLoader = SkillsLoader(context)
    private val channelManager = ChannelManager(context)

    init {
        // 确保 workspace 目录存在
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
            Log.d(TAG, "Created workspace directory: ${workspaceDir.absolutePath}")
        }

        // 初始化 Channel 状态
        channelManager.updateAccountStatus()
    }

    /**
     * 构建系统提示词（按照 OpenClaw 的 22 部分顺序）
     */
    fun buildSystemPrompt(
        userGoal: String = "",
        packageName: String = "",
        testMode: String = "exploration",
        promptMode: PromptMode = PromptMode.FULL,
        extraSystemPrompt: String = "",  // Group Chat / Subagent Context
        reasoningEnabled: Boolean = true  // Reasoning Format
    ): String {
        Log.d(TAG, "Building system prompt (OpenClaw aligned, mode=$promptMode)")

        val parts = mutableListOf<String>()

        // === OpenClaw 22-Part Structure ===

        // 1. Identity (核心身份) - 总是包含
        parts.add(buildIdentitySection())

        // 2. Tooling (工具列表) - 总是包含
        val tooling = buildToolingSection()
        if (tooling.isNotEmpty()) {
            parts.add(tooling)
        }

        // 3. Tool Call Style - FULL 模式
        if (promptMode == PromptMode.FULL) {
            parts.add(buildToolCallStyleSection())
        }

        // 4. Safety - 总是包含
        parts.add(buildSafetySection())

        // 5. Channel Hints (对应 OpenClaw 的 agentPrompt.messageToolHints) - 总是包含
        val channelHints = buildChannelSection()
        if (channelHints.isNotEmpty()) {
            parts.add(channelHints)
        }

        // 6. Skills (XML 格式) - FULL 模式
        if (promptMode == PromptMode.FULL) {
            val skills = buildSkillsSection(userGoal)
            if (skills.isNotEmpty()) {
                parts.add(skills)
            }
        }

        // 7. Memory Recall - FULL 模式
        if (promptMode == PromptMode.FULL) {
            val memoryRecall = buildMemoryRecallSection()
            if (memoryRecall.isNotEmpty()) {
                parts.add(memoryRecall)
            }
        }

        // 8. User Identity - FULL 模式
        if (promptMode == PromptMode.FULL) {
            val userIdentity = buildUserIdentitySection()
            if (userIdentity.isNotEmpty()) {
                parts.add(userIdentity)
            }
        }

        // 9. Current Date & Time - 总是包含
        parts.add(buildTimeSection())

        // 10. Workspace - 总是包含
        parts.add(buildWorkspaceSection())

        // 11. Documentation - 跳过（Android 环境无文档）

        // 12. Workspace Files (injected) - 标记 Bootstrap 注入
        parts.add("<!-- Workspace files injected above -->")

        // 13-15. Reply Tags, Messaging, Voice - 跳过

        // 16. Group Chat / Subagent Context - FULL 模式（如果有 extraSystemPrompt）
        if (promptMode == PromptMode.FULL && extraSystemPrompt.isNotEmpty()) {
            parts.add(buildGroupChatContextSection(extraSystemPrompt, promptMode))
        }

        // 17. Reactions - 跳过

        // 18. Reasoning Format - FULL 模式
        if (promptMode == PromptMode.FULL && reasoningEnabled) {
            parts.add(buildReasoningFormatSection())
        }

        // 19. Project Context (Bootstrap Files) - 总是包含
        val bootstrap = loadBootstrapFiles()
        if (bootstrap.isNotEmpty()) {
            parts.add(bootstrap)
        }

        // 20. Silent Replies - FULL 模式
        if (promptMode == PromptMode.FULL) {
            parts.add(buildSilentRepliesSection())
        }

        // 21. Heartbeats - FULL 模式
        if (promptMode == PromptMode.FULL) {
            val heartbeats = buildHeartbeatsSection()
            if (heartbeats.isNotEmpty()) {
                parts.add(heartbeats)
            }
        }

        // 22. Runtime - 总是包含
        parts.add(buildRuntimeSection(userGoal, packageName, testMode))

        val finalPrompt = parts.joinToString("\n\n---\n\n")

        Log.d(TAG, "✅ System prompt 构建完成:")
        Log.d(TAG, "  - 模式: $promptMode")
        Log.d(TAG, "  - 总长度: ${finalPrompt.length} chars")
        Log.d(TAG, "  - 预估 Tokens: ~${finalPrompt.length / 4}")

        return finalPrompt
    }

    // === Section Builders (按 OpenClaw 22 部分) ===

    /**
     * 1. Identity Section
     */
    private fun buildIdentitySection(): String {
        return """
# Identity

You are AndroidForClaw, an AI agent running on Android devices. You can observe and control Android apps through:
- **Observation**: screenshot, get_ui_tree
- **Actions**: tap, swipe, type, long_press
- **Navigation**: home, back, open_app
- **System**: wait, stop, notification

Your core loop: **Observe → Think → Act → Verify**
        """.trimIndent()
    }

    /**
     * 2. Tooling Section (工具列表)
     * 合并通用工具和 Android 平台工具
     */
    private fun buildToolingSection(): String {
        val parts = mutableListOf<String>()

        // 通用工具
        val universalTools = toolRegistry.getToolsDescription()
        if (universalTools.isNotEmpty()) {
            parts.add(universalTools)
        }

        // Android 平台工具
        val androidTools = androidToolRegistry.getToolsDescription()
        if (androidTools.isNotEmpty()) {
            parts.add(androidTools)
        }

        return if (parts.isNotEmpty()) {
            "# Tooling\n\n" + parts.joinToString("\n\n")
        } else {
            ""
        }
    }

    /**
     * 3. Tool Call Style Section
     */
    private fun buildToolCallStyleSection(): String {
        return """
# Tool Call Style

When calling tools:
- Be concise and direct
- Don't narrate obvious actions
- Focus on reasoning and decisions
        """.trimIndent()
    }

    /**
     * 4. Safety Section
     */
    private fun buildSafetySection(): String {
        return """
# Safety

- Never perform destructive actions without confirmation
- Respect user privacy and data
- Handle errors gracefully
- Always verify after operations
        """.trimIndent()
    }

    /**
     * 5. Channel Section (OpenClaw agentPrompt.messageToolHints)
     */
    private fun buildChannelSection(): String {
        val hints = channelManager.getAgentPromptHints()
        return if (hints.isNotEmpty()) {
            "# Channel: ${com.xiaomo.androidforclaw.channel.CHANNEL_META.emoji} ${com.xiaomo.androidforclaw.channel.CHANNEL_META.label}\n\n" +
            hints.joinToString("\n")
        } else {
            ""
        }
    }

    /**
     * 6. Skills Section (对齐 OpenClaw "Skills (mandatory)" 格式)
     */
    private fun buildSkillsSection(userGoal: String): String {
        // Always Skills
        val alwaysSkills = skillsLoader.getAlwaysSkills()

        // Relevant Skills
        val relevantSkills = if (userGoal.isNotEmpty()) {
            skillsLoader.selectRelevantSkills(userGoal, excludeAlways = true)
        } else {
            emptyList()
        }

        // 如果没有任何技能，不生成 Skills Section
        if (alwaysSkills.isEmpty() && relevantSkills.isEmpty()) {
            Log.w(TAG, "⚠️ No skills available (always=0, relevant=0)")
            return ""
        }

        val parts = mutableListOf<String>()
        parts.add("## Skills (mandatory)")
        parts.add("Before replying: scan available skills below.")
        parts.add("- If a skill clearly applies: follow its guidance and workflow")
        parts.add("- If multiple could apply: choose the most specific one")
        parts.add("- If none clearly apply: proceed without skills")
        parts.add("")

        // Always Skills (始终可用的技能)
        if (alwaysSkills.isNotEmpty()) {
            parts.add("### Always Available Skills")
            parts.add("")

            for (skill in alwaysSkills) {
                val reqCheck = skillsLoader.checkRequirements(skill)
                if (reqCheck is RequirementsCheckResult.Satisfied) {
                    parts.add("#### ${skill.metadata.emoji ?: "📋"} ${skill.name}")
                    parts.add(skill.description)
                    parts.add("")
                    parts.add(skill.content)
                    parts.add("")
                    Log.d(TAG, "✅ Injected Always Skill: ${skill.name} (~${skill.estimateTokens()} tokens)")
                }
            }
        }

        // Relevant Skills (与任务相关的技能)
        if (relevantSkills.isNotEmpty()) {
            parts.add("### Relevant Skills for Your Task")
            parts.add("")

            for (skill in relevantSkills) {
                val reqCheck = skillsLoader.checkRequirements(skill)
                if (reqCheck is RequirementsCheckResult.Satisfied) {
                    parts.add("#### ${skill.metadata.emoji ?: "📋"} ${skill.name}")
                    parts.add(skill.description)
                    parts.add("")
                    parts.add(skill.content)
                    parts.add("")
                    Log.d(TAG, "✅ Injected Relevant Skill: ${skill.name} (~${skill.estimateTokens()} tokens)")
                }
            }
        }

        return parts.joinToString("\n")
    }

    /**
     * 7. Memory Recall Section
     */
    private fun buildMemoryRecallSection(): String {
        // 检查是否有 memory tools
        val hasMemorySearch = toolRegistry.contains("memory_search")
        val hasMemoryGet = toolRegistry.contains("memory_get")

        if (!hasMemorySearch && !hasMemoryGet) {
            return ""
        }

        return """
## Memory Recall

Before answering anything about prior work, decisions, dates, people, preferences, or todos:
- Run memory_search on MEMORY.md + memory/*.md
- Then use memory_get to pull only the needed lines

If low confidence after search, say you checked.

**Memory file locations:**
- ${workspaceDir.absolutePath}/MEMORY.md (main memory)
- ${workspaceDir.absolutePath}/memory/*.md (topic-specific memories)

**When to use:**
- User asks "what did I say about..."
- User refers to previous decisions
- User mentions preferences or settings
- You need context from prior sessions
        """.trimIndent()
    }

    /**
     * 8. User Identity Section (对齐 OpenClaw "Authorized Senders")
     */
    private fun buildUserIdentitySection(): String {
        // 从 ChannelManager 获取当前用户信息
        val account = try {
            channelManager.getCurrentAccount()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get current account", e)
            return ""
        }

        // Android App 环境下，用户就是设备本身
        val deviceInfo = "${account.name} (Device ID: ${account.deviceId?.take(12)}...)"

        return """
## Authorized User

You are running on: $deviceInfo
This is a single-user Android device. All requests come from the device owner.
        """.trimIndent()
    }

    /**
     * 9. Current Date & Time Section
     */
    private fun buildTimeSection(): String {
        val timezone = java.util.TimeZone.getDefault().id
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale.getDefault())
            .format(Date())
        return """
# Current Date & Time

Timezone: $timezone
Current Time: $currentTime
        """.trimIndent()
    }

    /**
     * 10. Workspace Section
     */
    /**
     * 10. Workspace Section (对齐 OpenClaw 格式)
     * OpenClaw: ~/.openclaw/workspace
     * AndroidForClaw: /sdcard/.androidforclaw/workspace
     */
    private fun buildWorkspaceSection(): String {
        val workspacePath = workspaceDir.absolutePath
        return """
## Workspace

Your working directory is: $workspacePath

Treat this directory as the single global workspace for file operations unless explicitly instructed otherwise.

- Long-term memory: $workspacePath/memory/MEMORY.md (write important facts here)
- Custom skills: $workspacePath/skills/{skill-name}/SKILL.md
- User-editable files: You can read/write any files in this directory
        """.trimIndent()
    }

    /**
     * 16. Group Chat / Subagent Context Section
     */
    private fun buildGroupChatContextSection(extraSystemPrompt: String, promptMode: PromptMode): String {
        // 根据 prompt mode 选择合适的标题
        val contextHeader = when (promptMode) {
            PromptMode.MINIMAL -> "## Subagent Context"
            else -> "## Group Chat Context"
        }

        return """
$contextHeader

$extraSystemPrompt
        """.trimIndent()
    }

    /**
     * 18. Reasoning Format Section
     */
    private fun buildReasoningFormatSection(): String {
        return """
## Reasoning Format

ALL internal reasoning MUST be inside <think>...</think>.
Do not output any analysis outside <think>.
Format every reply as <think>...</think> then <final>...</final>, with no other text.
Only the final user-visible reply may appear inside <final>.
Only text inside <final> is shown to the user; everything else is discarded and never seen by the user.

Example:
<think>Short internal reasoning.</think>
<final>Hey there! What would you like to do next?</final>
        """.trimIndent()
    }

    /**
     * 20. Silent Replies Section
     */
    private fun buildSilentRepliesSection(): String {
        val token = "[[SILENT]]"
        return """
## Silent Replies

When you have nothing to say, respond with ONLY: $token

⚠️ Rules:
- It must be your ENTIRE message — nothing else
- Never append it to an actual response (never include "$token" in real replies)
- Never wrap it in markdown or code blocks

❌ Wrong: "Here's help... $token"
❌ Wrong: "$token"
✅ Right: $token

**When to use:**
- After executing a tool that speaks for itself
- When acknowledging without adding value
- When the tool output is the complete answer
        """.trimIndent()
    }

    /**
     * 21. Heartbeats Section
     */
    private fun buildHeartbeatsSection(): String {
        // 从 workspace 读取 HEARTBEAT.md（如果存在）
        val heartbeatFile = File(workspaceDir, "HEARTBEAT.md")
        val heartbeatPrompt = if (heartbeatFile.exists()) {
            try {
                heartbeatFile.readText().trim().lines().firstOrNull()?.trim() ?: "HEARTBEAT_CHECK"
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read HEARTBEAT.md", e)
                "HEARTBEAT_CHECK"
            }
        } else {
            "HEARTBEAT_CHECK"
        }

        return """
## Heartbeats

Heartbeat prompt: $heartbeatPrompt

If you receive a heartbeat poll (a user message matching the heartbeat prompt above), and there is nothing that needs attention, reply exactly:
HEARTBEAT_OK

AndroidForClaw treats a leading/trailing "HEARTBEAT_OK" as a heartbeat ack (and may discard it).

If something needs attention, do NOT include "HEARTBEAT_OK"; reply with the alert text instead.

**Examples:**
- User: "$heartbeatPrompt" → You: HEARTBEAT_OK (if all is well)
- User: "$heartbeatPrompt" → You: "⚠️ Screenshot failed 3 times, accessibility service may be down" (if issue)
        """.trimIndent()
    }

    /**
     * 22. Runtime Section (详细运行时信息，包含 Channel 信息)
     */
    private fun buildRuntimeSection(userGoal: String, packageName: String, testMode: String): String {
        val runtime = buildRuntimeInfo()
        val channelInfo = channelManager.getRuntimeChannelInfo()

        val taskInfo = mutableListOf<String>()
        if (userGoal.isNotEmpty()) taskInfo.add("**Goal**: $userGoal")
        if (packageName.isNotEmpty()) taskInfo.add("**Package**: $packageName")
        if (testMode.isNotEmpty()) taskInfo.add("**Mode**: $testMode (exploration=动态决策 / planning=先规划后执行)")

        return """
# Runtime

$runtime
$channelInfo

${if (taskInfo.isNotEmpty()) "## Current Task\n" + taskInfo.joinToString("\n") else ""}
        """.trimIndent()
    }

    /**
     * 加载 Bootstrap 文件（参考 OpenClaw 的 _load_bootstrap_files）
     * 优先级: workspace > assets (bundled)
     *
     * 对齐 OpenClaw:
     * - 以 "# Project Context" 开头
     * - SOUL.md 特殊处理（添加 persona 提示）
     * - 每个文件用 "## filename" 分隔
     */
    private fun loadBootstrapFiles(): String {
        val loadedFiles = mutableListOf<Pair<String, String>>() // (filename, content)
        var hasSoulFile = false

        for (filename in BOOTSTRAP_FILES) {
            try {
                // 1. 先尝试从 workspace 加载（用户自定义）
                val workspaceFile = File(workspaceDir, filename)
                val content = if (workspaceFile.exists()) {
                    Log.d(TAG, "Loaded bootstrap from workspace: $filename")
                    workspaceFile.readText()
                } else {
                    // 2. 从 assets 加载（内置）
                    try {
                        val inputStream = context.assets.open("bootstrap/$filename")
                        val content = inputStream.bufferedReader().use { it.readText() }
                        Log.d(TAG, "Loaded bootstrap from assets: $filename (${content.length} chars)")
                        content
                    } catch (e: Exception) {
                        Log.w(TAG, "Bootstrap file not found: $filename")
                        null
                    }
                }

                if (content != null && content.isNotEmpty()) {
                    loadedFiles.add(filename to content)
                    if (filename.equals("SOUL.md", ignoreCase = true)) {
                        hasSoulFile = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $filename", e)
            }
        }

        if (loadedFiles.isEmpty()) {
            return ""
        }

        // 构建 Project Context section (对齐 OpenClaw)
        val parts = mutableListOf<String>()
        parts.add("# Project Context")
        parts.add("")
        parts.add("The following project context files have been loaded:")

        if (hasSoulFile) {
            parts.add("If SOUL.md is present, embody its persona and tone. Avoid stiff, generic replies; follow its guidance unless higher-priority instructions override it.")
        }
        parts.add("")

        // 每个文件以 "## filename" 开头
        for ((filename, content) in loadedFiles) {
            parts.add("## $filename")
            parts.add("")
            parts.add(content)
            parts.add("")
        }

        return parts.joinToString("\n")
    }

    /**
     * 构建运行时信息（详细版，参考 OpenClaw）
     */
    private fun buildRuntimeInfo(): String {
        val model = "Claude Opus 4.6"
        val host = android.os.Build.MODEL
        val os = "Android ${android.os.Build.VERSION.RELEASE}"
        val api = android.os.Build.VERSION.SDK_INT
        val arch = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        return """
agent: AndroidForClaw v3.0
model: $model
host: $host
os: $os (API $api)
arch: $arch
channel: Android App
        """.trimIndent()
    }

    /**
     * 获取 Skills 统计信息（用于日志）
     */
    fun getSkillsStatistics(): String {
        try {
            val stats = skillsLoader.getStatistics()
            return stats.getReport()
        } catch (e: Exception) {
            Log.e(TAG, "获取 Skills 统计失败", e)
            return ""
        }
    }
}
