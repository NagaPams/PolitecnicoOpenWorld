package ovh.gabrielhuav.pow.domain.models.survival

// Armas y munición del modo supervivencia (t01): catálogo, arsenal del jugador y reglas puras. Las armas cuerpo a
// cuerpo se desgastan; las de fuego gastan munición y hacen ruido de disparo. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Los valores nuevos van AL FINAL (se guardan por nombre). */
enum class WeaponKind { PUNOS, PALO, BATE, CUCHILLO, PISTOLA, ESCOPETA, PALO_CLAVOS }

enum class AmmoKind { BALAS, CARTUCHOS }

data class WeaponSpec(
    val kind: WeaponKind,
    val damage: Float,
    val rangeMeters: Double,
    val cooldownMs: Long,
    /** null = cuerpo a cuerpo. */
    val ammo: AmmoKind?,
    /** Usos antes de romperse; 0 = no se desgasta. */
    val durability: Int,
) {
    val isFirearm: Boolean get() = ammo != null
}

object WeaponCatalog {
    private val specs = mapOf(
        WeaponKind.PUNOS to WeaponSpec(WeaponKind.PUNOS, 15f, 9.0, 1200L, null, 0),
        WeaponKind.PALO to WeaponSpec(WeaponKind.PALO, 25f, 10.0, 1100L, null, 18),
        WeaponKind.BATE to WeaponSpec(WeaponKind.BATE, 40f, 10.0, 1300L, null, 30),
        WeaponKind.CUCHILLO to WeaponSpec(WeaponKind.CUCHILLO, 30f, 8.0, 800L, null, 24),
        WeaponKind.PISTOLA to WeaponSpec(WeaponKind.PISTOLA, 35f, 28.0, 700L, AmmoKind.BALAS, 0),
        WeaponKind.ESCOPETA to WeaponSpec(WeaponKind.ESCOPETA, 80f, 16.0, 1500L, AmmoKind.CARTUCHOS, 0),
        WeaponKind.PALO_CLAVOS to WeaponSpec(WeaponKind.PALO_CLAVOS, 35f, 10.0, 1100L, null, 22),
    )

    fun of(kind: WeaponKind): WeaponSpec = specs.getValue(kind)
}

/** Arma guardada: [uses] = usos que le quedan (solo importa en las que se desgastan). */
data class OwnedWeapon(val kind: WeaponKind, val uses: Int)

/** Lo que carga el jugador. Los puños siempre están disponibles y no se guardan en [weapons]. */
data class SurvivalArsenal(
    val weapons: List<OwnedWeapon> = emptyList(),
    val equipped: WeaponKind = WeaponKind.PUNOS,
    val balas: Int = 0,
    val cartuchos: Int = 0,
) {
    fun ammoOf(kind: AmmoKind): Int = if (kind == AmmoKind.BALAS) balas else cartuchos
    fun has(kind: WeaponKind): Boolean = kind == WeaponKind.PUNOS || weapons.any { it.kind == kind }
}

enum class ShotOutcome { OK, NO_AMMO, BROKE }

data class ShotResult(val arsenal: SurvivalArsenal, val outcome: ShotOutcome)

object WeaponRules {
    const val MAX_AMMO = 60
    const val METERS_PER_DEGREE = 111_320.0

    fun active(arsenal: SurvivalArsenal): WeaponSpec = WeaponCatalog.of(arsenal.equipped)

    /** Rango del arma en grados (como el resto del combate del mundo libre). */
    fun rangeDegrees(spec: WeaponSpec): Double = spec.rangeMeters / METERS_PER_DEGREE

    /** Recoge un arma; si ya se tenía, solo repone sus usos. Equipa la nueva si se estaba con los puños. */
    fun pickUp(arsenal: SurvivalArsenal, kind: WeaponKind): SurvivalArsenal {
        if (kind == WeaponKind.PUNOS) return arsenal
        val uses = WeaponCatalog.of(kind).durability
        val owned = arsenal.weapons.firstOrNull { it.kind == kind }
        val weapons = if (owned == null) arsenal.weapons + OwnedWeapon(kind, uses)
        else arsenal.weapons.map { if (it.kind == kind) it.copy(uses = uses) else it }
        val equipped = if (arsenal.equipped == WeaponKind.PUNOS) kind else arsenal.equipped
        return arsenal.copy(weapons = weapons, equipped = equipped)
    }

