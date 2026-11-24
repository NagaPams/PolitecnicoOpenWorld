# 04 · Open World / `features/map_exterior/`

**ES:** El corazón del juego. El `WorldMapViewModel` (Activity-scoped, ~1426 líneas tras el refactor 2026-06-22) está **partido en
extensiones** (`WorldMap*.kt`) que agrupan la lógica por tema. El estado es `WorldMapState`.
**EN:** The game's heart. `WorldMapViewModel` (Activity-scoped, ~1426 lines after the 2026-06-22 refactor) is **split into
extension partials** (`WorldMap*.kt`) grouping logic by topic. State is `WorldMapState`.

> **🆕 Refactor de tamaño (2026-06-20):** se extrajeron 4 parciales nuevos del VM (bajó de ~3050 a
> ~2600 líneas): `WorldMapCombat.kt` (combate), `WorldMapCampaign.kt` (`setStorySpawn`),
> `WorldMapTeleport.kt` (teleport + Metro/Metrobús) y `WorldMapShineCTO.kt` (easter egg + fade de
> puerta). Todas tocan SOLO miembros `internal`/`public`; el ESTADO sigue en el VM. Los call-sites
> fuera del paquete `viewmodel` (MainActivity, WorldMapScreen) llevan import explícito. Ver 09 §0.

> **Gotcha (patrón histórico — los gemelos concretos YA se de-duplicaron el 2026-06-21):** en Kotlin,
> si una función existe como **miembro privado** en `WorldMapViewModel.kt` Y como **extensión** del mismo
> nombre en un parcial, el **miembro GANA** y la extensión queda muerta. Los 8 gemelos que había
> (`startGameLoop`, `updateVisibleRoads`, `handleMultiplayerMessage`, `addRemoteEntity`,
> `maybeRefetchRoadNetwork`, `triggerWastedSequence`, `spawnOustedDriver`, `updateDestinationRoute`) ya
> se resolvieron (ver `09 §12` y 09 §0/§12): cada función tiene UNA sola implementación
> viva. **El patrón sigue existiendo** (los parciales son extensiones), así que si creas una función
> nueva NO la dupliques como miembro+extensión. / The member-vs-extension twin pattern: member wins,
> extension dies. The 8 twins that existed were all de-duplicated on 2026-06-21 (see `09 §12`).

## Proveedores sin credenciales (2026-11-05)

