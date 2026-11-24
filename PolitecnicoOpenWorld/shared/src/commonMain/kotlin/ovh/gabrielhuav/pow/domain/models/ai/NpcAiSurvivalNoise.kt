package ovh.gabrielhuav.pow.domain.models.ai

import ovh.gabrielhuav.pow.domain.models.survival.NoiseEvent

/** Los productores pueden correr en hilos distintos; la memoria la consume el tick del host. */
fun NpcAiManager.queueSurvivalNoise(event: NoiseEvent): Boolean =
    survivalNoiseLock.ejecutar { survivalNoiseEnabled && survivalNoiseQueue.offer(event) }

fun NpcAiManager.setSurvivalNoiseEnabled(enabled: Boolean) {
    survivalNoiseLock.ejecutar {
        survivalNoiseEnabled = enabled
        if (!enabled) {
            while (survivalNoiseQueue.poll() != null) Unit
            survivalNoiseResetPending = true
        }
    }
}

fun NpcAiManager.clearSurvivalNoise() {
    survivalNoiseLock.ejecutar {
        while (survivalNoiseQueue.poll() != null) Unit
        survivalNoiseResetPending = true
    }
}

internal fun NpcAiManager.processSurvivalNoise(nowMs: Long) {
    survivalNoiseNowMs = nowMs
    if (!survivalNoiseEnabled && !survivalNoiseResetPending) return
    survivalNoiseLock.ejecutar {
        if (survivalNoiseResetPending) {
            survivalNoiseMemory.clear()
            hordeNoiseTarget = null
            hordeNoiseMembers.clear()
            survivalNoiseResetPending = false
        }
        while (true) {
            val event = survivalNoiseQueue.poll() ?: break
            survivalNoiseMemory.offer(event, nowMs)
        }
    }
}

internal fun NpcAiManager.startHordeNoise(event: NoiseEvent?, nowMs: Long) {
    hordeNoiseMembers.clear()
    hordeNoiseTarget = event
    hordeNoiseUntilMs = nowMs + 30_000L
}

internal fun NpcAiManager.rememberHordeMember(id: String) {
    if (hordeNoiseTarget != null && hordeNoiseMembers.size < HORDE_SIZE) hordeNoiseMembers.add(id)
}

internal fun NpcAiManager.hordeNoiseFor(id: String): NoiseEvent? =
    if (survivalNoiseNowMs < hordeNoiseUntilMs && id in hordeNoiseMembers) hordeNoiseTarget else null

internal fun NpcAiManager.strongestSurvivalNoise(lat: Double, lon: Double, nowMs: Long): NoiseEvent? =
    survivalNoiseMemory.strongestAt(lat, lon, nowMs)

/** ¿Nace un zombi en una zona ya despejada? Solo cuenta con supervivencia encendida (t18). */
internal fun NpcAiManager.isClearedZone(npc: ovh.gabrielhuav.pow.domain.models.map.Npc): Boolean =
    survivalNoiseEnabled && ovh.gabrielhuav.pow.domain.models.survival.ZoneKillRules.isCleared(zoneKills, npc.location.latitude, npc.location.longitude)
