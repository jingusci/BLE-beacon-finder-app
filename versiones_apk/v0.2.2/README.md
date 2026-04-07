# v0.2.2

## Fecha de generacion
- Mon 04/06/2026 21:41:34.64

## APK
- app-debug.apk

## Cambios respecto a la version anterior
- La pantalla principal dejo de mostrar iBeacons validos pero no registrados como si fueran resultados utiles.
- Ahora el escaneo de "Buscar baliza mas cercana" filtra exclusivamente las balizas conocidas cargadas en el catalogo.
- Si se detecta una baliza BLE cercana cuyo UUID no coincide con ninguna baliza conocida, se descarta del resultado final.
- En ese escenario, la app mantiene el estado de "No se detectaron balizas iBeacon" y reproduce el audio de ausencia de balizas conocidas.
- Se elimino el caso visual de "iBeacon desconocido", para que la pantalla principal quede alineada con el comportamiento esperado de la app.
