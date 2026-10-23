# Arrow 3D (nombre provisorio)

Puzzle 3D en Kotlin: un cubo con las caras **cubiertas por completo** de flechas de distintos largos.
Cada flecha se desliza **sobre la superficie** de su cara y sale por el borde; nunca atraviesa el cubo.
Las flechas **viajeras** (magenta) doblan una arista y salen por la cara vecina.
Las flechas **largas se doblan** y serpentean entre las otras sin cruzarlas: al salir avanzan como
una serpiente (la cabeza sigue derecho y el cuerpo va por donde pasó), así que solo las bloquea lo
que tienen delante de la cabeza.

Esta entrega cubre las **fases 0 y 1** del documento de diseño (prototipo + núcleo jugable) y suma
contenido: modo Adaptativo, Desafío diario, récords, sonido y modo daltonismo.

## Cómo correrlo

1. Abrir la carpeta en Android Studio (Ladybug o más nuevo, JDK 17+).
2. Dejar que sincronice Gradle (usa el wrapper 8.11.1, AGP 8.7.3, Kotlin 2.0.21).
3. Ejecutar la configuración `app` en un dispositivo o emulador con Android 8.0+ (API 26).

Tests:

```bash
./gradlew :core:domain:test      # reglas, superficie, solver, generador, motor
./gradlew :core:online:test       # protocolo y verificación de partidas
./gradlew :app:testDebugUnitTest # proyección/toque, sonido y ViewModels
./gradlew -p server test         # servidor del ranking (build aparte, sin Android)
```

El ranking mundial necesita la URL del servidor al compilar (`-Parrow3d.rankingUrl=https://...`);
ver [server/README.md](server/README.md).

## Qué incluye

| Área | Estado |
|---|---|
| Caras llenas con flechas de 1 a N celdas | ✅ generadas por nivel, siempre resolubles |
| Salida por la superficie, sin atravesar la forma | ✅ |
| Flecha viajera (dobla 1 arista) | ✅ aparece en Difícil y Experto |
| Flechas dobladas que serpentean entre las otras | ✅ desde Fácil; en Experto hasta 10 celdas |
| Combos: sacar flechas seguidas (menos de 2,5 s entre una y otra) multiplica los puntos | ✅ hasta ×8; un toque bloqueado o deshacer lo corta |
| Bloqueo con feedback (rebote, rojo, halo en la flecha que bloquea) | ✅ |
| Vidas, Deshacer, Pista, Reiniciar con límites por modo | ✅ |
| 5 modos: Zen, Fácil, Medio, Difícil, Experto | ✅ |
| Modo Adaptativo | ✅ una nota de 0 (Zen) a 4 (Experto) sube con victorias limpias y baja al perder; los sesgos del generador se interpolan entre modos |
| Desafío diario | ✅ un cubo por día, igual para todos (semilla = fecha); la dificultad sube en la semana; racha de días seguidos |
| Récords | ✅ top 10 por modo en el dispositivo, con nivel, mejor combo y fecha; "¡Nuevo récord!" al terminar |
| Sonido | ✅ efectos sintetizados en código (sin archivos), la nota de cada flecha sube con el combo; se puede apagar |
| Modo daltonismo | ✅ paleta Okabe-Ito y viajeras marcadas con puntos, así el tipo de flecha no depende solo del color |
| Colores por estado: reposo, bloqueada, saliendo (ámbar → violeta), liberada | ✅ |
| Rotar con inercia, pellizcar para zoom, doble toque para recentrar | ✅ |
| Toque generoso, prioridad a la flecha más cercana; un toque ambiguo no resta vida | ✅ |
| Vibración por evento (liberada, bloqueada, doblez, cadena, nivel completo, sin vidas) | ✅ con primitivas en API 30+ y opción para apagarla |
| Giro de 360° al terminar y pantalla de resultado con estrellas | ✅ |
| Progreso por modo y ajuste de vibración guardados (DataStore) | ✅ |
| Ranking mundial | ✅ servidor propio en `server/` (Ktor + SQLite + Docker); verifica cada partida volviéndola a jugar. Ver [server/README.md](server/README.md) |
| Tutorial | ✅ siete lecciones hechas a mano (tocar, bloqueos, cadenas, largas, dobladas, viajeras, combos), solo en las caras que se ven al empezar |
| Música | ✅ generativa, sintetizada en tiempo real; el arpegio y los destellos entran con el combo y se calman cuando vence |
| Logros y estadísticas | ✅ 14 logros calculados a partir de las estadísticas acumuladas; se avisan en la tarjeta de resultado |

## Arquitectura