La configuración de teselas y zoom nativo vive en el modelo compartido `MapProvider`.
El default es OSM_WEB. CARTO sirve marcas de agua sin una clave desde septiembre de 2026
([documentación oficial](https://carto.com/basemaps/apikey/index.html)); no tratar HTTP 200
como prueba de que ese mapa sea válido. Los valores antiguos CARTO y Google se conservan
para compatibilidad, pero `keyFreeProvider` los migra a OSM_WEB. Ajustes solo ofrece OSM,
OSM Web, Esri (calles/satélite) y OpenTopoMap en esta distribución sin credenciales.
Precarga y WebView usan la misma URL y límite de zoom; OpenTopo se limita a z17.
Si una capa Web falla al cargar teselas, Leaflet cambia a OSM y recuerda la solicitud
para evitar volver a pedir el proveedor fallido en cada frame. Una precarga fallida
no se presenta como lista: ofrece OSM. Las URLs nuevas no reutilizan la caché CARTO
con marcas de agua; no se elimina la caché ni los datos del usuario.

---

## Tabla "Key files" (salta directo aquí) / jump straight here

| Tema / Concern | Archivo / File |
|---|---|
| Game loop, multiplayer, NPCs, ESCOM, policía | `viewmodel/WorldMapViewModel.kt` + parciales |
| Proveedores de mapa + descarga/compuertas de tiles | `viewmodel/WorldMapProviders.kt` (NUEVO, refactor) |
| Modo Diseñador / landmarks (Room, edición, import/export) | `viewmodel/WorldMapDesigner.kt` (NUEVO, refactor) |
| Nivel de búsqueda + policía propia + carjack | `viewmodel/WorldMapWanted.kt` (NUEVO, refactor) |
| 🆕 Combate (melee jugador, atropello, daño contacto NPC/zombi, implacable) | `viewmodel/WorldMapCombat.kt` (NUEVO, refactor) |
| 🆕 Campaña/Modo Historia: punto de entrada del spawn (`setStorySpawn`) | `viewmodel/WorldMapCampaign.kt` (NUEVO, refactor) |
| 🆕 Teletransporte: gate de TP + estaciones Metro/Metrobús | `viewmodel/WorldMapTeleport.kt` (NUEVO, refactor) |
| 🆕 Easter egg ShineCTO + fade de puerta ESCOM | `viewmodel/WorldMapShineCTO.kt` (NUEVO, refactor) |
| 🆕 Guardado/carga de partida (Modo Historia, JSON) | `viewmodel/WorldMapSaveGame.kt` + `data/repository/SaveGameRepository.kt` |
| 🆕 Editor del Debug Interiores (líneas rojas/verdes/naranjas) | `viewmodel/WorldMapDebugEditor.kt` + `ui/components/InteriorDebugEditorPanel.kt` |
| Estado UI / UI state | `viewmodel/WorldMapState.kt` (⚠️ campos anotados "LOS POSEE XManager" = fachada combine) |
| 🆕 Managers con SUB-ESTADO propio (Etapa 3 calidad senior; el VM compone `uiState` con `combine`) | `viewmodel/DesignerManager.kt`, `CollectiblesManager.kt`, `CombatManager.kt`, `WantedManager.kt`, `TransitTeleportManager.kt`, `CampaignManager.kt` (tests en `app/src/test/.../viewmodel/*ManagerTest.kt`) |
| 🆕 DI con Hilt (BD/cachés/repos) | `di/AppModule.kt`; VMs `@HiltViewModel`/`@AssistedInject` (ver 01 + `_ARCHIVO/PLAN_DI_hilt.md`) |
| Game loop | `WorldMapViewModel.startGameLoop()` (MIEMBRO; `viewmodel/WorldMapGameLoop.kt` = tombstone tras de-dup 2026-06-21) |
| Multiplayer relay/parse | `viewmodel/WorldMapMultiplayer.kt` (+ `WorldMapMultiplayerModels.kt`) |
| Red de calles / road network | `viewmodel/WorldMapRoadNetwork.kt` |
| Routing / waypoints | `viewmodel/WorldMapRouting.kt` |
| ESCOM (puertas, ousted driver) | `viewmodel/WorldMapEscom.kt` |
| Coleccionables (lógica) | `viewmodel/WorldMapCollectiblesLogic.kt` |
| Misc (startMovementAction, secuencia WASTED, timer barra de vida) | `viewmodel/WorldMapMisc.kt` |
| 🆕 Cámara/zoom (auto/manual, pinch, pan) + toggles de widgets | `viewmodel/WorldMapCameraUi.kt` (+ `autoZoomTarget` en `WorldMapState.kt`) (NUEVO, refactor — extensiones) |
| 🆕 Ajustes (densidad/LOD NPCs) + skin | `viewmodel/WorldMapSettings.kt` (NUEVO, refactor — extensiones) |
| 🆕 Interacción del jugador (abordar/bajar vehículo, puerta/metro/coleccionable, reclamar objeto, teleport directo, toggle apocalipsis zombi) | `viewmodel/WorldMapInteractions.kt` (NUEVO 2026-06-22 — extensiones) |
| 🆕 Salida segura de vehículo (candidatos, fallback y coche abandonado) | `viewmodel/VehicleExitPlan.kt` + `viewmodel/WorldMapVehicleExit.kt` |
| 🆕 Salud del jugador (daño/curación/barra de vida) | `viewmodel/WorldMapHealth.kt` (NUEVO 2026-06-22 — extensiones) |
| 🆕 Items de ESCOM + inyección de coche dinámico (Diseñador) | `viewmodel/WorldMapEscomItems.kt` (NUEVO 2026-06-22 — extensiones) |
| 🆕 Movimiento del jugador a pie/joystick + aduana de colisión + snap-to-road + rescate anti-atasco | `viewmodel/WorldMapMovement.kt` (NUEVO 2026-06-22 — extensiones) |
| Render principal (selector de renderer: OSM/Google/Web) | `ui/WorldMapScreen.kt` (~1325) |
| 🆕 Rama de render Google Maps nativo (`GoogleMapLayer`) | `ui/WorldMapScreenGoogle.kt` (NUEVO, refactor — composable top-level; cachés LRU por parámetro) |
| 🆕 Rama de render WEB / Leaflet en WebView (`WebMapLayer`) | `ui/WorldMapScreenWeb.kt` (NUEVO, refactor — composable top-level; cachés base64 + holders de guarda por-frame por parámetro) |
| 🆕 Overlays/diálogos superpuestos (WASTED, vídeo zombi, prompts, diálogo Prankedy, popup coleccionable, fades puerta ESCOM/metro/metrobús) | `ui/WorldMapScreenOverlays.kt` (NUEVO, refactor) |
| 🆕 Controles en pantalla (D-pad/joystick/acciones, conducción con el MISMO diamante Xbox, salir apocalipsis, pulsación larga Y) | `ui/WorldMapScreenControls.kt` (NUEVO, refactor — `BoxScope.WorldMapControls`) |
| Render nativo osmdroid (fog, over-zoom, NPCs, landmarks) | `ui/NativeOsmMap.kt` |
| 🆕 Render de Prankedy en OSM nativo (+ proyectil) | `ui/NativeOsmMapPrankedy.kt` (NUEVO, refactor — `renderPrankedyOnMap`) |
| 🆕 Overlay de neblina (fog of war) OSM nativo | `ui/NativeOsmMapFog.kt` (NUEVO, refactor — `class FogOverlay`) |
| 🆕 Entrada a interiores data-driven (puerta→ruta) | `domain/models/InteriorEntryCatalog.kt` (NUEVO; lo usa `handleInteraction`) |
| 🆕 IA de NPCs: movers zombi/policía, aggro, seguimiento, distancia | `domain/models/ai/NpcAiManagerMovement.kt` (NUEVO, refactor — extensiones de `NpcAiManager`) |
| 🆕 IA de NPCs: movers de calles/campus (`moveNpc`/`moveLocalNpc`: tráfico, compromiso de intersección, esquive, parking, navGraph) | `domain/models/ai/NpcAiManagerTraffic.kt` (NUEVO, refactor — extensiones de `NpcAiManager`) |
| HTML de Leaflet (WebView) | `ui/WorldMapLeafletHtml.kt` |
| Intercepción de tiles Leaflet | `ui/CachingWebViewClient.kt` |
| Helpers de dibujo / health bar | `ui/WorldMapDrawingUtils.kt` |
| Sprite jugador / vehículo conducido | `ui/components/PlayerCharacter.kt` |
| Controles (D-Pad/joystick/A-B-X-Y) | `ui/components/GameControllers.kt` |
| 🆕 Botón estilo POW compartido (esquinas cortadas + vino; lo usa TITULACIÓN POR COMBATE) | `ui/components/PowButton.kt` |
| Menú anidado de opciones | `ui/components/OptionsMenu.kt` |
| Panel del modo diseñador | `ui/components/Designerpanel.kt` |
| Sprites NPC | `ui/components/CharacterSpriteManager.kt`, `VehicleSpriteManager.kt`, `PoliceSpriteManager.kt` |
| NPC especial Prankedy (IA/VM/render) | `domain/models/ai/PrankedyManager.kt` (ver 03), `viewmodel/WorldMapPrankedy.kt`, `ui/components/PrankedySpriteManager.kt` |
| 🆕 MISIÓN 2 "El rumor" (máquina de fases: esconderse/rumor/brote/plática/mochila) | `viewmodel/WorldMapMission2.kt` (guion/constantes en `domain/models/campaign/mission2/Mission2.kt`; ver `CAMPAIGN/02_MISSION_2.md`). ⚠️ 2026-07-08: la fase 1 "esconderse" ya NO corre aquí — se juega DENTRO del lobby (interiores; `completeMission2Hide`/`failMission2Hide` son los callbacks). Ver 09. |
| 🆕 MISIÓN 3 "Regreso a la ENCB" (viaje/cordón-sigilo/asalto) | `viewmodel/WorldMapMission3.kt` (NUEVO; constantes en `domain/models/campaign/mission3/Mission3.kt`; ver `CAMPAIGN/03_MISSION_3.md`) |
| 🆕 REGISTRO/SELECTOR de misiones (estilo Witcher) | `viewmodel/WorldMapMissionLog.kt` + `ui/components/MissionLogDialog.kt` (catálogo `MissionCatalog.missions`). 🆕 2026-07-04b: el diálogo se hospeda a nivel **AppNavGraph** (`MissionLogHost`, patrón SaveSlotsDialog) → sirve al mapa Y a interiores; REJUGAR ✔ completadas sin tocar el progreso (`replayingMissionId` transitorio); Modo Dev = seguir 🔒 + "TP al objetivo". Ver 09. |
| 🆕 MISIONES SECUNDARIAS (side1 entrega / side2 contención) | `viewmodel/WorldMapSideMissions.kt` (tick; SIN fase persistida: el id del objetivo ES el estado) + `domain/models/campaign/side/SideMissions.kt` (coords/objetivos). Badge SECUNDARIA en el registro. Ver `CAMPAIGN/04_SIDE_MISSIONS.md` y 09. |
| 🆕 EVENTOS DINÁMICOS del mundo (vida urbana: conversación / persecución / mini-brote) | `viewmodel/WorldMapDynamicEvents.kt` (tick del game loop; actores `DYN_*` en `dynamicEventNpcs`, scriptados beeline, no atacables). Mini-brote más probable de NOCHE y con la M3 completada. |
| 🆕 (2026-07-08) VIDA DE CAMPUS ESCOM (estudiantes ambientales del mapa global) | `viewmodel/WorldMapCampusLife.kt` (NUEVO): ~10 estudiantes `CAMPUS_*` dentro del bbox de la ESCOM — deambulan + corrillos de 3 platicando (💬 alternada). Determinista por cubetas de tiempo (sin estado por NPC). Lista `campusNpcs`; excluidos del guardado; pausados en escolta/chase M1 y zombi global. Ver 09. |
| 🆕 ECONOMÍA (dinero del jugador) | `viewmodel/WorldMapEconomy.kt` (`addMoney`, `MissionRewards`, `COLLECTIBLE_MONEY`). `WorldMapState.playerMoney` + chip 💵; persistido en `GameSaveData.playerMoney`. Recompensa SOLO la 1ª vez (hook en `markMissionCompleted`). |
| 🆕 CICLO DÍA/NOCHE | `viewmodel/WorldMapDayNight.kt` (reloj epoch: 1 min real = 1 h juego, ciclo 24 min; `gameHour`/`nightAlpha` en el estado). Velo nocturno = capa Compose renderer-agnóstica en `WorldMapScreen` + chip 🕐. |

---

## Estado / State — `WorldMapState.kt`

```kotlin
enum class MapProvider(displayName) {
  OSM, GOOGLE_MAPS_NATIVE, CARTO_VOYAGER, OSM_WEB, GOOGLE_MAPS, CARTO_DB_DARK,
  CARTO_DB_LIGHT, ESRI, ESRI_SATELLITE, OPEN_TOPO; // 9 proveedores (web = todos menos OSM y GOOGLE_MAPS_NATIVE)
  val isWebProvider: Boolean
}
// El render web pasa maxNativeZoom por proveedor a Leaflet (CARTO=20, OSM/ESRI=19,
// OPEN_TOPO=17, Google=20): a partir de ahí Leaflet escala (over-zoom).
enum class RoadSource { LOADING, LOCAL_DB, NETWORK }
enum class TileSource { LOCAL_OSM, LOCAL_CACHE, NETWORK }
data class PoliceShot(from: GeoPoint, to: GeoPoint, at: Long)
```

**`WorldMapState`** — campos por grupo / fields by group (default `mapProvider = CARTO_VOYAGER`):
- **Mapa/carga:** `currentLocation, isLoadingLocation, zoomLevel, mapProvider, pendingProvider,
  pendingProviderReady, isMapReady, mapLoadProgress, roadSource, tileSource, isRoadNetworkReady`.
- **Render flags:** `showCacheWidget(=true), showFpsWidget, showZoomWidget (widget de nivel de zoom,
  Ajustes→Interfaz), showSpeedometer (=true; velocímetro km/h, solo al conducir), showCoordsWidget
  (widget de coordenadas X/Y/Z, Ajustes→Interfaz, default oculto; X=lon, Y=lat, Z="GLOBAL"),
  showRoadNetwork, isUserPanningMap`.
- **Zoom por estado (constantes en `WorldMapState.kt`):** `ZOOM_ON_FOOT=22`, `ZOOM_DRIVING=21`;
  `autoZoomTarget(isDriving)` elige uno según si conduces (la velocidad ya no mueve la cámara; se eliminó
  `ZOOM_DRIVING_FAST`). `updateAutoZoom()` corre cada tick del game loop y solo actúa cuando CAMBIA el
  objetivo automático (`lastAutoZoomTarget`), así el pinch manual se respeta.
  **Suavizado de zoom:** `WorldMapState` ahora tiene `targetZoomLevel` y `zoomTransitionTicks`.
  `updateAutoZoom()` fija `targetZoomLevel` en vez de cambiar `zoomLevel` de golpe. El game loop
  interpola `zoomLevel` hacia `targetZoomLevel` (10 % de la distancia restante por tick) para
  evitar el "shock" visual al robar/salir de un auto.
- **Cámara fija norte-arriba (2026-11-10, estilo GTA Chinatown Wars):** el mapa NUNCA gira ni se adelanta: siempre centrado en el jugador. El zoom depende SOLO de si conduces (`autoZoomTarget`: a pie `ZOOM_ON_FOOT=22`, manejando `ZOOM_DRIVING=21`), no de la velocidad; `updateAutoZoom()` reasigna `targetZoomLevel` solo al subir/bajar de un vehículo (el pinch manual se respeta) e interpola `zoomLevel` 10 % por tick. El COCHE es lo que rota: `PlayerCharacter` usa `vehicleSpriteHeading(vehicleRotation)` (shared, la misma fórmula que el coche abandonado y los NPC) con frames de 7.5°. Se descartaron la cámara que gira con el coche, el zoom por velocidad y el look-ahead porque mareaban (video 2026-11-10). ⚠️ `PlayerCharacter` reaplica su `modifier` a la caja exterior Y a la imagen del coche: no pasar offsets por ahí.

  `ZOOM_GAMEPLAY_WEB=19` quedó SOLO como nivel máx. de tiles reales que se pre-descargan en web.
- **Controles/skin:** `controlType, controlsScale, swapControls, controlsLayout, selectedSkin, showSkinSelector`.
- **Editor de controles (`controlsLayout`):** `ControlsLayout` (`:shared`, `features/settings/models/ControlsLayout.kt`) guarda para el bloque de movimiento y el de acción un desplazamiento en dp (`offsetXDp/offsetYDp`, tope ±240), escala (0,6–1,4) y opacidad (0,2–1) **respecto a su sitio de fábrica**: todo en 0/1 = como antes. Se persiste como JSON en `SettingsRepository` (`CONTROLS_LAYOUT`, vacío = de fábrica; un JSON roto cae a la de fábrica, nunca lanza). El editor (`ControlsLayoutEditor.kt`, Ajustes → Controles → «Editar disposición») arrastra y ajusta sobre el estado temporal y se aplica con GUARDAR, como el resto de los controles; `WorldMapScreenControls.kt` lo aplica con `controlSlot()` a pie y conduciendo. Solo el mundo abierto: los interiores y el modo pelea siguen con escala y lado. Idea tomada del editor de controles de Zomdroid; el código es propio.
- **Jugador:** `playerAction, isPlayerFacingRight, isRunning, isDriving, currentVehicleModel,
  currentVehicleColor, vehicleSpeed, vehicleRotation, vehicleIsFirstTimeBoarded`.
- **NPCs/multiplayer:** `npcs, isMultiplayer, playerName`.
- **Diseñador:** `isDesignerMode, selectedLandmarkId, showAssetPicker, landmarks`.
- **Coleccionables/daño:** `activeCollectibles, nearbyCollectible, showClaimedPopupFor,
  interactionPrompt, showWastedScreen`.
- **Wanted/policía:** `wantedLevel(0-5), carjackWarning, policeShots`.
- **Navegación:** `destinationMarker, isTargetingWaypoint, routeWaypoints, showDestinationRoute,
  destinationArrivalThreshold=20.0`.
- **Zombi/interiores:** `showZombiVideo, isZombieHandSpawned, pendingInteriorDestination`.
- **Transiciones:** `showEscomDoorFade, escomDoorFadeComplete, pendingDoorDestination, showShineCTODiscovery`.
- **Metro:** `metroStations, nearbyMetroStation, showMetroFade, metroFadeCompleteStation`. **🆕 Zona de
  interacción:** la proximidad del metro usa `METRO_INTERACT_RADIUS_METERS=30.0` y la del **metrobús**
  `METROBUS_INTERACT_RADIUS_METERS=18.0` (más chico). Constantes de nivel de archivo
  en `WorldMapState.kt`; 4× los 15 m de coleccionables/puertas) para metro **y metrobús**, porque el logo de
  Overpass a veces cae sobre un edificio inaccesible por calle y con snap-to-road no se podía pisar. Esa zona
  se **DIBUJA** (círculo naranja translúcido) en **web** (`updateMetro`, `L.circle`) y **OSM nativo**
  (`Polygon.pointsAsCircle`, tag `route_overlay_tag+600`, culleada por viewport, bajo el icono). Google nativo = pendiente.
- **Prefetch offline (solo OSM nativo):** `zonePrefetchActive, zonePrefetchProgress, zoneOfflineReady, zoneOfflineWarning`.
- **Creador de rutas:** `routeDebugWaypoints, isParkingSlotMode, currentWayId(=100)`.
- **🆕 Economía / día-noche (2026-07-08):** `playerMoney` (persistido en `GameSaveData.playerMoney`;
  +$25 por coleccionable, recompensas por misión vía `MissionRewards`), `gameHour` (0-23) y
  `nightAlpha` (0..0.38, velo nocturno). Ver `WorldMapEconomy.kt` / `WorldMapDayNight.kt`.

---

## Game loop — `WorldMapGameLoop.kt` / `WorldMapViewModel.startGameLoop`

**ES:** Coroutine en `Dispatchers.Default`, **tick cada 33 ms** (~30 Hz). Arranque: espera ubicación,
carga calles (caché Room → celda estática `datos-calles` → Overpass con respaldos y backoff → malla
local transitable si falla). La malla no se persiste y `maybeRefetchRoadNetwork` conserva los reintentos
de calles reales en segundo plano. La cobertura inicial y la generación mensual se documentan en
`tools/calles/README.md`. Luego, por tick:
**EN:** Coroutine on `Dispatchers.Default`, **tick every 33 ms** (~30 Hz). Startup: wait for location,
load roads (Room cache → static `datos-calles` cell → Overpass backups with backoff → traversable local
grid on failure). The grid is not persisted and `maybeRefetchRoadNetwork` keeps retrying real roads in
the background. Initial coverage and monthly generation are documented in `tools/calles/README.md`. Then,
per tick:

```
1. ESCOM sync: dentro de ESCOM + red lista → `spawnEscomItems` (ya **NO** spawnea la mano; solo marca el flag); fuera → limpia.
2. cada 30 ticks → trySpawningCollectible; siempre → checkCollectibleProximity, checkDestinationArrival.
3. cada 30 ticks (si hay marker) → updateDestinationRoute.
4. si playerAction==SPECIAL → performPlayerAttack (golpe a NPCs Y jugadores remotos, ATTACK_RADIUS=0.00008 ~9 m, cuerpo a cuerpo).
   Si golpea a un jugador remoto, envía `PLAYER_ATTACK_HIT { targetId, damage }` al servidor.
5. si isDriving && !WASTED → física del coche:
     girar ±2°/tick (solo con velocidad), gas→+ACCELERATION (cap MAX_SPEED), freno→-BRAKING_FRICTION
     (reversa hasta -MAX_SPEED/2), inercia al soltar. dx=sin(rot)*v, dy=cos(rot)*v.
     **fuera de la calle (curva/bifurcación):** en vez de chocar y parar, **auto-direcciona** el coche
     hacia la calle (muestrea un punto ~22 m adelante en la red, gira suave hacia ahí) y baja la velocidad
     **proporcional** al desvío (`overshoot`); `maxRoadRadius=0.00004` (más holgado). Así, dejando
     acelerar, sigue la carretera. runOverNpcs(loc, v).
6. applyNpcContactDamage(loc)  // NPCs agresivos en embestida pegan a TU jugador (cada cliente al suyo)
7. si red lista && !WASTED → runPoliceTick(loc)
7b. 🆕 tras el `when` de misiones (escolta/chase/M2/M3/SECUNDARIAS): si mundo completo && !WASTED →
    runDynamicEventsTick(loc) (eventos ambientales); siempre → updateDayNightTick() (throttle ~1 Hz)
8. maybeRefetchRoadNetwork(loc); cada 5 ticks → updateVisibleRoads(loc)
9. si red lista, cada 3 ticks → setServerNpcs(npcs remotos) → npcAiManager.updateNpcs(loc, isHost)
     → si soy Host: aplicar pendingDespawns, volcar processedNpcs a remoteEntities → updateNpcsState()
     → enviar PLAYER_UPDATE; si Host: NPC_DESTROY de despawns + NPC_BATCH_UPDATE de NPCs activos (dist<=0.0012)
```
Constantes del VM / VM constants: `MAX_SPEED=0.000017`, `ACCELERATION=0.0000003`,
`BRAKING_FRICTION=0.000001`, `ATTACK_RADIUS=0.00008`, `RUN_OVER_RADIUS=0.00003`,
`NPC_CONTACT_COOLDOWN_MS=900`, `RELENTLESS_HIT_STREAK=6`, `CELL=0.0025` (grid de snap-to-road). El loop está envuelto en
try/catch (relanza `CancellationException`, traga el resto para no crashear). / Loop wrapped in
try/catch (rethrows cancellation, swallows the rest).

---

## Routing / snap-to-road — `WorldMapRouting.kt`

**ES:** El jugador **no puede salirse de las calles**. Cada movimiento se valida contra un **índice
espacial en grid** (`Seg` = segmento + `HashMap<celda, List<Seg>>`), corriendo *snap-to-road* en
O(candidatos cercanos).
**EN:** The player **can't leave roads**. Every move is validated against a **spatial grid index**
(`Seg` + `HashMap<cell, List<Seg>>`), running snap-to-road in O(nearby candidates).

> **🆕 ZONAS LIBRES (campus): ESCOM y ENCB.** Dentro del bounding box de ESCOM (`ESCOM_BASE_LAT/LON` ±
> `ESCOM_OFFSET`) o de la **ENCB** (`ENCB_BASE_LAT=19.5001588`, `ENCB_BASE_LON=-99.1450298`, `ENCB_OFFSET=0.0012`,
> ~130 m) se **suspende el snap-to-road**: `moveCharacter`/`moveCharacterByAngle` consultan
> `isFreeMovementZone(lat,lon)` (= `isInsideEscom || isInsideEncb`, miembros `internal`) y mueven al jugador a la
> coordenada `(x,y)` libre (tras la aduana de choque de bardas). **Prankedy** hace lo mismo: `runPrankedyTick`
> calcula `freeZone` desde la posición del jugador y, si está en campus, **apaga el snap** (`snapToRoad` devuelve
> el punto sin tocar) → persigue en **línea recta (steer-to-target)** a `p_run`. El estado es **per-tick**: al
> salir del perímetro vuelve el snap normal (salvo que entre al box de ESCOM, también libre). Ver 03 (Prankedy) y 07.
>
> **🆕 Regla visual simétrica (ESCOM y ENCB):** `updateVisibleRoads(location, force)` comprueba al inicio
> `isFreeMovementZone(location)`; si es true **vacía `_roadNetworkFlow` (emptyList) y hace `return`** (salta el
> filtro en `Dispatchers.Default`), de modo que **dentro de cualquiera de los dos campus NO se pintan las líneas
> de calles** (las amarillas de Overpass). Al salir del box recupera el filtrado normal. Misma condición que el
> movimiento libre, así ambas zonas quedan limpias.

- `ensureIndex()` / `candidates(loc): List<Seg>` / `getNearestPointOnNetwork(t): GeoPoint` /
  `project(p, v, w): GeoPoint` (proyección punto-segmento).
- `pack(r, c): Long`, `cell(v): Int` (celda de grid), `CELL` (tamaño de celda).
- **Rutas por calles (A\*):** `buildRoadGraph`, `nearestGraphNode`, `findRoadRoute(from, to)` (A* sobre el grafo de
  nodos únicos con heurística euclídea y tope de 6000 expansiones; sin camino cae a la línea recta; lo usa la policía),
  `rebuildRoadNodeGrid`, `nearbyRoadNodes`. `calculateRouteOnNetwork` (la versión greedy) ya no existe.
  **Claves `Pair<Double,Double>`** (sin allocs de String por paso).
- `updateDestinationRoute()` dibuja polilínea azul punteada; el marker se auto-borra a `destinationArrivalThreshold` (20 m).
- **🆕 Rescate anti-atasco (TP a metro sobre edificio):** `moveCharacter`/`moveCharacterByAngle` (MIEMBROS) tienen
  un guard que NO mueve si la calle más cercana al destino está a `> MAX_SNAP_DISTANCE_DEG` (~33 m) — evita "TP a
  calle al azar" durante recargas de red. **Pero** si el TP deja al jugador ATRAPADO lejos de toda calle (estación
  de metro encima de un edificio), ese guard lo dejaba inmóvil tras una "pared invisible". Fix: `rescueIfStuckOffNetwork(loc, step)`
  (miembro privado) — si la posición ACTUAL también está a `> MAX_SNAP` de la red (atasco real, no glitch), al moverse
  arrastra al jugador `step*4` hacia la calle más cercana hasta engancharlo. En juego normal (jugador sobre la calle)
  no se dispara. Ver 09.

> **Nota (2026-11-23):** el router open-world YA es A* (`findRoadRoute`); lo que NO tienen los zombis y policías cazadores
> es pathfinding de rodeo de edificios: van en línea recta y solo deslizan por la pared (`slideAroundBarrier`). Una horda
> que cruce el campus por calles necesitaría reutilizar `findRoadRoute`; no se implementa hasta que el juego lo pida.
> Medido con tráfico a 120 km/h en el Motorola: mediana 21 ms, p90 29 ms, p99 40 ms, así que las asignaciones por tick de
> `runOverNpcs` y `getNearestCarRoadPoint` no se tocan sin evidencia de GC. El servidor zombi sí usa Dijkstra (ver 08).

---

## Red de calles / Road network — `WorldMapRoadNetwork.kt`

- **Inicio degradado:** `OfflineRoadNetwork.kt` crea cuatro corredores locales para peatones y autos,
  centrados en la ubicación inicial, cuando Overpass no entrega una red antes del límite de arranque.
  `RoadSource.OFFLINE_FALLBACK` lo expone como **Malla local** en el widget; permite entrar al sandbox
  sin afirmar que son datos descargados.
- `updateVisibleRoads(location, force)` — filtra ways visibles (throttled a cada 5 ticks).
- `maybeRefetchRoadNetwork(currentLoc)` — re-fetch al alejarse de `lastNetworkFetchLocation`
  (cooldown 5 min); guarda en `RoadNetworkCache`.
- `prefetchCurrentZoneTiles(loc)` — dispara `TilePrefetchManager` (solo OSM nativo, offline).

## Multiplayer — `WorldMapMultiplayer.kt` (+ `WorldMapMultiplayerModels.kt`)

