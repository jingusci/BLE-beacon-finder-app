# Resumen del proyecto BLE Beacon Finder

## Descripción general

`BLE Beacon Finder` es una aplicación Android nativa desarrollada en Kotlin para detectar balizas Bluetooth Low Energy (BLE) cercanas que emiten paquetes compatibles con el formato iBeacon. El objetivo central de la app es identificar cuál de las balizas conocidas se encuentra más cerca del teléfono, usando como referencia la potencia de señal recibida (`RSSI`).

El proyecto se desarrolló como prototipo para sistemas embebidos, integrando una app Android con balizas BLE implementadas en placas ESP32-C6. La aplicación Android se encarga de escanear, interpretar los anuncios BLE, reconocer las balizas registradas, elegir la más cercana y comunicar el resultado al usuario mediante texto y audio.

Según el resumen original del desarrollo y el README del proyecto, el diseño e implementación de la app se realizaron con asistencia de Codex/ChatGPT.

## Idea inicial

La primera necesidad planteada fue construir una aplicación Android capaz de:

- Escanear balizas BLE cercanas.
- Leer la potencia de señal (`RSSI`) de las balizas detectadas.
- Comparar las detecciones con una lista de balizas conocidas.
- Determinar cuál parecía estar más cerca.
- Informar el resultado en pantalla.

La primera versión funcional contaba con una pantalla principal, un botón para iniciar el escaneo y una lista fija de balizas conocidas cargadas directamente en el código. El criterio inicial era tomar la baliza detectada con mayor potencia de señal y compararla contra las balizas conocidas.

## Evolución del proyecto

### 27 de marzo de 2026: base inicial y pantalla de escaneo

El historial de Git registra el `commit inicial` el 27 de marzo de 2026. Ese mismo día se realizaron cambios vinculados a los UUID de las balizas y se incorporó una mejora del scanner con una pantalla adicional para visualizar balizas detectadas.

La versión `v0.1.0`, guardada dentro de `versiones_apk/v0.1.0`, documenta que en ese momento el proyecto ya tenía:

- Scanner BLE funcionando.
- Pantalla principal con botón para buscar balizas.
- Pantalla secundaria para visualizar balizas detectadas.
- Flujo básico de navegación entre pantallas.
- Base funcional para continuar agregando mejoras.

### 29 y 30 de marzo de 2026: uso con botones físicos y avisos por audio

El 29 de marzo se agregó la posibilidad de iniciar la búsqueda usando los botones de volumen del teléfono. Esta función sigue presente en la app actual mediante el interruptor `Usar botones de volumen para iniciar busqueda`.

El 30 de marzo se incorporaron avisos por audio en la versión `v0.1.2`. Desde ese punto, el resultado de la detección dejó de depender únicamente del texto en pantalla y pasó a poder anunciarse con sonidos asociados a cada baliza.

### 1 al 6 de abril de 2026: balizas BLE, gestión desde la app y estabilidad del algoritmo

El 1 de abril se agregaron archivos de balizas BLE al proyecto. Actualmente existe una carpeta `BLE-beacons-for-esp32-c6` con sketches `.ino` para placas ESP32-C6.

El 5 de abril se incorporó la gestión de balizas desde la app. Luego, el 6 de abril se actualizó la ventana de gestión y se corrigió un bug documentado en el historial: al borrar una baliza, la app podía seguir encontrándola. Esta corrección quedó registrada en la versión `v0.2.2`.

También el 6 de abril se produjo una mejora importante en el algoritmo de reconocimiento. La versión `v0.2.4` documenta que la app dejó de decidir por una lectura aislada de RSSI y pasó a escanear durante 3 segundos, acumulando muestras por baliza. Al finalizar esa ventana, calcula el RSSI promedio y selecciona la baliza con mejor promedio.

Esta lógica continúa en la app actual: `MainActivity` usa `SCAN_DURATION_MS = 3_000L`, agrupa mediciones por `UUID`, `major` y `minor`, y muestra tanto el RSSI promedio como la cantidad de muestras consideradas.

