package com.xiaomo.androidforclaw.config

/**
 * OpenClaw Main Configuration (openclaw.json)
 *
 * 用户只需写想覆盖的字段，其余全用默认值。
 * 解析由 ConfigLoader 的 JSONObject 处理，不依赖 Gson 注解。
 */

data class OpenClawConfig(
    val thinking: ThinkingConfig = ThinkingConfig(),
    val agent: AgentConfig = AgentConfig(),
    val agents: AgentsConfig? = null,
    val models: ModelsConfig? = null,
    val skills: SkillsConfig = SkillsConfig(),
    val plugins: PluginsConfig = PluginsConfig(),
    val tools: ToolsConfig = ToolsConfig(),
    val gateway: GatewayConfig = GatewayConfig(),
    val ui: UIConfig = UIConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val memory: MemoryConfig = MemoryConfig(),
    val session: SessionConfig = SessionConfig(),
    val providers: Map<String, ProviderConfig> = emptyMap()
) {
    fun resolveProviders(): Map<String, ProviderConfig> {
        return models?.providers ?: providers
    }

    fun resolveDefaultModel(): String {
        return agents?.defaults?.model?.primary ?: agent.defaultModel
    }
}

data class ThinkingConfig(
    val enabled: Boolean = true,
    val budgetTokens: Int = 10000
)

data class AgentConfig(
    val maxIterations: Int = 20,
    val defaultModel: String = "openrouter/hunter-alpha",
    val timeout: Long = 300000,
    val retryOnError: Boolean = true,
    val maxRetries: Int = 3,
    val mode: String = "exploration"
)

data class SkillsConfig(
    val allowBundled: List<String>? = null,
    val extraDirs: List<String> = emptyList(),
    val watch: Boolean = true,
    val watchDebounceMs: Long = 250,
    val entries: Map<String, SkillConfig> = emptyMap()
)

data class SkillConfig(
    val enabled: Boolean = true,
    val apiKey: Any? = null,
    val env: Map<String, String>? = null,
    val config: Map<String, Any>? = null
) {
    fun resolveApiKey(): String? {
        return when (apiKey) {
            is String -> apiKey
            is Map<*, *> -> {
                val source = apiKey["source"] as? String
                val id = apiKey["id"] as? String
                if (source == "env" && id != null) System.getenv(id) else null
            }
            else -> null
        }
    }
}

data class ToolsConfig(
    val screenshot: ScreenshotToolConfig = ScreenshotToolConfig()
)

data class ScreenshotToolConfig(
    val enabled: Boolean = true,
    val quality: Int = 85,
    val maxWidth: Int = 1080,
    val format: String = "jpeg"
)

data class GatewayConfig(
    val enabled: Boolean = true,
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val feishu: FeishuChannelConfig = FeishuChannelConfig(),
    val discord: DiscordChannelConfig? = null
)

data class FeishuChannelConfig(
    val enabled: Boolean = false,
    val appId: String = "",
    val appSecret: String = "",
    val encryptKey: String? = null,
    val verificationToken: String? = null,
    val domain: String = "feishu",
    val connectionMode: String = "websocket",
    val webhookPath: String = "/feishu/webhook",
    val webhookPort: Int = 8765,
    val dmPolicy: String = "open",
    val allowFrom: List<String> = listOf("*"),
    val groupPolicy: String = "open",
    val groupAllowFrom: List<String> = emptyList(),
    val requireMention: Boolean = false,
    val groupCommandMentionBypass: String = "never",
    val allowMentionlessInMultiBotGroup: Boolean = false,
    val topicSessionMode: String = "disabled",
    val historyLimit: Int = 20,
    val dmHistoryLimit: Int = 100,
    val textChunkLimit: Int = 4000,
    val chunkMode: String = "length",
    val mediaMaxMb: Double = 20.0,
    val audioMaxDurationSec: Int = 300,
    val enableDocTools: Boolean = true,
    val enableWikiTools: Boolean = true,
    val enableDriveTools: Boolean = true,
    val enableBitableTools: Boolean = true,
    val enableTaskTools: Boolean = true,
    val enableChatTools: Boolean = true,
    val enablePermTools: Boolean = true,
    val enableUrgentTools: Boolean = true,
    val queueMode: String? = "followup",
    val queueCap: Int = 10,
    val queueDropPolicy: String = "old",
    val queueDebounceMs: Int = 100,
    val typingIndicator: Boolean = true,
    val reactionDedup: Boolean = true,
    val debugMode: Boolean = false
)

