package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import java.io.File
import kotlin.math.roundToLong
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.domain.models.map.FootprintWay

// Caché en disco de los edificios exteriores (t38): una zona ya visitada se dibuja sin red y no vuelve a pedirse a los
// servidores públicos. Un archivo por punto redondeado a 3 decimales (el mismo que se manda a Overpass). Ver 04_MAP_EXTERIOR.md.
internal object OuterBuildingsDiskCache {
    /** Cuántas zonas se guardan; al pasar el límite se borran las más viejas. */
    const val MAX_FILES = 24
    /** Pasada una semana se vuelve a pedir (los edificios de OSM cambian poco, pero cambian). */
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    fun fileName(lat: Double, lon: Double): String =
        "outer_${(lat * 1000.0).roundToLong()}_${(lon * 1000.0).roundToLong()}.json"

    /** `null` si no hay archivo, caducó o está dañado (se borra para no repetir el fallo). */
    fun read(dir: File, lat: Double, lon: Double, nowMs: Long = System.currentTimeMillis()): List<FootprintWay>? {
        val file = File(dir, fileName(lat, lon))
        if (!file.isFile || nowMs - file.lastModified() > MAX_AGE_MS) return null
        return try {
            PowJson.decodeFromString<List<FootprintWay>>(file.readText())
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    fun write(dir: File, lat: Double, lon: Double, ways: List<FootprintWay>) {
        try {
            if (!dir.isDirectory && !dir.mkdirs()) return
            val target = File(dir, fileName(lat, lon))
            val temp = File(dir, target.name + ".tmp")
            temp.writeText(PowJson.encodeToString(ways))
            if (!temp.renameTo(target)) { target.delete(); temp.renameTo(target) }
            prune(dir)
        } catch (e: Exception) {
            // Es solo una caché: si no se puede escribir, se sigue con la red.
        }
    }

    private fun prune(dir: File) {
        val files = dir.listFiles { f -> f.name.startsWith("outer_") && f.name.endsWith(".json") } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }
}