### 10 al 14 de abril de 2026: issues, filtros y mejoras de uso

El archivo `issues_todo.md` registra problemas y mejoras trabajadas durante esta etapa. Entre los puntos completados figuran:

- Evitar que la app falle silenciosamente si Bluetooth está desactivado.
- Corregir que al entrar en la pantalla de ver balizas se reprodujera un audio.
- Hacer que la ventana de ver balizas muestre solo balizas BLE compatibles con el protocolo usado, es decir, iBeacon, descartando otros dispositivos Bluetooth.
- Permitir tocar una baliza detectada para agregarla a la lista de conocidas.
- Agregar un botón para habilitar o deshabilitar el escaneo con botones de volumen.

El 14 de abril se registraron mejoras aplicadas desde `issues_todo`, una versión `v0.2.5`, un fix de botón en `v0.2.6` y cambios visuales en el tamaño del texto de la pantalla principal para `v0.2.7`.

### 24 al 26 de abril de 2026: audios ampliados y grabación personalizada

El 24 de abril aparece en el historial el commit `actualizo todo`. En `issues_todo.md` también quedaron anotadas necesidades de esa etapa:

- Cargar más audios con nombres de piezas o ambientes.
- Mejorar el algoritmo de reconocimiento de la baliza más cercana.
- Permitir grabar audio desde el celular para usarlo en habitaciones.

El 26 de abril se agregaron audios, se implementó la detección automática de audios a partir de los archivos y se incorporaron cambios para una versión capaz de grabar audios. Esto se refleja en la app actual mediante:

- La pantalla `CustomAudioActivity`.
- El permiso `RECORD_AUDIO` declarado en `AndroidManifest.xml`.
- Grabación con `MediaRecorder`.
- Guardado de audios personalizados como archivos `.m4a` dentro del almacenamiento interno de la app.
- Persistencia del catálogo de audios personalizados en `SharedPreferences`.
- Posibilidad de renombrar y borrar audios grabados.
- Limpieza de referencias cuando se borra un audio que estaba asignado a una baliza.

Actualmente, los audios integrados se encuentran en `app/src/main/res/raw`. Hay 18 archivos de audio, entre ellos `cocina.ogg`, `pieza.ogg`, `living.ogg`, `cuartito_cachibaches.ogg`, `nobeacon.ogg` y varios `.mp3` asociados a ambientes como `garage`, `lavadero`, `pasillo`, `quincho`, `comedor`, `banio`, `altillo` y dormitorios.

### 7 y 8 de mayo de 2026: ajustes, audio configurable y nuevas balizas

El 7 de mayo se registraron bugs encontrados por Teo y luego se actualizó el compilador para que los APK salieran con nombre de versión.

El 8 de mayo se agregó el archivo `.ino` de la baliza `Cuartito de los Cachibaches` y se registraron ajustes relacionados con botones y volumen máximo. Ese mismo día se integró la rama `4-audio-configurable`.

El archivo `issues_08052026.md` menciona dos puntos concretos:

- Bug: cuando escanea, el volumen multimedia se pone al máximo.
- Mejora: cambiar el color del botón `Volver` dentro de la pantalla de audios.
- Mejora: agregar a la app las balizas conocidas por defecto de los archivos `.ino` `Infierno` y `CuartitoCachibaches`.

En el estado actual del código, el catálogo por defecto incluye `Cuartito Cachibaches` e `Infierno`, por lo que esa mejora sí quedó incorporada.

### 15 de mayo de 2026: versión 0.4.x, más audios, diagramas y catálogo final

El 15 de mayo se actualizó el README de la versión `v0.4.0`, se integró la rama `3-agregar-mas-archivos-de-audio` y se generó la versión `v0.4.1` con cinco balizas conocidas y diagramas UML.

Actualmente el repositorio incluye:

- `arquitectura_conceptual.puml`, con una vista conceptual de la app, el teléfono Android, las balizas ESP32-C6 y el almacenamiento local.
- `diagrama_uml.puml`, con clases principales, recursos Android, almacenamiento local, dependencias del framework y sketches ESP32-C6.
- APKs versionados desde `v0.1.0` hasta `v0.4.1` dentro de `versiones_apk`.

