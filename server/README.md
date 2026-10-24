# Servidor del ranking

Servidor chico en Kotlin (Ktor + SQLite) para el ranking mundial de Arrow 3D.
Es un build aparte: no necesita el SDK de Android. Compila las reglas del juego desde `../core`,
así que verifica las partidas con el mismo motor que usa la app.

## Cómo funciona

- **Jugadores anónimos.** La app pide un apodo y el servidor devuelve un token secreto, que la app
  guarda. El servidor solo guarda el hash del token. No hay mail ni contraseña.
- **Partidas verificadas.** Cuando el jugador gana, la app manda la lista de jugadas (toques,
  deshacer, pista, reiniciar, con sus tiempos). El servidor regenera el nivel desde su semilla, las
  vuelve a jugar con `PuzzleEngine` y usa **su** puntaje. Se rechaza si el nivel no queda resuelto,
  si hay jugadas imposibles o si hay toques a menos de 60 ms. Un bot que juegue jugadas legales a
  velocidad humana sí podría entrar; para eso haría falta otra capa (por ejemplo Play Integrity).
- **Tablas.** Una por modo para todos los tiempos, y una por día para el desafío diario. Cuenta
  solo el mejor puntaje de cada jugador; en un empate gana quien lo hizo primero.
- **Límites por IP.** 10 registros por hora y 60 partidas por minuto.

## API

| Método | Ruta | Para qué |
|---|---|---|
| `GET` | `/health` | Chequeo de vida (`ok`) |
| `POST` | `/v1/players` `{"name"}` | Crear jugador; devuelve `id`, `name` y `token` |
| `PATCH` | `/v1/players/me` `{"name"}` | Cambiar el apodo (también `POST` con `X-HTTP-Method-Override: PATCH`) |
| `DELETE` | `/v1/players/me` | Borrar el jugador y todos sus puntajes |
| `GET` | `/privacy` | Política de privacidad (la URL que va en la ficha de Play Store) |
| `POST` | `/v1/runs` | Enviar una partida ganada (`Authorization: Bearer <token>`) |
| `GET` | `/v1/leaderboards/{modo}?day=&limit=` | Tabla de un modo; con token incluye `me` |

Modos: `daily`, `adaptive`, `zen`, `easy`, `medium`, `hard`, `expert`. Los formatos están en
`core/online/.../Protocol.kt`.

## Correrlo

Local, desde la raíz del repo:

```bash
./gradlew -p server test           # tests
./gradlew -p server run            # http://localhost:8080
```

Con Docker (también desde la raíz):

```bash
docker compose -f server/docker-compose.yml up --build
```

Variables de entorno:

| Variable | Por defecto | |
|---|---|---|
| `PORT` | `8080` | |
| `DB_PATH` | `data/ranking.db` (`/data/ranking.db` en Docker) | Archivo SQLite; en Docker va en el volumen `/data` |
| `TRUST_PROXY` | `false` | `true` si corre detrás de un proxy que pone `X-Forwarded-For` (Railway, Fly.io, nginx). Sin proxy dejalo en `false`: si no, cualquiera elige su propio límite |

## Desplegarlo

Cualquier hosting que corra un contenedor con un disco persistente sirve. Lo importante es que
`/data` sea un volumen, porque si no la base se pierde en cada despliegue.

- **Fly.io:** `fly launch --dockerfile server/Dockerfile`, crear un volumen montado en `/data`
  y poner `TRUST_PROXY=true`.
- **Railway:** servicio desde el repo con `server/Dockerfile`, un volumen en `/data`
  y `TRUST_PROXY=true`.
- **VPS:** `docker compose` como arriba, detrás de nginx o Caddy con HTTPS.

## Conectar la app

La URL se fija al compilar:

```bash
./gradlew :app:assembleRelease -Parrow3d.rankingUrl=https://tu-servidor.example.com
```

o con `arrow3d.rankingUrl=...` en `gradle.properties`. Sin URL, la versión release oculta el
ranking mundial. La versión debug usa por defecto `http://10.0.2.2:8080`, que es como el emulador
ve un servidor corriendo en tu computadora.
