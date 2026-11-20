# Rendimiento del combate y Android 6

## Cambios

- El reloj compartido descuenta el coste de procesar un tick del siguiente tiempo de espera. Antes añadía siempre otros 16/33 ms después de procesar. Afecta práctica, arcade y los demás modos que usan StreetFighterViewModel.
- Atlas del Titan compactado sin reescalar: 2560×6656 → 2048×1084. Memoria RGBA completa: 65 → 8.46875 MiB (~87 % menos). En submuestreo ×2 queda en unos 2.12 MiB.
- Se verificaron píxeles y orígenes de sus 258 cuadros antes de guardar. Cajas, tiempos y eventos no se modificaron. La auditoría del atlas termina sin errores.
- Se omite el escaneo de alturas de los personajes con preserveSpriteScale: no se usa para dibujarlos y suponía otra lectura completa del bitmap.
- `tools/compact_sf_atlas.py` permite repetir la compactación; el importador del Titan lo llama automáticamente.
- Ambos módulos Android ahora declaran minSdk 23 (Android 6). Esto habilita instalación, pero no sustituye una prueba en dispositivo API 23.

## Validación

Las pruebas de SfFramePacingTest comprueban el presupuesto a 60/30 Hz, ticks lentos y relojes sin avance. Compilación y pruebas Android correctas.

No se midieron FPS ni latencia en teléfono. El único sistema de emulador instalado es API 33; la fluidez y el inicio en Android 6 siguen pendientes de comprobar en equipo real o emulador API 23.

Resultados: {'shared': {'tests': 233, 'failures': 0, 'errors': 0}, 'app': {'tests': 126, 'failures': 0, 'errors': 0}}.

## Compatibilidad API 23 comprobada estáticamente

Se sustituyó ConcurrentHashMap.newKeySet por Collections.newSetFromMap con ConcurrentHashMap y el constructor PriorityQueue(comparator) por PriorityQueue(11, comparator). Ambas sustituciones mantienen la concurrencia y el orden de prioridades, respectivamente. Eran las dos causas de ocho errores NewApi al bajar minSdk a 23.

Tras corregirlas: :app:lintDebug y :app:testDebugUnitTest terminaron correctamente. No se silenciaron ni se añadieron exclusiones a las comprobaciones. Persisten advertencias no bloqueantes del proyecto. Continúa pendiente la comprobación de arranque y FPS en Android 6 real.
