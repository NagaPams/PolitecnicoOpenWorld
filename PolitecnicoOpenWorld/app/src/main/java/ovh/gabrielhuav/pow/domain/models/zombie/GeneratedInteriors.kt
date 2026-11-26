package ovh.gabrielhuav.pow.domain.models.zombie

import ovh.gabrielhuav.pow.domain.models.interior.InteriorGenerator
import ovh.gabrielhuav.pow.domain.models.interior.InteriorLayout
import ovh.gabrielhuav.pow.domain.models.survival.SearchProfile

// Interiores procedurales de edificios del mapa: el id de la sala lleva todo lo necesario para regenerarla (huella,
// tipo de lugar y tamaño), así que sirve de ruta de navegación y de punto de guardado sin tabla aparte.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object GeneratedInteriors {
    const val PREFIX = "gen_"

    /** Lado de una celda de la planta en píxeles de mundo (dos veces el ancho del jugador). */
    const val CELL_PX = 112f

    /** Id de sala `gen_<huella>_<PERFIL>_<ancho>_<fondo>` (metros enteros). */
    fun idFor(buildingId: Long, profile: SearchProfile, widthM: Double, depthM: Double): String =
        "$PREFIX${buildingId}_${profile.name}_${widthM.toInt()}_${depthM.toInt()}"

    data class Spec(val buildingId: Long, val profile: SearchProfile, val widthM: Double, val depthM: Double)

    fun parse(id: String): Spec? {
        if (!id.startsWith(PREFIX)) return null
        val parts = id.removePrefix(PREFIX).split('_')
        if (parts.size != 4) return null
        val building = parts[0].toLongOrNull() ?: return null
        val profile = SearchProfile.entries.firstOrNull { it.name == parts[1] } ?: return null
        val width = parts[2].toIntOrNull() ?: return null
        val depth = parts[3].toIntOrNull() ?: return null
        return Spec(building, profile, width.toDouble(), depth.toDouble())
    }

    /** Sala jugable de la planta [layout] (la puerta de entrada del sur sale al mapa). */
    fun roomFor(id: String, layout: InteriorLayout): ZombieRoom {
        val cols = layout.cols.toFloat()
        val rows = layout.rows.toFloat()
        val door = ZoneDoor(
            hitboxFrac = NormRect((layout.doorCol - 0.2f) / cols, (rows - 2.2f) / rows, (layout.doorCol + 1.2f) / cols, 1f),
            targetRoomId = ZombieRoomCatalog.EXIT_TO_WORLD,
            label = "Salir al mapa",
            kind = DoorKind.TO_WORLD,
        )
        return ZombieRoom(
            id = id,
            type = ZoneType.BUILDING,
            backgroundAsset = "",
            displayName = "Interior",
            worldWidth = cols * CELL_PX,
            worldHeight = rows * CELL_PX,
            zoom = 1.5f,
            playerSpawnFrac = NormPoint((layout.doorCol + 0.5f) / cols, (rows - 1.5f) / rows),
            doors = listOf(door),
            zombieCount = 3,
            collisionMatrix = CollisionMatrix(layout.toCollisionRows()),
            dimensionsLoaded = true,
            initAttempted = true,
            generatedLayout = layout,
        )
    }

    /** Sala de [id] si es un id generado válido. */
    fun build(id: String): ZombieRoom? {
        val spec = parse(id) ?: return null
        return roomFor(id, InteriorGenerator.generate(spec.buildingId, spec.profile, spec.widthM, spec.depthM))
    }
}
