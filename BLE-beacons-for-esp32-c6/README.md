# BLE Beacons para ESP32-C6

Esta carpeta contiene los sketches `.ino` usados para convertir placas ESP32-C6 en balizas BLE tipo iBeacon, una por habitacion.

## Archivos incluidos

- `BLE_Beacon_Cocina/BLE_Beacon_Cocina.ino`
  Emite una baliza iBeacon identificada como `Cocina`.

- `BLE_Beacon_Pieza/BLE_Beacon_Pieza.ino`
  Emite una baliza iBeacon identificada como `Pieza`.

- `BLE_Beacon_Living/BLE_Beacon_Living.ino`
  Emite una baliza iBeacon identificada como `Living`.

## Que hace cada `.ino`

Los tres archivos siguen la misma estructura:

1. inicializan la libreria `NimBLE`,
2. definen el nombre Bluetooth visible con `NimBLEDevice::init(...)`,
3. construyen manualmente el paquete iBeacon dentro de `beaconData`,
4. cargan ese paquete como `manufacturer data`,
5. configuran el intervalo de advertising,
6. comienzan a anunciar la baliza en `setup()`.

El `loop()` queda vacio porque la placa no necesita hacer otra tarea: solo anunciar la baliza BLE continuamente.

## Datos iBeacon que aparecen en los sketches

Cada beacon arma un paquete de 25 bytes con esta estructura:

- `0x4C 0x00`
  Apple Company ID. Es el identificador de fabricante usado por el formato iBeacon.

- `0x02 0x15`
  Cabecera que identifica el paquete como iBeacon.

- `UUID` de 16 bytes
  Identificador principal de la baliza.

- `Major` de 2 bytes
  En estos sketches vale siempre `0x0001`.

- `Minor` de 2 bytes
  Diferencia una habitacion de otra dentro del mismo esquema.

- `TX power` de 1 byte
  En estos sketches vale `0xC5`. Se usa como referencia de potencia a 1 metro para estimaciones de proximidad.

## Configuracion actual

### Cocina

- Nombre BLE: `BLE Beacon - Cocina`
- UUID: `B9407F30-F5F8-466E-AFF9-25556B57FE6D`
- Major: `1`
- Minor: `2`

### Pieza

- Nombre BLE: `BLE Beacon - Pieza`
- UUID: `A1B2C3D4-E5F6-4789-ABCD-1234567890AB`
- Major: `1`
- Minor: `1`

### Living

- Nombre BLE: `BLE Beacon - Living`
- UUID: `9F8E7D6C-5B4A-4321-9876-ABCDEF123456`
- Major: `1`
- Minor: `3`

## Como crear una nueva habitacion

Si quieres agregar una baliza nueva a partir de uno de estos `.ino`, los cambios principales a hacer son:

- `nombre`
- `uuid`
- `minor`

Concretamente:

1. cambia el nombre en `NimBLEDevice::init("BLE Beacon - ...")`,
2. reemplaza el arreglo `uuid[16]` por el UUID nuevo en hexadecimal,
3. cambia los 2 bytes de `Minor`.

Ejemplo: si quieres crear `Banio`, deberias cambiar:

- `NimBLEDevice::init("BLE Beacon - Banio")`
- el `uuid[16]` por uno nuevo y unico
- los bytes de `Minor` por otro valor no usado

## Que conviene mantener igual

En estos sketches conviene dejar igual:

- `Major = 1`
  Sirve para mantener todas las habitaciones dentro del mismo grupo logico.

- `TX power = 0xC5`
  Ya esta alineado entre las balizas actuales.

- `setMinInterval(32)` y `setMaxInterval(64)`
  Definen una emision relativamente rapida, util para deteccion cercana.

## Importante para que la app Android las reconozca

Si agregas una nueva habitacion, no alcanza con cambiar el `.ino`. Tambien debes registrar esa nueva baliza en la app Android, en el catalogo de balizas conocidas:

- `app/src/main/java/com/example/blebeaconfinder/BeaconSupport.kt`

Ahi debes agregar el mismo `UUID` y, si corresponde, asociarle el audio de esa habitacion.

## Notas sobre BLE Beacons en ESP32-C6

- El ESP32-C6 puede emitir advertising BLE sin necesidad de conexiones activas.
- Para este proyecto se usa `NimBLE`, que suele ser una opcion liviana y adecuada para BLE en ESP32.
- Un beacon iBeacon no "conversa" con la app: solo transmite paquetes periodicos.
- La app detecta esos paquetes, extrae `UUID`, `major` y `minor`, y decide si corresponden a una baliza conocida.
- Si varias balizas usan distinta combinacion de `UUID` o `minor`, la app puede distinguir habitaciones.

## Recomendacion practica

Para evitar conflictos, cada nueva habitacion deberia usar:

- un nombre claro,
- un UUID unico,
- un `minor` no repetido dentro del proyecto.

Asi sera mas facil mantener sincronizados los sketches del ESP32-C6 con la app Android.
