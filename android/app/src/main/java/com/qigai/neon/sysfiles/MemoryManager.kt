package com.qigai.neon.sysfiles

import com.qigai.neon.connectors.GoogleWorkspaceConnector
import com.qigai.neon.sandbox.RootfsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class MemoryManager(
    private val rootfsManager: RootfsManager,
    private val googleWorkspace: GoogleWorkspaceConnector,
) {
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    private fun memoryFile(): File = File(rootfsManager.memoryDir.apply { mkdirs() }, "memory.md")

    suspend fun load() = withContext(Dispatchers.IO) {
        val f = memoryFile()
        if (!f.exists()) {
            _items.value = emptyList()
            return@withContext
        }
        val lines = f.readLines()
            .map { it.trim() }
            .filter { it.startsWith("- ") }
            .map { it.removePrefix("- ").trim() }
            .filter { it.isNotEmpty() }
        _items.value = lines
    }

    suspend fun addMemory(entry: String) = withContext(Dispatchers.IO) {
        val clean = entry.trim()
        if (clean.isEmpty()) return@withContext
        val next = (_items.value + clean).distinct()
        saveItems(next)
    }

    suspend fun removeMemory(entry: String) = withContext(Dispatchers.IO) {
        val next = _items.value.filterNot { it == entry }
        saveItems(next)
    }

    private suspend fun saveItems(list: List<String>) {
        _items.value = list
        val content = buildString {
            appendLine("# Neon 个人长期记忆")
            appendLine()
            list.forEach { appendLine("- $it") }
        }
        memoryFile().writeText(content)
        googleWorkspace.uploadOrUpdateTextFile("记忆", "memory.md", content)
    }

    fun formatForPrompt(): String {
        val list = _items.value
        if (list.isEmpty()) return ""
        return "【个人长期记忆】\n" + list.joinToString("\n") { "- $it" }
    }
}