El README de `versiones_apk/v0.4.1` documenta una limpieza del catálogo de balizas por defecto: se eliminó una lógica anterior de migración/versionado de defaults y se dejó una única lista `defaultKnownBeacons`. También se corrigieron UUID de `Cuartito Cachibaches` e `Infierno` para evitar duplicaciones con `Living`.

## Estado actual de la aplicación

La app actual está compuesta por cuatro pantallas principales:

- `MainActivity`: pantalla principal para buscar la baliza conocida más cercana.
- `BeaconScannerActivity`: pantalla de monitoreo para ver balizas iBeacon detectadas en tiempo real.
- `KnownBeaconsActivity`: pantalla para administrar balizas conocidas.
- `CustomAudioActivity`: pantalla para grabar, guardar, renombrar y borrar audios personalizados.

## Funcionamiento actual

El flujo principal de búsqueda es:

1. El usuario inicia la búsqueda con el botón `Buscar baliza mas cercana` o con los botones de volumen si esa opción está habilitada.
2. La app verifica que el dispositivo soporte Bluetooth LE.
3. Solicita permisos según la versión de Android.
4. Verifica que Bluetooth esté activado.
5. Verifica que la ubicación esté activada, condición necesaria para escaneo BLE en determinados casos.
6. Inicia un escaneo BLE de 3 segundos.
7. Procesa solamente paquetes iBeacon válidos.
8. Descarta balizas que no estén en el catálogo de conocidas para la decisión principal.
9. Agrupa muestras por `UUID`, `major` y `minor`.
10. Calcula el RSSI promedio de cada baliza detectada.
11. Elige como más cercana la baliza con mejor RSSI promedio.
12. Muestra el nombre de la baliza, el RSSI promedio y la cantidad de muestras.
13. Reproduce el audio asociado, ya sea integrado o personalizado.
14. Si no detecta balizas conocidas válidas, informa que no se detectaron balizas iBeacon y reproduce el audio `nobeacon`.

## Compatibilidad iBeacon

La app interpreta datos de fabricante compatibles con iBeacon. El parser actual espera:

- Apple Company ID `0x004C`.
- Prefijo iBeacon `0x02 0x15`.
- UUID de 16 bytes.
- `major` de 2 bytes.
- `minor` de 2 bytes.
- `Tx Power` de 1 byte.

El parser se encuentra en `BeaconParser`, dentro de `BeaconSupport.kt`. Allí se extraen `UUID`, `major` y `minor` desde el `manufacturer data` recibido por BLE.

## Balizas conocidas actuales

El catálogo por defecto actual se encuentra en `BeaconCatalog.defaultKnownBeacons`. Incluye cinco balizas:

| Nombre | UUID | Audio integrado |
| --- | --- | --- |
| Cocina | `B9407F30-F5F8-466E-AFF9-25556B57FE6D` | `cocina` |
| Pieza | `A1B2C3D4-E5F6-4789-ABCD-1234567890AB` | `pieza` |
| Living | `9F8E7D6C-5B4A-4321-9876-ABCDEF123456` | `living` |
| Cuartito Cachibaches | `A1B2C3D4-E5F6-4789-ABCD-34567890ABCD` | `cuartito_cachibaches` |
| Infierno | `A2B3C4D5-E6F7-4889-ABCD-1234567890AB` | sin audio integrado asignado |

Las balizas se normalizan internamente a minúsculas para compararlas de forma consistente.

Además de las balizas por defecto, la app permite agregar, editar y borrar balizas conocidas desde la interfaz. Esa información se guarda localmente en `SharedPreferences`, usando JSON como formato de persistencia.

## Pantalla de balizas detectadas

La pantalla `BeaconScannerActivity` funciona como herramienta de monitoreo y depuración. Actualmente:

