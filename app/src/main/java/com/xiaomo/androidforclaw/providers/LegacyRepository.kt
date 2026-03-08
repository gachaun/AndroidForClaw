package com.xiaomo.androidforclaw.providers

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.config.ProviderConfig
import com.xiaomo.androidforclaw.util.AppConstants

/**
 * Legacy Repository
 * 提供更高级别的 API 封装
 * 根据配置自动选择 OpenAI 或 Anthropic 格式
 *
 * **配置来源**: 从 /sdcard/.androidforclaw/config/openclaw.json 和 models.json 读取配置
 */
class LegacyRepository(
    context: Context,
    apiKey: String? = null,  // 可选，默认从配置读取
    apiBase: String? = null,  // 可选，默认从配置读取
    private val apiType: String? = null  // 可选，默认从配置读取
) {
    companion object {
        private const val TAG = "LegacyRepository"
    }

    // 配置加载器
    private val configLoader = ConfigLoader(context)

    // 加载 OpenClaw 配置
    private val openClawConfig: OpenClawConfig by lazy {
        configLoader.loadOpenClawConfig()
    }

    // 根据 defaultModel 找到对应的 provider
    private fun getProviderForDefaultModel(): ProviderConfig? {
        val defaultModel = openClawConfig.agent.defaultModel
        val providerName = configLoader.findProviderByModelId(defaultModel)
        Log.d(TAG, "Default model: $defaultModel, Provider: $providerName")
        return providerName?.let { configLoader.getProviderConfig(it) }
    }

    // 从配置中读取 API 配置（优先使用构造函数参数，否则从配置文件读取）
    private val actualApiKey: String by lazy {
        apiKey ?: run {
            // 从 defaultModel 对应的 provider 读取 apiKey
            val provider = getProviderForDefaultModel() ?: configLoader.getProviderConfig("openrouter")
            provider?.apiKey ?: AppConstants.OPENROUTER_API_KEY
        }
    }

    private val actualApiBase: String by lazy {
        apiBase ?: run {
            // 从 defaultModel 对应的 provider 读取 baseUrl
            val provider = getProviderForDefaultModel() ?: configLoader.getProviderConfig("openrouter")
            provider?.baseUrl ?: "https://openrouter.ai/api/v1"
        }
    }

    private val actualApiType: String by lazy {
        apiType ?: run {
            // 从 defaultModel 对应的 provider 读取 api 类型
            val provider = getProviderForDefaultModel() ?: configLoader.getProviderConfig("openrouter")
            provider?.api ?: "openai-completions"
        }
    }

    // 根据 API 类型选择 Provider
    private val openAIProvider by lazy {
        val provider = getProviderForDefaultModel() ?: configLoader.getProviderConfig("anthropic")
        Log.d(TAG, "Creating OpenAI Provider:")
        Log.d(TAG, "  Provider name: ${provider?.let { configLoader.findProviderByModelId(openClawConfig.agent.defaultModel) }}")
        Log.d(TAG, "  authHeader from config: ${provider?.authHeader}")
        Log.d(TAG, "  Final authHeader value: ${provider?.authHeader ?: true}")
        LegacyProviderOpenAI(
            apiKey = actualApiKey,
            apiBase = actualApiBase,
            providerId = "legacy",
            authHeader = provider?.authHeader ?: true,
            customHeaders = provider?.headers
        )
    }

    private val anthropicProvider by lazy {
        LegacyProviderAnthropic(
            apiKey = actualApiKey,
            apiBase = actualApiBase
        )
    }

    /**
     * 带工具调用的聊天
     *
     * @param messages 消息列表
     * @param tools 工具定义列表
     * @param model 模型 ID（可选，默认从 openclaw.json 的 agent.defaultModel 读取）
     * @param reasoningEnabled Extended Thinking 是否启用（可选，默认从 openclaw.json 的 thinking.enabled 读取）
     */
    suspend fun chatWithTools(
        messages: List<LegacyMessage>,
        tools: List<ToolDefinition>,
        model: String? = null,
        reasoningEnabled: Boolean? = null
    ): LegacyResponse {
        // 从配置读取默认值
        val actualModel = model ?: openClawConfig.agent.defaultModel
        val actualReasoningEnabled = reasoningEnabled ?: openClawConfig.thinking.enabled

        Log.d(TAG, "chatWithTools: ${messages.size} messages, ${tools.size} tools")
        Log.d(TAG, "Model: $actualModel, API Type: $actualApiType")
        Log.d(TAG, "Reasoning enabled: $actualReasoningEnabled, Budget: ${openClawConfig.thinking.budgetTokens}")

        return when (actualApiType) {
            "anthropic-messages" -> {
                anthropicProvider.chat(
                    messages = messages,
                    tools = tools,
                    model = actualModel,
                    thinkingEnabled = actualReasoningEnabled,
                    thinkingBudget = openClawConfig.thinking.budgetTokens
                )
            }
            "openai-completions" -> {
                openAIProvider.chat(
                    messages = messages,
                    tools = tools,
                    model = actualModel
                )
            }
            else -> {
                Log.w(TAG, "Unknown API type: $actualApiType, falling back to Anthropic")
                anthropicProvider.chat(
                    messages = messages,
                    tools = tools,
                    model = actualModel,
                    thinkingEnabled = actualReasoningEnabled,
                    thinkingBudget = openClawConfig.thinking.budgetTokens
                )
            }
        }
    }

    /**
     * 简单聊天（无工具）
     *
     * @param userMessage 用户消息
     * @param systemPrompt 系统提示词（可选）
     * @param reasoningEnabled Extended Thinking 是否启用（可选，默认从 openclaw.json 读取）
     */
    suspend fun simpleChat(
        userMessage: String,
        systemPrompt: String? = null,
        reasoningEnabled: Boolean? = null
    ): String {
        val actualReasoningEnabled = reasoningEnabled ?: openClawConfig.thinking.enabled

        Log.d(TAG, "simpleChat: $userMessage")
        Log.d(TAG, "Reasoning enabled: $actualReasoningEnabled")

        return when (actualApiType) {
            "anthropic-messages" -> {
                anthropicProvider.simpleChat(
                    userMessage = userMessage,
                    systemPrompt = systemPrompt
                )
            }
            "openai-completions" -> {
                openAIProvider.simpleChat(
                    userMessage = userMessage,
                    systemPrompt = systemPrompt
                )
            }
            else -> {
                anthropicProvider.simpleChat(
                    userMessage = userMessage,
                    systemPrompt = systemPrompt
                )
            }
        }
    }

    /**
     * 继续对话
     *
     * @param messages 现有消息列表
     * @param newUserMessage 新的用户消息
     * @param tools 工具定义列表（可选）
     */
    suspend fun continueChat(
        messages: List<LegacyMessage>,
        newUserMessage: String,
        tools: List<ToolDefinition>? = null
    ): LegacyResponse {
        val updatedMessages = messages.toMutableList()
        updatedMessages.add(LegacyMessage("user", newUserMessage))

        return when (actualApiType) {
            "anthropic-messages" -> {
                anthropicProvider.chat(
                    messages = updatedMessages,
                    tools = tools
                )
            }
            "openai-completions" -> {
                openAIProvider.chat(
                    messages = updatedMessages,
                    tools = tools
                )
            }
            else -> {
                anthropicProvider.chat(
                    messages = updatedMessages,
                    tools = tools
                )
            }
        }
    }

    /**
     * 获取当前配置信息（用于调试）
     */
    fun getConfigInfo(): String {
        return """
            |Configuration:
            |  API Key: ${actualApiKey.take(10)}***
            |  API Base: $actualApiBase
            |  API Type: $actualApiType
            |  Default Model: ${openClawConfig.agent.defaultModel}
            |  Max Iterations: ${openClawConfig.agent.maxIterations}
            |  Thinking Enabled: ${openClawConfig.thinking.enabled}
            |  Thinking Budget: ${openClawConfig.thinking.budgetTokens}
        """.trimMargin()
    }
}
