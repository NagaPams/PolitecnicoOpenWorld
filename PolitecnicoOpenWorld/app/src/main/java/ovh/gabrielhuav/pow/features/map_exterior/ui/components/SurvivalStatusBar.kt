package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.survival.BlackoutRules
import ovh.gabrielhuav.pow.domain.models.survival.StaminaRules
import ovh.gabrielhuav.pow.domain.models.survival.Weather
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState

/**
 * Estado de supervivencia en UNA barra delgada (t31): día, clima y necesidades siempre; infección, sangrado, aliento,
 * coche y gasolina solo cuando importan (antes eran hasta seis chips apilados al centro que tapaban el juego).
 */
@Composable
fun SurvivalStatusBar(state: WorldMapState, paused: Boolean, modifier: Modifier = Modifier) {
    val red = Color(0xFFFF6B63)
    Row(
        modifier = modifier
            .background(Color(0xB3101418), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val day = "📅 ${state.survivalDay}" +
            (if (BlackoutRules.active(state.survivalDay)) " 🔌" else "") +
            when (state.survivalWeather) {
                Weather.RAIN -> " 🌧️"
                Weather.FOG -> " 🌫️"
                else -> ""
            }
        BarText(day, Color(0xFFB7C7B0))
        BarText("🍗 ${state.survivalNeeds.hunger.toInt()}", Color.White)
        BarText("💧 ${state.survivalNeeds.thirst.toInt()}", Color.White)
        BarText("⚡ ${state.survivalNeeds.energy.toInt()}", Color.White)
        if (state.survivalInfection.infected) {
            BarText(
                "🦠 " + stringResource(if (state.survivalInfection.symptomatic) R.string.wm_survival_symptoms else R.string.wm_survival_incubating),
                red,
            )
        }
        if (state.survivalBleeding) BarText("🩸", red)
        if (state.survivalFractured) BarText("🦴", red)
        if (state.survivalStamina < 99.0) {
            BarText("🫁 ${state.survivalStamina.toInt()}", if (state.survivalStamina < StaminaRules.RECOVER_AT) red else Color(0xFF4DD0E1))
        }
        if (state.isDriving) {
            BarText("🚗 ${state.survivalVehicleHealth.toInt()}%", if (state.survivalVehicleHealth < 35.0) red else Color(0xFF90A4AE))
            BarText("⛽ ${state.survivalFuel.toInt()}%", if (state.survivalFuel < 15.0) red else Color(0xFFFFB300))
        }
        if (paused) BarText("⏸ " + stringResource(R.string.wm_survival_paused), Color(0xFFFFD35A))
    }
}

@Composable
private fun BarText(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}
