# VoidTitan (Paper 1.21.4+)

## Compilar
    mvn clean package        # JDK 21 -> target/VoidTitan-1.0.0.jar

## Resource pack compartido
- `VoidTitan-Pack.zip` usa el namespace `casino` (assets/casino/...).
- Para COMPARTIR pack con tu plugin anterior: copia la carpeta `assets/<tu_namespace>` dentro del pack que ya usas
  (o renombra `assets/casino` a tu namespace) y pon el mismo valor en `config.yml -> pack-namespace`.
- `pack_format: 46` = 1.21.4. Ajustalo en pack.mcmeta si tu version es otra.

## Comandos (permiso titan.admin)
- /titan give egg|propulsor|coin [jugador] [cantidad]
- /titan spawn | /titan kill | /titan reload

## Jugabilidad
- Huevo: clic derecho sobre un bloque (necesita 5 de alto libres) -> invoca al jefe.
- Fases: 100-66% / 66-33% / <33% (transicion invulnerable con onda expansiva, mas velocidad y dano).
- Ataques: Golpe sísmico (esquivable saltando), Proyectiles del vacío, Invocar esbirros (F2+),
  Embestida (F2+), Lluvia de meteoros (F3).
- Drops: Propulsor del Vacío (infinito, irrompible, sin daño de caída) + monedas de tragaperras.
- Propulsor: clic derecho = impulso; agachado + clic derecho = super salto.

## Ajustes
- Si el jefe se ve de espaldas: `model-yaw-offset: 180`.
- Moneda compatible con tu plugin de tragaperras: `drops.coin-model` y `drops.coin-pdc-key/value`.
