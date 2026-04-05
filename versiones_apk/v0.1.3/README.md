# v0.1.3

## Fecha de generacion
- dom. 05/04/2026 15:41:16,26

## APK
- app-debug.apk

## Cambios respecto a la version anterior
- Se agrego una pantalla nueva para gestionar balizas conocidas directamente desde la app.
- Ahora es posible ver, agregar, editar y borrar balizas conocidas sin tocar el codigo fuente.
- Las balizas conocidas dejaron de depender de una lista hardcodeada y pasan a guardarse en almacenamiento local de la app.
- La deteccion principal y el monitor BLE usan esa lista persistida para reconocer balizas conocidas.
- Se incorporo validacion basica para evitar UUID invalidos o duplicados al cargar balizas.
- La pantalla principal suma un acceso directo a la gestion de balizas conocidas.
