package ovh.gabrielhuav.pow.domain.models.map

import ovh.gabrielhuav.pow.platform.color.colorArgb

// Pinturas de coche creíbles (t29): antes los estacionados salían de un RGB al azar y se veían como juguetes
// (verde fosforescente, magenta, amarillo). Las más comunes aparecen repetidas para pesar el sorteo.

object CarPaints {
    val PALETTE: IntArray = intArrayOf(
        colorArgb(232, 232, 228), colorArgb(232, 232, 228), colorArgb(232, 232, 228), // blanco
        colorArgb(176, 179, 183), colorArgb(176, 179, 183), colorArgb(176, 179, 183), // plata
        colorArgb(110, 114, 120), colorArgb(110, 114, 120),                           // gris
        colorArgb(32, 33, 36), colorArgb(32, 33, 36),                                 // negro
        colorArgb(36, 52, 86),                                                        // azul marino
        colorArgb(110, 36, 44),                                                       // vino
        colorArgb(40, 74, 56),                                                        // verde bosque
        colorArgb(196, 182, 150),                                                     // beige
        colorArgb(96, 70, 52),                                                        // café
        colorArgb(150, 52, 50),                                                       // rojo apagado
    )

    /** Una pintura de la paleta; [roll] es un entero cualquiera (p. ej. `Random.nextInt()`). */
    fun pick(roll: Int): Int = PALETTE[(roll and Int.MAX_VALUE) % PALETTE.size]
}
