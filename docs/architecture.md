# Arquitectura y alcance

Monolito modular Java 21 / Spring Boot 3.5, MySQL 8.4 y Maven.
Paquete raíz: `com.mtgdeckmanager`.

Los módulos son `catalog`, `inventory`, `deck` y `valuation`,
organizados internamente en `api`, `application`, `domain` e `infrastructure`.
Los módulos `catalog` e `inventory` ya están implementados. Solo se crean paquetes cuando contienen código. `configuration` y `common.error`
contienen la infraestructura compartida.

Controladores delgados, DTOs separados de entidades JPA, transacciones en servicios.
Original se deriva de copias activas y Diferencias se calcula; no tendrán tablas.
Fantasía no reserva ni mueve copias. Las proxies cuentan como poseídas y valen cero.
No se implementan frontend, autenticación ni mazos en esta etapa.

## Decisiones para siguientes etapas

- Etapa 4: creación atómica de Deck y Location DECK, archivado con destino explícito.
- Pruebas en cada etapa; no posponer concurrencia ni auditoría al final.
- Copias y entradas usarán versiones esperadas para detectar solicitudes obsoletas.
- No fusionar cartas por nombre ni inventar identificadores externos.
- Precios desconocidos permanecen null; no sustituir un precio foil por uno normal.

## Migraciones

Flyway administra el esquema. Hibernate solo valida (`ddl-auto=validate`).
La etapa 1 inicializa el historial sin una migración vacía. La etapa 2 agrega
`V1__create_catalog.sql`: cards, card_faces y printings. Las migraciones se guardan como
`V1__create_catalog.sql`, etc., en `src/main/resources/db/migration`.
No editar migraciones ya aplicadas; crear una siguiente migración.

## Errores

`application/problem+json` mediante ProblemDetail:
400 entrada inválida, 404 inexistente, 409 conflicto de estado/concurrencia,
422 regla de dominio y 500 genérico sin detalles internos.
Los endpoints usados para probar errores existen únicamente en el código de test.

## Catálogo implementado

`CatalogService` coordina páginas externas, caché acotada y lectura/importación.
`CatalogImporter` importa una página en una transacción y resuelve identidad por
oracle_id y scryfall_id; ninguna solicitud HTTP externa ocurre dentro de ella.
`CatalogReader` convierte entidades a DTOs dentro de transacciones de lectura.
Las operaciones de importación se serializan en la instancia monousuario; las
restricciones únicas siguen protegiendo el esquema. No se promete coordinación
entre múltiples instancias, que está fuera del MVP actual.

Se consultan páginas de Scryfall de 175 elementos y se adapta la respuesta a la
paginación pública de tamaño 1–100. No se sigue next_page a destinos arbitrarios.
Detalles importados se leen localmente; búsquedas usan un TTL de 15 minutos.
Los límites del proveedor se respetan con peticiones seriales a máximo 8/segundo,
timeouts y pausa tras 429; no hay reintentos automáticos.

## Inventario implementado

`InventoryService` es el límite transaccional de altas, transferencias, flags y
archivado. `CardCopy.version` usa bloqueo optimista; las operaciones de cambio
requieren la versión esperada y hacen flush antes de construir la respuesta.
El movimiento se registra en la misma transacción, incluso si falla después
el registro de auditoría: el cambio físico se revierte. No hay cascadas de borrado.

`LocationService` administra BINDER/BOX/OTHER. DECK se reserva para creación
atómica con un mazo en etapa 4; no hay endpoints que generen DECK huérfanos.
`InventoryReader` pagina copias con un entity graph de relaciones many-to-one,
y agrega disponibilidad en lote con una consulta agrupada por carta/ubicación.
Las copias archivadas conservan la ubicación histórica, excluida de las lecturas
activas. `V2__create_inventory.sql` agrega locations, card_copies y card_movements
con claves foráneas, controles de valores e índices para lecturas activas.
