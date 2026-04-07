# v0.2.4

## Fecha de generacion
- Mon 04/06/2026 22:16:31.33

## APK
- app-debug.apk

## Cambios respecto a la version anterior
- Se actualizo la logica de deteccion de "baliza mas cercana" en la pantalla principal.
- Antes la app elegia la baliza conocida con el valor de RSSI mas alto registrado en una lectura aislada dentro de la ventana de escaneo.
- Ahora, al iniciar una busqueda, la app abre una ventana de escaneo de exactamente 3 segundos para juntar la mayor cantidad posible de muestras de cada baliza conocida detectada.
- Durante esos 3 segundos se acumulan todas las lecturas de RSSI recibidas por baliza, agrupadas por UUID, major y minor.
- Finalizada la ventana de escaneo, la app calcula el RSSI promedio de cada baliza a partir de las muestras tomadas.
- La baliza seleccionada como "mas cercana" pasa a ser la que tenga el mejor RSSI promedio, en lugar de la que haya tenido un pico aislado de senal.
- El resultado mostrado ahora incluye el RSSI promedio utilizado para la decision y la cantidad de muestras consideradas.
- Se mantuvo sin cambios la forma de iniciar el escaneo: sigue pudiendo ejecutarse a demanda con el boton central o mediante los botones de volumen del telefono.
- Se agrego tolerancia al caso sin datos: si durante los 3 segundos no se recibe ninguna muestra valida de balizas conocidas, la app no intenta promediar y conserva el comportamiento de "No se detectaron balizas iBeacon".
- La mejora busca hacer mas estable la seleccion de la baliza cercana y reducir errores causados por picos momentaneos de RSSI.
