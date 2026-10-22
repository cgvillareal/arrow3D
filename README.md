# Arrow 3D (nombre provisorio)

Puzzle 3D en Kotlin: un cubo con las caras **cubiertas por completo** de flechas de distintos largos.
Cada flecha se desliza **sobre la superficie** de su cara y sale por el borde; nunca atraviesa el cubo.
Las flechas **viajeras** (magenta) doblan una arista y salen por la cara vecina.

Esta entrega cubre las **fases 0 y 1** del documento de diseño (prototipo + núcleo jugable).

## Cómo correrlo

1. Abrir la carpeta en Android Studio (Ladybug o más nuevo, JDK 17+).
2. Dejar que sincronice Gradle (usa el wrapper 8.11.1, AGP 8.7.3, Kotlin 2.0.21).
3. Ejecutar la configuración `app` en un dispositivo o emulador con Android 8.0+ (API 26).

Tests:

```bash
./gradlew :core:domain:test      # reglas, superficie, solver, generador, motor
./gradlew :app:testDebugUnitTest # proyección/toque y GameViewModel
```

## Qué incluye

| Área | Estado |
|---|---|
| Caras llenas con flechas de 1 a N celdas | ✅ generadas por nivel, siempre resolubles |
| Salida por la superficie, sin atravesar la forma | ✅ |
| Flecha viajera (dobla 1 arista) | ✅ aparece en Difícil y Experto |
| Bloqueo con feedback (rebote, rojo, halo en la flecha que bloquea) | ✅ |
| Vidas, Deshacer, Pista, Reiniciar con límites por modo | ✅ |
| 5 modos: Zen, Fácil, Medio, Difícil, Experto | ✅ (Adaptativo figura como "Próximamente") |
| Colores por estado: reposo, bloqueada, saliendo (ámbar → violeta), liberada | ✅ |
| Rotar con inercia, pellizcar para zoom, doble toque para recentrar | ✅ |
| Toque generoso, prioridad a la flecha más cercana; un toque ambiguo no resta vida | ✅ |
| Vibración por evento (liberada, bloqueada, doblez, cadena, nivel completo, sin vidas) | ✅ con primitivas en API 30+ y opción para apagarla |
| Giro de 360° al terminar y pantalla de resultado con estrellas | ✅ |
| Progreso por modo y ajuste de vibración guardados (DataStore) | ✅ |
| Combos, sonido, modo daltonismo, desafío diario, ranking | ⏳ fases siguientes |

## Arquitectura

```
:core:domain   Kotlin puro, sin Android. Testeable en la JVM.
  surface/     CubeSurface: caras con grilla, pasos y plegado de aristas (coordenadas dobladas, enteras)
  model/       Arrow (común / viajera), Level (dueño de cada celda, caminos y rutas)
  rules/       exitPath (celdas a cruzar) y route (geometría del movimiento, para dibujar)
  solver/      Solver voraz: sacar una flecha nunca bloquea a otra, así que alcanza
  generator/   LevelGenerator: arma el cubo lleno en orden inverso; Difficulty con los presets
  engine/      PuzzleEngine: tap, vidas, deshacer, pista, reiniciar (funciones puras)

:app           Android + Jetpack Compose, MVI, Koin
  render/      Proyección 3D, geometría de flechas y detección de toque (Kotlin puro, con tests)
  presentation/game/cube/  CubeView (Canvas + gestos), cámara con inercia, animaciones, renderer
  presentation/game/       GameViewModel, GameState/Event/SideEffect, GameScreenRoot + GameScreen
  presentation/select/     Selección de modo
  data/        IProgressRepository + ProgressRepositoryImpl (DataStore)
  haptics/     IHaptics + AndroidHaptics (VibrationEffect, Composition en API 30+)
```

Decisiones:

- **Render propio en Compose Canvas.** El cubo es convexo, así que alcanza con descartar las caras que no miran a la cámara. El dominio no sabe nada del render: pasar a libGDX o Filament más adelante solo cambia `:app`.
- **Una sola geometría para todas las flechas.** Cada flecha se dibuja como un tramo de su `Route`. La misma función dibuja la flecha quieta, saliendo y doblando la arista.
- **Dificultad por profundidad, no por cantidad.** El generador prefiere flechas con recorridos largos. La profundidad media de la cadena de bloqueos sube de unos 2 en Zen a unos 7 en Experto.
- **Animaciones en la capa de dibujo.** El reloj de cuadros se lee dentro del `Canvas`, así que animar no recompone la pantalla.

## Valores a calibrar con pruebas

- `Difficulty`: tamaño de grilla, largo máximo, sesgos de largo y recorrido, probabilidad de viajeras, vidas, deshacer y pistas.
- `ArrowFxState`: duraciones de salida (250 a 400 ms), rebote y halo.
- `CubeCameraState`: sensibilidad de giro, fricción de la inercia, límites de zoom.
- `HitTest`: radio de toque y umbral de ambigüedad.
