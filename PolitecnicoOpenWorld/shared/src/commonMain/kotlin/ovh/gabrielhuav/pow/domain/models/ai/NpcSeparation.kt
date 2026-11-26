package ovh.gabrielhuav.pow.domain.models.ai

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcNavState
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.map.VehicleFootprint
import ovh.gabrielhuav.pow.domain.models.map.bodyDimensions
import ovh.gabrielhuav.pow.domain.models.map.offsetMeters
import kotlin.math.cos
import kotlin.math.sqrt

// Separación de cuerpos a pie: los NPC que van a pie (peatones, zombis, policías) no pueden quedar encima del
// jugador ni unos sobre otros. Sin esto los zombis y policías perseguían hasta el centro del jugador y toda una
// horda se apilaba en el mismo punto. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Distancia mínima entre centros de dos cuerpos a pie (dos radios de 0,35 m). */
internal const val BODY_SEPARATION_METERS = 0.7
private const val METERS_PER_DEGREE_LAT = 111_320.0

private fun Npc.walksOnFoot(): Boolean =
    displayName.isNullOrEmpty() && !isDying && health > 0f &&
        (type == NpcType.PERSON || type == NpcType.ZOMBIE || type == NpcType.POLICE_COP)

/**
 * Empuja a los NPC a pie de [npcs] que estén a menos de [BODY_SEPARATION_METERS] del jugador ([playerLat], [playerLon]) o
 * entre sí. Un empujón que metería al NPC dentro de una barrera (edificio) se descarta. Modifica la lista en su sitio.
 */
internal fun NpcAiManager.separateBodies(npcs: MutableList<Npc>, playerLat: Double, playerLon: Double) {
    val lonScale = METERS_PER_DEGREE_LAT * cos(playerLat * kotlin.math.PI / 180.0)
    for (i in npcs.indices) {
        val a = npcs[i]
        if (!a.walksOnFoot()) continue
        val dyPlayer = (a.location.latitude - playerLat) * METERS_PER_DEGREE_LAT
        val dxPlayer = (a.location.longitude - playerLon) * lonScale
        if (dyPlayer * dyPlayer + dxPlayer * dxPlayer < BODY_SEPARATION_METERS * BODY_SEPARATION_METERS) {
            pushTo(npcs, i, playerLat, playerLon, 1.0, lonScale)
        }
    }
    for (i in npcs.indices) {
        val a = npcs[i]
        if (!a.walksOnFoot()) continue
        for (j in i + 1 until npcs.size) {
            val b = npcs[j]
            if (!b.walksOnFoot()) continue
            val dy = (b.location.latitude - a.location.latitude) * METERS_PER_DEGREE_LAT
            if (dy > BODY_SEPARATION_METERS || dy < -BODY_SEPARATION_METERS) continue
            val dx = (b.location.longitude - a.location.longitude) * lonScale
            if (dy * dy + dx * dx >= BODY_SEPARATION_METERS * BODY_SEPARATION_METERS) continue
            // El primero cede la mitad; el segundo cubre lo que falta, ya medido contra el primero desplazado.
            pushTo(npcs, i, b.location.latitude, b.location.longitude, 0.5, lonScale)
            pushTo(npcs, j, npcs[i].location.latitude, npcs[i].location.longitude, 1.0, lonScale)
        }
    }
}

/** Mueve el NPC [index] lejos de ([fromLat], [fromLon]) hasta separarlo [share] de lo que falta para la separación. */
private fun NpcAiManager.pushTo(npcs: MutableList<Npc>, index: Int, fromLat: Double, fromLon: Double, share: Double, lonScale: Double) {
    val npc = npcs[index]
    var dy = (npc.location.latitude - fromLat) * METERS_PER_DEGREE_LAT
    var dx = (npc.location.longitude - fromLon) * lonScale
    var current = sqrt(dx * dx + dy * dy)
    if (current < 1e-6) {
        // Justo encima: se aparta hacia un lado distinto por NPC para que una horda no se vuelva a apilar.
        val angle = (index % 8) * (kotlin.math.PI / 4.0)
        dx = cos(angle)
        dy = kotlin.math.sin(angle)
        current = 0.0
    }
    val push = (BODY_SEPARATION_METERS - current.coerceAtMost(BODY_SEPARATION_METERS)) * share
    if (push <= 0.0) return
    val length = if (current == 0.0) 1.0 else current
    val target = GeoPoint(
        npc.location.latitude + (dy / length) * push / METERS_PER_DEGREE_LAT,
        npc.location.longitude + (dx / length) * push / lonScale,
    )
    if (exteriorCollisions?.segmentBlocked(npc.location, target) == true) return
    npcs[index] = npc.copy(location = target)
}

/** Lo máximo que se mueve un coche por tick al separarlo de otro (evita saltos visibles). */
internal const val CAR_SEPARATION_MAX_STEP_METERS = 0.5
/** Dos coches a más de esto (m) en latitud no pueden solaparse: descarta rápido sin construir rectángulos. */
private const val CAR_COARSE_METERS = 8.0

private fun Npc.isTrafficCar(): Boolean = type == NpcType.CAR && displayName.isNullOrEmpty() && !isDying

private fun Npc.footprint() = VehicleFootprint(location, (rotationAngle + 90f) % 360f, bodyDimensions)

/**
 * Separa a los coches del tráfico que ya quedaron solapados (cruces, giros, aparición). Un estacionado no se mueve:
 * el otro sale entero; entre dos en marcha, cada uno cede la mitad. Máximo [CAR_SEPARATION_MAX_STEP_METERS] por tick,
 * y un empujón que metería al coche en un edificio se descarta. Modifica la lista en su sitio.
 */
internal fun NpcAiManager.separateCars(npcs: MutableList<Npc>) {
    for (i in npcs.indices) {
        if (!npcs[i].isTrafficCar()) continue
        for (j in i + 1 until npcs.size) {
            val a = npcs[i]
            val b = npcs[j]
            if (!b.isTrafficCar()) continue
            val dy = (b.location.latitude - a.location.latitude) * METERS_PER_DEGREE_LAT
            if (dy > CAR_COARSE_METERS || dy < -CAR_COARSE_METERS) continue
            val fa = a.footprint()
            val fb = b.footprint()
            if (!fa.intersects(fb)) continue
            val aFixed = a.navState == NpcNavState.PARKED
            val bFixed = b.navState == NpcNavState.PARKED
            if (aFixed && bFixed) continue
            val exitA = fa.separatingOffsetFrom(fb)
            val share = if (aFixed || bFixed) 1.0 else 0.5
            if (!aFixed) moveCar(npcs, i, exitA.northMeters * share, exitA.eastMeters * share)
            if (!bFixed) {
                val exitB = npcs[j].footprint().separatingOffsetFrom(npcs[i].footprint())
                moveCar(npcs, j, exitB.northMeters * share, exitB.eastMeters * share)
            }
        }
    }
}

private fun NpcAiManager.moveCar(npcs: MutableList<Npc>, index: Int, north: Double, east: Double) {
    val length = sqrt(north * north + east * east)
    if (length < 1e-6) return
    val k = if (length > CAR_SEPARATION_MAX_STEP_METERS) CAR_SEPARATION_MAX_STEP_METERS / length else 1.0
    val npc = npcs[index]
    val target = npc.location.offsetMeters(north * k, east * k)
    if (exteriorCollisions?.segmentBlocked(npc.location, target) == true) return
    npcs[index] = npc.copy(location = target)
}

