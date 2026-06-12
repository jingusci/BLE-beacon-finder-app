# BLE Beacon Finder

Aplicación Android nativa para detectar balizas Bluetooth Low Energy (BLE) cercanas, con foco en paquetes publicitarios compatibles con iBeacon.

Este proyecto fue desarrollado con asistencia de **Codex-ChatGPT** para el diseño, implementación y ajuste de la lógica de escaneo BLE.

## Descripción

`BLE Beacon Finder` es una app simple pensada para proyectos de sistemas embebidos, pruebas de laboratorio y prototipos de localización por proximidad. La aplicación permite:

- Activar el flujo de búsqueda BLE desde un único botón.
- Solicitar permisos necesarios según la versión de Android.
- Escanear dispositivos BLE cercanos.
- Detectar tramas compatibles con `iBeacon`.
- Extraer y mostrar `UUID`, `major`, `minor` y `RSSI`.
- Identificar la baliza más cercana en función de la intensidad de señal.

La app fue probada en el contexto de una baliza emitida desde un `ESP32-C6-Zero`.

## Características principales

- Interfaz simple y directa.
- Compatibilidad con Android `8.0+` (`minSdk 26`).
- Soporte para permisos modernos de Bluetooth en Android 12 o superior.
- Reconocimiento de balizas registradas en una lista conocida.
- Detección de cualquier iBeacon válido, incluso si no fue precargado.
- Selección automática de la baliza con mejor `RSSI` al finalizar el escaneo.
- Administración de balizas conocidas desde la app.
- Grabación y asignación de audios personalizados.

## Compatibilidad iBeacon

La lógica actual de la app interpreta advertising BLE con:

- `Company ID`: `0x004C`
- Prefijo iBeacon: `0x02 0x15`
- UUID de 16 bytes
- `major` de 2 bytes
- `minor` de 2 bytes
- `Tx Power` de 1 byte

Esto significa que la app está preparada para reconocer balizas que emitan un paquete iBeacon estándar a través de `manufacturer data`.

## Stack tecnológico

- `Kotlin`
- `Android SDK`
- `AndroidX`
- `Material Components`
- `ConstraintLayout`
- `Gradle Kotlin DSL`
- `Java 17`

## Requisitos

- Android Studio con soporte para proyectos Android actuales.
- JDK 17.
- Un dispositivo Android físico con Bluetooth Low Energy.
- Bluetooth habilitado.
- Permisos de escaneo BLE concedidos.
- En Android 11 o inferior, ubicación activada para el escaneo BLE.

## Instalación y ejecución

### 1. Clonar o descargar el proyecto

```bash
git clone <URL_DEL_REPOSITORIO>
cd <CARPETA_DEL_PROYECTO>
```

Si todavía no subiste el repositorio, también se puede abrir directamente la carpeta del proyecto desde Android Studio.

### 2. Abrir en Android Studio

- Abrir Android Studio.
- Elegir `Open`.
- Seleccionar la carpeta raíz del proyecto.
- Esperar la sincronización de Gradle.

### 3. Ejecutar la app

- Conectar un celular Android físico.
- Habilitar `Bluetooth`.
- Ejecutar la configuración `app`.
- Instalar el APK de debug en el dispositivo.

También se puede compilar por consola:

```bash
./gradlew assembleDebug
```

En Windows:

```bat
gradlew.bat assembleDebug
```

## Uso

1. Abrir la aplicación.
2. Presionar el botón `Buscar baliza mas cercana` para detectar la baliza conocida más próxima.
3. Aceptar los permisos solicitados.
4. Usar `Ver balizas` para monitorear iBeacons detectados, aunque todavía no estén cargados como conocidos.
5. Usar `Gestionar conocidas` para agregar, editar o borrar UUID de balizas y asignarles audios.
6. Usar `Audios` para grabar audios personalizados desde el teléfono.

Si se detecta una baliza iBeacon, la app informa:

- Nombre de la baliza
- UUID
- Major
- Minor
- RSSI

Si no encuentra ninguna baliza válida, informa que no se detectaron balizas iBeacon.

## Estructura del proyecto

