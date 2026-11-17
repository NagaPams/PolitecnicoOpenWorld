# Titan del Bienestar

Peleador invitado disponible desde el inicio en Titulación por Combate (Android e iOS).
ID de red/guardado: `TITAN_BIENESTAR`. Escenario asociado: Mictlán.

Arte: cinco hojas proporcionadas por el usuario en `newSFAssets/TitanBienestar/`.
Regeneración: `python tools/import_titan_bienestar.py` (Pillow y NumPy).
Salida: `DATA/titan_bienestar.json` y `IMAGES/TitanBienestar.webp`.
Solo estos dos recursos finales se empaquetan; originales y revisión quedan fuera del APK.

La integración utiliza tiempos y cajas de Escomboy. Todas las animaciones tienen sprite,
pero algunas poses se remuestrean/reutilizan: puñetazo medio, agarres, overhead, reposo y hablar.
Los especiales usan la mecánica existente de proyectiles y el súper existente; las velas
orbitales, estampida independiente, impacto de pantalla y tamaño colosal no están implementados.
No se añadieron voces: usa efectos genéricos. El atlas conserva la escala de combate existente.

Validación visual: `newSFAssets/TitanBienestar/revision.png`.
Mapeo y adaptaciones: `newSFAssets/TitanBienestar/importacion.json`.

## Validación realizada

- Compilación Android y pruebas: BUILD SUCCESSFUL (2026-11-06).
- Shared: 229 pruebas, 0 fallos.
- App: 126 pruebas, 0 fallos.
- Auditoría de atlas: 258 cuadros, 58 animaciones, cero errores duros; ocho avisos de poses reutilizadas.
- Revisión visual de la hoja de contacto completada. No se ejecutó una partida en dispositivo ni se compiló iOS desde Windows.
- Comando: Gradle 9.5.0 `:app:compileDebugKotlin :shared:testAndroidHostTest :app:testDebugUnitTest`. Se usó la instalación local porque falta gradle-wrapper.jar.

## Escala y controles

El atlas conserva su escala con `preserveSpriteScale = true`: el renderer no amplía poses bajas para llevarlas a 100 px. La configuración anterior podía ampliar ataques hasta un 25 %. Esta política se aplica solo al Titan; los demás atlas conservan su comportamiento. Prueba: `SfFighterScaleTest`.

Especial: abajo, diagonal abajo-hacia el rival, hacia el rival + puño. Súper: R2 con barra llena. No tiene botón P de poderes extra.

Corrección de escala verificada: :shared:testAndroidHostTest y :app:compileDebugKotlin, BUILD SUCCESSFUL. Prueba SfFighterScaleTest: todos los estados y alturas de 40 a 180 px conservan factor 1 para el Titan. Controles: tras el cuarto de círculo hacia el rival, X ligero, Y medio, B fuerte. Pendiente confirmar visualmente en dispositivo.

## Atlas compacto

El atlas final ahora mide 2048×1084, con rectángulos variables y orígenes ajustados por frame; ya no usa celdas finales de 256². Conserva la escala visual y todos los píxeles. Ver RENDIMIENTO_ANDROID6.md.

## Corrección de especiales X/Y/B y R2
Recortes explícitos desde TitanBienestar_5.png, conservando componentes separados (fuego, pétalos y alebrijes). Apoyo definido por el cuerpo, no por el límite del efecto. Se conservan siete poses para especial ligero/medio, seis para fuerte y las seis poses de invocación en ocho pasos del súper. Emisión del proyectil sincronizada con la pose. Importador llama automáticamente a tools/repair_titan_specials.py. Atlas final 2048x1086, 263 cuadros, ~8.48 MiB RGBA. Auditoría sin errores; vistas animadas en newSFAssets/TitanBienestar/*_revision.gif. Falta verificar en partida real.

## Golpes normales y adelante + Y
El overhead reutilizaba incorrectamente crouch-hp: arrancaba agachado y mezclaba escala mayor de la hoja 3. Ahora usa poses de golpe fuerte DE PIE como aproximación, porque las hojas no incluyen un overhead descendente dedicado. El upper agachado mantiene su secuencia propia con escala corregida por pose. El directo medio muestra extensión y recuperación. Importador ejecuta repair_titan_attacks.py después de los especiales. Marcador de versión evita aplicar dos veces la reducción. Vistas en golpes_corregidos.png, overhead_corregido.gif y crouch-hp_corregido.gif. Auditoría del atlas correcta; pendiente revisión en dispositivo.

## Celebración con calavera
La fila de la calavera y pétalos antes estaba asignada a power-throw/throw, no a victoria. Ahora victory usa sus siete poses completas (94 frames lógicos, 1.57 s), sin espera inicial de un segundo y con pose final sostenida. tools/repair_titan_victory.py se ejecuta al final del importador. Vistas en victoria_corregida.gif/png. Pendiente comprobar al ganar una ronda en dispositivo.
