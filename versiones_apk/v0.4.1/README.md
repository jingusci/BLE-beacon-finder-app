# v0.4.1

## Fecha de generacion
- vie. 15/05/2026 18:28:33,42

## APK
- v0.4.1.apk

## Cambios respecto a la version anterior
- Se simplifico el catalogo de balizas por defecto en `BeaconSupport.kt`.
- Se elimino `defaultKnownBeaconsV2` y la logica asociada de migracion/versionado de defaults.
- Se dejo una unica lista `defaultKnownBeacons` con todas las balizas iniciales.
- Se corrigieron los UUID de `Cuartito Cachibaches` e `Infierno` para que no dupliquen el UUID de `Living`.
- Se quito la referencia a `R.raw.cuartito_cachibaches`, ya que ese recurso no existe.
- Se verifico la compilacion con `assembleDebug`.
