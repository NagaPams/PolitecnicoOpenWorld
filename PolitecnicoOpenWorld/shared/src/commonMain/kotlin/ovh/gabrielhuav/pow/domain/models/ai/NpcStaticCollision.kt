package ovh.gabrielhuav.pow.domain.models.ai

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CircleCollider
import ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.map.isInsideBarrier
import ovh.gabrielhuav.pow.domain.models.map.resolveCirclePath
import ovh.gabrielhuav.pow.domain.models.map.resolveNpcPath
import ovh.gabrielhuav.pow.platform.tiempo.ahoraMs

// Colisión ESTÁTICA de los NPC que siguen el navGraph local del campus (`moveLocalNpc`): peatones, gatos y
// coches que salen del estacionamiento. Esas vías se dibujaron sobre el mapa sin conocer los edificios de OSM
// (en el ESCOM ~126 m de las vías peatonales y 14 de 158 nodos caen DENTRO de edificios), y `moveLocalNpc` no
// llamaba al resolvedor: el 46 % de las observaciones de NPC caían dentro de un edificio. Ver 04_MAP_EXTERIOR.md.

private const val LOCAL_NPC_RADIUS_METERS = 0.4

/** ¿El tramo a→b está bloqueado? Cuenta como bloqueado si un extremo cae DENTRO de una barrera. */
internal fun ExteriorCollisionsConfig.segmentBlocked(a: GeoPoint, b: GeoPoint, radiusMeters: Double = LOCAL_NPC_RADIUS_METERS): Boolean =
    isInsideBarrier(a) || isInsideBarrier(b) || !resolveCirclePath(a, b, CircleCollider(radiusMeters)).reachedTarget

/** ¿El nodo local cae dentro de una barrera (edificio)? */
internal fun ExteriorCollisionsConfig.localNodeBlocked(landmark: Landmark, node: LocalNode): Boolean =
    isInsideBarrier(landmark.toGlobalGeoPoint(node.localX, node.localY))

/** ¿Algún nodo o tramo de la vía local toca una barrera? Las vías bloqueadas no se usan para caminar. */
internal fun ExteriorCollisionsConfig.localWayBlocked(landmark: Landmark, way: LocalWay): Boolean {
    var previous: GeoPoint? = null
    for (node in way.nodes) {
        val point = landmark.toGlobalGeoPoint(node.localX, node.localY)
        if (isInsideBarrier(point)) return true
        val from = previous
        if (from != null && segmentBlocked(from, point)) return true
        previous = point
    }
    return false
}

/** Las vías de [ways] que no tocan ningún edificio; sin colisiones cargadas devuelve todas. */
internal fun NpcAiManager.walkableLocalWays(landmark: Landmark, ways: List<LocalWay>): List<LocalWay> {
    val collisions = exteriorCollisions ?: return ways
    return ways.filter { !collisions.localWayBlocked(landmark, it) }
}

/** ¿El nodo es un buen sitio para aparecer (fuera de edificios)? */
internal fun NpcAiManager.localNodeFree(landmark: Landmark, node: LocalNode): Boolean =
    exteriorCollisions?.localNodeBlocked(landmark, node) != true

/**
 * Aplica la colisión estática (edificios y bardas) al resultado de un movimiento directo en línea recta
 * (perseguir, huir, esquivar): si el paso de [from] a [moved] cruzaría una barrera, se queda en el último punto
 * seguro. Los movimientos por vías ya pasan por `resolveNpcPath`; estos (aggro, zombis, cazadores y esquiva)
 * iban "en beeline" y atravesaban los edificios.
 */
