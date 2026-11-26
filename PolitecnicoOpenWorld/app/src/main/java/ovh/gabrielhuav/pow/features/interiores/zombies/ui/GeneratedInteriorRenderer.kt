package ovh.gabrielhuav.pow.features.interiores.zombies.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import ovh.gabrielhuav.pow.domain.models.interior.FurnitureKind
import ovh.gabrielhuav.pow.domain.models.interior.InteriorIso
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
            paint.color = if (state == WindowState.REFORZADA) 0xFF5C6670.toInt() else if (state == WindowState.TAPIADA) 0xFF6B4A2B.toInt() else if (state == WindowState.ROTA) 0xFF2B2F33.toInt() else 0xFF9CC8E6.toInt()
            canvas.drawRect(rect.left + CELL * 0.12f, rect.top + CELL * 0.12f, rect.right - CELL * 0.12f, rect.bottom - CELL * 0.12f, paint)
            if (state == WindowState.TAPIADA || state == WindowState.REFORZADA) {
                paint.color = if (state == WindowState.REFORZADA) 0xFFB8C2CC.toInt() else 0xFF9A7345.toInt()
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

    // ─── VISTA ISOMÉTRICA (t82) ─────────────────────────────────────────────────────────────────────────────────────────
    // Misma planta, proyectada 2:1 como el mundo exterior (InteriorIso): suelo en rombos, muros con altura (los de atrás
    // altos y los de adelante bajos para no tapar el cuarto), ventanas pegadas a la cara visible del muro y muebles como
    // cajas. Se ordena por columna + fila (de atrás hacia adelante).

    private const val WALL_BACK = 54f
    private const val WALL_PARTITION = 46f
    private const val WALL_FRONT = 9f

    /** Un muro, mueble o la puerta horneado aparte (con transparencia) para volver a dibujarlo ENCIMA de los actores. */
    class IsoBlock(val key: Int, val image: Bitmap, val x: Int, val y: Int)

    /** Imagen completa del interior más sus bloques por separado (t82: oclusión por profundidad). */
    class IsoScene(val bitmap: Bitmap, val blocks: List<IsoBlock>)

    fun renderIso(layout: InteriorLayout, dark: Boolean = false, survivorVisible: Boolean = true, windowState: (Int) -> WindowState = { WindowState.CERRADA }): IsoScene {
        val w = InteriorIso.bitmapWidth(layout.cols, layout.rows).toInt()
        val h = InteriorIso.bitmapHeight(layout.cols, layout.rows).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; textAlign = Paint.Align.CENTER }
        canvas.drawColor(0xFF121010.toInt())
        for (room in layout.rooms) {
            for (r in room.top..room.bottom) for (c in room.left..room.right) {
                val tone = if ((c + r) % 2 == 0) 0x22000000 else 0
                fillQuad(canvas, paint, layout.rows, floorColor(room.type), c, r, 1, 1, 0f)
                if (tone != 0) fillQuad(canvas, paint, layout.rows, tone, c, r, 1, 1, 0f)
            }
        }
        // Felpudo de la entrada.
        fillQuad(canvas, paint, layout.rows, 0x88C9A24B.toInt(), layout.doorCol, layout.rows - 2, 1, 1, 0f)
        if (dark) {
            // Apagón sin generador: el interior queda a oscuras (el suelo aquí; cada bloque se oscurece al hornearse).
            paint.style = Paint.Style.FILL
            paint.color = 0xA0050A18.toInt()
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        val blocks = ArrayList<IsoBlock>()
        fun block(key: Int, left: Float, top: Float, right: Float, bottom: Float, draw: (Canvas) -> Unit) {
            val x0 = (left - 8f).toInt().coerceAtLeast(0)
            val y0 = (top - 8f).toInt().coerceAtLeast(0)
            val x1 = (right + 8f).toInt().coerceAtMost(w)
            val y1 = (bottom + 8f).toInt().coerceAtMost(h)
            if (x1 <= x0 || y1 <= y0) return
            val image = Bitmap.createBitmap(x1 - x0, y1 - y0, Bitmap.Config.ARGB_8888)
            val bc = Canvas(image)
            bc.translate(-x0.toFloat(), -y0.toFloat())
            draw(bc)
            if (dark) {
                val dim = Paint().apply { color = 0xA0050A18.toInt(); xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
                bc.drawRect(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), dim)
            }
            canvas.drawBitmap(image, x0.toFloat(), y0.toFloat(), null)
            blocks.add(IsoBlock(key, image, x0, y0))
        }
        val rows = layout.rows
        val byCell = layout.furniture.associateBy { it.col to it.row }
        for (s in 0..(layout.cols + layout.rows - 2)) {
            for (c in 0 until layout.cols) {
                val r = s - c
                if (r < 0 || r >= layout.rows) continue
                if (layout.isWall(c, r)) {
                    block(c + r, InteriorIso.x(c.toFloat(), r + 1f, rows), InteriorIso.y(c.toFloat(), r.toFloat(), 64f), InteriorIso.x(c + 1f, r.toFloat(), rows), InteriorIso.y(c + 1f, r + 1f)) {
                        drawWallBlock(it, paint, layout, c, r, windowState)
                    }
                }
                byCell[c to r]?.let { f ->
                    val key = (f.col + f.widthCells - 1) + f.row
                    block(key, InteriorIso.x(f.col.toFloat(), f.row + 1f, rows), InteriorIso.y(f.col.toFloat(), f.row.toFloat(), 46f), InteriorIso.x((f.col + f.widthCells).toFloat(), f.row.toFloat(), rows), InteriorIso.y((f.col + f.widthCells).toFloat(), f.row + 1f)) {
                        drawFurnitureBlock(it, paint, text, rows, f)
                    }
                }
                if (r == layout.rows - 1 && c == layout.doorCol) {
                    block(c + r, InteriorIso.x(c.toFloat(), r + 1f, rows), InteriorIso.y(c.toFloat(), r.toFloat(), 2f), InteriorIso.x(c + 1f, r.toFloat(), rows), InteriorIso.y(c + 1f, r + 1f)) {
                        drawDoorFrame(it, paint, rows, c, r)
                    }
                }
                if (layout.survivorCell == c to r && survivorVisible) {
                    val cx = InteriorIso.x(c + 0.5f, r + 0.5f, rows)
                    val cy = InteriorIso.y(c + 0.5f, r + 0.5f)
                    block(c + r, cx - 30f, cy - 40f, cx + 30f, cy + 12f) {
                        paint.style = Paint.Style.FILL
                        paint.color = 0x55FFD54F
                        it.drawCircle(cx, cy, 22f, paint)
                        it.drawText("🧍", cx, InteriorIso.y(c + 0.5f, r + 0.5f, 6f), text)
                    }
                }
            }
        }
        return IsoScene(bitmap, blocks)
    }

    private fun shade(color: Int, factor: Float): Int {
        val r = ((color shr 16 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    private fun path(canvas: Canvas, paint: Paint, color: Int, vararg points: Pair<Float, Float>) {
        val p = Path()
        points.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.first, pt.second) else p.lineTo(pt.first, pt.second) }
        p.close()
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(p, paint)
    }

    /** Rombo de [wCells] x [hCells] celdas con su esquina de atrás en (col, row), a la altura [z]. */
    private fun fillQuad(canvas: Canvas, paint: Paint, rows: Int, color: Int, col: Int, row: Int, wCells: Int, hCells: Int, z: Float) {
        val c0 = col.toFloat(); val r0 = row.toFloat(); val c1 = (col + wCells).toFloat(); val r1 = (row + hCells).toFloat()
        path(canvas, paint, color,
            InteriorIso.x(c0, r0, rows) to InteriorIso.y(c0, r0, z), InteriorIso.x(c1, r0, rows) to InteriorIso.y(c1, r0, z),
            InteriorIso.x(c1, r1, rows) to InteriorIso.y(c1, r1, z), InteriorIso.x(c0, r1, rows) to InteriorIso.y(c0, r1, z))
    }

    /** Caja con tapa y las dos caras que ve la cámara (la de adelante a la izquierda y la de adelante a la derecha). */
    private fun drawBox(canvas: Canvas, paint: Paint, rows: Int, col: Int, row: Int, wCells: Int, hCells: Int, height: Float, top: Int, side: Int) {
        val c0 = col.toFloat(); val r0 = row.toFloat(); val c1 = (col + wCells).toFloat(); val r1 = (row + hCells).toFloat()
        // Cara frontal izquierda (hacia +fila): del vértice izquierdo al de abajo.
        path(canvas, paint, shade(side, 0.92f),
            InteriorIso.x(c0, r1, rows) to InteriorIso.y(c0, r1, 0f), InteriorIso.x(c1, r1, rows) to InteriorIso.y(c1, r1, 0f),
            InteriorIso.x(c1, r1, rows) to InteriorIso.y(c1, r1, height), InteriorIso.x(c0, r1, rows) to InteriorIso.y(c0, r1, height))
        // Cara frontal derecha (hacia +columna): del vértice de abajo al derecho.
        path(canvas, paint, shade(side, 0.7f),
            InteriorIso.x(c1, r1, rows) to InteriorIso.y(c1, r1, 0f), InteriorIso.x(c1, r0, rows) to InteriorIso.y(c1, r0, 0f),
            InteriorIso.x(c1, r0, rows) to InteriorIso.y(c1, r0, height), InteriorIso.x(c1, r1, rows) to InteriorIso.y(c1, r1, height))
        fillQuad(canvas, paint, rows, top, col, row, wCells, hCells, height)
    }

    private fun drawWallBlock(canvas: Canvas, paint: Paint, layout: InteriorLayout, col: Int, row: Int, windowState: (Int) -> WindowState) {
        val height = when {
            row == layout.rows - 1 || col == layout.cols - 1 -> WALL_FRONT
            row == 0 || col == 0 -> WALL_BACK
            else -> WALL_PARTITION
        }
        drawBox(canvas, paint, layout.rows, col, row, 1, 1, height, 0xFF6A615A.toInt(), 0xFF433D39.toInt())
        // Ventanas: solo se ven las de los muros del fondo (norte y oeste), en su cara que mira al cuarto.
        layout.windows.forEachIndexed { index, win ->
            val onThis = when (win.side) {
                WallSide.NORTE -> col == win.index && row == 0
                WallSide.OESTE -> col == 0 && row == win.index
                WallSide.ESTE -> col == layout.cols - 1 && row == win.index
            }
            if (onThis) drawWindowFace(canvas, paint, layout.rows, col, row, win.side, height, windowState(index))
        }
    }

    private fun drawWindowFace(canvas: Canvas, paint: Paint, rows: Int, col: Int, row: Int, side: WallSide, height: Float, state: WindowState) {
        val color = when (state) {
            WindowState.REFORZADA -> 0xFF5C6670.toInt()
            WindowState.TAPIADA -> 0xFF6B4A2B.toInt()
            WindowState.ROTA -> 0xFF2B2F33.toInt()
            WindowState.CERRADA -> 0xFF9CC8E6.toInt()
        }
        val bar = when (state) {
            WindowState.REFORZADA -> 0xFFB8C2CC.toInt()
            WindowState.TAPIADA -> 0xFF9A7345.toInt()
            else -> 0
        }
        if (side == WallSide.ESTE) {
            // El muro este es bajo y su cara interior mira para otro lado: la ventana se marca sobre su tapa.
            fillQuad(canvas, paint, rows, color, col, row, 1, 1, height + 0.5f)
            if (bar != 0) fillQuad(canvas, paint, rows, bar, col, row, 1, 1, height + 1f)
            return
        }
        // La cara visible del muro norte es la de +fila; la del muro oeste, la de +columna.
        val edge = if (side == WallSide.NORTE) {
            listOf(col + 0.18f, row + 1f, col + 0.82f, row + 1f)
        } else {
            listOf(col + 1f, row + 0.82f, col + 1f, row + 0.18f)
        }
        val ax = edge[0]; val ay = edge[1]; val bx = edge[2]; val by = edge[3]
        val z0 = height * 0.3f
        val z1 = height * 0.82f
        fun quad(t0: Float, t1: Float, za: Float, zb: Float, colr: Int) {
            val x0 = ax + (bx - ax) * t0; val y0 = ay + (by - ay) * t0
            val x1 = ax + (bx - ax) * t1; val y1 = ay + (by - ay) * t1
            path(canvas, paint, colr,
                InteriorIso.x(x0, y0, rows) to InteriorIso.y(x0, y0, za), InteriorIso.x(x1, y1, rows) to InteriorIso.y(x1, y1, za),
                InteriorIso.x(x1, y1, rows) to InteriorIso.y(x1, y1, zb), InteriorIso.x(x0, y0, rows) to InteriorIso.y(x0, y0, zb))
        }
        quad(0f, 1f, z0, z1, color)
        if (bar != 0) {
            quad(0f, 1f, z0 + (z1 - z0) * 0.2f, z0 + (z1 - z0) * 0.42f, bar)
            quad(0f, 1f, z0 + (z1 - z0) * 0.58f, z0 + (z1 - z0) * 0.8f, bar)
        } else {
            quad(0.47f, 0.53f, z0, z1, 0xFFE9E4DA.toInt())
        }
    }

    private fun drawDoorFrame(canvas: Canvas, paint: Paint, rows: Int, col: Int, row: Int) {
        fillQuad(canvas, paint, rows, 0xFF6B4A2B.toInt(), col, row, 1, 1, 0f)
        fillQuad(canvas, paint, rows, 0xFFB98B4F.toInt(), col, row, 1, 1, 1f)
    }

    private fun drawFurnitureBlock(canvas: Canvas, paint: Paint, text: Paint, rows: Int, f: ovh.gabrielhuav.pow.domain.models.interior.InteriorFurniture) {
        val (top, front) = furnitureColors(f.kind)
        val height = when (f.kind) {
            FurnitureKind.CAMA, FurnitureKind.CAMILLA, FurnitureKind.BANCO -> 14f
            FurnitureKind.MESA, FurnitureKind.MOSTRADOR, FurnitureKind.COCINA -> 20f
            FurnitureKind.INODORO, FurnitureKind.GRIFO -> 18f
            FurnitureKind.ESTANTE, FurnitureKind.ARMARIO -> 34f
            FurnitureKind.GENERADOR -> 24f
        }
        drawBox(canvas, paint, rows, f.col, f.row, f.widthCells, 1, height, top, front)
        val cx = f.col + f.widthCells / 2f
        canvas.drawText(glyph(f.kind), InteriorIso.x(cx, f.row + 0.5f, rows), InteriorIso.y(cx, f.row + 0.5f, height) + 9f, text)
    }
}
