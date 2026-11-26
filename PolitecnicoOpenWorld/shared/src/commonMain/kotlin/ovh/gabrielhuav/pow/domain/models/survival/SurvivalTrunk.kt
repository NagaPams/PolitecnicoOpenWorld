package ovh.gabrielhuav.pow.domain.models.survival

// Cajuela de los coches estacionados (t80/t04): X junto a uno la registra una vez por enfriamiento. Lo que hay es estable por
// coche (sale de su id) y casi siempre poco. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object TrunkRules {
    const val ID_PREFIX = "cajuela_"
    /** A esta distancia (m) del coche aparece el punto para registrar su cajuela. */
    const val REACH_METERS = 3.5
    /** Cuánto tarda una cajuela en volver a tener algo (ms de reloj real). */
    const val COOLDOWN_MS = 25L * 60L * 1000L

    fun key(carId: String): String = ID_PREFIX + carId

    fun isTrunkKey(id: String): Boolean = id.startsWith(ID_PREFIX)

    /** Lo que guarda la cajuela del coche [carId]; vacío 1 de cada 6 veces. */
    fun gainsFor(carId: String): List<SearchGain> = when (stableHash(carId) % 6) {
        0 -> listOf(SearchGain.BIDON)
        1 -> listOf(SearchGain.VENDA)
        2 -> listOf(SearchGain.REFRESCO, SearchGain.PIZZA)
        3 -> listOf(SearchGain.TABLON)
        4 -> listOf(SearchGain.CRUDA)
        else -> emptyList()
    }

    /** FNV-1a de 32 bits, siempre >= 0: no depende de `hashCode` (estable entre plataformas). */
    internal fun stableHash(text: String): Int {
        var h = -2128831035
        for (ch in text) {
            h = h xor ch.code
            h *= 16777619
        }
        return h and Int.MAX_VALUE
    }
}