internal fun NpcAiManager.collided(from: Npc, moved: Npc): Npc {
    val collisions = exteriorCollisions ?: return moved
    if (moved.location == from.location) return moved
    val chaser = moved.type == NpcType.ZOMBIE || moved.type == NpcType.POLICE_COP
    if (chaser && localPaths.isNotEmpty()) {
        val stepped = followLocalPath(collisions, from, moved)
        if (stepped != null) return stepped
    }
    val resolution = collisions.resolveNpcPath(
        type = moved.type,
        carModel = moved.carModel,
        from = from.location,
        to = moved.location,
        headingDegrees = (moved.rotationAngle + 90f) % 360f,
    )
    if (resolution.reachedTarget) {
        if (slideSides.isNotEmpty()) slideSides.remove(from.id)
        if (stuckSteps.isNotEmpty()) stuckSteps.remove(from.id)
        return moved
    }
    // Zombis y policías cazadores van en línea recta: contra una pared se quedaban pegados. Prueban desvíos y
    // toman el que los deja más cerca de su objetivo (deslizan por la pared); sin salida, el último punto seguro.
    if (chaser) {
        val slid = slideAround(from.id, from.location, moved.location)
        val stuck = (stuckSteps[from.id] ?: 0) + 1
        if (stuckSteps.size >= MAX_REMEMBERED_SIDES) stuckSteps.clear()
        stuckSteps[from.id] = stuck
        // Atorado (sin desvío, o dando vueltas a la pared demasiado tiempo): A* local hacia el jugador.
        if ((slid == null || stuck >= STUCK_STEPS_BEFORE_PATH) && startLocalPath(collisions, from)) {
            followLocalPath(collisions, from, moved)?.let { return it }
        }
        if (slid != null) return moved.copy(location = slid)
    }
    return moved.copy(location = resolution.position)
}

private val SLIDE_ANGLES_DEGREES = doubleArrayOf(50.0, -50.0, 95.0, -95.0)
/** Ángulos que prueba el seguimiento de pared por el lado recordado: de suave a casi dar la vuelta. */
private val FOLLOW_ANGLES_DEGREES = doubleArrayOf(50.0, 95.0, 140.0)
private val BOTH_SIDES = intArrayOf(1, -1)
private const val MAX_REMEMBERED_SIDES = 256
/** Pasos seguidos contra una pared antes de pedir un camino A* (≈ 1 s de vueltas). */
private const val STUCK_STEPS_BEFORE_PATH = 30
private const val PATH_COOLDOWN_MS = 3_000L
private const val PATH_LIFETIME_MS = 12_000L
/** ~0,8 m en grados: un punto del camino se da por alcanzado a esta distancia. */
private const val WAYPOINT_ARRIVE_DEGREES = 0.0000072

/** Camino local de un NPC: puntos cada ~1 m, el siguiente por alcanzar y cuándo caduca. */
internal class FollowPath(val points: List<GeoPoint>, var index: Int, val expiresMs: Long)

/** Pide un camino A* del NPC al jugador; false si no toca (enfriamiento) o no hay camino. */
private fun NpcAiManager.startLocalPath(collisions: ExteriorCollisionsConfig, npc: Npc): Boolean {
    val now = ahoraMs()
    if (now < (nextPathAtMs[npc.id] ?: 0L)) return false
    if (nextPathAtMs.size >= MAX_REMEMBERED_SIDES) { nextPathAtMs.clear(); localPaths.clear() }
    nextPathAtMs[npc.id] = now + PATH_COOLDOWN_MS
    val goal = GeoPoint(aggroPlayerLat, aggroPlayerLon)
    val points = LocalPathfinder.find(collisions, npc.location, goal) ?: return false
    if (points.isEmpty()) return false
    localPaths[npc.id] = FollowPath(points, 0, now + PATH_LIFETIME_MS)
    slideSides.remove(npc.id)
    stuckSteps.remove(npc.id)
    return true
}

/** Un paso por el camino local del NPC, del mismo largo que [moved]; null si no hay camino vigente o se bloquea. */
private fun NpcAiManager.followLocalPath(collisions: ExteriorCollisionsConfig, from: Npc, moved: Npc): Npc? {
    val path = localPaths[from.id] ?: return null
    if (ahoraMs() > path.expiresMs) { localPaths.remove(from.id); return null }
    while (path.index < path.points.size) {
        val wp = path.points[path.index]
        val dLat = wp.latitude - from.location.latitude
        val dLon = wp.longitude - from.location.longitude
        if (kotlin.math.sqrt(dLat * dLat + dLon * dLon) >= WAYPOINT_ARRIVE_DEGREES) break
        path.index++
    }
    if (path.index >= path.points.size) { localPaths.remove(from.id); return null }
    val wp = path.points[path.index]
    val toLat = wp.latitude - from.location.latitude
    val toLon = wp.longitude - from.location.longitude
    val toLen = kotlin.math.sqrt(toLat * toLat + toLon * toLon)
    val stepLat = moved.location.latitude - from.location.latitude
    val stepLon = moved.location.longitude - from.location.longitude
    val stepLen = kotlin.math.sqrt(stepLat * stepLat + stepLon * stepLon).coerceAtMost(toLen)
    val next = GeoPoint(from.location.latitude + toLat / toLen * stepLen, from.location.longitude + toLon / toLen * stepLen)
    if (!collisions.resolveCirclePath(from.location, next, CircleCollider(LOCAL_NPC_RADIUS_METERS)).reachedTarget) {
        localPaths.remove(from.id)
        return null
    }
    return moved.copy(location = next)
}