- Escanea balizas iBeacon en tiempo real.
- Descarta dispositivos BLE que no tengan formato iBeacon.
- Muestra nombre, UUID, `major`, `minor`, RSSI y cuánto tiempo pasó desde la última detección.
- Ordena las detecciones por RSSI descendente.
- Elimina de la lista las balizas que no fueron vistas recientemente.
- Permite tocar una baliza detectada para agregarla a conocidas o editar su configuración.

Esta pantalla fue una mejora temprana del proyecto y luego evolucionó hasta convertirse en una herramienta útil para cargar nuevas balizas desde detecciones reales.

## Gestión de balizas conocidas

La pantalla `KnownBeaconsActivity` permite administrar el catálogo local:

- Ver la lista de balizas conocidas.
- Agregar una baliza manualmente.
- Editar nombre, UUID y audio asociado.
- Borrar balizas, incluidas las cargadas por defecto.
- Validar que el nombre no esté vacío.
- Validar el formato del UUID.
- Evitar UUID duplicados.
- Abrir la pantalla de audios personalizados.

La selección de audio combina audios integrados del recurso `res/raw` y audios personalizados grabados por el usuario.

## Audios integrados y personalizados

La app usa `MediaPlayer` para reproducir audios asociados a los resultados. Cada baliza puede tener:

- Un audio integrado incluido en `app/src/main/res/raw`.
- Un audio personalizado grabado desde el teléfono.
- Ningún audio asignado.

La pantalla `CustomAudioActivity` permite:

- Solicitar permiso de micrófono.
- Grabar audio con `MediaRecorder`.
- Guardar el audio con un nombre.
- Persistir el archivo como `.m4a`.
- Listar audios guardados.
- Renombrar audios.
- Borrar audios.

Cuando se borra un audio personalizado, la app actualiza las balizas que lo usaban para evitar referencias inválidas.

## Balizas ESP32-C6

El proyecto incluye sketches Arduino/NimBLE para convertir placas ESP32-C6 en balizas iBeacon. Están dentro de `BLE-beacons-for-esp32-c6`.

Los sketches actuales cubren:

- `BLE_Beacon_Cocina`
- `BLE_Beacon_Pieza`
- `BLE_Beacon_Living`
- `BLE_Beacon_CuartitoCachibaches`
- `BLE_Beacon_Infierno`

Cada sketch:

- Inicializa `NimBLEDevice`.
- Define un nombre Bluetooth visible.
- Construye manualmente el paquete iBeacon.
- Carga el paquete como `manufacturer data`.
- Usa Apple Company ID `0x004C`.
- Usa cabecera iBeacon `0x02 0x15`.
- Define UUID, `major`, `minor` y `Tx Power`.
- Configura intervalos de advertising.
- Comienza a anunciar en `setup()`.
- Deja `loop()` vacío porque la placa solo transmite anuncios BLE.

Los datos actuales observados en los `.ino` incluyen:

| Baliza ESP32-C6 | Nombre BLE | Major | Minor |
| --- | --- | --- | --- |
| Cocina | `BLE Beacon - Cocina` | `1` | `2` |
| Pieza | `BLE Beacon - Pieza` | `1` | `1` |
| Living | `BLE Beacon - Living` | `1` | `3` |
| Cuartito Cachibaches | `BLE Beacon - Cuartito de los Cachibaches` | `1` | `10` |
| Infierno | `BLE Beacon - Infierno` | `1` | `4` |

## Tecnología usada

El proyecto actual usa:

- Kotlin.
- Android SDK.
- AndroidX.
- AppCompat.
- Material Components.
- ConstraintLayout.
- Gradle Kotlin DSL.
- Java 17.
- `compileSdk 34`.
- `targetSdk 34`.
- `minSdk 26`.

En el lado de las balizas se usa:

- ESP32-C6.
- Arduino.
- NimBLE.
- Advertising BLE compatible con iBeacon.

## Permisos declarados

El `AndroidManifest.xml` actual declara:

- `BLUETOOTH`, hasta Android 11.
- `BLUETOOTH_ADMIN`, hasta Android 11.
- `ACCESS_FINE_LOCATION`.
- `BLUETOOTH_SCAN`.
- `BLUETOOTH_CONNECT`.
- `MODIFY_AUDIO_SETTINGS`.
- `RECORD_AUDIO`.

