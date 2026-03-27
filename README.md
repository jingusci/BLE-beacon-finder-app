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
2. Presionar el botón `Buscar baliza mas cercana`.
3. Aceptar los permisos solicitados.
4. Esperar la ventana de escaneo.
5. Revisar el resultado mostrado en pantalla.

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
│   ├── src/main/java/com/example/blebeaconfinder/MainActivity.kt
│   ├── src/main/res/layout/activity_main.xml
│   ├── src/main/res/values/strings.xml
│   └── src/main/AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

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

La app adapta el pedido de permisos en tiempo de ejecución según el nivel de API.

## Balizas conocidas

El proyecto incluye una pequeña lista de balizas conocidas dentro de `MainActivity.kt` para poder asignarles nombres amigables cuando el UUID coincide.

Ejemplo:

- `Baliza A - Cocina`
- `Baliza B - Pieza`
- `Baliza C - Living`

Aunque una baliza no esté en esa lista, la app igualmente puede mostrarla si el advertising recibido cumple el formato iBeacon.

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
- No lista todos los dispositivos BLE encontrados.
- No implementa persistencia local ni base de datos.
- No diferencia regiones ni geocercas de beacons.

## Posibles mejoras

- Mostrar una lista completa de balizas detectadas.
- Agregar cálculo estimado de proximidad.
- Guardar historial de detecciones.
- Permitir editar balizas conocidas desde la interfaz.
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
