package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "ToolManager"

object ToolManager {
    private val ALLOWED_SCHEMES = setOf("https", "http")

    val openWebsiteDeclaration = FunctionDeclaration(
        name = "openWebsite",
        description = "Opens a safe http or https website URL in the user's browser when explicitly requested by the user.",
        parameters = mapOf(
            "url" to Schema.string("The full https:// or http:// website URL to open.")
        )
    )

    val toolDefinition: Tool = Tool.functionDeclarations(listOf(openWebsiteDeclaration))

    fun executeToolCall(
        context: Context,
        functionCall: FunctionCallPart,
        onAuditLog: (String) -> Unit
    ): FunctionResponsePart {
        return when (functionCall.name) {
            "openWebsite" -> {
                val rawUrl = functionCall.args["url"]?.jsonPrimitive?.content?.trim().orEmpty()
                val validation = validateUrl(rawUrl)
                if (validation.isFailure) {
                    val reason = validation.exceptionOrNull()?.message ?: "Invalid URL"
                    Log.w(TAG, "Blocked openWebsite call: $reason")
                    onAuditLog("Tool blocked (openWebsite): $reason")
                    return FunctionResponsePart(
                        name = functionCall.name,
                        response = JsonObject(
                            mapOf(
                                "success" to JsonPrimitive(false),
                                "error" to JsonPrimitive(reason)
                            )
                        )
                    )
                }

                val safeUri = validation.getOrThrow()
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, safeUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onAuditLog("Tool executed (openWebsite): ${safeUri.host}")
                    FunctionResponsePart(
                        name = functionCall.name,
                        response = JsonObject(
                            mapOf(
                                "success" to JsonPrimitive(true),
                                "url" to JsonPrimitive(safeUri.toString()),
                                "message" to JsonPrimitive("Opened ${safeUri.host} in browser.")
                            )
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open website", e)
                    onAuditLog("Tool error (openWebsite): ${e.localizedMessage}")
                    FunctionResponsePart(
                        name = functionCall.name,
                        response = JsonObject(
                            mapOf(
                                "success" to JsonPrimitive(false),
                                "error" to JsonPrimitive("No browser available to open this link.")
                            )
                        )
                    )
                }
            }

            else -> {
                onAuditLog("Unknown tool requested: ${functionCall.name}")
                FunctionResponsePart(
                    name = functionCall.name,
                    response = JsonObject(
                        mapOf(
                            "success" to JsonPrimitive(false),
                            "error" to JsonPrimitive("Unsupported function: ${functionCall.name}")
                        )
                    )
                )
            }
        }
    }

    fun validateUrl(rawUrl: String): Result<Uri> {
        if (rawUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("URL cannot be empty."))
        }
        val lower = rawUrl.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("data:") ||
            lower.startsWith("file:") ||
            lower.startsWith("intent:") ||
            lower.startsWith("content:")
        ) {
            return Result.failure(IllegalArgumentException("Disallowed URL scheme."))
        }
        val normalized = if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            "https://$rawUrl"
        } else {
            rawUrl
        }
        val uri = Uri.parse(normalized)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if (scheme !in ALLOWED_SCHEMES || host.isNullOrBlank() || !host.contains(".")) {
            return Result.failure(IllegalArgumentException("URL must be a valid http or https address."))
        }
        return Result.success(uri)
    }
}
