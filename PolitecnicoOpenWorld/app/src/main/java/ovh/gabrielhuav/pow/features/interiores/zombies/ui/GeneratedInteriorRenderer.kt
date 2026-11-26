package ovh.gabrielhuav.pow.features.interiores.zombies.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import ovh.gabrielhuav.pow.domain.models.interior.FurnitureKind
import ovh.gabrielhuav.pow.domain.models.interior.InteriorLayout
import ovh.gabrielhuav.pow.domain.models.interior.RoomType
import ovh.gabrielhuav.pow.domain.models.interior.WallSide
import ovh.gabrielhuav.pow.domain.models.interior.WindowState

// Dibuja la planta de un interior procedural con primitivas (sin assets): suelo por tipo de habitación, muros con
// borde, ventanas, puerta y muebles como cajas con tapa y frente. Se hornea una sola vez por sala a media resolución
// (el fondo se estira al tamaño del mundo). Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

internal object GeneratedInteriorRenderer {
    /** Píxeles de la imagen por celda (el mundo usa el doble). */
    private const val CELL = 56f

    fun render(layout: InteriorLayout, dark: Boolean = false, survivorVisible: Boolean = true, windowState: (Int) -> WindowState = { WindowState.CERRADA }): Bitmap {
        val bitmap = Bitmap.createBitmap((layout.cols * CELL).toInt(), (layout.rows * CELL).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        drawFloor(canvas, paint, layout)
        drawWalls(canvas, paint, layout)
        drawWindows(canvas, paint, layout, windowState)
        drawDoor(canvas, paint, layout)
        drawFurniture(canvas, paint, layout)
        layout.survivorCell?.takeIf { survivorVisible }?.let { drawSurvivor(canvas, paint, it) }
        if (dark) {
            // Apagón sin generador: el interior queda a oscuras (se sigue viendo lo suficiente para moverse).
            paint.style = Paint.Style.FILL
            paint.color = 0xA0050A18.toInt()
            canvas.drawRect(0f, 0f, layout.cols * CELL, layout.rows * CELL, paint)
        }
        return bitmap
    }

    private fun cell(col: Int, row: Int, w: Int = 1, h: Int = 1) = RectF(col * CELL, row * CELL, (col + w) * CELL, (row + h) * CELL)

    private fun floorColor(type: RoomType): Int = when (type) {
        RoomType.SALA -> 0xFF8C6A48.toInt()
        RoomType.COCINA -> 0xFFB9B4A6.toInt()
        RoomType.DORMITORIO -> 0xFF7C5E60.toInt()
        RoomType.BANO -> 0xFF9FB6BE.toInt()
        RoomType.BODEGA -> 0xFF6E6558.toInt()
        RoomType.TIENDA -> 0xFFA29B8A.toInt()
        RoomType.CONSULTORIO -> 0xFFC4CCCB.toInt()
    }

    private fun drawFloor(canvas: Canvas, paint: Paint, layout: InteriorLayout) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF1D1B1B.toInt()
        canvas.drawRect(0f, 0f, layout.cols * CELL, layout.rows * CELL, paint)
        for (room in layout.rooms) {
            paint.color = floorColor(room.type)
            canvas.drawRect(cell(room.left, room.top, room.right - room.left + 1, room.bottom - room.top + 1), paint)
            paint.color = 0x22000000
            paint.strokeWidth = 1.5f
            for (r in room.top..room.bottom) for (c in room.left..room.right) {
                if ((c + r) % 2 == 0) canvas.drawRect(cell(c, r), paint)
            }
        }
    }

    private fun drawWalls(canvas: Canvas, paint: Paint, layout: InteriorLayout) {
        for (r in 0 until layout.rows) for (c in 0 until layout.cols) {
            if (!layout.isWall(c, r)) continue
            val rect = cell(c, r)
            paint.style = Paint.Style.FILL
            paint.color = 0xFF3A3532.toInt()
            canvas.drawRect(rect, paint)
            paint.color = 0xFF5B534D.toInt()
            canvas.drawRect(rect.left, rect.top, rect.right, rect.top + CELL * 0.22f, paint)
        }
    }