```kotlin
enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }
data class MultiplayerPlayer(id, displayName, x/*lon*/, y/*lat*/, action, facingRight, isDriving,
                             carModel, carColor, vehicleRotation, health)
data class MultiplayerNpc(id, x, y, rotation, npcType, ownerId, carModel, carColor,
                          hairId, hairColor, shirtColor, pantsColor, health, isDying, aggroUntil)
```
- `handleMultiplayerMessage(json)` — parsea por `type` y actualiza `remoteEntities`/jugadores.
  **Nuevo:** procesa `PLAYER_ATTACK_HIT` (reenviado por el servidor) para aplicar daño al jugador
  local si fue golpeado por otro jugador en melee.
- `addRemoteEntity(remote)` / `updateNpcsState()` — fusiona NPCs locales + remotos + **policía**
  (`policeManager.activeUnits()` + `remotePolice`) en `uiState.npcs`.
- **Zone Host:** soy Host dentro de ~400 m; el menor `sessionId` cede en solapamiento; solo el Host
  corre la IA y emite `NPC_BATCH_UPDATE`. Mensajes de red: ver **08**.

## ESCOM / Misc / Collectibles (parciales)

- `WorldMapEscom.kt`: `spawnEscomDoors()`, `isInsideEscom(lat, lon)`, `spawnOustedDriver(carLocation)`
  (conductor desalojado al robar coche ocupado, aparece ~2 m).
- **🆕 Estacionamiento compartido exterior↔lobby:** los nodos `isParkingSlot` de `escom_navgraph.json` (coords
  locales 0-1) son la FUENTE ÚNICA: el exterior spawnea autos NPC ahí, y el **lobby interior** dibuja los mismos
  autos como escenografía (`ParkedCarsLayer`, ver 05) vía `domain/models/map/CampusParkingCatalog`. Un solo JSON
  para ambas vistas; expandible a otras universidades con una línea en el catálogo.
- `WorldMapMisc.kt`: `startMovementAction(isMovingRight?)`, `startHealthBarTimer(delayMillis)`,
  `triggerWastedSequence()` (congela movimiento, fantasma alpha 0.3, respawn ~80 m del lugar de
  muerte **dentro de la zona ya cacheada**, snapeado a calle — sin teleport a ESCOM ni descarga).
- `WorldMapCollectiblesLogic.kt`: `trySpawningCollectible` (1 cada ~1s, 300–600 m, snapeado),
  `checkCollectibleProximity` (prompt a 15 m, X recoge).

## API pública del ViewModel (para la View) / public VM API (selección)

`connectToMultiplayer(url, name)`, `disconnectFromMultiplayer()`, `moveCharacter(direction)`,
`moveCharacterByAngle(angleRad)`, `updateActionState(action, isPressed)`, conducción
(`steerLeft/steerRight/accelerate/brake(pressed)`), `onInteractButtonPressed()`,
`setMapProvider/requestMapProvider/commitMapProvider/cancelPendingProvider`, zoom
(`zoomIn/zoomOut/onMapZoomChanged/zoomToPlayer`), cámara (`centerOnPlayer/onMapPanStart/onMapPanEnd`),
landmarks (`addLandmarkAtPlayer/move/rotate/scaleX/scaleY/deleteSelectedLandmark/save +
export/importLandmarksToUri/FromUri`), `toggleDesignerMode/showAssetPicker/selectLandmark`,
teleport (`teleportTo(lat,lon)`, `teleportToMetroStation(name)`, `teleportToMetrobusStation(name)`,
`toggleTeleportMenu`; 🆕 el diálogo de **Puntos de Teletransporte** —en `WorldMapScreen`, solo Modo
Desarrollador— tiene secciones **COLAPSABLES** "🚇 Metro" y "🚌 Metrobús" (▶/▼): por defecto cerradas,
al pulsarlas despliegan su buscador + estaciones (`metroStations`/`metrobusStations`) → `teleportTo*Station`,
para que la lista de TP no crezca de más),
`takeDamage(amount)`, `heal(amount)`, `onClaimCollectiblePressed`, widgets (`toggleCacheWidget/FpsWidget`).

> **`onInteractButtonPressed` (botón Y, MIEMBRO):** sube/baja del coche. 🆕 (2026-07-13) A PIE el
> toque corto de Y actúa **al SOLTAR**; **MANTENER Y ~450 ms abre el INVENTARIO del mapa**
> (`WorldInventoryDialog`, solo lectura: los mismos objetos que interiores vía
> `currentInteriorInventory`; `showWorldInventory` + `toggleWorldInventory`/`worldInventoryUnlockedSlots`
> en `WorldMapInteractions.kt`; hold en `WorldMapScreenControls`). `INTERACT_RADIUS` bajó
> **0.00018→0.00008 (~9 m)**: te subía a autos "medio lejos". Si no hay coche civil (CAR) en
> `remoteEntities` dentro de `INTERACT_RADIUS`, intenta **subir a una PATRULLA** (POLICE_CAR) vía
> `policeManager.boardPatrol(id)`: la roba, difunde `POLICE_DESTROY` y fija `wantedLevel=5` (robar una
> patrulla = máximo nivel de búsqueda). **Skin conducible:** `isDrivingPoliceCar` en `WorldMapState` hace
> que `PlayerCharacter` dibuje el asset de `PoliceSpriteManager` (overlay común a los 3 renderers).
> **Al bajarte**, el coche queda como `type=CAR` con `Npc.isPoliceSkin=true` (no POLICE_CAR, que la IA
> despawnearía): los 3 renderers lo dibujan como patrulla con `type==POLICE_CAR || isPoliceSkin`, y es
> re-abordable (al re-subir, `isDrivingPoliceCar = carNpc.isPoliceSkin`). Ver 09.

**Wanted/policía (internal):** `raiseWantedLevel(amount=1)`, `tickWantedDecay(now)`,
`anyAggressorAdjacent(loc, now)`, `handleCarjack(driving, aggressorAdjacent, now)`,
`forceExitVehicle()`, `runPoliceTick(location)`, `fireImpactEffect()`.

---

## Rendering

### `WorldMapScreen.kt` (selector de renderer / renderer switch)
Elige render según `mapProvider`: **OSM nativo** (`NativeOsmMap`), **Google nativo** (Maps Compose),
o **Web** (WebView + Leaflet con HTML de `WorldMapLeafletHtml`). El **fog Compose (`Canvas`) solo se
usa para Google nativo** (OSM nativo y web tienen su propio fog). / Compose `Canvas` fog is used
**only** for the Google native renderer.

> **🆕 Refactor (2026-06-20):** los overlays/diálogos superpuestos (pantalla WASTED, vídeo zombi,
> prompts de interacción, diálogo de Prankedy, popup de coleccionable y los fades de puerta ESCOM/
> metro/metrobús) se extrajeron al composable `WorldMapOverlays` en `ui/WorldMapScreenOverlays.kt`
> (mismo paquete). `WorldMapScreen` ahora solo los invoca (`WorldMapOverlays(uiState, viewModel,
> onNavigateToInterior)`). MVVM intacto. Ver 09 §0.

> **Culling de NPCs = borde del fog:** `NPC_CULL_MARGIN_M=0` (antes 15) → los 3 renderers (que usan
> `npcVisionRadiusMeters() = NPC_FOG_VISION_METERS + margen`) dibujan civiles SOLO dentro de los 70 m del
> fog (antes hasta 85 m → "veo NPCs fuera del fog"). Policía fuera del fog = waypoint 🚓 (`wantedLevel>0`),
> Prankedy = render propio sin culling → **ambos siempre visibles**. Ver 09.

### `NativeOsmMap.kt` (osmdroid)
- **`FogOverlay`** anclado al jugador cada frame; rect del tamaño de pantalla siempre (cámara norte-arriba:
  `mapOrientation = 0f`, ya no se sobredimensiona por rotación). `FogOverlay.rotated` quedó sin uso (siempre `false`). Evita rellenar ~10× el área cada frame.
- **Over-zoom z20–22:** `MapTileApproximater` + `setMaxZoomLevel(22)` escala z19 (OSM solo sirve tiles
  reales hasta z19). Default OSM = zoom máx 22.
- **Lambda `update` ~30 Hz — mantener barata** (ver 09): landmarks `GroundOverlay` solo re-`setPosition`/
  `setImage` cuando cambia su firma (`landmarkSigCache`); las puertas (`DOORS/`) sí refrescan cada frame;
  ~160 marcadores de metro con el **icono `metro_cdmx/icon.webp`** (24 dp, `ExactSizeDrawable`), **culleados
  por viewport** (`Marker.isEnabled`).
- **Icono del Metro en los 3 renderers (paridad):** cada estación de `metroStations` (cargado de
  `res/raw/metro.json`) muestra `metro_cdmx/icon.webp`. OSM nativo = `Marker` 24 dp; **web** = `updateMetro`
  en `WorldMapLeafletHtml` (img fija ~26 px vía `file:///android_asset/`, push con guarda desde
  `WorldMapScreen`, `lastWebMetroHolder` + heartbeat); **Google nativo** = `Marker` 24 dp con
  `BitmapDescriptor` cacheado. Tamaño FIJO en pantalla (no metros), como los demás POIs de metro.
  **🆕 Zona de interacción visible (60 m):** además del icono, web (`metroZones`/`L.circle` en `updateMetro`) y
  OSM nativo (`metroZoneCache`/`Polygon.pointsAsCircle`, tag `+600`) dibujan un **círculo naranja translúcido**
  del tamaño de `METRO_INTERACT_RADIUS_METERS` bajo el logo, para que se vea desde dónde se puede entrar aunque
  la estación caiga sobre un edificio inaccesible por calles. Google nativo = pendiente.
  **🆕 METROBÚS en el mapa global:** el OSM nativo ya dibujaba sus estaciones; ahora el **web** también
  (`updateMetrobus` en `WorldMapLeafletHtml` + push en `WorldMapScreenWeb`, icono `TRANSIT/METROBUS/icon.png` en
  ROJO + su círculo de zona). Antes el web NO marcaba el metrobús → no se sabía dónde entrar. Google nativo = pendiente (solo metro).
- Patrulla 🚓 fuera de la fog: marcador + línea de ruta (cachés recordadas, **NO** view tags — añadir
  `R.id`s rompía un hack `id+100`/`id+400` y causaba `ClassCastException`).

### `WorldMapLeafletHtml.kt` (Web)
- `#map-wrapper` **fijo**: `100vw×100vh` siempre. La cámara es norte-arriba: el mapa no gira (solo el sprite del
  coche, vía `vehicleSpriteHeading`), así que ya no hay `setMapOversize`/`setMapRotation`/`applyVehicleViewport`. `WebVehicleRenderPlan` se eliminó; si el usuario panea, el único avatar auxiliar es el marcador Leaflet (`updatePlayerMarker(lat, lng, isUserPanningMap)`).
- Follow-camera con **`map.panBy`** por frame (no `setView`, que hace `_resetView` completo); `setView`
  solo al cambiar zoom o teleport. Tile layer: `keepBuffer:3`, `updateWhenIdle:false`.
- `updateNpcs`: solo dibuja imágenes para `type` `"CAR"`/`"MODULAR"`; el resto cae a SVG. Por eso
  **`POLICE_CAR` se serializa como imagen base64 (type="CAR")** vía `PoliceSpriteManager`, y `POLICE_COP`
  como **👮 base64 (type="MODULAR")**. `updatePolice(playerLat, playerLng, data)` dibuja 🚓+línea fuera de la fog.
- `#fog` div dentro de `#map-wrapper` (que ya no rota); `drawFog` **cachea el string del gradiente** y
  no re-rasteriza si no cambia.

### `CachingWebViewClient.kt`
Intercepta cada tile de Leaflet y lo cachea en Room. Key = URL normalizada (quita subdominios de
balanceo + parámetros volátiles) hasheada con SHA-256. Permite juego offline en zonas visitadas.

### Sprites (managers singleton, con `clearCaches()` — liberados en `onTrimMemory`)
- `CharacterSpriteManager` — peatones ensamblados con tintado por píxel.
- `VehicleSpriteManager` — modelos de `CarModel`, base blanca tintada por píxel. 🆕 **Extensible:** cada
  `CarModel` (en `domain/models/map/Npc.kt`) lleva `dirName/prefix/frameCount(=48)/tintable(=true)` y
  `collisionDimensions` (caja física, deliberadamente menor que el sprite). **Añadir un
  vehículo = 1 línea en el enum + su carpeta de frames en `assets/SPRITES/VEHICLES/<dirName>/`** (`<prefix>000..0NN.webp`).
  `tintable=false` = asset ya coloreado (se dibuja sin palette swap; ignora `carColor`). El NOMBRE del enum es el id
  que viaja por red (`CarModel.valueOf`) y guardado: AÑADE libremente, NO renombres/elimines entradas. Los nuevos
  modelos aparecen solos donde se itere `CarModel.entries` (tráfico `…random()`, estacionamiento del lobby).
- `PoliceSpriteManager` — asset `VEHICLES/POLICE_TOPDOWN` sin repintar (48 frames). Cop = emoji 👮 vía
  `emojiToDrawable` en `WorldMapDrawingUtils.kt`.
- **`nativeDrawableCache`** (declarado en `WorldMapScreen`, usado por `NativeOsmMap`) es un **LRU por
  orden de acceso** (cap ~384); sus claves embeben salud/zoom/frame → nunca volver a `mutableMapOf` (OOM).
- `MapZombieSpriteManager` (`ui/components/`) — carga `ZOMBIES_MOD/z_walk/z_walk_1..9.webp` (9 frames),
  `getZombieDrawable(context, npc, timeMs, scale)`. Liberado en `onTrimMemory`.
- `PrankedySpriteManager` (`ui/components/`) — sprites del **NPC especial Prankedy**: frames `.webp` de
  `assetsNPC/Prankedy/{p_idle,p_walk,p_run,p_run_tanque,p_attack}` + proyectil `p_objeto`, con `LruCache`
  + emoji de fallback. Render en **OSM nativo** (`renderPrankedyOnMap` en `NativeOsmMap`, ~1.3 m como un
  peatón, **sin** indicador flotante, proyectil interpolado encima de la niebla) **y web** (`updatePrankedy`/
  `updatePrankedyProjectile` en `WorldMapLeafletHtml`, base64 por frame empujado desde `WorldMapScreen`).
  Google nativo = pendiente. IA/comportamiento → ver 03.
- **🆕 Línea GPS de campaña (Modo Historia):** ruta A* `findRoadRoute(ENCB, ESCOM)` (en `WorldMapRouting.kt`,
  sobre la red vial) → `WorldMapState.campaignRouteWaypoints`, dibujada como **`Polyline` VERDE VIVO (`#00E676`, gruesa)** en OSM nativo
  (`NativeOsmMap`, tag `route_overlay_tag+900`, `overlays.add(0,…)` → sobre teselas, bajo personajes/HUD) y en
  web (JS `updateCampaignRoute` en `WorldMapLeafletHtml`). La activa `maybeSpawnPrankedyCompanion` y la oculta
  `maybeHideCampaignRouteNearEscom` (~100 m de ESCOM) en `WorldMapPrankedy.kt`. Google nativo = pendiente. Ver 07.
- **LOD de emojis (gama baja):** si `uiState.npcEmojiLod` (Ajustes→Jugabilidad), `NativeOsmMap` dibuja los
  NPCs a **>40 m** del jugador como un **emoji barato** (🧍/🚗/🧟/👮 cacheado por tipo+tamaño) en vez del
  sprite/bitmap completo; solo los muy cercanos llevan el asset. Recorta el costo de render. **Solo OSM
  nativo** por ahora (web/Google = trabajo futuro).

---

## 🧟 Modo Zombi Global / Global Zombie Mode

**ES:** Apocalipsis sobre el mapa del mundo abierto (distinto del minijuego de interiores). Toggle
global replicado en multijugador. Modelos + IA del zombi → ver **03**.
**EN:** Apocalypse on the open-world map (distinct from the interior minigame). Global toggle, replicated
in multiplayer. Zombie models + AI → see **03**.