data class DiscordChannelConfig(
    val enabled: Boolean = false,
    val token: String? = null,
    val name: String? = null,
    val dm: DmPolicyConfig? = null,
    val groupPolicy: String? = null,
    val guilds: Map<String, GuildPolicyConfig>? = null,
    val replyToMode: String? = null,
    val accounts: Map<String, DiscordAccountPolicyConfig>? = null
)

data class DmPolicyConfig(
    val policy: String? = "pairing",
    val allowFrom: List<String>? = null
)

data class GuildPolicyConfig(
    val channels: List<String>? = null,
    val requireMention: Boolean? = true,
    val toolPolicy: String? = null
)

data class DiscordAccountPolicyConfig(
    val enabled: Boolean? = true,
    val token: String? = null,
    val name: String? = null,
    val dm: DmPolicyConfig? = null,
    val guilds: Map<String, GuildPolicyConfig>? = null
)

data class UIConfig(
    val theme: String = "auto",
    val language: String = "zh"
)

data class LoggingConfig(
    val level: String = "INFO",
    val logToFile: Boolean = true
)

data class MemoryConfig(
    val enabled: Boolean = true,
    val path: String = "/sdcard/.androidforclaw/workspace/memory"
)

data class SessionConfig(
    val maxMessages: Int = 100
)

data class AgentsConfig(
    val defaults: AgentDefaultsConfig = AgentDefaultsConfig()
)

data class AgentDefaultsConfig(
    val model: ModelSelectionConfig? = null,
    val bootstrapMaxChars: Int = 20_000,
    val bootstrapTotalMaxChars: Int = 150_000
)

data class ModelSelectionConfig(
    val primary: String? = null,
    val fallbacks: List<String>? = null
)

data class PluginsConfig(
    val entries: Map<String, PluginEntry> = emptyMap()
)

data class PluginEntry(
    val enabled: Boolean = false,
    val skills: List<String> = emptyList()
)

/**
 * 配置常量
 */
object ConfigDefaults {
    const val DEFAULT_THINKING_BUDGET = 10000
    const val MIN_THINKING_BUDGET = 1000
    const val MAX_THINKING_BUDGET = 50000
    const val DEFAULT_MAX_ITERATIONS = 20
    const val MIN_MAX_ITERATIONS = 1
    const val MAX_MAX_ITERATIONS = 100
    const val DEFAULT_TIMEOUT_MS = 300000L
    const val MIN_TIMEOUT_MS = 10000L
    const val MAX_TIMEOUT_MS = 3600000L
    const val DEFAULT_SCREENSHOT_QUALITY = 85
    const val MIN_SCREENSHOT_QUALITY = 10
    const val MAX_SCREENSHOT_QUALITY = 100
    const val DEFAULT_SCREENSHOT_MAX_WIDTH = 1080
    const val DEFAULT_GESTURE_DURATION = 100L
    const val MIN_GESTURE_DURATION = 50L
    const val MAX_GESTURE_DURATION = 5000L
    const val DEFAULT_GATEWAY_PORT = 8080
    const val MIN_GATEWAY_PORT = 1024
    const val MAX_GATEWAY_PORT = 65535
    const val DEFAULT_LOG_MAX_FILE_SIZE = 10 * 1024 * 1024L
    const val DEFAULT_LOG_MAX_FILES = 5
}
