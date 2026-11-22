package ovh.gabrielhuav.pow.features.map_exterior.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.map.DoorAction
import ovh.gabrielhuav.pow.domain.models.map.DoorRules
import ovh.gabrielhuav.pow.domain.models.map.DoorRuntime
import ovh.gabrielhuav.pow.domain.models.map.DoorState

/** Menú de una puerta de edificio con el modo supervivencia: solo emite la acción elegida. */
@Composable
fun DoorMenuDialog(
    doorState: DoorRuntime,
    boards: Int,
    canEnter: Boolean,
    onAction: (DoorAction) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFFFBC02D), RoundedCornerShape(12.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.door_menu_title, boards), color = Color(0xFFFBC02D), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (doorState.state) {
                        DoorState.OPEN -> stringResource(R.string.door_state_open)
                        DoorState.CLOSED -> stringResource(R.string.door_state_closed)
                        DoorState.BARRICADED -> stringResource(
                            R.string.door_state_barricaded,
                            (doorState.health / DoorRules.BARRICADE_HEALTH * 100).toInt().coerceIn(0, 100),
                        )
                    },
                    color = Color.LightGray,
                    fontSize = 14.sp,
                )
                for (action in DoorRules.actionsFor(doorState.state, boards, canEnter)) {
                    Button(
                        onClick = { onAction(action) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A), contentColor = Color.White),
                    ) {
                        Text(
                            stringResource(
                                when (action) {
                                    DoorAction.ENTER -> R.string.door_action_enter
                                    DoorAction.OPEN -> R.string.door_action_open
                                    DoorAction.CLOSE -> R.string.door_action_close
                                    DoorAction.BARRICADE -> R.string.door_action_barricade
                                    DoorAction.DISMANTLE -> R.string.door_action_dismantle
                                },
                            ),
                        )
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C21), contentColor = Color.White),
                ) { Text(stringResource(R.string.door_menu_cancel)) }
            }
        }
    }
}
