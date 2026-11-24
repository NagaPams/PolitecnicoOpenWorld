package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.os.SystemClock
import android.util.Log
import java.io.File
import ovh.gabrielhuav.pow.domain.platform.MemoryPressureRules

// Recorte automático de NPC y zombis con poca memoria (t21): lee MemAvailable de /proc/meminfo cada pocos segundos y
// ajusta `npcAiManager.memoryFactor`. Si no se puede leer la memoria no cambia nada. Ver 04_MAP_EXTERIOR.md.

private fun readAvailableMemoryMb(): Long = try {
    MemoryPressureRules.parseMemAvailableMb(File("/proc/meminfo").readText())
} catch (e: Exception) {
    -1L
}

internal fun WorldMapViewModel.updateMemoryPressureTick() {
    val now = SystemClock.elapsedRealtime()
    if (now - lastMemoryCheckMs < MemoryPressureRules.CHECK_INTERVAL_MS) return
    lastMemoryCheckMs = now
    val before = npcAiManager.memoryFactor
    val after = MemoryPressureRules.factor(readAvailableMemoryMb(), before)
    if (after != before) {
        npcAiManager.memoryFactor = after
        Log.i("MemoryPressure", "población x$after (antes x$before)")
    }
}
