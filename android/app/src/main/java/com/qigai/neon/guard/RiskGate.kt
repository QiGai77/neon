package com.qigai.neon.guard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class GuardDecision { ALLOW_ONCE, ALLOW_LEASE, DENY }

data class AccessLease(
    val scopeKey: String,
    val expiresAtMs: Long,
)

data class PendingGuardRequest(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetLabel: String,
    val targetPackage: String,
    val reasons: List<String>,
    val requireBiometric: Boolean,
    val stepSummary: String,
    val createdAtMs: Long = System.currentTimeMillis(),
)

class RiskGate {
    private val leases = mutableMapOf<String, AccessLease>()
    private val _pendingRequest = MutableStateFlow<PendingGuardRequest?>(null)
    val pendingRequest: StateFlow<PendingGuardRequest?> = _pendingRequest.asStateFlow()

    private var activeDeferred: CompletableDeferred<GuardDecision>? = null

    fun hasValidLease(scopeKey: String): Boolean {
        val lease = leases[scopeKey] ?: return false
        if (System.currentTimeMillis() > lease.expiresAtMs) {
            leases.remove(scopeKey)
            return false
        }
        return true
    }

    fun clearAllLeases() {
        leases.clear()
    }

    suspend fun requestApproval(request: PendingGuardRequest, scopeKey: String): GuardDecision {
        if (!request.requireBiometric && hasValidLease(scopeKey)) {
            return GuardDecision.ALLOW_ONCE
        }
        val deferred = CompletableDeferred<GuardDecision>()
        activeDeferred = deferred
        _pendingRequest.value = request
        return try {
            val decision = deferred.await()
            if (decision == GuardDecision.ALLOW_LEASE) {
                leases[scopeKey] = AccessLease(scopeKey, System.currentTimeMillis() + 15 * 60_000L)
            }
            decision
        } finally {
            if (_pendingRequest.value?.id == request.id) {
                _pendingRequest.value = null
            }
            activeDeferred = null
        }
    }

    fun resolve(decision: GuardDecision) {
        activeDeferred?.complete(decision)
    }
}
