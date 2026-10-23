# Checklist para publicar en Google Play

Lo que ya está en el código y lo que tenés que hacer vos en Play Console.

## Ya listo en el repo

- [x] `versionName` 1.0.0, `versionCode` 1 (`app/build.gradle.kts`). Subí `versionCode` en cada envío.
- [x] `targetSdk` 35, `minSdk` 26.
- [x] Release minificado con R8 y recursos reducidos (`isMinifyEnabled`, `isShrinkResources`), con reglas
      para kotlinx.serialization en `app/proguard-rules.pro`.
- [x] Firma por propiedades o variables de entorno, sin claves en el repo (ver abajo).
- [x] Ícono adaptativo con capa monocromática (íconos temáticos de Android 13).
- [x] Pantalla de inicio de Android 12+ (`values-v31/themes.xml`).
- [x] Español (por defecto) e inglés, con selector de idioma por app en Android 13+ (`res/xml/locales_config.xml`).
- [x] Política de privacidad servida por el servidor en `/privacy` y enlazada desde Ajustes.
- [x] Borrado de cuenta del ranking dentro de la app (Play lo exige si la app crea cuentas).
- [x] Sin anuncios, sin compras, sin permisos peligrosos: solo `INTERNET` y `VIBRATE`.
- [x] Tráfico sin cifrar (`http`) permitido solo en debug; release solo habla HTTPS.

## Antes de compilar el release

1. **Nombre.** `app_name` dice "Arrow 3D", que el README marca como provisorio. Revisá que no choque
   con otra app ni con una marca registrada.
2. **Servidor del ranking desplegado con HTTPS** (ver `server/README.md`).
3. **Clave de firma.** Generala una sola vez y guardala fuera del repo, con copia de seguridad:

   ```bash
   keytool -genkeypair -v -keystore arrow3d-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```

4. **Compilar el bundle:**

   ```bash
   ./gradlew :app:bundleRelease \
     -Parrow3d.rankingUrl=https://tu-servidor.example.com \
     -Parrow3d.keystore=/ruta/arrow3d-upload.jks \
     -Parrow3d.storePassword=... -Parrow3d.keyAlias=upload -Parrow3d.keyPassword=...
   ```

   (o las variables `ARROW3D_KEYSTORE`, `ARROW3D_STORE_PASSWORD`, `ARROW3D_KEY_ALIAS`, `ARROW3D_KEY_PASSWORD`).
   El archivo queda en `app/build/outputs/bundle/release/app-release.aab`.
5. **Probar el release en un teléfono real**, porque R8 puede romper cosas que en debug andan:
   jugar un nivel, unirse al ranking, enviar una victoria, ver el ranking y borrar la cuenta.

## En Play Console

- **Firma de apps de Play:** activala y subí el `.aab` firmado con tu clave de subida.
- **Ficha:** descripción corta y larga (español e inglés), ícono de 512×512, gráfico destacado de
  1024×500 y capturas de teléfono (mínimo 2). Buenas capturas: el cubo a medio resolver con un combo,
  una flecha doblada, el desafío diario, la tabla mundial.
- **Política de privacidad:** `https://tu-servidor.example.com/privacy`.
- **Seguridad de los datos** (formulario), según lo que guarda el servidor:
  - Recopila datos: **sí**, solo si el jugador se une al ranking.
  - *Información personal → Otra info (apodo)*: recopilado, no compartido, opcional, para
    "Funcionalidad de la app".
  - *Actividad en la app → Otro contenido (puntajes y jugadas)*: recopilado, no compartido, para
    "Funcionalidad de la app". Las jugadas no se guardan.
  - *Identificadores de dispositivo u otros*: un id aleatorio del jugador, no vinculado al dispositivo.
  - Datos cifrados en tránsito: **sí** (HTTPS). El usuario puede pedir que se borren: **sí**, desde la app.
- **Clasificación de contenido:** cuestionario IARC; es un puzzle sin violencia ni chat. El apodo es
  visible para otros jugadores: declaralo como "los usuarios pueden ver contenido de otros" si el
  cuestionario lo pregunta.
- **Público objetivo:** no lo declares dirigido a menores de 13 sin revisar antes los requisitos de
  la política de familias (el ranking muestra apodos elegidos por otros jugadores).
- **Pruebas:** las cuentas personales nuevas necesitan una prueba cerrada con al menos 12 testers
  durante 14 días antes de pasar a producción.

## Pendiente o para más adelante

- Filtro de apodos ofensivos en el servidor (hoy solo valida largo y caracteres).
- Logs del hosting: el servidor no guarda IPs, pero el proxy de tu hosting (Fly.io, Railway, nginx)
  puede registrarlas. Revisá su retención o mencioná esos logs en la política de privacidad.
- Play Integrity, si el ranking atrae bots.
- Capturas y textos de la ficha.