    private fun drawWindows(canvas: Canvas, paint: Paint, layout: InteriorLayout, windowState: (Int) -> WindowState) {
        layout.windows.forEachIndexed { index, w ->
            val rect = when (w.side) {
                WallSide.NORTE -> cell(w.index, 0)
                WallSide.ESTE -> cell(layout.cols - 1, w.index)
                WallSide.OESTE -> cell(0, w.index)
            }
            val state = windowState(index)
            paint.style = Paint.Style.FILL
            paint.color = if (state == WindowState.TAPIADA) 0xFF6B4A2B.toInt() else if (state == WindowState.ROTA) 0xFF2B2F33.toInt() else 0xFF9CC8E6.toInt()
            canvas.drawRect(rect.left + CELL * 0.12f, rect.top + CELL * 0.12f, rect.right - CELL * 0.12f, rect.bottom - CELL * 0.12f, paint)
            if (state == WindowState.TAPIADA) {
                paint.color = 0xFF9A7345.toInt()
                canvas.drawRect(rect.left + CELL * 0.12f, rect.top + CELL * 0.3f, rect.right - CELL * 0.12f, rect.top + CELL * 0.45f, paint)
                canvas.drawRect(rect.left + CELL * 0.12f, rect.top + CELL * 0.58f, rect.right - CELL * 0.12f, rect.top + CELL * 0.73f, paint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0xFFE9E4DA.toInt()
            canvas.drawRect(rect.left + CELL * 0.12f, rect.top + CELL * 0.12f, rect.right - CELL * 0.12f, rect.bottom - CELL * 0.12f, paint)
            canvas.drawLine(rect.centerX(), rect.top + CELL * 0.12f, rect.centerX(), rect.bottom - CELL * 0.12f, paint)
        }
    }

    private fun drawDoor(canvas: Canvas, paint: Paint, layout: InteriorLayout) {
        val rect = cell(layout.doorCol, layout.rows - 1)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF6B4A2B.toInt()
        canvas.drawRect(rect, paint)
        paint.color = 0xFFB98B4F.toInt()
        canvas.drawRect(rect.left + CELL * 0.18f, rect.top + CELL * 0.08f, rect.right - CELL * 0.18f, rect.bottom, paint)
        // Felpudo dentro de la casa, para ver dónde está la salida.
        paint.color = 0x88C9A24B.toInt()
        canvas.drawRect(cell(layout.doorCol, layout.rows - 2), paint)
    }

    private fun drawSurvivor(canvas: Canvas, paint: Paint, cell: Pair<Int, Int>) {
        val rect = cell(cell.first, cell.second)
        paint.style = Paint.Style.FILL
        paint.color = 0x55FFD54F
        canvas.drawCircle(rect.centerX(), rect.centerY(), CELL * 0.42f, paint)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = CELL * 0.62f; textAlign = Paint.Align.CENTER }
        canvas.drawText("🧍", rect.centerX(), rect.centerY() + CELL * 0.2f, text)
    }

    private fun furnitureColors(kind: FurnitureKind): Pair<Int, Int> = when (kind) {
        FurnitureKind.CAMA -> 0xFFD9D2C6.toInt() to 0xFF8E6E6C.toInt()
        FurnitureKind.MESA -> 0xFFA77B4B.toInt() to 0xFF70502F.toInt()
        FurnitureKind.ESTANTE -> 0xFF8B6B45.toInt() to 0xFF5A4429.toInt()
        FurnitureKind.COCINA -> 0xFFC9CCCE.toInt() to 0xFF7E8386.toInt()
        FurnitureKind.GRIFO -> 0xFFB6C6CF.toInt() to 0xFF6F8794.toInt()
        FurnitureKind.INODORO -> 0xFFEFEFEF.toInt() to 0xFFA9A9A9.toInt()
        FurnitureKind.ARMARIO -> 0xFF7A5C3B.toInt() to 0xFF4B3823.toInt()
        FurnitureKind.MOSTRADOR -> 0xFFC2A36B.toInt() to 0xFF7C6537.toInt()
        FurnitureKind.CAMILLA -> 0xFFDDE6E8.toInt() to 0xFF8CA0A6.toInt()
        FurnitureKind.BANCO -> 0xFF9A7B52.toInt() to 0xFF664C2D.toInt()
        FurnitureKind.GENERADOR -> 0xFFD0A52B.toInt() to 0xFF6E5A19.toInt()
    }

    private fun glyph(kind: FurnitureKind): String = when (kind) {
        FurnitureKind.CAMA -> "🛏"
        FurnitureKind.MESA -> "🍽"
        FurnitureKind.ESTANTE -> "📦"
        FurnitureKind.COCINA -> "🍳"
        FurnitureKind.GRIFO -> "🚰"
        FurnitureKind.INODORO -> "🚽"
        FurnitureKind.ARMARIO -> "🗄"
        FurnitureKind.MOSTRADOR -> "🛒"
        FurnitureKind.CAMILLA -> "🩺"
        FurnitureKind.BANCO -> "🪑"
        FurnitureKind.GENERADOR -> "⚡"
    }

    private fun drawFurniture(canvas: Canvas, paint: Paint, layout: InteriorLayout) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = CELL * 0.5f; textAlign = Paint.Align.CENTER }
        for (f in layout.furniture) {
            val rect = cell(f.col, f.row, f.widthCells, 1)
            val (top, front) = furnitureColors(f.kind)
            paint.style = Paint.Style.FILL
            paint.color = front
            canvas.drawRect(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 2f, paint)
            paint.color = top
            canvas.drawRect(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - CELL * 0.3f, paint)
            canvas.drawText(glyph(f.kind), rect.centerX(), rect.centerY() + CELL * 0.1f, text)
        }
    }
}