- **Estado:** `WorldMapState.globalZombieMode: Boolean`. El game loop hace `npcAiManager.globalZombieMode = uiState.globalZombieMode` cada tick.
- **Activación:** (a) **ítem de menú "Activar/Desactivar Apocalipsis"** (Opciones) que funciona en cualquier lugar; (b) **botón flotante de salida** cuando está activo (`exitGlobalZombieMode()`). *(La antigua mano "Mano del Apocalipsis" en ESCOM fue ELIMINADA — `spawnEscomItems` ya no la crea; ver 09.)*
- **Debug Interiores (`showInteriorDebugOverlay`, menú Mapa):** además de los puntos de edificios + bbox de ESCOM, dibuja los **caminos del `navGraph`** (VERDE=peatonal, NARANJA=autos) y las formas de `exterior_collisions.json`: polígonos/barreras físicas en **ROJO** y `NAV_GUIDE` en **AZUL**. Las cuatro clases del modelo llevan `@Serializable`: si falta esa anotación, la carga atrapa el error y deja el mundo sin colisiones. El polígono `EDIFICIO_ESCOM_TECHO_CENTRAL` protege el techo central reproducido en Motorola; (2026-11-16: ya no es un rectángulo recto sino un cuadrilátero girado ~25° que sigue el techo del edificio central; los demás edificios del campus, incluido el conector y el ala este, siguen SIN colisión); cubre peatón, vehículo y tráfico mediante el resolvedor compartido. `exteriorCollisions` se expone en `WorldMapState`. OSM nativo (`NativeOsmMap`) + web (`updateInteriorPaths` recibe `{paths, blocks, walls}`); Google nativo pendiente. Ver 09.
- **🆕 EDITOR del Debug Interiores (`WorldMapDebugEditor.kt` + `InteriorDebugEditorPanel.kt`):** con el overlay activo aparece una **barra horizontal abajo** (los controles de movimiento se **ocultan** al editar) para **EDITAR** las líneas **DIBUJANDO con el dedo** (estilo Paint): eliges herramienta `DebugEditTool` (WALL=barda roja, BLOCK=zona roja, NAV_PED=verde peatonal, NAV_CAR=naranja autos) y **arrastras** sobre el mapa → línea (WALL/NAV_*) o rectángulo (BLOCK); con herramienta activa el mapa NO panea (touch consumido), con `NONE` vuelve a panear. **Deshacer** (último trazo) / **Limpiar** / **Exportar/Importar JSON** (formato `exterior_collisions.json` + sección `navPaths`). El dibujo es una **capa Compose sobre el mapa** (`InteriorDebugDrawSurface`, en `WorldMapScreen`) que funciona con **cualquier renderer** (web/OSM/Google) — clave porque el proveedor por defecto es WEB (Leaflet): captura el gesto desde el `ACTION_DOWN` (`awaitEachGesture`+`consume()`) para que el mapa NO panee, convierte pantalla↔geo con Web Mercator (`256·densidad·2^zoom`, centro = jugador) y commitea con `commitDebugStroke`. En modo debug el mapa **se mantiene centrado en el jugador** (la capa asume ese centro). Estado nuevo en `WorldMapState`: `debugEditTool`, `debugEditWalls`, `debugEditBlocks`, `debugEditNavPed`, `debugEditNavCar`.
- **🆕 Guardado de partida (Modo Historia):** API del VM en `WorldMapSaveGame.kt` (`saveGame`/`loadGame`/`buildSaveData`/`restoreSaveData`) + `SaveGameRepository` (JSON). Campos del VM `campaignSchoolId`/`inCampaign`. Ver 07/09. **Fix de `setStorySpawn`:** ahora se comporta como un teletransporte (`gateMapDownloadAfterTeleport()` + reset de `lastNetworkFetchLocation`/`lastFetchAttemptMs`/`npcWarmupCycles`) para que "COMENZAR/CARGAR" SÍ carguen y suelten al jugador (antes `prepareMapForEntry`, idempotente por `mapPrepStarted`, dejaba la 2ª entrada cargando para siempre). Ver 09.
- **VM API:** `toggleGlobalZombieMode()` (flip + broadcast `ZOMBIE_MODE_SET`), `exitGlobalZombieMode()`.
- **Daño al jugador (¡crítico!):** los zombis muerden vía `applyNpcContactDamage(location)` y el atropello vía `runOverNpcs(finalLoc, speed)`. **Viven en el game loop, que es el MIEMBRO `startGameLoop()` de `WorldMapViewModel.kt`.** (2026-06-21: el gemelo de `WorldMapGameLoop.kt` se de-duplicó — ese archivo es ahora un **tombstone**; `startGameLoop` es solo miembro, e incluye además el **audio** fusionado: caminar/correr/coche/zombi.) Editar el game loop = editar el miembro en `WorldMapViewModel.kt`.
- **Multijugador:** el Host simula los zombis y los replica por `NPC_BATCH_UPDATE` (conserva `npcType=ZOMBIE` + health/isDying). El toggle viaja en `ZOMBIE_MODE_SET` (global) — relayado por `Multiplayer/server.js` (NO por `MultiplayerInteriores/`). `addRemoteEntity` reconstruye el zombi remoto con `visualConfig=null` y `speed=PERSON_SPEED` (animan). Ver **08**.

### Render del zombi / zombie rendering (3 renderers)

Los **3** renderers revisan `npc.visualConfig != null` ANTES que `ZOMBIE`, así que la rama de peatón
lleva la guarda **`&& npc.type != NpcType.ZOMBIE`** y el seed pone `visualConfig=null`. Si no, el zombi
se dibuja como humano. / All 3 renderers check `visualConfig != null` before `ZOMBIE`, so the pedestrian
branch carries `&& npc.type != ZOMBIE` and the seed nulls `visualConfig`.

- **OSM nativo** (`NativeOsmMap`): drawable de `MapZombieSpriteManager` → `ExactSizeDrawable` (~1.3 m).
- **Google nativo** (`WorldMapScreen`): mismo drawable → `BitmapDescriptor` (en los dos `when`).
- **Web** (`WorldMapScreen`): base64 `type="MODULAR"`. **El frame del `cacheKey` DEBE ser `% 9`** (no `(timeMs/220).toInt()` sin acotar) — si no, cada frame crea un key nuevo y la imagen base64 async nunca se registra → no se ve (ver 09).

**Roles (palette swap):** los 3 renderers incluyen `npc.zombieRole` en el `cacheKey`, aplican el tinte
(en `MapZombieSpriteManager.getZombieDrawable`), un `roleSizeMul` (Tank 1.45×, Runner 0.9×) y pasan
`npc.maxHealth` a `drawHealthBarOnDrawable` (barra proporcional por rol; en web la vida se normaliza a
0-100 en el payload). Burbuja del SCOUT: 📢 sobre la cabeza mientras `screamUntil > now` (render nativo
`screamCache`, espejo del 📞 `callingUntil`). Roles + comportamiento → ver **03**.

- **💥 FX de impacto:** `fireImpactEffect()` tiene **throttle** (`IMPACT_EFFECT_THROTTLE_MS=900`) para que
  las mordidas de zombi/NPC no lo spameen en el centro de la pantalla. Además `takeDamage` y
  `applyNpcContactDamage` **ignoran el daño si `showWastedScreen` o `playerHealth<=0`** (si no, los zombis
  sobre el cadáver re-disparaban el 💥 al morir), y el 💥 **no** se muestra en el golpe mortal.
- **Knockback de zombi:** al golpear (B) un zombi que sobrevive, `performPlayerAttack` lo **empuja** ~7-8 m
  alejándolo del jugador (recoil visible; el Host lo retoma desde `remoteEntities`).
- **Atropello estilo Midnight Club (`runOverNpcs`):** el peatón (PERSON) **esquiva con un sidestep
  ANIMADO, no un teletransporte**. `runOverNpcs` NO mueve al peatón: solo marca un **estado de esquive**
  (`dodgeUntil = now + DODGE_MS`, `dodgeDirLat/Lon` = perpendicular hacia el lado al que ya se inclina) y
  el **Host lo anima** en `NpcAiManager.updateNpcs` (rama `npc.dodgeUntil > now`: se mueve `personSpeed*10`
  perpendicular, **sin snap a la calle**; al expirar, `moveNpc` lo re-engancha → "salta a un lado y
  regresa"). El esquive es **predictivo**: solo si el peatón está **DELANTE** del coche (producto punto con
  el avance > 0) y dentro de `DODGE_TRIGGER_RADIUS` (~7-8 m). Solo lo atropellas yendo **casi a fondo**
  (`spd >= RUN_OVER_EXTREME_SPEED = MAX_SPEED*0.92`) y dentro de `RUN_OVER_RADIUS`. Los **zombis NO
  esquivan** (atropellables siempre). Cada coche usa un **rectángulo orientado en metros**
  (`VehicleFootprint`), no un radio: tráfico solo choca si las carrocerías se tocan, se separa por el
  vector mínimo y reduce la velocidad del jugador a 35 % + 💥. El 💥 solo salta en atropello real o
  choque de auto (no al esquivar). El estado de esquive vive en `remoteEntities`, se
  reconstruye en `setServerNpcs` y la posición animada se replica vía `MultiplayerNpc` (no hace falta
  añadir campos de esquive al modelo de red).
- **Obstáculos y descenso:** al conducir, `vehiclePathObstacle` barre el rectángulo cada ≤0.5 m contra
  edificios y bardas; se conserva la última posición válida, sin knockback artificial. Al bajar,
  `exitVehicle` deja el coche en su centro y prueba primero el lado del conductor, luego copiloto y
  diagonales traseras; el carjack forzado amplía a un anillo de 1.7–3 m. Si no hay hueco manual, el
  jugador permanece dentro y recibe un aviso localizado. El spawner de estacionamiento también rechaza
  una carrocería que cubra al jugador. Debug: Logcat `VehicleCollision` emite `traffic`, `building`,
  `wall` o `exit-blocked`.
- **Índice de barreras y seguimiento de la auditoría (2026-11-16):** `ExteriorCollisionsConfig.barrierIndex` (`BarrierIndex.kt`, propiedad `by lazy` no serializada) guarda solo los polígonos/bardas con rol BARRIER y su caja en grados. `obstacleAt` y `resolveVehiclePath` descartan por caja SIN asignar memoria lo que está lejos (≈50× más rápido lejos de ESCOM en JVM; dentro del edificio igual); la geometría exacta es la misma y `BarrierIndexEquivalenceTest` la compara contra la referencia sin índice (`obstacleAtUnindexed`/`resolveVehiclePathUnindexed`). Si añades un campo que cambie qué cuenta como barrera, actualiza el índice. `resolveTrafficImpact`: si el punto previo ya solapa, el jugador sale por el vector mínimo y el tráfico NO se desplaza (antes se separaba ≈2×). `selectForcedVehicleExit` añade el tramo "fuera de la carrocería Y fuera de todo edificio BARRIER" (`ExteriorCollisionsConfig.isInsideBarrier`) antes del último recurso. Pendiente (necesita teléfono): precalentar en segundo plano los frames vecinos del sprite del jugador, solo si `gfxinfo` mide tirones al girar.
- **Resolvedor exterior único (P2):** `VehicleCollisionGeometry.kt` concentra las formas físicas: círculo para avatares/peatones y rectángulo orientado para coches. `resolveCirclePath` y `resolveVehiclePath` hacen el barrido estático y devuelven la última posición válida y el motivo. Solo `BARRIER` bloquea; los 48 `CARRIL_*` heredados se clasifican como `NAV_GUIDE`, por lo que guían tráfico sin crear paredes invisibles. Botones y joystick usan el mismo guardia de jugador a pie; tráfico usa el mismo barrido OBB. En builds debug, Logcat `WorldCollision` añade actor, colisionador, motivo y destino bloqueado.
- **Daño moderado de mordida:** `applyNpcContactDamage` aplica la mordida de zombi con un **cooldown
  GLOBAL** (`ZOMBIE_BITE_TO_PLAYER_MS=650`, `lastZombieBiteMs`) → como mucho una mordida cada ~650 ms
  (`ZOMBIE_BITE_TO_PLAYER_DMG=6`) aunque te rodee una horda. El SCOUT no muerde.
- **Sin "se busca" en apocalipsis:** `raiseWantedLevel` es **no-op** si `globalZombieMode` → pegarle a un
  zombi/civil (o **robar un auto**) NO invoca a la policía del sistema de delitos.
- **Policía del apocalipsis (caza-zombis):** el Host spawnea NPCs `POLICE_COP` (probabilidad, máx
  `POLICE_HUNTER_MAX`) que **persiguen y disparan al zombi más cercano** (`movePoliceHunter` en
  `NpcAiManager`; ayudan a los civiles). **Disparos VISIBLES:** `movePoliceHunter` acumula los disparos en
  `npcAiManager.pendingPoliceShots`; el game loop los vuelca a `uiState.policeShots` (balas, expiran 450 ms
  vía `runPoliceTick`). Los **zombis también muerden a los policías** (escaramuza: los cops pueden caer).
  **Te atacan SOLO si los provocas:** `provokeApocalypsePolice` (VM) les pone `aggroUntil` cuando (a) los
  golpeas o (b) agredes a un civil con un poli **literalmente enfrente** (~33 m). Provocados, persiguen al
  jugador y dañan vía `applyNpcContactDamage` (`isAggroCop`). Render 👮, replicados por `NPC_BATCH_UPDATE`.
- **Hordas migratorias:** el game loop lee `npcAiManager.hordeIncomingAt` y, en cada nueva oleada, muestra
  un aviso en el HUD (vía `interactionPrompt`). La lógica (punto de calor + spawn) está en `NpcAiManager`
  (ver 03). El SCOUT es el aviso *in-game* de que viene una horda.
- **Instancing (apocalipsis = instancia):** `toggleGlobalZombieMode()` → `setZombieInstance(apocalypse)`
  envía `JOIN_INSTANCE` ("apocalipsis"/"normal"), limpia `remoteEntities` y deja que el servidor reenvíe
  el roster del nuevo mundo. Los de "normal" no ven zombis. **No** queda `ZOMBIE_MODE_SET` (eliminado). Ver 08.

### Waypoints de zombi (fuera del fog) / zombie waypoints (outside fog)

🧟 + línea **roja** punteada jugador→zombi para zombis fuera de `NPC_FOG_VISION_METERS`, en modo
apocalipsis. Implementado en los **3** proveedores: OSM nativo (`NativeOsmMap`, `zombieWpCache`/`zombieRouteCache`),
web (Leaflet `updateZombies(...)` + push en `WorldMapScreen`), y Google nativo (Marker 🧟 + Polyline roja en el composable `GoogleMap`).

### Choque coche-coche (contacto persistente)

`runOverNpcs` (WorldMapCombat.kt) separa **física** de **evento**: mientras haya contacto con un vehiculo, el jugador vuelve al ultimo punto seguro y frena (x0.35) en cada tick, pero el impacto (efecto, miedo, log) se emite una sola vez por par gracias a `VehicleContactLatch` (shared, `RELEASE_MS = 400`). Los candidatos se ordenan por id (`firstContactingId`) porque `remoteEntities` es un `ConcurrentHashMap`. Coches `PARKED` nunca se desplazan.

### Rumbo seguro al subir a un coche (`safeBoardingHeading`)

Al subir (`WorldMapInteractions.kt`: civil y patrulla) el rumbo inicial ya no es siempre `rotationAngle + 90`: `ExteriorCollisionsConfig?.safeBoardingHeading(position, preferido, dimensiones)` (`shared/.../domain/models/map/VehicleBoardingHeading.kt`) conserva el preferido si el coche puede avanzar 3 m sin tocar una barrera y, si no, busca el rumbo libre más cercano girando de 15° en 15° (alternando lados). Sin configuración o con todo bloqueado devuelve el preferido. Motivo: un coche aparcado de cara a un edificio arrancaba con `reason=building` en cada ciclo y acelerar no lo movía (medido: 80 líneas, 0 m; ahora avanza ~7 m). Pruebas: `VehicleBoardingHeadingTest`.

