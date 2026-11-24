package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalCrafting
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalArsenal
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalPack
import ovh.gabrielhuav.pow.domain.models.survival.WeaponCatalog
import ovh.gabrielhuav.pow.domain.models.survival.WeaponKind
import ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop

// ─── INVENTARIO EN EL MAPA GLOBAL (🆕 2026-07-13, mantener Y a pie) ───────────
// Panel de SOLO LECTURA: muestra los mismos objetos que el inventario de interiores
// (currentInteriorInventory: llave de la M1, lata de la M2…) con sus assets reales.
// Probar/desechar viven en INTERIORES (ahí está la lógica del puzzle); aquí solo consultas
// qué llevas. Mismo look que el panel de ZombieHud (slots dorados, bloqueados en rojo).
@Composable
fun WorldInventoryDialog(
    inventoryKeys: List<String>,
    unlockedSlots: Int,
    onDismiss: () -> Unit,
    survivalPack: SurvivalPack? = null,
    onCraftKit: () -> Unit = {},
    onUseKit: () -> Unit = {},
    arsenal: SurvivalArsenal? = null,
    onEquip: (WeaponKind) -> Unit = {},
    fuel: Double = 0.0,
    onUseFuelCan: () -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x88000000))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.systemBarsPadding()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(Color(0xFF1E1E24), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(16.dp))
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text(
                    stringResource(R.string.zhud_inventory),
                    color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val totalSlots = 4
                    for (i in 0 until totalSlots) {
                        val unlocked = i < unlockedSlots
                        val held = inventoryKeys.getOrNull(i)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (unlocked) Color(0xFF2A2A33) else Color(0x55B71C1C),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    2.dp,
                                    if (unlocked) Color(0xFFD4AF37) else Color(0xFFB71C1C),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                !unlocked -> Text("🔒", fontSize = 24.sp)
                                held != null -> WorldInventoryItemIcon(
                                    KeyDrop.entryAsset(held), modifier = Modifier.size(46.dp)
                                )
                                else -> {}
                            }
                        }
                    }
                }
                Text(
                    if (inventoryKeys.isEmpty()) stringResource(R.string.zhud_inv_empty)
                    else stringResource(R.string.wm_inv_readonly_hint),
                    color = Color(0xFFB0BEC5), fontSize = 12.sp,
                    modifier = Modifier.widthIn(max = 260.dp)
                )
                // Mochila de supervivencia (solo con el modo encendido): guarda lo que no servía al recogerlo.
                if (survivalPack != null) {
                    Text(stringResource(R.string.pack_title), color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        stringResource(R.string.pack_counts, survivalPack.pizza, survivalPack.refresco, survivalPack.botiquin),
                        color = Color.White, fontSize = 14.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onCraftKit,
                            enabled = SurvivalCrafting.canCraftKit(survivalPack),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A), contentColor = Color.White),
                        ) { Text(stringResource(R.string.pack_craft_kit), fontSize = 12.sp) }
                        Button(
                            onClick = onUseKit,
                            enabled = survivalPack.botiquin > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                        ) { Text(stringResource(R.string.pack_use_kit), fontSize = 12.sp) }
                    }
                }
                if (survivalPack != null) {
                    Text(stringResource(R.string.fuel_counts, survivalPack.bidones, fuel.toInt()), color = Color.White, fontSize = 14.sp)
                    Button(
                        onClick = onUseFuelCan,
                        enabled = survivalPack.bidones > 0 && fuel < 100.0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B4A1C), contentColor = Color.White),
                    ) { Text(stringResource(R.string.pack_use_fuel), fontSize = 12.sp) }
                }
                // Armas (solo con el modo encendido): toca una para equiparla; las de fuego piden munición.
                if (arsenal != null) {
                    Text(stringResource(R.string.weapons_title), color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(stringResource(R.string.weapons_ammo, arsenal.balas, arsenal.cartuchos), color = Color.White, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (kind in listOf(WeaponKind.PUNOS) + arsenal.weapons.map { it.kind }) {
                            val uses = arsenal.weapons.firstOrNull { it.kind == kind }?.uses ?: 0
                            val name = stringResource(weaponNameRes(kind))
                            Button(
                                onClick = { onEquip(kind) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (kind == arsenal.equipped) Color(0xFFD4AF37) else Color(0xFF3A3A44),
                                    contentColor = if (kind == arsenal.equipped) Color.Black else Color.White,
                                ),
                            ) { Text(if (WeaponCatalog.of(kind).durability > 0) "$name $uses" else name, fontSize = 12.sp) }
                        }
                    }
                }
            }
            // ✕ CERRAR — esquina superior derecha del panel (mismo patrón que ZombieHud).
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .background(Color(0x66000000), CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun weaponNameRes(kind: WeaponKind): Int = when (kind) {
    WeaponKind.PUNOS -> R.string.weapon_punos
    WeaponKind.PALO -> R.string.weapon_palo
    WeaponKind.BATE -> R.string.weapon_bate
    WeaponKind.CUCHILLO -> R.string.weapon_cuchillo
    WeaponKind.PISTOLA -> R.string.weapon_pistola
    WeaponKind.ESCOPETA -> R.string.weapon_escopeta
}

// Icono del ítem: carga la IMAGEN REAL del asset (PNG submuestreado); si falla, cae a un 🔑.
// (Copia local del InventoryKeyIcon privado de ZombieHud — mantiene los features desacoplados.)
@Composable
private fun WorldInventoryItemIcon(assetPath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(assetPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(assetPath) {
        bmp = withContext(Dispatchers.IO) {
            try {
                context.assets.open(assetPath).use {
                    val o = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(it, null, o)?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }
    val img = bmp
    if (img != null) {
        Image(img, contentDescription = stringResource(R.string.cd_key), modifier = modifier)
    } else {
        Text("🔑", fontSize = 24.sp)
    }
}