    fun addAmmo(arsenal: SurvivalArsenal, kind: AmmoKind, count: Int): SurvivalArsenal = when (kind) {
        AmmoKind.BALAS -> arsenal.copy(balas = (arsenal.balas + count).coerceIn(0, MAX_AMMO))
        AmmoKind.CARTUCHOS -> arsenal.copy(cartuchos = (arsenal.cartuchos + count).coerceIn(0, MAX_AMMO))
    }

    /** Equipa un arma que se tiene; null si no está en el arsenal. */
    fun equip(arsenal: SurvivalArsenal, kind: WeaponKind): SurvivalArsenal? =
        if (arsenal.has(kind)) arsenal.copy(equipped = kind) else null

    /** Siguiente arma del ciclo (puños → armas en el orden recogido → puños). */
    fun next(arsenal: SurvivalArsenal): SurvivalArsenal {
        val order = listOf(WeaponKind.PUNOS) + arsenal.weapons.map { it.kind }
        val index = order.indexOf(arsenal.equipped).coerceAtLeast(0)
        return arsenal.copy(equipped = order[(index + 1) % order.size])
    }

    /** ¿Se puede atacar con el arma equipada? (las de fuego piden munición). */
    fun canUse(arsenal: SurvivalArsenal): Boolean {
        val ammo = active(arsenal).ammo ?: return true
        return arsenal.ammoOf(ammo) > 0
    }

    /** Gasta un uso: una bala/cartucho en las de fuego, un uso de desgaste en las blancas (se rompen al llegar a 0). */
    fun consumeShot(arsenal: SurvivalArsenal): ShotResult {
        val spec = active(arsenal)
        val ammo = spec.ammo
        if (ammo != null) {
            if (arsenal.ammoOf(ammo) <= 0) return ShotResult(arsenal, ShotOutcome.NO_AMMO)
            return ShotResult(addAmmo(arsenal, ammo, -1), ShotOutcome.OK)
        }
        if (spec.durability <= 0) return ShotResult(arsenal, ShotOutcome.OK)
        val owned = arsenal.weapons.firstOrNull { it.kind == spec.kind } ?: return ShotResult(arsenal, ShotOutcome.OK)
        if (owned.uses > 1) {
            val weapons = arsenal.weapons.map { if (it.kind == spec.kind) it.copy(uses = it.uses - 1) else it }
            return ShotResult(arsenal.copy(weapons = weapons), ShotOutcome.OK)
        }
        val weapons = arsenal.weapons.filterNot { it.kind == spec.kind }
        return ShotResult(arsenal.copy(weapons = weapons, equipped = WeaponKind.PUNOS), ShotOutcome.BROKE)
    }

    /** `PALO:12;PISTOLA:0` — vacío si no hay armas. */
    fun encode(weapons: List<OwnedWeapon>): String = weapons.joinToString(";") { "${it.kind.name}:${it.uses}" }

    /** Tolerante: ignora entradas desconocidas o dañadas y las repetidas. */
    fun decode(text: String): List<OwnedWeapon> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<OwnedWeapon>()
        for (part in text.split(';')) {
            val pieces = part.split(':')
            val kind = WeaponKind.entries.firstOrNull { it.name == pieces.getOrNull(0) } ?: continue
            if (kind == WeaponKind.PUNOS || out.any { it.kind == kind }) continue
            val max = WeaponCatalog.of(kind).durability
            val uses = (pieces.getOrNull(1)?.toIntOrNull() ?: max).coerceIn(if (max > 0) 1 else 0, maxOf(max, 0))
            out.add(OwnedWeapon(kind, uses))
        }
        return out
    }

    /** Un guardado viejo o editado a mano dentro de rango. */
    fun sanitized(arsenal: SurvivalArsenal): SurvivalArsenal {
        val weapons = decode(encode(arsenal.weapons))
        val equipped = if (arsenal.equipped == WeaponKind.PUNOS || weapons.any { it.kind == arsenal.equipped }) arsenal.equipped else WeaponKind.PUNOS
        return SurvivalArsenal(weapons, equipped, arsenal.balas.coerceIn(0, MAX_AMMO), arsenal.cartuchos.coerceIn(0, MAX_AMMO))
    }
}
