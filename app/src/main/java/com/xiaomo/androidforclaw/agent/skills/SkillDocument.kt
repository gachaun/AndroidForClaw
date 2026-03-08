package com.xiaomo.androidforclaw.agent.skills

/**
 * Skill 文档数据模型
 * 对应 AgentSkills.io 格式
 *
 * 文件格式:
 * ---
 * name: skill-name
 * description: 技能描述
 * metadata:
 *   {
 *     "openclaw": {
 *       "always": true,
 *       "emoji": "📱",
 *       "requires": {
 *         "bins": ["binary"],
 *         "env": ["ENV_VAR"],
 *         "config": ["config.key"]
 *       }
 *     }
 *   }
 * ---
 * # Skill Content
 * ...
 */
data class SkillDocument(
    /**
     * Skill 名称（唯一标识）
     * 例如: "mobile-operations", "app-testing"
     */
    val name: String,

    /**
     * Skill 描述（1-2 句话）
     * 例如: "移动设备操作核心技能"
     */
    val description: String,

    /**
     * Skill 元数据
     */
    val metadata: SkillMetadata,

    /**
     * Skill 正文内容（Markdown 格式）
     * 这部分会注入到系统提示词中
     */
    val content: String,

    /**
     * Skill 来源
     * "bundled" - 内置在 assets/skills/
     * "managed" - 来自 /sdcard/.androidforclaw/skills/ (对齐 ~/.openclaw/skills/)
     * "workspace" - 来自 /sdcard/.androidforclaw/workspace/skills/ (对齐 ~/.openclaw/workspace/)
     */
    val source: SkillSource = SkillSource.BUNDLED
) {
    /**
     * 获取完整内容（带标题）
     */
    fun getFormattedContent(): String {
        val emoji = metadata.emoji ?: ""
        val title = if (emoji.isNotEmpty()) "$emoji $name" else name
        return """
# $title

$content
        """.trim()
    }

    /**
     * 估算 Token 数量（粗略估算: 1 token ≈ 4 字符）
     */
    fun estimateTokens(): Int {
        return (content.length / 4.0).toInt()
    }
}

/**
 * Skill 元数据
 */
data class SkillMetadata(
    /**
     * 是否始终加载（启动时加载）
     * true: 加载到所有系统提示词
     * false: 按需加载
     */
    val always: Boolean = false,

    /**
     * Skill 的 emoji 图标
     * 例如: "📱", "🧪", "🐛"
     */
    val emoji: String? = null,

    /**
     * Skill 依赖要求
     */
    val requires: SkillRequires? = null
)

/**
 * Skill 来源枚举
 * 对齐 OpenClaw 三层架构
 */
enum class SkillSource(val displayName: String) {
    BUNDLED("bundled"),      // assets/skills/
    MANAGED("managed"),      // /sdcard/.androidforclaw/skills/ (对齐 ~/.openclaw/skills/)
    WORKSPACE("workspace")   // /sdcard/.androidforclaw/workspace/skills/ (对齐 ~/.openclaw/workspace/)
}

/**
 * Skill 依赖要求
 * 用于检查 Skill 是否可用
 */
data class SkillRequires(
    /**
     * 需要的二进制工具
     * 例如: ["adb", "ffmpeg"]
     */
    val bins: List<String> = emptyList(),

    /**
     * 需要的环境变量
     * 例如: ["ANDROID_HOME", "PATH"]
     */
    val env: List<String> = emptyList(),

    /**
     * 需要的配置项
     * 例如: ["api.key", "device.id"]
     */
    val config: List<String> = emptyList()
) {
    /**
     * 是否有任何依赖
     */
    fun hasRequirements(): Boolean {
        return bins.isNotEmpty() || env.isNotEmpty() || config.isNotEmpty()
    }
}
