package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.*

/** Una conexión por ataque activo; los proyectiles se consumen en su colisión habitual. */
internal fun StreetFighterViewModel.hitPrinter(
    sim: StreetFighterViewModel.Sim, strength: SfAttackStrength, type: SfAttackType, now: Long,
) {
    val bonus = _state.value.printerBonus ?: return
    val next = bonus.hit(damageForAttack(sim.p0, strength), now)
    if (next == bonus) return
    _state.value = _state.value.copy(printerBonus = next)
    sim.p0 = sim.p0.copy(attackStruck = true,
        superMeter = chargeSuper(sim.p0, SfConstants.SUPER_METER_ON_HIT))
    _soundEvents.tryEmit("${strength.name.lowercase()}-${type.name.lowercase()}-hit")
    sim.splashes.add(SfHitSplash(x = SfPrinterBonus.X - 30f, y = SfConstants.STAGE_FLOOR - 65f,
        playerId = 0, strength = strength, animationTimerMs = now))
}
