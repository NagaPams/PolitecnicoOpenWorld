package ovh.gabrielhuav.pow.features.settings.models

import kotlinx.serialization.Serializable
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.platform.log.powLog

// Disposición de los controles en pantalla del mundo abierto: cuánto se mueve, escala y se
// transparenta cada bloque respecto a su sitio de fábrica. Todo en cero/uno = igual que antes.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md (Editor de controles).

/** Ajuste de un bloque de controles. Los desplazamientos son en dp respecto a su posición por defecto. */
@Serializable
data class ControlSlotLayout(
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
) {
    /** Devuelve el ajuste dentro de rangos seguros (un JSON viejo o editado a mano nunca saca el control de la pantalla). */
    fun clamped(): ControlSlotLayout = ControlSlotLayout(
        offsetXDp = offsetXDp.coerceIn(-CONTROL_OFFSET_MAX_DP, CONTROL_OFFSET_MAX_DP),
        offsetYDp = offsetYDp.coerceIn(-CONTROL_OFFSET_MAX_DP, CONTROL_OFFSET_MAX_DP),
        scale = scale.coerceIn(CONTROL_SLOT_SCALE_MIN, CONTROL_SLOT_SCALE_MAX),
        opacity = opacity.coerceIn(CONTROL_OPACITY_MIN, 1f),
    )
}

/** Disposición de los dos bloques del mundo: movimiento (joystick/D-pad) y acción (A/B/X/Y). */
@Serializable
data class ControlsLayout(
    val version: Int = 1,
    val movement: ControlSlotLayout = ControlSlotLayout(),
    val actions: ControlSlotLayout = ControlSlotLayout(),
) {
    fun clamped(): ControlsLayout = copy(movement = movement.clamped(), actions = actions.clamped())

    /** ¿Es la disposición de fábrica? */
    val isDefault: Boolean get() = this == ControlsLayout()

    fun encode(): String = PowJson.encodeToString(ControlsLayout.serializer(), clamped())

    companion object {
        /** Lee una disposición guardada; si está vacía o rota devuelve la de fábrica (nunca lanza). */
        fun decode(json: String): ControlsLayout {
            if (json.isBlank()) return ControlsLayout()
            return try {
                PowJson.decodeFromString(serializer(), json).clamped()
            } catch (e: Exception) {
                powLog("ControlsLayout", "Disposición ilegible, se usa la de fábrica: ${e::class.simpleName}: ${e.message}")
                ControlsLayout()
            }
        }
    }
}

const val CONTROL_OFFSET_MAX_DP = 240f
const val CONTROL_SLOT_SCALE_MIN = 0.6f
const val CONTROL_SLOT_SCALE_MAX = 1.4f
const val CONTROL_OPACITY_MIN = 0.2f
