package ovh.gabrielhuav.pow.domain.models.map

// Variantes de los coches «WAGON» del tráfico: según el id se dibujan como camión de caja, tractocamión, autobús o sedán.
// Antes solo cambiaba el sprite y la colisión seguía siendo la del WAGON (4,9 x 2,1 m): el jugador entraba al tractocamión
// o al autobús. Ahora el cuerpo sólido sigue a lo que se ve. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

enum class WagonVariant(val dimensions: VehicleDimensions?) {
    BOX_TRUCK(VehicleDimensions(7.2, 2.4)),
    SEMI_TRACTOR(VehicleDimensions(6.3, 2.5)),
    BUS(VehicleDimensions(10.7, 2.6)),
    /** Dibuja el sedán de siempre: conserva la colisión del WAGON. */
    SEDAN(null),
}

object CarVariantRules {
    /** Variante de un coche WAGON de id [id] (la misma que usa el render isométrico). */
    fun wagonVariant(id: String): WagonVariant = when (id.hashCode().mod(4)) {
        0 -> WagonVariant.BOX_TRUCK
        1 -> WagonVariant.SEMI_TRACTOR
        2 -> WagonVariant.BUS
        else -> WagonVariant.SEDAN
    }

    /** Cuerpo sólido del coche [model] con id [id]: el de su variante si es WAGON con id, el del modelo en otro caso. */
    fun dimensionsFor(model: CarModel, id: String): VehicleDimensions =
        if (model == CarModel.WAGON && id.isNotEmpty()) wagonVariant(id).dimensions ?: model.collisionDimensions else model.collisionDimensions
}

/** Cuerpo sólido de este NPC (un tractocamión o un autobús no miden lo mismo que un sedán). */
val Npc.bodyDimensions: VehicleDimensions get() = CarVariantRules.dimensionsFor(carModel, id)