### Escenarios de depuración por adb (solo build debug)

`DebugScenarioReceiver.kt` (registrado en `MainActivity.onCreate`, se anula en `onDestroy`) y `WorldMapDebugScenarios.kt` (`WorldMapViewModel.applyDebugScenario`) permiten jugar sin multitoque. Con el mundo ya abierto: `adb shell am broadcast -a ovh.gabrielhuav.pow.DEBUG_SCENARIO --es name <escenario> [extras]`. En release NO se registra ni hace nada (`BuildConfig.DEBUG`).

| `name` | Qué hace |
|---|---|
| `controls` | Solo aplica los controles (ver extras). |
| `drive_open` | Pone al jugador conduciendo. Extras: `lat`, `lon`, `heading`, `speedPct` (0-100 de `MAX_SPEED`). |
| `board_facing_building` | Crea un coche aparcado de cara al edificio del caso DEV-08 y sube a él (comprueba el rumbo corregido). El coche mira a 296° la pared este de "ESCOM techo 3" (los techos del arte), 7.5 m al este del cruce de esa pared con su latitud: la carrocería no toca el edificio pero 3 m adelante sí, así que `safeBoardingHeading` corrige el rumbo (medido con las colisiones reales: ~356°). A ≤ 6 m la carrocería ya toca, todo sale bloqueado y se devuelve el preferido sin corregir; a ≥ 9.5 m no hay pared. ⚠️ Hasta el 2026-11-21 el punto quedaba 4 m DENTRO de ese techo y la prueba no comprobaba nada. Tras subir, la aduana de calle arrastra el coche hacia la vía más cercana (el punto está a ~13 m de una). |
| `steal_parked` | Lleva al jugador junto al coche aparcado más cercano y lo roba (estrella y conductor desalojado). |
| `overlay` | Activa (`--es on 0` apaga) el overlay de colisiones "Debug Interiores": rojo = físico, azul = guía. Abre encima el editor de líneas (botón Exit para cerrarlo). Con `lat`/`lon` mueve ahí al jugador a pie. |
| `footprints` | Activa (`on 0` apaga) el prototipo de mapa cenital estilizado. Extras: `lat`, `lon`, `zoom`, `fpstyle`. |

Controles mantenidos hasta que se cambien: `--es gas 1|0`, `--es brake 1|0` (con el coche parado, frenar da reversa), `--es steer left|right|none`. `--es trace <s>` (1–30) registra cada 250 ms posición, velocidad, rumbo y metros a la vía de coches más cercana (`roadM`). El resultado se escribe en el logcat con la etiqueta `DebugScenario`. Ejemplo de vuelta en círculo: `--es name drive_open --es speedPct 60 --es gas 1 --es steer right`. ⚠️ Si el coche sigue rodando, el siguiente `drive_open` puede perderse (el ciclo de juego escribe encima): frena antes o mándalo dos veces.

### Calentamiento de sprites del coche del jugador

`PlayerCharacter.kt` (rama conductor) lanza un `LaunchedEffect` en `Dispatchers.Default` que pide a `VehicleSpriteManager.getTintedCarNpc` (o `PoliceSpriteManager.getPoliceCar`) los 48 giros, de los más cercanos al actual hacia fuera. Sin él cada rumbo nuevo decodificaba, escalaba y tintaba en el hilo principal. Medido en un Motorola One Fusion (debug, 4 arranques en frío por lado): huecos > 250 ms entre cuadros 11.5 → 6.75 por ventana y huecos > 600 ms de ~17 a ~6. Es una muestra pequeña; en release el efecto no se ha medido (no hay escenarios en release).

### Logs de depuración limitados

`logVehicleCollision` (`WorldMapVehicleExit.kt`) escribe como mucho una línea cada 500 ms por tipo de motivo (antes, una por ciclo: 16-20 por segundo); el log `VehicleViewport` de `WorldMapScreen.kt` ya no incluye la rotación, así que solo se escribe en cambios de modo.


### ⚠️ Orden de inicialización: `textoCarjack` antes del `init`

En `WorldMapViewModel` el texto del aviso de carjack se precarga en un `init` con `viewModelScope.launch { textoCarjack = getString(...) }`. La propiedad `textoCarjack` DEBE declararse antes de ese `init`: Kotlin ejecuta inicializadores e `init` en orden de declaración, y si la carga termina sin suspender, un `= ""` declarado después borraba el texto y el aviso salía como un rectángulo rojo vacío (DEV-05). `WorldMapScreen` además no pinta el aviso si el texto está en blanco.

**Deslizar por el borde de la calle (2026-11-16, DEV-09):** el coche solo circula a ≤ `maxRoadRadius` (0.00004° ≈ 4.4 m) de una vía `isForCars` (`WorldMapViewModel`, aduana de calle). Antes, al rozar el borde frenaba ×0.6 por tick y se jalaba 30 % hacia la calzada: con el gas pulsado quedaba clavado (el HUD marcaba 15–21 km/h sin avanzar) o se arrastraba de lado. Ahora, si el punto previo estaba dentro (≤ `ROAD_SLIDE_ENTRY_FACTOR`×radio), `slideAlongCorridor` (`shared/.../RoadCorridorSlide.kt`) lo deja en el borde en la misma dirección y el coche avanza a lo largo de la calle; solo pierde velocidad de frente (`ROAD_SLIDE_BASE_KEEP` = 0.85 + 0.15×proporción avanzada). Si venía de fuera (p. ej. subió en un estacionamiento) se conserva el jalón de reincorporación. Medido en el Motorola (traza `--es trace <s>` de los escenarios): velocidad contra el borde 4.4e-7 → 1.6–6e-6 (deg/tick). Pruebas: `RoadCorridorSlideTest`.

**Salir de un edificio aunque la calle quede detrás (2026-11-21):** la aduana de calle se aplica ANTES del barrido estático, y fuera del corredor jala el destino hasta ~4.4 m por tick hacia la vía más cercana. Si el coche ya estaba encimado en un edificio (centro dentro, aparición o teletransporte) y esa vía quedaba detrás del edificio, cada tick apuntaba hacia dentro, `sweepVehiclePath` lo bloqueaba y la velocidad volvía a 0: ni avanzaba ni daba reversa (medido con el punto viejo de DEV-08, 39 m de la vía: 0 m en 5 s con gas y en 3 s de reversa). Ahora `resolveVehicleDrivingStep` (`WorldMapMovement.kt`) reintenta con el paso puro del rumbo cuando el destino de la calle choca y la carrocería arrancó encimada. El barrido solo acepta pasos que aumentan la distancia con signo al obstáculo, así que el reintento nunca deja entrar más: solo salir hacia donde apunta el coche (o en reversa). Ya fuera rige la aduana normal; si la vía sigue detrás del edificio el coche se queda en el borde y hay que bajarse (a pie sí se sale). Pruebas: `VehicleDrivingStepTest`.

## Prototipo: mapa cenital estilizado (estilo Chinatown Wars) — 2026-11-16

- **Datos:** `assets/CONFIG/escom_osm_footprints.json` (44 formas de OpenStreetMap, ODbL: 30 edificios, canchas, estacionamientos, parques). Modelo y parser en `domain/models/map/CampusFootprints.kt` (`:shared`).
- **Render:** `ui/components/CampusFootprintLayer.kt`, una capa Compose SOBRE el mapa (techo, paredes sur, sombra y contorno) con la misma proyección Mercator que `InteriorDebugDrawSurface`. Activa con el escenario debug `--es name footprints [--es lat .. --es lon .. --es zoom ..]` (`on=0` la apaga); no hay botón de usuario todavía.
- **Medición (Motorola One Fusion, conduciendo, `gfxinfo`):** sin capa mediana 17-18 ms; con capa completa 29-30 ms (p90 40 ms). Grabar la geometría en una `GraphicsLayer` NO mejoró nada: el costo es de GPU, no de construir paths. Atribución: contornos ≈ 5 ms, sombra ≈ 3 ms, techos ≈ 4 ms, paredes ≈ 1 ms.
- **Estilo final del prototipo:** sin contornos ni sombra (paredes en tono más oscuro), SIN suelo (estacionamiento/canchas/parques no se pintan: opacos tapaban toda la pantalla), capas semitransparentes (alpha 0.8) y **una `GraphicsLayer` por edificio**; la del edificio que contiene al jugador se funde a transparente (250 ms) para ver lo que hay dentro. Se enciende desde Opciones → "Mapa estilizado" (o `--es name footprints`). Costo medido en el Motorola conduciendo: mediana 14-25 ms con capa vs 16-17 sin ella (≈ +3-4 ms, medición ruidosa). Los extras `--es fpstyle <bits>` (1 contornos, 2 sombra, 4 sin paredes, 8 suelo) son solo para medir.
- ⚠️ **La altura visual se limita a 1.5 m** (`MAX_VISUAL_RISE_METERS`): la colisión usa la huella en el suelo, y con los ~10 m reales el techo dibujado se levantaba hacia arriba y el jugador, que frena en el borde de la huella, se veía ENCIMA del techo y cruzando sus paredes. Además las huellas son de OSM y pueden no coincidir al metro con los edificios del mapa satelital: la colisión sigue a OSM, no a la imagen.
- ⚠️ **Nunca grabar cada capa con el tamaño de todo el campus** (~15000×10000 px): con alpha Android pidió un búfer fuera de pantalla de ese tamaño y el Motorola One Fusion (3.8 GB de RAM) llegó a reiniciarse. Cada capa mide lo que su edificio y usa `CompositingStrategy.ModulateAlpha`.
- Aún no sustituye al mapa satelital; solo lo tapa (con velo) donde hay huellas.

## Colisiones de edificios desde las huellas de OSM — 2026-11-16

- **Qué bloquea:** `cargarColisionesConHuellas()` (`CampusFootprints.kt`) = `exterior_collisions.json` + un polígono `BARRIER` por cada `building` de `escom_osm_footprints.json` (nombre `OSM_<id>_<nombre>`; no entran techos sueltos, canchas, estacionamientos ni parques). Lo usa `WorldMapViewModel.loadExteriorCollisions()`; el polígono a mano `EDIFICIO_ESCOM_TECHO_CENTRAL` se conserva. **iOS sigue con `cargarColisionesExteriores()`** (su `chocaAlMoverse` no sabe salir de un solape): no portar sin probar en simulador.
- **Salida a pie (`resolveCirclePath`):** si el actor ya está dentro de un edificio (spawn, salida de un interior: los marcadores PALAPAS, BIBLIOTECA, CANCHAS y AUDITORIO caen sobre techos), camina libre hasta quedar fuera; si solo está pegado a un borde, cada paso debe alejarse del obstáculo más cercano. Una vez fuera, no puede volver a entrar. Antes un actor dentro de un polígono quedaba clavado para siempre.
- **Rendimiento:** `circleObstacleAt` ahora descarta por caja con `BarrierIndex` (antes recorría todos los polígonos y reasignaba listas en cada muestra). En el Motorola, conduciendo, la mediana sigue en 15-18 ms.
- **Ojo:** el overlay "Debug Interiores" y su export incluyen estos polígonos (viven en `uiState.exteriorCollisions`); no pegar ese export de vuelta sobre `exterior_collisions.json` sin quitar los `OSM_*`.
- **Pruebas:** `CampusCollisionsTest` (shared) y `CampusFootprintAssetTest` (assets reales: ninguna entrada de interior queda sin salida a pie).

## Lag al caminar: fuga de peatones del campus — 2026-11-16

- **Síntoma:** a pie en la plaza el juego iba cada vez más lento (36 ms de mediana y p90 85 ms tras unos minutos; a zoom 19 ~4 fps) y el proceso de render del WebView llegaba a ~1 GB de RAM, hasta dejar de responder a DevTools. Con el juego recién abierto la misma caminata iba a 19 ms.
- **Causa:** `NpcAiManager` excluía a los `CAMPUS_PED_*` de TODOS los cupos (`activeCount`, `totalCount`, `nonParkedAlive`, recorte de exceso), así que `nonParkedAlive() < maxTotalNpcs` era siempre verdadero y cada escaneo de spawn (900 ms) añadía 1-3 estudiantes; solo se retiraban a > `despawnDistance` (~310 m), que en el campus no ocurre. Medido por DevTools: `npcMarkers` 57 → 114 → 193 en ~6 min con el jugador parado, uno por NPC en el DOM de Leaflet (más DOM, más capas, más `updateNpcs`).
- **Arreglo:** tope propio `maxCampusPeds = campusPedestrianCap(popFactor)` (36 × densidad, entre 8 y 100) y recorte de los más lejanos si se pasa (`CampusPedestrianCap.kt`, usado en `NpcAiManager.updateNpcs`). Probado en `CampusPedestrianCapTest`.
- **Resultado (Motorola, plaza, joystick mantenido):** zoom 22: 16-17 ms de mediana (p90 24-28); zoom 20: 22 ms; zoom 19: 24 ms (antes 200-250 ms). Estable tras 8+ min; el render del WebView queda en ~200-250 MB. NPC enviados al WebView: ~45 (antes 158 → 193 y subiendo).
- **Cómo medir (método reutilizable):** `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>` y envolver con contadores las funciones JS (`updateNpcs`, `updateCollectibles`, `updateMapView`, `map.panBy`…); `Object.keys(npcMarkers).length` y `__lastNpcData` para comparar lo enviado con lo pintado; `dumpsys gfxinfo` con el joystick mantenido (`input motionevent DOWN/MOVE/UP`). Medir SIEMPRE con la misma antigüedad de sesión: el lag crecía con el tiempo.
- **Pendiente (menor, ya sin urgencia):** `updateNpcs` y `updateCollectibles` se llaman a ~30 Hz aunque nada cambie (≈ 150 ms/s y ≈ 35 ms/s de JS en el WebView); guardarlos por cambio o limitar a ~15 Hz daría margen extra.

## Escala del mundo: personas y coches en metros reales — 2026-11-16

- **Antes (medido en el Motorola, 280 dpi, donde 1 dp = 1 px CSS del WebView):** jugador a pie ≈ 0.8 m (fijo en pantalla, 23.5 dp, no seguía el zoom), peatones NPC ≈ 1.0 m de cuerpo (caja 1.3 m × 0.74 de cuerpo opaco) y sedán ≈ 3.4 m visibles (caja 4.0 m × 0.84). Las personas medían la mitad de lo real frente a coches y edificios.
- **Ahora (una sola fuente, `domain/models/map/WorldScale.kt`):** cuerpo de persona `PERSON_BODY_METERS = 1.7` (caja del peatón `PERSON_BOX_METERS` = 1.7 / 0.74 ≈ 2.3 m) y caja de coche `CAR_BOX_METERS = 5.0` (sedán ≈ 4.2 m visibles). Lo usan el WebView (`WorldMapLeafletHtml.kt`, interpolado en el JS), el jugador (`PlayerCharacter.kt`, cuerpo = 1.7 m / m-por-dp del zoom, con tope 14-64 dp) y el mapa nativo (`NativeOsmMap`, `NativeOsmMapPrankedy`). `metersPerDp`/`dpForMeters` convierten metros a dp.
- **Colisión de coches:** `CarModel.collisionDimensions` escalada ×1.25 (sedán 4.5×2.0 m …) para seguir al sprite. Colisión a pie sin cambios (radio 0.35 m).
- **Velocidad a pie:** sin cambios (7.8 m/s medidos); con personas el doble de grandes cubre menos cuerpos por segundo.
- **Efecto del zoom:** a zoom 22 el jugador mide ~48 dp (≈ 84 px en el Motorola); a zoom 21 ~24 dp, que coincide con el tamaño fijo anterior; a zoom ≤ 20 llega al tope mínimo de 14 dp. Si se quiere que a pie se vea más mundo, evaluar `ZOOM_ON_FOOT = 21`.
- **Pendiente:** ZombiHand/otros sprites propios (gatos, policías) conservan su tamaño; el mapa nativo de Google (`WorldMapScreenGoogle`) no usa estas constantes todavía; el aparcamiento de coches más grandes en cajones estrechos no se midió.

