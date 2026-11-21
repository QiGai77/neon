package com.qigai.neon.connectors

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class McpAuthDiscoveryTest {
    @Test
    fun `parses oauth authorization server metadata`() {
        val discovery = McpAuthDiscovery(OkHttpClient())
        val raw = """
            {
              "authorization_endpoint": "https://auth.example.com/authorize",
              "token_endpoint": "https://auth.example.com/token",
              "registration_endpoint": "https://auth.example.com/register",
              "scopes_supported": ["read", "write"]
            }
        """.trimIndent()

        val meta = discovery.parseAuthServerMetadata(raw)
        assertNotNull(meta)
        assertEquals("https://auth.example.com/authorize", meta!!.authorizationEndpoint)
        assertEquals("https://auth.example.com/token", meta.tokenEndpoint)
        assertEquals(listOf("read", "write"), meta.scopesSupported)
    }
}
