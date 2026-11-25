package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.survival.Moodle
import ovh.gabrielhuav.pow.domain.models.survival.MoodleKind
import ovh.gabrielhuav.pow.domain.models.survival.MoodleRules
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalCrafting
import ovh.gabrielhuav.pow.domain.models.survival.TemperatureRules
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState

/** Moodles (t11): fila de iconos con gravedad (puntos) para lo que molesta ahora; vacía si todo va bien. */
@Composable
fun MoodleColumn(state: WorldMapState, modifier: Modifier = Modifier) {
    val weight = SurvivalCrafting.weightOf(state.survivalPack)
    val moodles = remember(
        state.survivalNeeds, state.survivalStamina, state.survivalBleeding, state.survivalFractured,
        state.survivalInfection, weight, state.survivalWeather, state.nightAlpha > 0.05f,
    ) {
        MoodleRules.active(
            state.survivalNeeds, state.survivalStamina, state.survivalBleeding, state.survivalFractured,
            state.survivalInfection, weight, TemperatureRules.coldLevel(state.survivalWeather, state.nightAlpha > 0.05f),
        )
    }
    if (moodles.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        moodles.forEach { MoodleChip(it) }
    }
}

@Composable
private fun MoodleChip(moodle: Moodle) {
    val tint = when (moodle.level) {
        1 -> Color(0xFFE0E0E0)
        2 -> Color(0xFFFFD35A)
        3 -> Color(0xFFFF9A3C)
        else -> Color(0xFFFF6B63)
    }
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .clickable { Toast.makeText(context, context.getString(nameOf(moodle.kind)), Toast.LENGTH_SHORT).show() }
            .background(Color(0xB3101418), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(iconOf(moodle.kind), fontSize = 16.sp)
        Text("●".repeat(moodle.level), color = tint, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun nameOf(kind: MoodleKind): Int = when (kind) {
    MoodleKind.HAMBRE -> R.string.moodle_hambre
    MoodleKind.SED -> R.string.moodle_sed
    MoodleKind.CANSANCIO -> R.string.moodle_cansancio
    MoodleKind.SIN_ALIENTO -> R.string.moodle_aliento
    MoodleKind.SANGRADO -> R.string.moodle_sangrado
    MoodleKind.FRACTURA -> R.string.moodle_fractura
    MoodleKind.INFECCION -> R.string.moodle_infeccion
    MoodleKind.CARGA -> R.string.moodle_carga
    MoodleKind.FRIO -> R.string.moodle_frio
}

private fun iconOf(kind: MoodleKind): String = when (kind) {
    MoodleKind.HAMBRE -> "🍗"
    MoodleKind.SED -> "💧"
    MoodleKind.CANSANCIO -> "😴"
    MoodleKind.SIN_ALIENTO -> "🫁"
    MoodleKind.SANGRADO -> "🩸"
    MoodleKind.FRACTURA -> "🦴"
    MoodleKind.INFECCION -> "🦠"
    MoodleKind.CARGA -> "🎒"
    MoodleKind.FRIO -> "🥶"
}
