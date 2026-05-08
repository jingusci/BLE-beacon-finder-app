# v0.3.1

## Fecha de generacion
- dom 26/04/2026 18:01:49,92

## APK
- app-debug.apk

## Cambios respecto a la version anterior
- Se completo la gestion de audios personalizados dentro de la app.
- La pantalla de audios ahora permite grabar desde el celular, guardar con nombre, renombrar audios existentes y borrarlos.
- Si se borra un audio personalizado que estaba asignado a una baliza conocida, esa baliza queda automaticamente sin audio para evitar referencias invalidas.
- Los audios personalizados siguen pudiendo asignarse desde la edicion de balizas conocidas y tambien desde la edicion de balizas detectadas en la pantalla "Ver balizas".
- La pantalla "Ver balizas" dejo de reproducir anuncios de audio al detectar balizas cercanas.
- Se elimino de esa pantalla toda la logica de reproduccion de audio, no solo los botones de prueba, para que quede dedicada unicamente al monitoreo y la gestion de balizas detectadas.
- Se endurecio el manejo del reproductor de audio en la pantalla principal para reducir fallas al cambiar entre ventanas o interrumpir reproducciones en curso.
