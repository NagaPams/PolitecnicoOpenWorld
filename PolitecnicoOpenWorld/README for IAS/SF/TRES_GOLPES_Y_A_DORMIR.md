# 3 golpes y a dormir — equipos 3 vs 3

ES: Nueva opción en Otros modos / Seleccionar modo. Selector con los 20 sprites originales,
tres espacios J1 y tres CPU. Elegir manualmente; tocar un espacio para quitarlo. Aleatorio
por equipo y Sortear los 6; sin duplicados dentro de cada equipo, espejos entre equipos válidos.
El orden de selección es el orden de entrada. Todos los participantes están disponibles en
esta modalidad; los desbloqueos y reglas del arcade siguen independientes.

Combate por eliminación, no al mejor de tres rondas. KO o timeout elimina al perdedor;
el ganador conserva su vida restante, súper e identidad metamorfoseada. El siguiente
peleador entra con vida completa. Hay un máximo de cinco duelos. Gana quien elimina
a los tres rivales. Revancha reinicia los seis; Cambiar equipos vuelve al selector de equipos.
Fácil / Medio / Difícil ajustan CPU. Mapa del primer rival, fijo durante la serie.
Pausa y carga congelan el reloj; no hay guardado persistente de la serie ni premios arcade.

EN: Offline 3-vs-3 elimination mode with manual or random teams, original 2D sprites,
twenty available fighters, unique teammates and optional mirror opponents. The survivor
keeps remaining HP and super meter. Defeat all three opponents to win; rematch resets teams.

Archivos: dominio `SfTeamBattle`; parcial `StreetFighterTeamBattle`; selector `SfTeamSelectOverlay`;
enlaces de menú/controller/pantalla y rama de fin/relevo del ViewModel compartido.
El selector carga una sola textura pequeña en IO, sin abrir veinte atlas de combate.
Assets: `IMAGES/team_roster.webp` y `DATA/team_roster.json` en STREETFIGHTER.
Regenerar si cambia el orden de ALL_PARTICIPANTS. Solo se cargan los peleadores activos al combatir.

## Validación realizada — 2026-11-06

- Compilación Android y suite completas correctas: `:app:compileDebugKotlin :shared:testAndroidHostTest :app:testDebugUnitTest`.
- Shared: 249 pruebas; app: 129 pruebas; cero fallos. Incluye diez pruebas de equipos y dos de recursos.
- `git diff --check` sin errores. Hoja de veinte sprites revisada visualmente (1280x256, 185444 bytes).
- Recursos de texto ES/EN en paridad. Generador reproducible: `python tools/build_team_roster.py` (Pillow).
- Sin prueba manual en dispositivo/emulador ni compilación iOS desde Windows.
- Prueba manual pendiente: Otros modos → 3 golpes y a dormir; elegir 3+3 manualmente,
  quitar/cambiar un espacio y probar sorteo individual y de los seis; iniciar, perder/ganar
  duelos, verificar HP del superviviente, entradas de relevo, tercera eliminación, pausa,
  revancha y regreso a Cambiar equipos. Probar selección horizontal y vertical en pantalla pequeña.
