package ovh.gabrielhuav.pow.domain.models.map

/** Pared orientada hacia la cámara; el signo del área permite invertir el orden de los vértices. */
fun isoWallFacesCamera(startX: Double, endX: Double, winding: Double): Boolean =
    when {
        winding > 0.0 -> startX > endX
        winding < 0.0 -> startX < endX
        else -> false
    }

/** Punto dentro del polígono por conteo de cruces; sin crear objetos para poder llamarlo cada cuadro. */
fun isoPointInPolygon(x: Float, y: Float, xs: FloatArray, ys: FloatArray): Boolean {
    var inside = false
    var j = xs.size - 1
    for (i in xs.indices) {
        if ((ys[i] > y) != (ys[j] > y) &&
            x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]
        ) inside = !inside
        j = i
    }
    return inside
}
