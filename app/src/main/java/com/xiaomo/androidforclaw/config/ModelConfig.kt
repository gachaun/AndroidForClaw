package com.xiaomo.androidforclaw.config

import com.google.gson.annotations.SerializedName

/**
 * 模型配置数据类 - 对齐 OpenClaw 的配置格式
 *
 * 配置文件位置：/sdcard/.androidforclaw/config/models.json
 *
 * 参考：OpenClaw src/config/types.models.ts
 */

/**
 * 顶级模型配置
 */
data class ModelsConfig(
    @SerializedName("mode")
    val mode: String = "merge",  // "merge" | "replace"

    @SerializedName("providers")
    val providers: Map<String, ProviderConfig> = emptyMap()
)

/**
 * Provider 配置
 */
data class ProviderConfig(
    @SerializedName("baseUrl")
    val baseUrl: String,  // API 端点基础 URL (必需)

    @SerializedName("apiKey")
    val apiKey: String? = null,  // API 密钥 (可选，支持 ${ENV_VAR} 格式)

    @SerializedName("api")
    val api: String = "openai-completions",  // API 类型

    @SerializedName("auth")
    val auth: String? = null,  // 认证模式: "api-key" | "oauth" | "token"

    @SerializedName("authHeader")
    val authHeader: Boolean = true,  // 是否在 Authorization 头中发送 API 密钥

    @SerializedName("headers")
    val headers: Map<String, String>? = null,  // 自定义 HTTP 头

    @SerializedName("injectNumCtxForOpenAICompat")
    val injectNumCtxForOpenAICompat: Boolean? = null,  // OpenAI 兼容性标志

    @SerializedName("models")
    val models: List<ModelDefinition> = emptyList()  // 模型定义数组
)

/**
 * 模型定义
 */
data class ModelDefinition(
    @SerializedName("id")
    val id: String,  // 模型 ID (e.g., "claude-opus-4-6")

    @SerializedName("name")
    val name: String,  // 模型显示名称

    @SerializedName("api")
    val api: String? = null,  // 模型级 API 类型覆盖 (可选)

    @SerializedName("reasoning")
    val reasoning: Boolean = false,  // 是否支持推理/思考 (Extended Thinking)

    @SerializedName("input")
    val input: List<String> = listOf("text"),  // 支持的输入类型: ["text", "image"]

    @SerializedName("cost")
    val cost: CostConfig = CostConfig(),  // 成本配置

    @SerializedName("contextWindow")
    val contextWindow: Int = 128000,  // 上下文窗口大小 (tokens)

    @SerializedName("maxTokens")
    val maxTokens: Int = 8192,  // 最大完成 token 数

    @SerializedName("headers")
    val headers: Map<String, String>? = null,  // 模型级自定义头 (可选)

    @SerializedName("compat")
    val compat: ModelCompatConfig? = null  // 兼容性配置 (可选)
)

/**
 * 模型兼容性配置
 * 用于处理不同模型 API 的差异
 */
data class ModelCompatConfig(
    @SerializedName("supportsStore")
    val supportsStore: Boolean? = null,  // 是否支持会话存储

    @SerializedName("supportsDeveloperRole")
    val supportsDeveloperRole: Boolean? = null,  // 是否支持 developer 角色

    @SerializedName("supportsReasoningEffort")
    val supportsReasoningEffort: Boolean? = null,  // 是否支持推理力度控制

    @SerializedName("supportsUsageInStreaming")
    val supportsUsageInStreaming: Boolean? = null,  // 流式输出中是否包含 usage

    @SerializedName("supportsStrictMode")
    val supportsStrictMode: Boolean? = null,  // 是否支持严格模式

    @SerializedName("maxTokensField")
    val maxTokensField: String? = null,  // maxTokens 字段名称: "max_completion_tokens" | "max_tokens"

    @SerializedName("thinkingFormat")
    val thinkingFormat: String? = null,  // 思考格式: "openai" | "zai" | "qwen"

    @SerializedName("requiresToolResultName")
    val requiresToolResultName: Boolean? = null,  // 是否需要 tool_result 中的 name 字段

    @SerializedName("requiresAssistantAfterToolResult")
    val requiresAssistantAfterToolResult: Boolean? = null,  // 是否需要在 tool_result 后添加 assistant 消息

    @SerializedName("requiresThinkingAsText")
    val requiresThinkingAsText: Boolean? = null,  // 是否需要将思考内容作为普通文本

    @SerializedName("requiresMistralToolIds")
    val requiresMistralToolIds: Boolean? = null  // 是否需要 Mistral 风格的 tool ID
)

/**
 * 成本配置 (单位: USD per 1M tokens)
 */
data class CostConfig(
    @SerializedName("input")
    val input: Double = 0.0,  // 输入成本

    @SerializedName("output")
    val output: Double = 0.0,  // 输出成本

    @SerializedName("cacheRead")
    val cacheRead: Double = 0.0,  // 缓存读取成本

    @SerializedName("cacheWrite")
    val cacheWrite: Double = 0.0  // 缓存写入成本
)

/**
 * API 类型常量
 * 对齐 OpenClaw src/config/types.models.ts 中的 MODEL_APIS
 */
object ModelApi {
    const val OPENAI_COMPLETIONS = "openai-completions"  // OpenAI Chat Completions API
    const val OPENAI_RESPONSES = "openai-responses"  // OpenAI Responses API (streaming)
    const val OPENAI_CODEX_RESPONSES = "openai-codex-responses"  // OpenAI Codex API
    const val ANTHROPIC_MESSAGES = "anthropic-messages"  // Anthropic Messages API
    const val GOOGLE_GENERATIVE_AI = "google-generative-ai"  // Google Gemini API
    const val GITHUB_COPILOT = "github-copilot"  // GitHub Copilot API
    const val BEDROCK_CONVERSE_STREAM = "bedrock-converse-stream"  // AWS Bedrock API
    const val OLLAMA = "ollama"  // Ollama local API

    // 所有支持的 API 类型
    val ALL_APIS = listOf(
        OPENAI_COMPLETIONS,
        OPENAI_RESPONSES,
        OPENAI_CODEX_RESPONSES,
        ANTHROPIC_MESSAGES,
        GOOGLE_GENERATIVE_AI,
        GITHUB_COPILOT,
        BEDROCK_CONVERSE_STREAM,
        OLLAMA
    )

    /**
     * 检查 API 类型是否有效
     */
    fun isValidApi(api: String): Boolean {
        return api in ALL_APIS
    }

    /**
     * 判断是否为 OpenAI 兼容 API
     */
    fun isOpenAICompat(api: String): Boolean {
        return api in listOf(
            OPENAI_COMPLETIONS,
            OPENAI_RESPONSES,
            OPENAI_CODEX_RESPONSES,
            OLLAMA,  // Ollama 提供 OpenAI 兼容端点
            GITHUB_COPILOT  // GitHub Copilot 使用 OpenAI 格式
        )
    }
}

/**
 * 认证模式常量
 */
object AuthMode {
    const val API_KEY = "api-key"
    const val OAUTH = "oauth"
    const val TOKEN = "token"
    const val AWS_SDK = "aws-sdk"
}
