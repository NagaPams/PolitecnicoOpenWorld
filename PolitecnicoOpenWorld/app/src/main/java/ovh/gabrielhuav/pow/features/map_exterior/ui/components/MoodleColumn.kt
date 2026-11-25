package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.domain.models.survival.Moodle
import ovh.gabrielhuav.pow.domain.models.survival.MoodleKind
import ovh.gabrielhuav.pow.domain.models.survival.MoodleRules
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalCrafting
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState

/** Moodles (t11): columna de iconos con gravedad (puntos) para lo que molesta ahora; vacía si todo va bien. */
@Composable
fun MoodleColumn(state: WorldMapState, modifier: Modifier = Modifier) {
    val weight = SurvivalCrafting.weightOf(state.survivalPack)
    val moodles = remember(
        state.survivalNeeds, state.survivalStamina, state.survivalBleeding, state.survivalFractured,
        state.survivalInfection, weight,
    ) {
        MoodleRules.active(
            state.survivalNeeds, state.survivalStamina, state.survivalBleeding, state.survivalFractured,
            state.survivalInfection, weight,
        )
    }
    if (moodles.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    Column(
        modifier = Modifier
            .background(Color(0xB3101418), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(iconOf(moodle.kind), fontSize = 16.sp)
        Text("●".repeat(moodle.level), color = tint, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
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
}