/**
 * Rodeo de una pared con MEMORIA de lado (t17): la primera vez elige el desvío más cercano al objetivo
 * ([slideAroundBarrier]) y recuerda de qué lado fue; los pasos siguientes siguen por ese mismo lado (50°, 95° y
 * 140°) y solo cambian de lado si ese está cerrado. Sin memoria el zombi vacilaba entre los dos lados en una
 * esquina cóncava (pared en U) y se quedaba atorado. La memoria se borra al volver a avanzar en línea recta.
 */
internal fun NpcAiManager.slideAround(id: String, from: GeoPoint, desired: GeoPoint): GeoPoint? {
    val collisions = exteriorCollisions ?: return null
    val remembered = slideSides[id] ?: 0
    if (remembered == 0) {
        val best = collisions.slideAroundBarrier(from, desired) ?: return null
        if (slideSides.size >= MAX_REMEMBERED_SIDES) slideSides.clear()
        slideSides[id] = slideSideOf(from, desired, best)
        return best
    }
    for (turn in BOTH_SIDES) {
        val side = remembered * turn
        for (angle in FOLLOW_ANGLES_DEGREES) {
            val candidate = collisions.slideStep(from, desired, side * angle) ?: continue
            if (side != remembered) slideSides[id] = side
            return candidate
        }
    }
    slideSides.remove(id)
    return null
}

/** +1 si [chosen] se desvió con ángulo positivo respecto a [from]→[desired], -1 si negativo. */
private fun slideSideOf(from: GeoPoint, desired: GeoPoint, chosen: GeoPoint): Int {
    val dLat = desired.latitude - from.latitude
    val dLon = desired.longitude - from.longitude
    val cLat = chosen.latitude - from.latitude
    val cLon = chosen.longitude - from.longitude
    return if (dLat * cLon - dLon * cLat < 0.0) 1 else -1
}

/** Paso desviado [angleDegrees] del directo [from]→[desired]; null si cruza una barrera. */
internal fun ExteriorCollisionsConfig.slideStep(from: GeoPoint, desired: GeoPoint, angleDegrees: Double): GeoPoint? {
    val dLat = desired.latitude - from.latitude
    val dLon = desired.longitude - from.longitude
    val rad = angleDegrees * kotlin.math.PI / 180.0
    val cos = kotlin.math.cos(rad)
    val sin = kotlin.math.sin(rad)
    val candidate = GeoPoint(from.latitude + dLon * sin + dLat * cos, from.longitude + dLon * cos - dLat * sin)
    return if (resolveCirclePath(from, candidate, CircleCollider(LOCAL_NPC_RADIUS_METERS)).reachedTarget) candidate else null
}

/**
 * Paso desviado ±50° o ±95° del directo [from]→[desired] que no cruza ninguna barrera, el que acaba más cerca
 * de [desired]; null si todos están bloqueados (esquina cóncava). Sin allocaciones salvo el punto devuelto.
 */
internal fun ExteriorCollisionsConfig.slideAroundBarrier(from: GeoPoint, desired: GeoPoint): GeoPoint? {
    val dLat = desired.latitude - from.latitude
    val dLon = desired.longitude - from.longitude
    var best: GeoPoint? = null
    var bestDistance = Double.MAX_VALUE
    for (angle in SLIDE_ANGLES_DEGREES) {
        val rad = angle * kotlin.math.PI / 180.0
        val cos = kotlin.math.cos(rad)
        val sin = kotlin.math.sin(rad)
        val candidate = GeoPoint(from.latitude + dLon * sin + dLat * cos, from.longitude + dLon * cos - dLat * sin)
        if (!resolveCirclePath(from, candidate, CircleCollider(LOCAL_NPC_RADIUS_METERS)).reachedTarget) continue
        val remainingLat = desired.latitude - candidate.latitude
        val remainingLon = desired.longitude - candidate.longitude
        val remaining = remainingLat * remainingLat + remainingLon * remainingLon
        if (remaining < bestDistance) {
            bestDistance = remaining
            best = candidate
        }
    }
    return best
}
