package com.qigai.neon.connectors

import com.qigai.neon.guard.RiskGate
import com.qigai.neon.sandbox.PRootKernel
import com.qigai.neon.sandbox.RootfsManager
import com.qigai.neon.sandbox.ShellExecutor
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalStdioMcpBridgeTest {
    @Test
    fun `parses json-rpc tools list from stdio output`() {
        val sample = """
            [debug] starting server
            {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"query_db","description":"Run SQL","inputSchema":{"type":"object"}}]}}
        """.trimIndent()

        // Test pure parser method directly
        val bridgeClass = LocalStdioMcpBridge::class.java
        val constructor = bridgeClass.getDeclaredConstructor(ShellExecutor::class.java)
        val dummyExecutor = UnsafeAllocator.allocateShellExecutor()
        val bridge = constructor.newInstance(dummyExecutor)
        val tools = bridge.parseToolsResponse(sample)
        assertEquals(1, tools.size)
        assertEquals("query_db", tools[0].name)
    }
}

private object UnsafeAllocator {
    fun allocateShellExecutor(): ShellExecutor {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val f = unsafeClass.getDeclaredField("theUnsafe")
        f.isAccessible = true
        val unsafe = f.get(null)
        val m = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return m.invoke(unsafe, ShellExecutor::class.java) as ShellExecutor
    }
}
