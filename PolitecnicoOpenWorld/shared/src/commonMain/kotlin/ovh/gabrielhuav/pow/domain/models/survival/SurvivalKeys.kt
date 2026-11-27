package ovh.gabrielhuav.pow.domain.models.survival

import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.CarVariantRules
import ovh.gabrielhuav.pow.domain.models.map.WagonVariant

// Llaves de coche como objeto (t80/t03) y asientos por vehículo (t80/t05). Reglas puras, sin Android.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Cómo se sube a un coche. */
enum class BoardingMethod { OWNED, KEY, HOTWIRE, HOTWIRE_FAILED }

object KeyRules {
    /**
     * Subir a un coche que ya es tuyo ([firstTime] = false) no cuesta nada. En uno ajeno, una llave de la mochila lo abre
     * sin ruido de motor y se gasta; sin llave hay que hacerle el puente (puede fallar, ver [HotwireRules]).
     */
    fun resolve(firstTime: Boolean, keys: Int, roll: Double): BoardingMethod = when {
        !firstTime -> BoardingMethod.OWNED
        keys > 0 -> BoardingMethod.KEY
        HotwireRules.fails(true, roll) -> BoardingMethod.HOTWIRE_FAILED
        else -> BoardingMethod.HOTWIRE
    }

    /** Con llave solo suena la puerta; con puente suena también el motor. */
    fun noisesOnBoard(method: BoardingMethod): List<NoiseSource> = when (method) {
        BoardingMethod.HOTWIRE, BoardingMethod.HOTWIRE_FAILED -> HotwireRules.noisesOnBoard(true)
        else -> HotwireRules.noisesOnBoard(false)
    }
}

object SeatRules {
    /** Plazas totales (conductor incluido) de un [model]; los WAGON dependen de su variante por [vehicleId]. */
    fun seatsOf(model: CarModel, vehicleId: String): Int = when (model) {
        CarModel.SEDAN -> 4
        CarModel.SPORT, CarModel.SUPERCAR -> 2
        CarModel.SUV -> 5
        CarModel.VAN -> 8
        CarModel.WAGON -> if (vehicleId.isEmpty()) 5 else when (CarVariantRules.wagonVariant(vehicleId)) {
            WagonVariant.BOX_TRUCK -> 3
            WagonVariant.SEMI_TRACTOR -> 2
            WagonVariant.BUS -> 20
            WagonVariant.SEDAN -> 5
        }
    }

    /** Acompañantes que caben: todos los asientos menos el del conductor. */
    fun passengerSeats(model: CarModel, vehicleId: String): Int = seatsOf(model, vehicleId) - 1

    /** Miembros del grupo que viajan en el coche; los que no caben esperan y vuelven contigo al bajar. */
    fun riding(members: Int, passengerSeats: Int): Int = members.coerceAtLeast(0).coerceAtMost(passengerSeats.coerceAtLeast(0))

    /** Miembros que se quedan fuera por falta de asientos. */
    fun leftOut(members: Int, passengerSeats: Int): Int = members.coerceAtLeast(0) - riding(members, passengerSeats)
}