```
:core:domain   Kotlin puro, sin Android. Testeable en la JVM.
  surface/     CubeSurface: caras con grilla, pasos y plegado de aristas (coordenadas dobladas, enteras)
  model/       Arrow (común / viajera, recta o doblada), Level (dueño de cada celda, caminos y rutas)
  rules/       exitPath (celdas a cruzar) y route (geometría del movimiento, para dibujar)
  solver/      Solver voraz: sacar una flecha nunca bloquea a otra, así que alcanza
  generator/   LevelGenerator: arma el cubo lleno en orden inverso; Difficulty con los presets
  engine/      PuzzleEngine: tap, vidas, combos y puntos, deshacer, pista, reiniciar (funciones puras)
  modes/       PlayMode, LevelSpec, AdaptiveDifficulty, DailyChallenge (racha), Leaderboard (top 10), Tutorial
  progress/    PlayerStats, StatsRules y Achievement (cada logro es una condición sobre las estadísticas)

:core:online   Kotlin puro. Protocolo del ranking, RunVerifier (vuelve a jugar una partida) y RankingClient (HTTP)

server/        Build aparte: servidor del ranking (Ktor + SQLite), usa :core:domain y :core:online

:app           Android + Jetpack Compose, MVI, Koin
  render/      Proyección 3D, geometría de flechas y detección de toque (Kotlin puro, con tests)
  presentation/game/cube/  CubeView (Canvas + gestos), cámara con inercia, animaciones, renderer
  presentation/game/       GameViewModel, GameState/Event/SideEffect, GameScreenRoot + GameScreen
  presentation/select/     Selección de modo, desafío diario y ajustes
  presentation/records/    Récords por modo
  audio/       ToneSynth y MusicGenerator (Kotlin puro) + AndroidSoundPlayer (SoundPool) y AndroidMusicPlayer (AudioTrack)
  presentation/achievements/  Logros y estadísticas
  data/        IProgressRepository + ProgressRepositoryImpl (DataStore): niveles, ajustes, adaptativo, diario, récords
               IOnlineRanking + OnlineRankingImpl: cuenta anónima, envío de partidas y cola sin conexión
  haptics/     IHaptics + AndroidHaptics (VibrationEffect, Composition en API 30+)
```

Decisiones:

- **Render propio en Compose Canvas.** El cubo es convexo, así que alcanza con descartar las caras que no miran a la cámara. El dominio no sabe nada del render: pasar a libGDX o Filament más adelante solo cambia `:app`.
- **Una sola geometría para todas las flechas.** Cada flecha se dibuja como un tramo de su `Route`. La misma función dibuja la flecha quieta, saliendo y doblando la arista.
- **Dificultad por profundidad, no por cantidad.** El generador prefiere flechas con recorridos largos. La profundidad media de la cadena de bloqueos sube de unos 2 en Zen a unos 8 en Experto.
- **Flechas dobladas sin cerrar salidas.** Una flecha doblada crece desde su celda por celdas vacías, sin meterse en los anillos más cercanos al borde: esas celdas se visitan después y necesitan su propia salida recta. Así el generador nunca tiene que caer al plan de flechas de una celda.
- **Sonido sin archivos.** Los efectos se sintetizan al arrancar, se guardan como WAV en la caché y se tocan con `SoundPool`. La síntesis es Kotlin puro y tiene tests.
- **Modos como datos.** Cada modo se reduce a un `LevelSpec` (configuración del generador, semilla y reglas), así el ViewModel juega igual un nivel clásico, uno adaptativo o el diario.
- **Ranking que no se puede inventar.** La app no manda un puntaje: manda las jugadas. El servidor rearma el nivel desde la semilla y las vuelve a jugar con el mismo `PuzzleEngine`, así que el puntaje del ranking es siempre el que da el motor.
- **Combos en el dominio.** `PuzzleEngine.tap` recibe la hora del toque, así que la regla del combo es pura y testeable; el ViewModel le pasa un reloj inyectable.
- **Animaciones en la capa de dibujo.** El reloj de cuadros se lee dentro del `Canvas`, así que animar no recompone la pantalla.

## Valores a calibrar con pruebas

- `Difficulty`: tamaño de grilla, largo máximo, sesgos de largo y recorrido, probabilidad de viajeras y de dobladas, largo máximo de dobladas, vidas, deshacer y pistas.
- `ComboRules`: ventana del combo (2,5 s), multiplicador máximo y puntos por flecha.
- `AdaptiveDifficulty`: cuánto sube o baja la nota por victoria limpia, con ayuda, con vidas perdidas o derrota.
- `DailyChallenge`: qué dificultad toca cada día de la semana.
- `SoundBank`: notas, duraciones y timbres de cada sonido.
- `MusicGenerator`: tempo, progresión, volumen de cada capa y desde qué intensidad entra cada una.
- `Achievement`: umbrales de cada logro.
- `ArrowFxState`: duraciones de salida (250 a 400 ms), rebote y halo.
- `CubeCameraState`: sensibilidad de giro, fricción de la inercia, límites de zoom.
- `HitTest`: radio de toque y umbral de ambigüedad.