## NPC y edificios: colisión de NPC y personajes más grandes — 2026-11-16

- **Síntoma:** los NPC atravesaban los edificios. Medido por DevTools (muestreo de `lat/lng` de cada NPC del WebView contra las huellas OSM, jugador parado en la plaza): **46 % de las observaciones de NPC caían dentro de un edificio** (peatones del campus, estudiantes `CAMPUS_0..9`, gatos, conversaciones dinámicas).
- **Causas:** (1) `moveLocalNpc` (peatones del campus, gatos y coches que salen del estacionamiento) nunca llamaba al resolvedor estático; (2) el navGraph peatonal se dibujó sin conocer los edificios de OSM: 14 de 158 nodos y ~126 m de ~1270 m de vía caen dentro (las vías de coches y los 81 cajones están limpios, ≥ 13 m); (3) los 10 estudiantes `CAMPUS_*` (`WorldMapCampusLife.kt`) nacían y caminaban en línea recta por todo el recuadro del campus; (4) aggro, zombis, cazadores y esquiva iban en línea recta sin colisión; (5) `moveNpc` ponía `speed = 0.0` tras un bloqueo y el NPC quedaba congelado para siempre; (6) hueco en la regla `escaping` de `resolveCirclePath` (un actor que solo rozaba el borde podía entrar al edificio).
- **Arreglos:** `NpcStaticCollision.kt` (`localWayBlocked`, `walkableLocalWays`, `localNodeFree`, `collided`): las vías que tocan un edificio no se usan ni para aparecer ni para elegir ruta; `moveLocalNpc` resuelve cada paso y, al topar, conserva el último punto seguro y da la vuelta; `moveNpc` ya no congela; aggro, zombis, cazadores y esquiva pasan por `collided`; `CAMPUS_*` usan puntos libres de edificios (`campusFreePoint`) y un paso con colisión que rodea (`campusStep`); `startedInside` cierra el hueco de `escaping`; `exteriorCollisions` del manager es `@Volatile`.
- **Resultado (Motorola, mismo protocolo):** **0 de 359 observaciones dentro de edificios** (antes 46 %; tras solo el arreglo compartido, 0.7 % por actores de eventos). Rendimiento caminando sin cambio (ver abajo).
- **Pendiente (escenas cortas de guion, sin colisión):** `CampaignEscortPolice`, `PrankedyManager`, misión 3, persecución de la misión 1, eventos dinámicos `DYN_*` y el empuje de zombis (`WorldMapCombat.kt`). Quedaron 4 observaciones sueltas de `DYN_*` en la medición intermedia.
- **Personajes más grandes (decisión de diseño):** `CHARACTER_VISUAL_SCALE = 1.3` en `WorldScale.kt`: jugador y peatones se dibujan con un cuerpo de ≈ 2.2 m (`PERSON_VISUAL_BODY_METERS`); los coches no cambian (la relación coche/persona baja de 2.5 a 1.9). El zoom máximo sigue en 22 (Leaflet `maxZoom: 22`, pinch y OSM nativo lo limitan). `PLAYER_BODY_MAX_DP` 64 → 84; radios de colisión a pie 0.35 → 0.45 m (jugador) y 0.3 → 0.4 m (NPC) para seguir al sprite.
- **Cómo medir "NPC dentro de edificios":** `adb forward` al WebView, envolver `updateNpcs` para guardar `__lastNpcData` y contar con un punto-en-polígono contra `escom_osm_footprints.json` (script usado: muestreo cada 4 s durante ~2 min con el jugador parado en la plaza).

## Colisión del edificio ESCOM desde la IMAGEN del campus — 2026-11-16

- **Problema:** las huellas de OSM no coincidían con el campus que se ve: el campus es una imagen propia (`BUILDINGS/IPN/building_escom.webp`, 1857×2275, colocada y rotada 25.8° sobre el mapa) y OSM dejaba sin colisión el bloque central con paneles solares y desplazaba las alas. El jugador y los NPC caminaban "sobre el techo".
- **Solución:** los techos se extraen de la imagen por color (techo naranja + paneles azules que tocan techo; celdas de 4 px ≈ 0.45 m; cierre/apertura morfológica de radio ≈ 0.9 m; se rellenan huecos < 80 m² y los patios grandes se parten en franjas verticales para que cada pieza sea un polígono simple), se traza el CONTORNO de cada pieza y se simplifica (Douglas-Peucker, ≈ 0.7 m) a **polígonos de n lados** (31, 11 y 52 vértices, ≈ 10 500 m²; antes 43 rectángulos). Se guardan en `escom_osm_footprints.json` con `tags.source = "art"` (`ESCOM techo N`, ids 9000000+). Los edificios de OSM cuyo centroide cae dentro de la imagen se descartan; el resto de OSM (CIC, Plaza Torres…) se conserva. Se descartan componentes < 1000 m² (canchas, bancas).
- **Regenerar:** `tools/escom_art_footprints/` (`extract_polys.js` se evalúa por DevTools con el juego abierto → `polys.json`; `python art_to_footprints.py [apply]`). Guarda además las huellas OSM originales.
- **Validación:** 0 de 158 nodos peatonales y 0 de 186 de coche del navGraph caen dentro de los techos (con OSM caían 14 peatonales); el spawn de la plaza queda libre; NPC dentro de edificios: 0.3 % (solo actores de eventos dinámicos). Pruebas: `CampusFootprintAssetTest` (navGraph fuera de los techos, spawn libre, un techo bloquea).
- **⚠️ Trampa de la rotación:** la imagen se DIBUJA con la rotación contraria a `Landmark.toGlobalGeoPoint` (se comprobó comparando las 4 esquinas reales del `<img>` en el DOM con `latLngToContainerPoint`: coinciden con `-rotationAngle`), pero las vías del navGraph se dibujaron con el diseñador sobre el mapa, es decir en el sistema de `toGlobalGeoPoint` (+rot). Por eso las coordenadas de IMAGEN (techos) se convierten con `-rot` y los nodos del navGraph con `+rot`. Quien convierta píxeles de la imagen con `toGlobalGeoPoint` obtendrá formas giradas al revés (los contornos salen inclinados al lado contrario de los edificios).
- **⚠️ Cierre morfológico:** con radio 3 (≈ 5 m) se rellenaban los pasillos entre alas y el spawn de la plaza quedaba dentro de un techo; con radio 1 no.
- **Se eliminó** el polígono a mano `EDIFICIO_ESCOM_TECHO_CENTRAL` de `exterior_collisions.json`: tapaba un pasillo por donde pasan rutas de NPC; lo sustituyen los techos de la imagen.
- **Pendiente:** los marcadores de entrada a interiores (AUDITORIO, BIBLIOTECA, CANCHAS, PALAPAS, CAFETERÍA) caen dentro de techos; se sale caminando (regla `escaping`) y se alcanzan desde el borde. Otras imágenes de campus (CIC, Plaza Torres) siguen con OSM; el mismo método aplica.

## iOS: colisión de edificios y NPC — 2026-11-16

- **Estado antes:** `MapaMundoIos` cargaba `cargarColisionesExteriores()` y movía al jugador con `chocaAlMoverse()` (solo miraba el DESTINO). Al retirar el polígono a mano del techo central, esa carga ya no traía ningún edificio (solo carriles de guía): **en iOS no había colisión de edificios**. Además el `NpcAiManager` de iOS nunca recibía las colisiones (`setExteriorCollisions`), así que los NPC atravesaban todo.
- **Ahora:** `cargarColisionesConHuellas()` (bardas + techos de la imagen + huellas de OSM), `cerebro.setExteriorCollisions(colisiones)`, y el jugador avanza con `avanzarConColision()` (`commonMain`, `ColisionesExteriores.kt`): mismo `resolveCirclePath` que Android, hasta el último punto libre; quien aparece dentro de un edificio camina libre hasta salir. El radio del cuerpo sale de `PLAYER_FOOT_COLLIDER_METERS` (`WorldScale.kt`), que también usa Android.
- **Bundle:** el script de Xcode ya copia TODA la carpeta `assets/CONFIG` (`rsync`), así que `escom_osm_footprints.json` llega a iOS sin tocar el proyecto.
- **Pruebas:** `AvanzarConColisionTest` (commonTest, corre también en `:shared:testAndroidHostTest`). iOS compila (`compileKotlinIosSimulatorArm64`); **NO se probó en simulador ni en un iPhone** (no hay Mac aquí): conviene comprobar a mano que el jugador frena en los techos y que los NPC no los cruzan.

## Vista isométrica (experimental) — 2026-11-22

- **Qué es:** un renderer nuevo del mundo abierto con cámara isométrica 2:1 (estilo Project Zomboid) para el **campus ESCOM y calles cercanas**. Es un `MapProvider` más (`ISOMETRIC`, **al final del enum**) y desde 2026-11-22 es la **vista inicial** (default de `SettingsState`, `WorldMapState` y `MainMenuState`) y ya NO depende del modo desarrollador: está en la lista de Ajustes → Mapa y quien prefiera el mapa cenital lo elige ahí (los demás proveedores siguen existiendo). iOS no ofrece el mundo abierto, así que no cambia.
- **Arquitectura:** una proyección única en `:shared/commonMain` y un renderer que recibe **solo datos** (no el ViewModel). Colisiones, IA, misiones y ESCOM siguen en coordenadas geográficas y **no se tocaron**.
  - `domain/models/map/IsoProjection.kt`: plano métrico local (este/norte con `cos(lat)`), rotación de 45° y aplastado 2:1; `projectX/projectY` (sin objetos temporales), `unprojectGround` (inversa exacta a altura 0), `depth` (mayor = más lejos) y `forZoom` (usa `metersPerDp`).
  - `domain/models/map/IsoInput.kt`: `isoInputToWorldAngle` (joystick/D-pad: «arriba» en pantalla = noreste del mundo; se aplica **una sola vez** en `WorldMapScreenControls.kt`) e `isoSpriteHeading` (rumbo de mundo → ángulo de pantalla).
  - `domain/models/map/IsoSceneGeometry.kt`: `isoWallFacesCamera` e `isoPointInPolygon` (cruces, sin asignar objetos).
  - `features/map_exterior/ui/iso/IsoWorldView.kt`: capas suelo → calles → ruta → coleccionables → edificios y actores **ordenados por profundidad** → niebla elíptica → marcadores. Cada edificio es una `GraphicsLayer` horneada a zoom 18 (nunca una capa del tamaño del campus).