La app también declara como requerido el feature `android.hardware.bluetooth_le`.

## Organización del código actual

Los archivos Kotlin principales son:

- `MainActivity.kt`: búsqueda de baliza más cercana, permisos, Bluetooth, botones de volumen y reproducción de audio.
- `BeaconScannerActivity.kt`: monitoreo en vivo de balizas iBeacon y alta/edición desde detecciones reales.
- `KnownBeaconsActivity.kt`: CRUD de balizas conocidas y asociación de audios.
- `CustomAudioActivity.kt`: grabación y administración de audios personalizados.
- `BeaconSupport.kt`: modelos, catálogo de balizas, catálogo de audios, parser iBeacon y configuración de escaneo.

Los layouts principales son:

- `activity_main.xml`.
- `activity_beacon_scanner.xml`.
- `activity_known_beacons.xml`.
- `activity_custom_audio.xml`.
- `dialog_edit_beacon.xml`.
- `item_observed_beacon.xml`.
- `item_known_beacon.xml`.
- `item_custom_audio.xml`.

## Versiones APK guardadas

El repositorio conserva APKs y README por versión dentro de `versiones_apk`, desde `v0.1.0` hasta `v0.4.1`:

- `v0.1.0`
- `v0.1.1`
- `v0.1.2`
- `v0.2.0`
- `v0.2.1`
- `v0.2.2`
- `v0.2.3`
- `v0.2.4`
- `v0.2.5`
- `v0.2.6`
- `v0.2.7`
- `v0.2.8`
- `v0.3.0`
- `v0.3.1`
- `v0.3.2`
- `v0.3.3`
- `v0.4.0`
- `v0.4.1`

Aunque el APK más reciente guardado es `v0.4.1.apk`, el `app/build.gradle.kts` actual mantiene `versionName = "0.4.0"` y `versionCode = 400`.

## Pendientes o puntos no completados registrados

El archivo `issues_todo.md` marca como no completado:

- Agregar un botón de restablecer en la ventana de gestión de balizas para volver a las originales compiladas por defecto.

Ese botón no aparece implementado en `KnownBeaconsActivity` al momento de este resumen.

## Estado final del prototipo

El prototipo actual ya no es solamente un scanner básico. La app permite:

- Buscar la baliza conocida más cercana.
- Iniciar la búsqueda desde pantalla o desde los botones de volumen.
- Activar o desactivar el uso de botones de volumen.
- Escanear durante 3 segundos y promediar RSSI.
- Reconocer paquetes iBeacon.
- Trabajar con cinco balizas conocidas por defecto.
- Ver balizas iBeacon detectadas en tiempo real.
- Agregar balizas detectadas a la lista de conocidas.
- Administrar balizas conocidas desde la app.
- Asociar audios integrados a cada baliza.
- Grabar audios personalizados desde el celular.
- Asignar audios personalizados a balizas.
- Guardar configuración localmente.
- Integrarse con sketches ESP32-C6 que emiten como balizas iBeacon.

En síntesis, el proyecto evolucionó desde una app Android simple para detectar una baliza cercana por RSSI hasta un prototipo completo de localización por proximidad con BLE, catálogo editable, soporte de audio, herramientas de depuración y firmware de balizas ESP32-C6 incluido en el mismo repositorio.

## Fuentes verificadas dentro del proyecto

Este resumen se elaboró a partir de:

- `resumen.md.txt`, provisto como resumen inicial.
- Historial de commits de Git.
- `README.md`.
- `issues_todo.md`.
- `issues_08052026.md`.
- READMEs dentro de `versiones_apk`.
- Código Kotlin en `app/src/main/java/com/example/blebeaconfinder`.
- Recursos Android en `app/src/main/res`.
- `AndroidManifest.xml`.
- `app/build.gradle.kts`.
- Sketches `.ino` dentro de `BLE-beacons-for-esp32-c6`.
- `arquitectura_conceptual.puml`.
- `diagrama_uml.puml`.
