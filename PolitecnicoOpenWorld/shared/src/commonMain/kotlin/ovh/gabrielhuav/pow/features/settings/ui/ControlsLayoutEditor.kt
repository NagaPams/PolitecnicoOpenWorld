package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.features.settings.models.CONTROL_OPACITY_MIN
import ovh.gabrielhuav.pow.features.settings.models.CONTROL_SLOT_SCALE_MAX
import ovh.gabrielhuav.pow.features.settings.models.CONTROL_SLOT_SCALE_MIN
import ovh.gabrielhuav.pow.features.settings.models.ControlSlotLayout
import ovh.gabrielhuav.pow.features.settings.models.ControlsLayout
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.*

private val EditorAccent = Color(0xFFD4AF37)
private val EditorWine = Color(0xFF6B1C3A)
private val EditorPanel = Color(0xFF1B1216)
private const val MOVEMENT_BLOCK_DP = 120
private const val ACTIONS_BLOCK_DP = 120
private const val EDGE_MARGIN_DP = 24

/**
 * Editor de la disposición de los controles del mundo: arrastra cada bloque y ajusta su escala y
 * opacidad. Trabaja sobre una copia ([layout]); [onChange] recibe cada cambio y [onClose] cierra.
 * Solo emite intenciones: el guardado lo hace el botón GUARDAR de Ajustes → Controles.
 */
@Composable
internal fun ControlsLayoutEditor(
    layout: ControlsLayout,
    swapSides: Boolean,
    onChange: (ControlsLayout) -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf(EditorBlock.MOVEMENT) }
    val current by rememberUpdatedState(layout)
    val slot = if (selected == EditorBlock.MOVEMENT) layout.movement else layout.actions
    fun update(next: ControlSlotLayout) {
        onChange(
            if (selected == EditorBlock.MOVEMENT) current.copy(movement = next) else current.copy(actions = next),
        )
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0E0A0C))) {
            val movementAlign = if (swapSides) Alignment.BottomEnd else Alignment.BottomStart
            val actionsAlign = if (swapSides) Alignment.BottomStart else Alignment.BottomEnd
            EditorBlockView(
                modifier = Modifier.align(movementAlign),
                size = MOVEMENT_BLOCK_DP,
                slot = layout.movement,
                selected = selected == EditorBlock.MOVEMENT,
                label = stringResource(Res.string.layout_movement),
                onSelect = { selected = EditorBlock.MOVEMENT },
                onDrag = { dx, dy -> onChange(current.copy(movement = current.movement.moved(dx, dy))) },
            )
            EditorBlockView(
                modifier = Modifier.align(actionsAlign),
                size = ACTIONS_BLOCK_DP,
                slot = layout.actions,
                selected = selected == EditorBlock.ACTIONS,
                label = stringResource(Res.string.layout_actions),
                onSelect = { selected = EditorBlock.ACTIONS },
                onDrag = { dx, dy -> onChange(current.copy(actions = current.actions.moved(dx, dy))) },
            )
            EditorPanelView(
                slot = slot,
                onScale = { update(slot.copy(scale = it).clamped()) },
                onOpacity = { update(slot.copy(opacity = it).clamped()) },
                onReset = { onChange(ControlsLayout()) },
                onClose = onClose,
            )
        }
    }
}

private enum class EditorBlock { MOVEMENT, ACTIONS }

private fun ControlSlotLayout.moved(dxDp: Float, dyDp: Float): ControlSlotLayout =
    copy(offsetXDp = offsetXDp + dxDp, offsetYDp = offsetYDp + dyDp).clamped()

@Composable
private fun EditorBlockView(
    modifier: Modifier,
    size: Int,
    slot: ControlSlotLayout,
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current.density
    val onDragState by rememberUpdatedState(onDrag)
    val onSelectState by rememberUpdatedState(onSelect)
    Box(
        modifier = modifier
            .padding(EDGE_MARGIN_DP.dp)
            .offset(x = slot.offsetXDp.dp, y = slot.offsetYDp.dp)
            .alpha(slot.opacity)
            .scale(slot.scale)
            .size(size.dp)
            .background(Color(0x33FFFFFF), CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) EditorAccent else Color.Gray, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { onSelectState() }) { change, drag ->
                    change.consume()
                    onDragState(drag.x / density, drag.y / density)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoxScope.EditorPanelView(
    slot: ControlSlotLayout,
    onScale: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(0.6f)
            .background(EditorPanel, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(Res.string.layout_editor_title), color = Color.White, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.layout_editor_hint), color = Color.Gray, fontSize = 11.sp)
        Text(stringResource(Res.string.layout_scale, (slot.scale * 100).toInt()), color = Color.White, fontSize = 12.sp)
        Slider(
            value = slot.scale,
            onValueChange = onScale,
            valueRange = CONTROL_SLOT_SCALE_MIN..CONTROL_SLOT_SCALE_MAX,
            colors = SliderDefaults.colors(thumbColor = EditorAccent, activeTrackColor = EditorWine),
        )
        Text(stringResource(Res.string.layout_opacity, (slot.opacity * 100).toInt()), color = Color.White, fontSize = 12.sp)
        Slider(
            value = slot.opacity,
            onValueChange = onOpacity,
            valueRange = CONTROL_OPACITY_MIN..1f,
            colors = SliderDefaults.colors(thumbColor = EditorAccent, activeTrackColor = EditorWine),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C21), contentColor = Color.White),
            ) { Text(stringResource(Res.string.layout_reset), fontSize = 12.sp) }
            Button(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = EditorAccent, contentColor = Color.White),
            ) { Text(stringResource(Res.string.layout_done), fontSize = 12.sp) }
        }
    }
}