- **⚠️ Capas de edificios:** `IsoLayerPool` (un `RememberObserver`) **libera** las `GraphicsLayer` al reconstruir la caché y al salir de la composición. No crear capas sin registrarlas ahí.
- **Techo que se funde:** si el jugador está dentro de la huella (`isoPointInPolygon` con el polígono proyectado), el edificio baja a 10 % de opacidad en ~8 cuadros (`layer.alpha`) y vuelve a 100 % al salir.
- **Orientación del sprite:** en isométrica `moveCharacterByAngle` decide «mira a la derecha» con el eje X de **pantalla** (`cos a − sin a`), no con el ángulo de mundo. El jugador y los zombis se espejan con ese lado.
- **Jugador:** hoja `tools/iso_art_sources/student_sheet_v1.png` (5 direcciones × idle/caminar×3/correr×3/golpe) cortada al estándar de NPC con `tools/slice_iso_player.py` → `SPRITES/PLAYER/EstudianteIso/{Idle,Walk,Run,Special}/iso_<dir>_<w|r|s|i>_<N>.webp` (lienzo 193×249, dir = s, se, e, ne, n; SW/W/NW se espejan). `IsoWorldView` los decodifica una vez (`reduccion = 2`) y elige cuadro con `isoWorldAngleToScreenHeading` + `isoScreenSector` y `PlayerAction`; el ángulo del último movimiento vive en `WorldMapState.playerMoveAngle` (solo se escribe en isométrica).
- **Esquema de edificio (2026-11-22):** `CONFIG/escom_buildings_spec.json` se une por `id` OSM a las huellas (`BuildingSpec.kt`): `levels` (altura = niveles × 3,2 m), `material` (BRICK/CONCRETE/PLASTER/DEFAULT, los valores nuevos van al final), `roofColor` (`#RRGGBB`), `windows.spacingM` y `doors` (`edge`, `t`, `widthM`, `state` OPEN/CLOSED/BARRICADED, `entry`). Todo con valor por defecto: un edificio sin entrada se comporta como antes. `cargarEsquemaEdificios()` nunca lanza (si falta o está roto, esquema vacío). `IsoWorldView` dibuja material, ventanas por nivel y puertas desde él.
- **Colisión por bordes:** un edificio CON puertas deja de ser un polígono macizo: `toCollisionWalls(spec)` emite un `CollisionWall` por arista (`OSM_<id>_e<k>_<n>`) y una puerta OPEN abre un hueco (CLOSED/BARRICADED no). Sin puertas sigue el polígono (`toCollisionPolygons`). ⚠️ Solo importa donde el jugador camina libre (zonas ESCOM/ENCB): fuera de ellas el movimiento a pie se ajusta a la red de calles y no llega a las puertas. `entry` = ruta de interior de la puerta: ver «Puertas con interior (`entry`)».
- **Puertas con interior (`entry`):** `entryDoors()` (`BuildingSpec.kt`) da el centro de cada puerta con `entry` no vacío y `cargarPuertasDeEntrada()` las carga junto con las colisiones (`WorldMapState.campusEntryDoors`, no depende de la capa cenital). `checkCollectibleProximity` las convierte en puertas virtuales `escom_door_spec_<huella>_e<arista>` (radio de 20 m, aviso «X para entrar») y `handleInteraction` navega a su `entry` (el catálogo `InteriorEntryCatalog` sigue sirviendo a las puertas del Diseñador). Las 3 puertas de ESCOM llevan a `interiores_zombies` (lobby). Verificado en el Motorola: X junto a la puerta abre el interior «Campus ESCOM».
- **Puertas de ESCOM (2026-11-22):** dentro de la zona libre los únicos edificios son las 3 formas de arte «ESCOM techo 1-3» (ids 9000000-9000002, `source=art`). La única puerta del esquema es la entrada real: arista 8 de «ESCOM techo 3» (`t` 0,57, 3 m, `OPEN`), puesta a 1,3 m de la puerta «Puerta ESCOM» que el Diseñador colocó en `default_landmarks.json` (id 14, 19,5051/-99,1462). Los otros dos edificios quedan sólidos (polígono): entrar desde fuera da `reason=building` y salir estando dentro sí se permite. Verificado en el Motorola con la vista isométrica: entra y sale por el hueco y en el resto de la pared bloquea (log `WorldCollision`). ⚠️ En isométrica el joystick está rotado 45°: para caminar al rumbo `B` hay que empujarlo hacia el ángulo `B-45°`. Para otra puerta: elegir la arista con la distancia mínima al landmark y poner `t` en la proyección.
- **NPC, zombis, policía y vehículos (2026-11-22):** `IsoActorArt.kt` (`:shared`, `ui/iso`). Cada hoja (5 filas S/SE/E/NE/N × 8 columnas idle/caminar×3/correr×3/acción) se corta con `tools/slice_iso_actor.py`, que detecta cada figura por componentes conectados (tolera celdas desplazadas y descarta motas). Los personajes cargan SOLO idle + caminar (4 cuadros × 5 direcciones ≈ 1 MB por skin) bajo demanda y en segundo plano, con un máximo de 16 skins residentes; la skin sale del `id` (9 de NPC, 10 de zombi, 1 de policía) y la dirección y el paso se deducen de la posición (`IsoActorArt.track`: sin rumbo en el modelo). Los vehículos (sedán, pickup, van, autobús, camión de caja, tractocamión, patrulla) cargan sus 5 vistas al arrancar; `isoVehicleFor` elige por `CarModel` (VAN→van, SUV→pickup, WAGON reparte camiones y autobús por `id`, el resto sedán) y la patrulla va sin teñir. El tinte por `carColor` sigue con `isoCarTint` (ganancia 1,6 para no saturar el gris neutro del arte nuevo).
- **Sitios que comparan proveedor:** `isWebProvider` lo excluye (plantilla de tiles vacía); `CachingWebViewClient` y `WorldMapWidgets` ya no lo tratan como web. Las huellas se cargan con `ensureCampusFootprintsLoaded` (`WorldMapFootprints.kt`).
- **Depuración:** `InteriorDebugDrawSurface.kt` usa `IsoProjection.unprojectGround` cuando el proveedor es `ISOMETRIC`, así se ven hitboxes y huellas sobre la vista.
- **Medido (Motorola One Fusion, 2026-11-22):** mediana 14 ms, p90 22 ms, p99 34 ms caminando en el estacionamiento (línea base del mapa actual: mediana ~32 ms); `logcat -b crash` vacío.
- **Barricadas (t30, 2026-11-22):** con el modo supervivencia encendido, la X en una puerta del esquema abre `DoorMenuDialog` (Entrar si está abierta y tiene `entry`, Abrir/Cerrar, Barricar con 1 tablón, Desmontar). `DoorRules`/`DoorRuntime` (`DoorRuntime.kt`, `:shared`) llevan el estado por puerta (clave `<huella>_e<arista>`, solo las cambiadas) en `SurvivalManager` (`boards` máx. 9, `doors`); `toCollisionWalls(spec, doorStates)` abre hueco solo con la puerta OPEN, y `rebuildDoorCollisions()` (`WorldMapDoors.kt`) recarga las colisiones al cambiar. Los tablones se compran en la tienda (`SurvivalItem.TABLONES`, $20, al final del enum). Cada zombi a ≤3,5 m de una barricada le quita 4 de vida por segundo (100 en total) y, al romperse, la puerta queda ABIERTA con aviso; desmontar devuelve el tablón solo con más de la mitad de la vida. Se guarda en la partida (`survivalBoards`, `survivalDoors` = `id:ESTADO:vida;...`; guardados viejos cargan con todo como el esquema). Sin supervivencia, la X entra directo como antes. Probado en el Motorola: menú, barricar (queda 1 tablón), y tres zombis rompieron la barricada y la puerta quedó abierta.
- **Física de conducción (t37, en curso):** el paso de velocidad y rumbo del coche del jugador (gas, freno, volante, solo reversa cerca de la ESCOM, espera a Prankedy) vive en `VehicleDriving.step` (`:shared`, `domain/models/map/VehicleDriving.kt`, con `DriveInput`/`DriveState`) y `WorldMapViewModel.startGameLoop` solo lo llama. El esquive automático ante el tráfico (radio de rebase, empuje lateral y tope por tick) vive en `VehicleDodge` (`:shared`, acumulador reutilizable sin allocations: `begin`/`consider`/`finish`). El ajuste a la calzada (dentro del corredor no cambia; rozando el borde desliza; apenas arrancando se jala con fricción 0,95; fuera de la calle a velocidad frena ×0,6 y se jala) vive en `VehicleRoadClamp` (`:shared`, con las constantes `CORRIDOR_RADIUS`, `SLIDE_ENTRY_FACTOR` y `SLIDE_BASE_KEEP` que antes estaban en el ViewModel). Siguen dentro del game loop la aduana de colisiones con edificios y el atropello (siguientes pasos, uno por compilación).
- **Zombis y policías cazadores contra paredes (2026-11-22):** `collided` (`NpcStaticCollision.kt`) ya no los deja pegados al último punto seguro: `slideAroundBarrier` prueba desvíos de ±50° y ±95° del paso directo y toma el que no cruza ninguna barrera y los deja más cerca del objetivo. Es un deslizamiento voraz, NO un buscador de rutas: en una esquina cóncava o un patio cerrado siguen atorados. Verificado en el Motorola (estacionamiento de ESCOM, 6 zombis a 104 m con disparos): dos avanzaron de 98 a 83 y 92 m y se detuvieron, y uno no se movió; antes ninguno avanzaba. Para que una horda cruce el campus hace falta pathfinding real sobre la red de calles.
- **Rodeo con memoria de lado (t17, 2026-11-23):** el deslizamiento voraz de arriba vacilaba entre los dos lados en una esquina cóncava. Ahora `slideAround` (`NpcStaticCollision.kt`) recuerda por NPC el lado por el que rodea (`NpcAiManager.slideSides`, +1/-1, tope 256 entradas): el primer paso elige como antes y los siguientes siguen la pared por ese mismo lado (50°, 95° y 140°), cambiando solo si está cerrado; la memoria se borra en cuanto `collided` logra avanzar en línea recta. Sin asignaciones por tick salvo el punto devuelto. Prueba `ZombieUWallTest` (pared en U abierta al sur: un zombi dentro llega a un objetivo fuera del brazo, y la memoria queda vacía al terminar). Límite que sigue: sigue siendo un seguimiento de pared sin mapa, así que un objetivo justo detrás del fondo de una U cerrada (rodear por fuera) no se resuelve; eso pide pathfinding real. Motorola (estacionamiento, 6 zombis a 60 m): 4 llegaron al jugador en menos de 30 s y 2 quedaron a 39 y 79 m, sin confirmar si por pared o por salir del radio de simulación.
- **Separación de cuerpos y A* local (2026-11-23):** (1) `separateBodies` (`NpcSeparation.kt`, `:shared`) corre al final de cada `updateNpcs`: ningún NPC a pie (PERSON, ZOMBIE, POLICE_COP, sin `displayName`, vivo) puede quedar a menos de `BODY_SEPARATION_METERS` = 0,7 m del jugador ni de otro NPC; empuja lo que falta (la mitad el primero y el resto el segundo en los pares) y descarta el empujón si cruzaría una pared (`segmentBlocked`). Antes los zombis y policías perseguían hasta el centro del jugador y una horda se apilaba en un punto; si están justo encima se apartan en una dirección distinta por índice. Coches y jugadores remotos no se tocan. (2) `LocalPathfinder` (`:shared`): A* de 8 vecinos sobre una cuadrícula que cubre solo la zona NPC→jugador más 14 m de margen; celdas de 1 m hasta ~50 por lado y más grandes para distancias mayores (tope 200 m, 900 expansiones); las aristas se prueban con `segmentBlocked`. `collided` lo pide cuando un zombi/cazador lleva 30 pasos rodeando una pared o no tiene desvío (enfriamiento de 3 s por NPC) y mientras el camino (`FollowPath`, caduca a 12 s) está vigente lo sigue en vez de la línea recta, así que ya sale de una U cerrada con el objetivo detrás del fondo (`ZombieUWallTest`, `LocalPathfinderTest`; ~17 ms en la JVM). La meta es la posición del jugador (`aggroPlayerLat/Lon`). Medido en el Motorola: los zombis cercanos rodean y llegan; dos de seis a 60 m siguen a 36–80 m tras 60 s porque vagan fuera de su rango de visión (~100 m), no por paredes.
- **Saqueo de consumibles (t09, 2026-11-22):** con supervivencia activa hay botín suelto en el campus. `SurvivalLoot` (`:shared`) decide por un hash estable (sin `hashCode`) si cada celda de ~40 m trae botín (1 de cada 5) y de qué tipo: pizza 30 %, refresco 30 %, tablones 15 %, suero 8 %, venda 3 %, contenedor 3 %, gasolina 4 %, munición 4 %, arma blanca 1 %, arma de fuego 2 % (`LootKind`, valores nuevos al final; `LootPoint.variant` 0–9 decide qué arma o munición exacta). `WorldMapLoot.kt` toma las celdas de 7×7 alrededor del jugador, ancla cada punto a la calle más cercana (descarta los que no tienen calle a ~65 m), los publica en `WorldMapState.survivalLoot` y los convierte en coleccionables virtuales `survival_loot_<celda>` (radio de 15 m, X para recoger). Pizza, refresco y suero se aplican con `SurvivalRules.consume` y NO se gastan si no sirven («No lo necesitas ahora»); los tablones suman a la mochila (máx. 9). El **contenedor** (marcador dorado) da un tablón si hay lugar MÁS todo lo que sirva ahora de pizza, refresco y suero, de una vez (`lootContainer`); si no serviría de nada no se gasta. Lo recogido tarda 10 min en reaparecer (el contenedor, 30; solo en memoria, se pierde al cerrar). Se dibuja en `IsoWorldView` como un punto de color (naranja pizza, azul refresco, café tablones, verde suero). Sin supervivencia no hay botín. **Mochila y crafteo (2026-11-22):** pizza y refresco recogidos que NO hacen falta (hambre/sed por encima de 60 %, `SurvivalCrafting.worthUsingNow`; ojo: la tienda usa otra regla, cualquier cambio cuenta) se guardan en `SurvivalPack` (máx. 5 de cada uno; el suero y los tablones no) en vez de perderse; los contenedores también guardan lo que sobra. Mantener Y a pie abre el inventario y, con supervivencia, muestra la mochila (🍕 🥤 🩹) con dos botones: Craftear botiquín (2 refrescos + 1 pizza, `craftSurvivalKit`) y Usar botiquín (+40 HP, `useSurvivalKit`). Se guarda en la partida (`survivalPizza`, `survivalRefresco`, `survivalKits`, con valor por defecto). Falta: peso de carga (idea futura).
- **Armas y munición (t01, 2026-11-23):** `SurvivalWeapons.kt` (`:shared`) define `WeaponKind` (PUNOS, PALO, BATE, CUCHILLO, PISTOLA, ESCOPETA; valores nuevos al final), `WeaponCatalog` (daño, alcance en metros, enfriamiento, munición y desgaste), `SurvivalArsenal` (armas con usos, la equipada, balas y cartuchos; tope 60) y `WeaponRules` (recoger, equipar, `consumeShot`, guardado `NOMBRE:usos;...`). Con supervivencia encendida, `performPlayerAttack` (A en el mundo libre) pega con el arma equipada: las blancas hacen más daño que los puños, se desgastan y se rompen (el arma desaparece y vuelves a los puños); la pistola (balas, 28 m) y la escopeta (cartuchos, 16 m) gastan munición, hacen ruido `GUNSHOT` (160 m, atrae zombis) y pegan solo al objetivo más cercano a mano; sin munición muestra «Sin munición». Apagada, siempre son los puños de antes. Las armas de fuego NO barren policías ni a Prankedy (esos golpes siguen siendo cuerpo a cuerpo). El botín trae armas blancas, de fuego y munición (marcadores gris, rojo y amarillo) y el contenedor suma 2 balas. El inventario (mantener Y) lista las armas y toca para equipar. Se guarda en `survivalWeapons`, `survivalEquipped`, `survivalBalas`, `survivalCartuchos` (con valor por defecto). Falta: apuntar con dirección (hoy apunta al más cercano), recarga, cargadores y combate a pie con empuje (t02).
- **Gasolina (t03, 2026-11-23):** con supervivencia encendida el coche gasta gasolina. `FuelRules` (`:shared`, `SurvivalFuel.kt`) define el tanque (0–100, empieza en 70), el gasto por paso (`BURN_PER_TICK_AT_TOP_SPEED` = 0,018: unos 3 min a tope, proporcional a la velocidad, también en reversa) y el bidón (+40 %, máx. 3 guardados en `SurvivalPack.bidones`). La fuente del tanque es `WorldMapViewModel.fuelTank` (una variable simple, sin copiar estado en el bucle) que `burnFuel` baja en cada paso de conducción y el tick de 1 s publica a `SurvivalSubState.fuel`; es UN solo tanque que sigue al jugador, no uno por coche. Con el tanque en 0 el motor no responde al acelerador (`gas = isGasPressed && !engineDead` en `startGameLoop`) y sale «Sin gasolina» una vez; el coche se frena solo. El botín trae bidones (`LootKind.GASOLINA`, morado, 4 %): al volante con el tanque bajo de 60 % se vacían al recogerlos; si no, se guardan. Inventario (mantener Y a pie): «Bidones N · Tanque N %» y botón «Cargar gasolina» (`useFuelCan`). Chip «Gasolina» abajo al centro solo al conducir. Se guarda en `survivalFuel` y `survivalFuelCans` (con valor por defecto). Falta: llaves/encendido, gasolineras, un tanque por coche y que el motor haga ruido según el estado (t03/t04 en el tablero).
- **Heridas: sangrado y vendas (t10, 2026-11-23):** con supervivencia encendida cada mordida de zombi tiene 50 % de probabilidad de hacerte sangrar (`BleedingRules` en `SurvivalBleeding.kt`, `:shared`), independiente del 25 % de infección. Sangrando pierdes 1 HP por segundo (`SurvivalManager.healthAfter` lo suma al desgaste de necesidades, dentro del mismo `updateHealth`) y nunca bajas del piso de vida del modo (`SurvivalRules.HEALTH_FLOOR` = 10, ahora público). Se detiene con una **venda** (`SurvivalPack.vendas`, máx. 5; botón «Vendarse» en el inventario, `useBandage`) o con un botiquín. Las vendas están en el botín (`LootKind.VENDA`, rosa, 3 %): si sangras al recogerla se aplica de inmediato; si no, se guarda. Chip rojo «Sangrado» abajo al centro. Se guarda en `survivalBleeding` y `survivalBandages` (con valor por defecto). Falta: fracturas, heridas por tipo y tiempo de curación (t10 en el tablero).
- **Aliento (t11, 2026-11-23):** con supervivencia encendida correr gasta aliento. `StaminaRules`/`StaminaState` (`SurvivalStamina.kt`, `:shared`) son puros: cada paso a pie corriendo resta 0,35 (unos 9 s de carrera con el aliento lleno), caminando repone 0,05 por paso y quieto 8 por segundo. Al llegar a 0 queda «agotado»: solo se camina hasta recuperar 25. `playerRunsThisStep()` (en `WorldMapSurvival.kt`) lo aplica en los dos pasos de `WorldMapMovement.kt` (joystick y botones) en vez de leer `isRunning` directo; con supervivencia apagada devuelve `isRunning` como antes. La fuente es `WorldMapViewModel.staminaState` y el tick de 1 s la publica a `SurvivalSubState.stamina` (y repone si no hubo pasos ese segundo). Chip «Aliento» (🫁) abajo al centro mientras no esté lleno; sale «Sin aliento» al agotarse. No se guarda en la partida (al cargar empieza lleno). Falta: límite de peso de carga y moodles (t11 en el tablero).
- **Agarrón de zombi (t02, 2026-11-23):** con supervivencia encendida cada mordida que conecta te sujeta 1,5 s (`GrabRules`, `SurvivalGrab.kt`, `:shared`): tus pasos a pie valen 40 % (`playerStepSize()` en `WorldMapSurvival.kt` los multiplica; `WorldMapMovement.kt` ya no lee `isRunning` directo) y sale «¡Te agarró! Golpéalo para soltarte» (si esa mordida además infecta, el aviso de infección lo reemplaza). Un golpe tuyo a un zombi lo suelta de inmediato (`grabbedUntilMs = 0` en `performPlayerAttack`). No se guarda. Combate a pie que ya existía: golpe con cooldown y alcance por arma, empuje de ~8 m al zombi que sobrevive. Ojo con el equilibrio: una mordida son 6 HP cada 0,65 s (~9 HP/s, global aunque te rodeen), así que sin pelear un solo zombi te mata en ~11 s. Falta: empujón con botón propio y esquivar (t02 en el tablero).
- **Salud del coche (t04, 2026-11-23):** con supervivencia encendida el coche tiene salud 0–100 (`VehicleHealthRules`, `SurvivalVehicle.kt`, `:shared`). Los choques la bajan según la velocidad: contra pared o edificio hasta 45 puntos a tope y contra otro coche hasta 28; por debajo del 15 % de la velocidad máxima no cuenta (rozones), con enfriamiento de 400 ms entre golpes (`damageVehicle`, `WorldMapLoot.kt`, llamado desde `startGameLoop` en el choque estático y en el `trafficImpact`). Con menos de 35 % el coche solo llega al 60 % de su velocidad máxima (`speedCap`); a 0 el motor no responde, igual que sin gasolina, y sale «El coche está destruido». Se repara con tablones desde el inventario (a pie): 1 tablón = +25 % (`repairVehicle`). Es UN solo valor que sigue al jugador, como el tanque, y se publica a `SurvivalSubState.vehicleHealth` cada segundo; chip «Coche» al conducir. Se guarda en `survivalVehicleHealth` (con valor por defecto). El ruido del motor ya existía (`NoiseSource.ENGINE`). Falta: cajuela con almacenamiento, daño por atropellos, humo y estado por coche (t04 en el tablero).
- **Puerta del coche (t05, 2026-11-23):** con supervivencia encendida subir a un coche o a una patrulla deja el coche parado 700 ms (`CarDoorRules.LOCK_MS`, `SurvivalVehicle.kt`; `carDoorLockedUntilMs` suma a `engineDead` en `startGameLoop`) y subir o bajar emite ruido `NoiseSource.DOOR` (25 m, atrae zombis cercanos) vía `onCarDoor(boarding)` (`WorldMapSurvival.kt`, llamado desde `onInteractButtonPressed`). Sin supervivencia nada cambia. Pasajeros: el único que existe es Prankedy como acompañante de la escolta (sube contigo y el coche espera a que suba, `prankedyBoarding`); no hay asientos ni más pasajeros. Probado: compila y pasa las pruebas; en el Motorola no pude subir por X porque los coches del estacionamiento no son abordables y, rodeado de coches, tampoco bajar (`exitVehicle` no halla lugar libre): el candado de 700 ms solo está probado por prueba unitaria. Falta: asientos, más pasajeros, puertas que se abren y cierran con animación (t05 en el tablero).
- **Registrar edificios (t06, 2026-11-23):** con supervivencia encendida los edificios y techos de las huellas de OSM que NO tienen puerta de entrada se pueden registrar desde afuera con X (`BuildingSearchRules`, `SurvivalBuildingSearch.kt`, `:shared`). El punto de registro es el medio de la pared más larga, 2 m hacia afuera, y sale como botín `LootKind.EDIFICIO` (marcador turquesa en la vista isométrica) a menos de ~110 m del jugador (`refreshSurvivalLoot`, `WorldMapLoot.kt`; los edificios se calculan una vez de `campusFootprints`, que se piden al cargador si faltan). Lo que da depende del tipo de lugar, deducido del nombre de OSM (`SearchProfile`): tienda (Plaza Torres Lindavista) pizza + 2 refrescos, gasolinera (G500) bidón + refresco, clínica (Centro de Transfusión) 2 vendas + suero, escuela/centro 2 tablones + pizza, establo tablón + venda, genérico tablón + pizza, refresco o balas según el id. Reusa lo que ya hay: consumibles que se usan si hacen falta o se guardan en la mochila, tablones, vendas, bidones y balas, con sus topes; si nada cupo no se gasta. Se puede volver a registrar a los 30 min (`lootTaken`, no se guarda). Se excluyen los techos dibujados a mano del campus (ids 9 000 000–9 000 099, `source=art`) y los edificios con puerta. Límite real: solo existen datos de edificios dentro del recuadro del campus (38 huellas en `escom_osm_footprints.json`); fuera de él no hay nada que registrar ni interiores nuevos — para una ciudad entera haría falta una fuente de datos (descarga de Overpass o un archivo más grande), y esas puertas no se pueden abrir, cerrar ni barricar. Probado en el Motorola (ver tablero, t06).
- **Dormir (t12, 2026-11-23):** con supervivencia encendida, el botón «Dormir (+energía)» del inventario (mantener Y) deja al jugador quieto `SleepRules.SLEEP_MS` = 5 s (`SleepRules`, `SurvivalSleep.kt`, `:shared`; `sleepUntilMs` en el ViewModel y `playerStepSize()` devuelve 0 mientras duerme). Al terminar da +70 de energía de noche o +40 de día y cuesta 12 de hambre y 16 de sed (nunca por debajo de 5, ni empeora lo que ya estaba peor); es el equivalente a que pase tiempo de juego, porque el reloj del mundo se deriva del reloj real y no se puede adelantar. No se puede empezar con energía ≥ 85 ni con un zombi a menos de 40 m, y un zombi a 25 m o menos, o una mordida, lo despierta sin ganar nada (`startSleep`, `updateSleep`, `wakeUp` en `WorldMapSurvival.kt`; solo a pie). Dormir no hace ruido y no se guarda. Aparte, se corrigió `updateBarricadeWear` (`WorldMapDoors.kt`): comparaba grados con metros, así que cualquier zombi, estuviera donde estuviera, desgastaba las barricadas; ahora mide en metros. Probado en el Motorola: 20 de energía pasó a 91 (79/79 → 67/62) y el joystick no movió al jugador mientras dormía; con un zombi cerca no durmió. Falta: camas y refugios, avanzar la hora de juego, temperatura por clima y ropa que abrigue (t12 en el tablero).
- **Clima y días (t13, 2026-11-23):** con supervivencia encendida hay clima (`Weather` = CLEAR/RAIN/FOG, `WeatherRules.at(día, hora)`, `SurvivalWeather.kt`, `:shared`). Es DETERMINISTA: sale del día y la hora del reloj de juego (`clockNow / DAY_NIGHT_CYCLE_MS` y `currentGameHour`), cambia solo cada 6 h de juego (6 min reales) y no se guarda, así todos ven lo mismo; reparto aproximado 65 % despejado, 25 % lluvia, 10 % niebla. Lo calcula el tick de supervivencia (1 Hz, `updateSurvivalTick`) y lo publica `SurvivalSubState.weather` → `WorldMapState.survivalWeather`. Efectos: con lluvia el ruido llega al 70 % de su radio (`NoiseEvent.rain`, que `emitSurvivalNoise` llena) y la sed baja al 60 % (`SurvivalRules.advance(thirstScale)`); la niebla es solo visual por ahora. Se dibuja un velo (azul grisáceo con lluvia, gris claro con niebla) en `WorldMapScreen.kt` sobre el mapa y bajo el HUD, y el chip de Necesidades muestra 📅 día y el icono del clima. Días sobrevividos: `SurvivalSubState.activeMs` suma el tiempo de supervivencia activa (no cuenta pausas, misiones ni interiores) y `DayRules.daysSurvived` da el día (empieza en 1; 24 min reales por día); se guarda en `survivalActiveMs` (con valor por defecto). Probado en el Motorola con el gancho local de depuración que fuerza el clima: el chip muestra el día, el velo de lluvia y el de niebla; el efecto sobre ruido y sed solo está probado con pruebas unitarias, y el clima real no lo esperé. Falta: niebla con efecto real (visión de zombis), temperatura y ropa, y que el clima baje la visibilidad (t13 en el tablero).
- **Apagón (t14, 2026-11-23):** con supervivencia encendida, a partir del día 4 de supervivencia (`BlackoutRules.FROM_DAY`, `SurvivalWeather.kt`, `:shared`; el día sale de `DayRules`) se va la luz: la tienda del campus deja de vender (`buySurvivalItem` en `WorldMapSurvival.kt` avisa «Sin luz: la tienda no vende» y no cobra), las noches se ven más oscuras (`BlackoutRules.veilAlpha` multiplica el velo nocturno por 1,5 con tope 0,6; el día no cambia) y el chip de Necesidades muestra 🔌. Se avisa una sola vez al empezar (`announceBlackout`, `blackoutAnnounced`); una partida cargada que ya estaba en apagón no repite el aviso. Probado en el Motorola con el gancho local `DAYS` que suma días: aviso, chip y noche más oscura; la negativa de la tienda solo está probada con la regla pura (no fui a la tienda). El agua no se corta, no hay generador ni fuego: el juego no tiene hoy nada que dependa de ellos fuera de la tienda (t14 en el tablero).
- **Habilidades y oficios (t15, 2026-11-23):** con supervivencia encendida hay tres habilidades que suben con el uso (`Skill` = COMBATE, PUNTERIA, CARPINTERIA; `SkillRules`, `SurvivalSkills.kt`, `:shared`), con niveles 0–5 a 10/25/50/90/150 XP. XP: +1 por golpe que conecta a un zombi o civil (puntería con arma de fuego, combate con el resto; `performPlayerAttack`), +3 al barricadar una puerta y +2 por tablón al reparar el coche. Efectos por nivel: +8 % de daño (`damageFactor`), +12 % de resistencia de la barricada nueva (`barricadeFactor`, en `performDoorAction`) y +3 puntos de salud del coche por tablón (`repairBonus`, en `repairVehicle`). Al subir de nivel sale «📈 Habilidad: nivel N» (`onSkillUse`, `WorldMapSurvival.kt`). El oficio (`Occupation` = NINGUNO, BOXEADOR, SOLDADO, CARPINTERO) se elige UNA vez desde el inventario (mantener Y) y sube su habilidad a nivel 2 (25 XP); el soldado además recibe 6 balas y el carpintero 2 tablones. El inventario muestra las tres habilidades con nivel y XP. Todo vive en `SurvivalSubState` (`skills`, `occupation`) y se guarda en `survivalSkills` (`combate,punteria,carpinteria` en XP) y `survivalOccupation`, ambos con valor por defecto y tolerantes a null de guardados viejos. Probado en el Motorola: elegir Boxeador dejó Combate en nivel 2 (25 XP) y los golpes a zombis subieron a 27 XP. El efecto de daño, barricada y reparación solo tiene pruebas de las reglas puras. Falta: más habilidades (cocina, mecánica), efectos más fuertes, el oficio al empezar el modo en vez de desde el inventario, y equilibrio (t15 en el tablero).
- **Crafteo con tiempo (t16, 2026-11-23):** con supervivencia encendida el crafteo ya no es instantáneo: `startCraft(receta)` (`WorldMapSurvival.kt`) deja al jugador quieto (`craftUntilMs`; `playerStepSize()` devuelve 0), muestra una barra de acción en el centro (`WorldMapScreen.kt`, avanza sola con el reloj cada 50 ms, sin depender del tick de 1 Hz) y los ingredientes se gastan SOLO al terminar (`finishCraft` vuelve a comprobar el inventario). Un zombi a 10 m o menos (`RecipeRules.INTERRUPT_METERS`) o una mordida lo interrumpen sin gastar nada (`cancelCraft`, `updateCraft`) y tampoco deja empezar. Recetas (`Recipe`, `RecipeRules`, `SurvivalRecipes.kt`, `:shared`): BOTIQUIN (2 refrescos + 1 pizza, 3 s; la misma regla de antes, ahora con tiempo) y PALO_CLAVOS (2 tablones, 4 s; la carpintería lo acorta 10 % por nivel con mínimo la mitad y da +3 XP). El palo con clavos es un arma nueva (`WeaponKind.PALO_CLAVOS`, al final del enum): 35 de daño, 10 m, 1,1 s, 22 usos; al fabricarlo se equipa si estabas con los puños y, si ya lo tenías, repone sus usos. El estado vive en `SurvivalSubState.craft` (`CraftProgress`) y no se guarda. Probado en el Motorola: con 5 tablones el botón se habilita, la barra avanza, al terminar aparece «Palo con clavos 22» equipado y la carpintería sube 3 XP; la interrupción por zombi y la duración con niveles solo están probadas con las reglas puras. No hay cocina ni destilar agua porque el juego no tiene comida cruda ni agua como objetos (depende de contenedores, t08), ni libro de recetas ni herramientas (t16 en el tablero).
- **Población persistente y migración (t18, 2026-11-23):** con supervivencia encendida se cuentan los zombis que el jugador mata por zona (cuadrícula de ~200 m, `ZoneKillRules`, `SurvivalPopulation.kt`, `:shared`; `onZombieKilled` en `WorldMapSurvival.kt`, llamado desde `performPlayerAttack`). Con 10 bajas la zona queda despejada («Zona despejada», una sola vez) y deja de generar zombis nuevos: `NpcAiManager.isClearedZone` filtra el nacimiento de zombis de la siembra inicial y de las hordas (`takeUnless` sobre `spawnNpcOnRoad`) y solo cuenta con supervivencia encendida (`survivalNoiseEnabled`); sin ella nada cambia. El conteo (`zoneKills` en el ViewModel, copiado a `npcAiManager.zoneKills`) se guarda en `survivalZoneKills` (`clave:bajas;…`, con valor por defecto, tolerante a null y limitado a 200 zonas: al pasarse olvida la de menos bajas). Solo cuentan las bajas a golpes o disparos del jugador; los zombis que mata la policía u otro medio no se anotan. Migración: los disparos y el claxon se oyen a 1,8 veces su radio (`NoiseEvent.hearingMeters`, `NoiseRules.migrationFactor`; `NoiseMemory.strongestAt` lo usa), así los zombis simulados más lejos del ruido caminan hacia él en vez de ignorarlo; el resto de ruidos no cambia. Probado en el Motorola: las bajas se anotan por zona (1 → 4 tras varias rondas con zombis de prueba); la zona despejada, que no vuelvan a nacer zombis y la migración por disparo solo están probadas con las reglas puras. Falta: migración real entre zonas (los zombis fuera del radio de simulación no existen), despejar por otras bajas y que la población vuelva a crecer con los días (t18 en el tablero).
- **Búsqueda de la policía y misiones (2026-11-22):** las estrellas bajan solas (25 s sin delitos y luego 15 s × nivel, `WantedManager.tickWantedDecay`) SOLO en el mundo libre: mientras una misión de historia 1–3 está activa el game loop corre la policía de campaña y NO `runPoliceTick`, así que ni hay policía normal ni decaimiento (por diseño). Morir con estrellas dispara `triggerWastedSequence`: respawn fijo en ESCOM (19.504603, -99.145985), vida llena y `clearWanted`. Al probar la fuga con el gancho hay que mantener la vida llena y no tener una misión activa (verificado: 2★ → 0 en ~85 s).
- **Los cuatro renderers y su contrato de datos (h09, 2026-11-23):** el mundo se dibuja con `IsoWorldView` (por defecto, `:shared`), `NativeOsmMap` (OSM nativo), `WorldMapScreenWeb` (Leaflet/OSM Web) y `WorldMapScreenGoogle` (Google nativo, que migra a OSM Web). Todos reciben del `WorldMapState` lo mismo: centro, zoom, red de calles, huellas, NPCs, coleccionables, objetivo, destino, ruta y ruta de misión; solo el isométrico entiende además `survivalLoot` (botín y contenedores), el rumbo de los actores por posición, las puertas del esquema y la niebla elíptica. Regla: un dato nuevo del mundo se dibuja primero en `IsoWorldView` (es el predeterminado); si hace falta en los planos, se agrega al contrato de los otros tres y a esta lista, o se documenta aquí que el plano no lo muestra. Hoy el botín suelto NO se ve en los mapas planos. Retirar un renderer es decisión de producto: Google nativo es el primer candidato.
- **Orden de profundidad y techos de arte (h05/h06, 2026-11-23):** el orden actor/edificio es una heurística por arista frontal; verificado en el Motorola junto al CIDETEC (actores y un coche pegados a la pared sur salen delante, correctos). Limitaciones conocidas: edificios no convexos y actores justo en una esquina pueden ordenarse mal. Los tres techos «ESCOM techo 1-3» (ids 9000000+) son polígonos extraídos de la imagen y por eso aproximados: su puerta de entrada se validó en el acceso real y las paredes bloquean, pero el contorno exacto solo se puede validar contra imagen satelital por una persona (rotación: `-rot` en techos, `+rot` en el navgraph).
- **Pendiente:** arte isométrico del granadero, paramédico, Prankedy y el gato (siguen con arte 2D), animación de muerte/golpe y las columnas correr/acción de NPC y zombis (hoy solo idle + caminar), contraste de techo/paredes y tamaño de sprites al alejar el zoom, ventanas/puertas, edificios emblemáticos y la versión iOS. El orden de profundidad entre edificios no convexos y sprites es una **heurística** (por arista frontal).
