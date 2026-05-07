# v0.3.2

## Fecha de generacion
- jue 07/05/2026 18:21:09,06

## APK
- app-debug.apk

## Cambios respecto a la version anterior

### Pedidos de cambios


Bugs : 
1. Sigue pasando que cuando voy a "Ver balizas" me pide primero permiso para habilitar bluetooth, y después de salir y volver a entrar a ese menú recién me pide permiso de ubicación (en vez de pedir las dos cosas al mismo tiempo). Incluso, creo que tendría sentido pedir permiso de ubicación ni bien entrás en la app. (Que por cierto, por lo que investigué, efectivamente es necesario ese permiso si querés escanear dispositivos bluetooth, porque justamente es posible determinar la ubicación usando dispositivos bluetooth, es choto igual que no sea un permiso por separado porque nosotros obviamente no usamos la ubicación por GPS)


2. Pareciera que los audios por defecto (Cocina, Pieza y Living) no existen o no tienen volúmen, al menos por lo que probé no sonaba al encontrar la baliza cuando usaba esos audios, en cambio con los que grabé yo sí sonaba.


Mejoras: 


En el menú de audios:

1. Hay texto de color lila clarito, sugiero que sea negro como el resto de la interfaz (por consistencia y aparte es difícil de leer en el caso del text input del nombre del audio).

2. Hay un botón que cuando no lo podés usar tiene transparencia re baja, casi no es visible, estaría bueno que esté visible pero que sea evidente que no es interactuable.

3. No te deja grabar audio sin poner un nombre primero, aunque en realidad solo necesitás el nombre cuando lo querés guardar.

4. Las interacciones para manejar la lista de audios me confundieron un toque, tocar es para renombrar y mantener apretado es borrar actualmente, creo que sería mejor que sea igual/parecido a la lista de balizas, donde tocar te da opciones y de ahí podés borrar o renombrar. (quizás incluso mejor sería si hay un botón para borrar y tappear en el nombre del audio te deje renombrar).


---

### Propuesta codex

Bugs prioritarios:
- Permisos BLE/ubicación: conviene unificar el flujo para que, si falta Bluetooth y además faltan permisos, la app no te haga entrar dos veces al menú para completar todo. Lo razonable es:
  - pedir permisos de runtime apenas el usuario entra a la app o antes del primer escaneo real,
  - y dejar la activación de Bluetooth como paso posterior cuando corresponda.
- Audios por defecto sin sonido: hay que verificar si el problema es de carga/reproducción o de recursos. Como los personalizados sí suenan, el foco está en la ruta `raw -> SoundPool`. Eso se chequea primero en `MainActivity`.
- Menú de audios: son mejoras de UX puntuales y de bajo riesgo. Se pueden hacer en un solo paquete:
  - colores/contraste,
  - estado visual del botón deshabilitado,
  - permitir grabar sin nombre previo,
  - y rediseñar interacción tocar/mantener.

Orden recomendado:
1. Corregir permisos/flujo de entrada, porque afecta uso base.
2. Corregir audios por defecto, porque impacta la función principal.
3. Ajustar UX del menú de audios, porque es más superficial y menos riesgoso.

Si querés, avanzo ya con ese orden y te dejo una tanda de cambios cerrada.


---


### Resultado de codex

Hecho.

Qué cambié:
- Flujo de permisos:
  - `MainActivity.kt` ahora pide permisos BLE/ubicación apenas entrás a la app si faltan.
  - Tanto en `MainActivity.kt` como en `BeaconScannerActivity.kt` ahora primero se resuelven permisos y después Bluetooth. Eso evita el flujo partido de “entrar una vez para Bluetooth y otra para ubicación”.

- Audios por defecto:
  - En `MainActivity.kt` saqué `SoundPool` y pasé toda la reproducción de la pantalla principal a `MediaPlayer`, tanto para audios `raw` como personalizados.
  - Esto apunta directamente a que los audios built-in vuelvan a sonar de forma consistente y además simplifica bastante la lógica de reproducción.

- Menú de audios:
  - En `CustomAudioActivity.kt` ahora se puede grabar sin nombre previo; el nombre solo se exige al guardar.
  - Tocar un audio ahora abre acciones claras (`Renombrar` / `Borrar`) en lugar de depender de tocar vs mantener apretado.
  - Ajusté textos y estados visuales para que el botón `Guardar audio` siga visible aunque esté deshabilitado.
  - En `activity_custom_audio.xml` fijé colores del input y del botón para evitar el look lila tenue.
  - Agregué los color state lists `save_audio_button_tint.xml` y `save_audio_button_text.xml`.

- “Ver balizas”:
  - Se mantiene sin reproducir audios. Ya estaba así y sigue así.

Validación:
- `./gradlew.bat assembleDebug` compiló bien.

Lo único que no puedo confirmar desde acá es si el bug de cierres quedó 100% resuelto, porque no tengo stacktrace del crash. Pero el cambio relevante quedó hecho: menos caminos de reproducción distintos y menos estado compartido al navegar entre pantallas.