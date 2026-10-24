package com.arrow3d.server

/**
 * Privacy policy served at /privacy, so the Play Store listing can link to it. Keep it in sync
 * with docs/privacy-policy.md and with what the server actually stores.
 */
object PrivacyPolicy {
    val html: String = """
        <!doctype html>
        <html lang="es">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Arrow 3D · Privacidad</title>
          <style>
            body { font-family: system-ui, sans-serif; max-width: 42rem; margin: 2rem auto; padding: 0 1rem; line-height: 1.5; color: #1e2240; }
            h1, h2 { line-height: 1.2; }
          </style>
        </head>
        <body>
          <h1>Política de privacidad de Arrow 3D</h1>
          <p>Arrow 3D se juega sin cuenta. Todo tu progreso, tus ajustes, tus récords y tus logros
          se guardan solo en tu dispositivo.</p>
          <h2>Ranking mundial (opcional)</h2>
          <p>Si te unís al ranking mundial, el servidor guarda:</p>
          <ul>
            <li>el apodo que elegís;</li>
            <li>un identificador aleatorio y el resumen (hash) de una clave secreta que queda en tu teléfono;</li>
            <li>tu mejor puntaje en cada modo, con su combo, nivel y fecha.</li>
          </ul>
          <p>Para verificar cada partida, la app envía las jugadas que hiciste en ese nivel. El
          servidor las usa para calcular el puntaje y no las guarda.</p>
          <p>Tu dirección IP se usa solo en memoria para limitar abusos (cantidad de pedidos por
          minuto) y no se guarda. No pedimos nombre real, mail, ubicación ni contactos, no hay
          publicidad y no compartimos datos con terceros.</p>
          <h2>Borrar tus datos</h2>
          <p>En la app, en Récords → Mundial → "Borrar mi cuenta del ranking", se borran del
          servidor tu apodo y todos tus puntajes. Desinstalar la app borra todo lo guardado en el
          teléfono.</p>
          <hr>
          <h1 lang="en">Arrow 3D privacy policy</h1>
          <p lang="en">Arrow 3D needs no account. Your progress, settings, records and achievements
          are stored only on your device. If you join the optional world ranking, the server stores
          the nickname you choose, a random id, a hash of a secret key kept on your phone, and your
          best score per mode with its combo, level and date. The moves of a level are sent to
          verify the score and are not stored. Your IP address is used in memory only, to limit
          abuse, and is not stored. No real name, email, location or contacts are collected, there
          are no ads and no data is shared with third parties. You can delete your ranking account
          and all its scores in the app (Records → World → "Delete my ranking account").</p>
        </body>
        </html>
    """.trimIndent()
}
