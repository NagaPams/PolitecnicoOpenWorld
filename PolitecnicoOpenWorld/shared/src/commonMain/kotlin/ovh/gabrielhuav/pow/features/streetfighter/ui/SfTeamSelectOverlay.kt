package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfArcadeLadder
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfTeamBattle
import ovh.gabrielhuav.pow.platform.imagen.PowImagen
import ovh.gabrielhuav.pow.shared.recursos.*

private val TeamBlue = Color(0xFF51E2ED)
private val TeamRed = Color(0xFFFF6868)
private val TeamGold = Color(0xFFD4AF37)

/** Una textura pequeña, estática y con sprites originales; nunca abre veinte atlas de combate. */
@Composable
internal fun SfTeamSelectOverlay(
    onStart: (List<SfFighterId>, List<SfFighterId>, SfCpuDifficulty) -> Unit,
    onBack: () -> Unit,
) {
    var player by remember { mutableStateOf<List<SfFighterId>>(emptyList()) }
    var cpu by remember { mutableStateOf<List<SfFighterId>>(emptyList()) }
    var side by remember { mutableStateOf(0) }
    var difficulty by remember { mutableStateOf(SfCpuDifficulty.NORMAL) }
    var atlas by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        atlas = withContext(Dispatchers.IO) {
            runCatching { PowImagen.deAsset("STREETFIGHTER/IMAGES/team_roster.webp") }.getOrNull()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF170F1C)).padding(8.dp)) {
        val columns = if (maxWidth > maxHeight) 10 else 5
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.sf_team_title), color = TeamGold,
                fontWeight = FontWeight.Black, fontSize = 20.sp, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamSelectionStrip(player, 0, side == 0, atlas, Modifier.weight(1f),
                    onSide = { side = 0 }, onRemove = { index -> player = player.filterIndexed { i, _ -> i != index }; side = 0 },
                    onRandom = { player = SfTeamBattle.randomTeam(); side = 1 })
                TeamSelectionStrip(cpu, 1, side == 1, atlas, Modifier.weight(1f),
                    onSide = { side = 1 }, onRemove = { index -> cpu = cpu.filterIndexed { i, _ -> i != index }; side = 1 },
                    onRandom = { cpu = SfTeamBattle.randomTeam(); side = if (player.size < 3) 0 else 1 })
            }
            Text(stringResource(if (side == 0) Res.string.sf_team_pick_player else Res.string.sf_team_pick_cpu),
                color = if (side == 0) TeamBlue else TeamRed, fontSize = 11.sp)
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SfArcadeLadder.ALL_PARTICIPANTS.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { id ->
                            val p = player.indexOf(id)
                            val c = cpu.indexOf(id)
                            val selected = id in if (side == 0) player else cpu
                            val edge = if (selected) if (side == 0) TeamBlue else TeamRed else Color(0xFF695570)
                            Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF252031))
                                .border(if (selected) 2.dp else 1.dp, edge).clickable {
                                    if (side == 0 && id !in player && player.size < 3) {
                                        player = player + id
                                        if (player.size == 3 && cpu.size < 3) side = 1
                                    } else if (side == 1 && id !in cpu && cpu.size < 3) cpu = cpu + id
                                }.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                TeamSprite(id, atlas, Modifier.fillMaxWidth().weight(1f))
                                Text(id.displayName, color = Color.White, fontSize = 9.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (p >= 0 || c >= 0) Text(
                                    listOfNotNull(if (p >= 0) "J1 ${p+1}" else null, if (c >= 0) "CPU ${c+1}" else null).joinToString(" · "),
                                    color = TeamGold, fontSize = 8.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.sf_team_difficulty), color = Color.White, fontSize = 11.sp)
                listOf(SfCpuDifficulty.BASICA, SfCpuDifficulty.NORMAL, SfCpuDifficulty.AVANZADA).forEach { value ->
                    val label = when (value) {
                        SfCpuDifficulty.BASICA -> Res.string.sf_team_easy
                        SfCpuDifficulty.NORMAL -> Res.string.sf_team_medium
                        else -> Res.string.sf_team_hard
                    }
                    Text(stringResource(label), color = if (difficulty == value) TeamGold else Color.LightGray,
                        fontWeight = if (difficulty == value) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp,
                        modifier = Modifier.border(1.dp, if (difficulty == value) TeamGold else Color.Transparent)
                            .clickable { difficulty = value }.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(Res.string.sf_back), color = TeamGold) }
                TextButton(onClick = { player = SfTeamBattle.randomTeam(); cpu = SfTeamBattle.randomTeam() }) {
                    Text(stringResource(Res.string.sf_team_random_all), color = TeamGold)
                }
                TextButton(enabled = SfTeamBattle.validTeam(player) && SfTeamBattle.validTeam(cpu),
                    onClick = { onStart(player, cpu, difficulty) }) {
                    Text(stringResource(Res.string.sf_team_fight),
                        color = if (player.size == 3 && cpu.size == 3) TeamBlue else Color.Gray,
                        fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun TeamSelectionStrip(team: List<SfFighterId>, side: Int, active: Boolean, atlas: ImageBitmap?,
    modifier: Modifier, onSide: () -> Unit, onRemove: (Int) -> Unit, onRandom: () -> Unit,
) {
    val color = if (side == 0) TeamBlue else TeamRed
    Column(modifier.border(if (active) 2.dp else 1.dp, if (active) color else Color(0xFF554655))
        .clickable(onClick = onSide).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(if (side == 0) Res.string.sf_team_player else Res.string.sf_team_cpu),
                color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.sf_team_random), color = TeamGold, fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onRandom).padding(horizontal = 4.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                val id = team.getOrNull(index)
                Box(Modifier.weight(1f).height(50.dp).background(Color(0xFF252031))
                    .clickable { if (id != null) onRemove(index) else onSide() }, contentAlignment = Alignment.Center) {
                    if (id != null) TeamSprite(id, atlas, Modifier.fillMaxSize())
                    Text("${index+1}", color = color, fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
                    if (id == null) Text("+", color = Color.Gray, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun TeamSprite(id: SfFighterId, atlas: ImageBitmap?, modifier: Modifier) {
    val index = SfArcadeLadder.ALL_PARTICIPANTS.indexOf(id)
    Canvas(modifier) {
        if (atlas != null && index >= 0) {
            val side = minOf(size.width, size.height).toInt().coerceAtLeast(1)
            drawImage(atlas, srcOffset = IntOffset(index % 10 * 128, index / 10 * 128),
                srcSize = IntSize(128,128), dstSize = IntSize(side,side),
                dstOffset = IntOffset(((size.width-side)/2).toInt(),((size.height-side)/2).toInt()),
                filterQuality = FilterQuality.None)
        }
    }
}

@Composable
internal fun BoxScope.TeamBattleStatus(team: SfTeamBattle, showResult: Boolean) {
    Column(Modifier.align(Alignment.TopCenter).padding(top = 58.dp)
        .background(Color(0xD9170F1C)).padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.sf_team_remaining, team.playerRemaining, team.cpuRemaining),
            color = TeamGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (showResult) Text(stringResource(if (team.winner == 0) Res.string.sf_team_win else Res.string.sf_team_lose),
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}