```text
.
├── app/
│   ├── build.gradle.kts
│   ├── src/main/AndroidManifest.xml
│   ├── src/main/java/com/example/blebeaconfinder/
│   │   ├── MainActivity.kt
│   │   ├── BeaconScannerActivity.kt
│   │   ├── KnownBeaconsActivity.kt
│   │   ├── CustomAudioActivity.kt
│   │   └── BeaconSupport.kt
│   └── src/main/res/
│       ├── layout/
│       ├── values/
│       └── raw/
│           ├── cocina.ogg
│           ├── pieza.ogg
│           ├── living.ogg
│           ├── cuartito_cachibaches.ogg
│           ├── nobeacon.ogg
│           └── otros audios .mp3/.ogg
├── BLE-beacons-for-esp32-c6/
│   ├── BLE_Beacon_Cocina/
│   ├── BLE_Beacon_Pieza/
│   ├── BLE_Beacon_Living/
│   ├── BLE_Beacon_CuartitoCachibaches/
│   └── BLE_Beacon_Infierno/
├── versiones_apk/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

Los audios incluidos con la app deben agregarse en `app/src/main/res/raw/`. Android exige nombres de recursos en minúsculas, sin espacios ni guiones; por ejemplo: `cocina.ogg`, `dormitorio_javi.mp3` o `cuartito_cachibaches.ogg`. Una vez agregado el archivo, se puede referenciar desde Kotlin como `R.raw.nombre_del_archivo_sin_extension`.

Los UUID y la asociación inicial entre baliza, nombre y audio se cargan en `app/src/main/java/com/example/blebeaconfinder/BeaconSupport.kt`, dentro de `BeaconCatalog.defaultKnownBeacons`. Cada entrada usa `BeaconDefinition(name, uuid, audioResId, customAudioId)`. Por ejemplo:

```kotlin
BeaconDefinition(
    name = "Cocina",
    uuid = "B9407F30-F5F8-466E-AFF9-25556B57FE6D",
    audioResId = R.raw.cocina,
)
```

La app normaliza los UUID a minúsculas al guardar y comparar, pero conviene cargarlos en formato estándar `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`.

Los audios grabados desde la propia app no se ponen en `res/raw`: se guardan automáticamente en el almacenamiento interno de la aplicación, bajo el directorio `custom_audio`, y quedan registrados por `CustomAudioStore`.

## Lógica de funcionamiento

La app sigue este flujo:

1. Verifica que el dispositivo tenga soporte para `Bluetooth LE`.
2. Verifica que Bluetooth esté activado.
3. Solicita permisos según la versión de Android.
4. Inicia el escaneo BLE.
5. Analiza los paquetes publicitarios recibidos.
6. Filtra los que cumplen el formato iBeacon.
7. Guarda el mejor resultado según `RSSI`.
8. Muestra en pantalla la baliza más cercana encontrada.

## Permisos utilizados

En el `AndroidManifest.xml` se declaran los siguientes permisos:

- `android.permission.BLUETOOTH`
- `android.permission.BLUETOOTH_ADMIN`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.BLUETOOTH_SCAN`
- `android.permission.BLUETOOTH_CONNECT`
- `android.permission.MODIFY_AUDIO_SETTINGS`
- `android.permission.RECORD_AUDIO`

La app adapta el pedido de permisos en tiempo de ejecución según el nivel de API. El permiso de micrófono se usa para grabar audios personalizados.

## Balizas conocidas

El proyecto incluye una lista inicial de balizas conocidas dentro de `BeaconSupport.kt`, en `BeaconCatalog.defaultKnownBeacons`, para poder asignarles nombres amigables y audios cuando el UUID coincide.

Ejemplo:

- `Cocina`
- `Pieza`
- `Living`
- `Cuartito Cachibaches`
- `Infierno`

Desde la pantalla de administración de balizas también se pueden agregar, editar o eliminar balizas conocidas sin recompilar la app. Esos cambios se guardan en `SharedPreferences` mediante `BeaconCatalog.saveKnownBeacons`.

Aunque una baliza no esté en esa lista, la pantalla de monitoreo puede mostrarla si el advertising recibido cumple el formato iBeacon. La búsqueda de baliza más cercana, en cambio, usa las balizas conocidas para decidir qué nombre y qué audio reproducir.

## Caso de uso con ESP32-C6-Zero

Este proyecto fue pensado para integrarse con una baliza BLE emitida desde un `ESP32-C6-Zero`. Para que la app la detecte correctamente como iBeacon, el firmware debe publicar:

- `manufacturer data` con Apple Company ID `0x004C`
- tipo iBeacon `0x02 0x15`
- UUID válido de 16 bytes
- `major`
- `minor`
- `Tx Power`

## Limitaciones actuales

- La app selecciona una única baliza final: la de mejor `RSSI`.
- No mantiene historial de escaneos.
- No calcula distancia estimada.
- No lista todos los dispositivos BLE encontrados: la pantalla de monitoreo se enfoca en tramas iBeacon válidas.
- No implementa base de datos; la configuración editable se guarda en `SharedPreferences`.
- No diferencia regiones ni geocercas de beacons.

## Posibles mejoras

- Mostrar una lista completa de balizas detectadas.
- Agregar cálculo estimado de proximidad.
- Guardar historial de detecciones.
- Incorporar logs de debugging BLE.
- Agregar tests instrumentados.
- Soportar otros formatos además de iBeacon.

## Compilación verificada

El proyecto compila correctamente con:

```bat
gradlew.bat assembleDebug
```

APK de salida esperada:

`app/build/outputs/apk/debug/app-debug.apk`

## Autoría y asistencia

Proyecto realizado como trabajo/práctica de desarrollo Android y sistemas embebidos.

Implementación asistida con **Codex-ChatGPT**, utilizado como apoyo para:

- estructura del proyecto
- desarrollo de la app Android
- parsing de advertising BLE
- compatibilidad con iBeacon
- mejoras de diagnóstico y documentación

## Licencia

Podés agregar la licencia que prefieras antes de publicar el repositorio. Si querés una base simple y común para proyectos académicos o de código abierto, una buena opción es `MIT`.
