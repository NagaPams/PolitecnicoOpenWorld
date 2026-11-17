# Bonus de la impresora / Printer bonus

ES: Después de vencer al segundo rival del arcade, Continuar abre un bonus independiente
del personaje y del rival: destruir una impresora en 30 segundos jugables, tras 2 s de aviso.
Usa movimiento, salto, golpes y proyectiles habituales; los agarres están desactivados.
Tiene 500 de resistencia, ocho estados visuales, papeles, escombros, polvo y sonidos de impacto.
Cada punto de daño da 10 puntos. Destruirla añade 5000 + 100 por segundo restante.
El resultado muestra los puntos del bonus; no modifica el marcador de combates ni desbloqueos.
Tanto al destruirla como al agotar el tiempo se puede continuar al tercer rival.
Pausa/carga congelan el reloj. El checkpoint guarda un bonus pendiente antes del rival 3;
cerrar y retomar reinicia el bonus completo. Terminarlo elimina el pendiente.

EN: After the second arcade opponent, Continue opens a 30-second printer destruction bonus
for any fighter. Normal attacks and projectiles work; grabs are disabled. Completion or timeout
continues to opponent three. Pausing freezes the timer; reopening a pending bonus restarts it.

Código: dominio `SfPrinterBonus`, simulación `StreetFighterPrinterBonus` y rama de tick del VM,
avance/checkpoint en `StreetFighterArcade`, dibujo/overlay `ui/SfPrinterBonus`.
Android guarda el indicador en repository/adapter; iOS usa la sesión serializable compartida.
Originales y hojas preparadas: `newSFAssets/PrinterBonus/` (raíz).
Recursos finales: `STREETFIGHTER/IMAGES/printer_bonus.webp` (1024x512) y
`printer_particles.webp` (256x256); decodificados en IO solo durante el bonus.

## Verificación realizada — 2026-11-06

- Compilación Android correcta: `:app:compileDebugKotlin`.
- Shared: 239 pruebas, cero fallos; incluye seis pruebas nuevas del bonus.
- App: 127 pruebas, cero fallos; incluye round-trip del checkpoint pendiente y compatibilidad antigua.
- `git diff --check` sin errores. Hojas preparadas revisadas visualmente; ancla al piso y transparencia conservadas.
- No hubo dispositivo/emulador conectado; no se verificó una partida en pantalla. iOS no se compiló desde Windows.
- Prueba manual pendiente: ganar dos rivales → Continuar → bonus; comprobar ataques cercanos y proyectiles,
  pausa, destrucción y timeout; ambos resultados deben abrir el rival 3. Cerrar durante el bonus
  debe permitir retomarlo desde el inicio sin repetir los primeros dos rivales.
- Regeneración de assets: `python tools/import_printer_bonus.py` (Pillow y NumPy).
