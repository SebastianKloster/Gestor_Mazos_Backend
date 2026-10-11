# MTG Deck Manager — Backend

Etapa 3: infraestructura, catálogo Scryfall e inventario físico.
Aún no incluye mazos, Fantasía, diferencias ni valoraciones. Diseño: [docs/architecture.md](docs/architecture.md).

## Requisitos

- JDK 21 completo (incluye `javac`; un JRE no alcanza).
- Docker Engine y Docker Compose v2, disponibles para el usuario actual.
- Acceso a Maven Central y Docker Hub para la primera instalación.
- Maven 3.9.11 se descarga mediante el Wrapper con checksum SHA-256 fijado.

## Desarrollo local

1. Copiar `.env.example` a `.env` y asignar contraseñas locales a
   `DB_PASSWORD` y `MYSQL_ROOT_PASSWORD`. No usar credenciales de producción.
2. Mantener `.env` compatible con Bash; entrecomillar valores especiales.
3. Ejecutar `./scripts/dev-start.sh`.

El script exporta las variables de `.env`, espera a que MySQL esté saludable y
arranca el perfil `dev`. MySQL conserva datos en el volumen `mysql_data`.
Si cambia `MYSQL_PORT`, actualizar también el puerto en `DB_URL`.
Cambiar contraseñas en `.env` no cambia las de una base ya inicializada.

La aplicación y MySQL escuchan solo en `127.0.0.1` por defecto. Salud:
`GET /actuator/health`. En desarrollo: `/v3/api-docs` y `/swagger-ui/index.html`.
No exponer una API de inventario sin protección cuando se implemente.
Fuera de desarrollo, OpenAPI y Swagger están deshabilitados.

Para una base externa, exportar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` y ejecutar
`./mvnw spring-boot:run`. No hay credenciales predeterminadas en la aplicación.
`SERVER_ADDRESS` y `SERVER_PORT` permiten configurar la escucha explícitamente.

## Pruebas y build

```bash
./mvnw verify
```

Surefire ejecuta tests unitarios y Failsafe los `*IT` contra MySQL 8.4.8 con
Testcontainers. Docker es obligatorio: la integración no se omite si falta.
No se usa H2 como sustituto de MySQL. Testcontainers usa una base desechable y
no necesita `.env`. El JAR queda en `target/mtg-deck-manager-0.0.1-SNAPSHOT.jar`.
Flyway aplica `V1__create_catalog.sql` y `V2__create_inventory.sql`, con validación
del esquema y comprobación de repetibilidad.
Las pruebas del catálogo usan un servidor HTTP controlado y MySQL real; no
dependen de la disponibilidad ni de los precios actuales de Scryfall.

## Entorno Codex cloud

Usar el checkout existente; las tareas ya están aisladas y no necesitan worktrees.
`./scripts/cloud-install.sh` instala un JDK completo Temurin 21.0.8+9 con
checksum SHA-256 fijado, descarga MySQL y ejecuta `verify`. Su helper
`python3 scripts/cloud-prepare.py` prepara una caché Maven escribible en
`/workspace/.m2`, configura el proxy si hace falta y crea credenciales aleatorias
solo para el MySQL de desarrollo en `.env` ignorado. Preserva archivos existentes.

```bash
source scripts/cloud-env.sh
./mvnw -s /workspace/.m2/settings.xml -Dmaven.repo.local=/workspace/.m2/repository verify
./scripts/dev-start.sh -s /workspace/.m2/settings.xml -Dmaven.repo.local=/workspace/.m2/repository
```

`cloud-env.sh` selecciona el JDK y el almacén de certificados de la plataforma
para validar TLS a través del proxy. No desactiva la verificación de certificados.

Los procesos y contenedores deben reiniciarse en cada instancia. Para detener
MySQL sin borrar datos: `docker compose stop mysql`. No usar `down -v` salvo que
se quiera eliminar explícitamente la base local.

## Catálogo (etapa 2)

- `GET /api/cards?search=Reanimate&page=0&size=20`: búsqueda por nombre inglés.
- `GET /api/cards/{cardId}`: detalle local de la carta lógica y sus caras.
- `GET /api/cards/{cardId}/printings?page=0&size=20`: impresiones en inglés.
- `GET /api/printings/{printingId}`: impresión importada, imágenes y precios.

Los IDs de la API son internos y numéricos. Para empezar, buscar una carta;
la búsqueda importa las cartas e impresiones de la página solicitada. Consultar
luego sus impresiones para obtener otros prints e IDs. Las consultas de detalle
son locales y no hacen solicitudes externas. No se descarga todo el catálogo.

Páginas desde 0, tamaño 1–100 (por defecto 20). Respuesta: `content`, `page`,
`size`, `totalElements` y `hasNext`. La búsqueda usa Scryfall `unique=cards`,
`name:"..." lang:en`; las impresiones usan `unique=prints` y `oracleid`.
La búsqueda de nombre no acepta sintaxis libre de consultas Scryfall.

Caché en memoria de páginas externas: 15 minutos y máximo 256 páginas;
los datos importados permanecen en MySQL. Precios se refrescan al consultar
páginas externas después del TTL; no hay actualización programada todavía.
`priceUpdatedAt` representa la consulta externa, no una lectura de caché.
Precios desconocidos son `null`, un cero conocido permanece cero; USD y EUR
son referencias separadas. No se generan imágenes foil ni se descargan imágenes.

Se soportan identidades con `oracle_id` en la raíz, incluidas cartas de varias
caras. Layouts especiales sin esa identidad devuelven 422; nunca se fusionan
por nombre ni se inventan UUIDs. Respuestas no inglesas se rechazan sin importar
parcialmente la página. Scryfall no disponible/datos inválidos: 502; límite de
peticiones: 503 con pausa local y sin reintentos automáticos.

Configuración opcional: `SCRYFALL_USER_AGENT` (nombre identificable de la app),
`SCRYFALL_CACHE_TTL` (duración ISO-8601, por defecto `PT15M`) y
`SCRYFALL_BASE_URL` (solo override administrado para pruebas). Destinos de red:
`api.scryfall.com` y documentación en `scryfall.com`. Las imágenes son URLs de
Scryfall para una futura interfaz. Antes de publicar, revisar sus condiciones
sobre API, datos e imágenes: https://scryfall.com/docs/api.

## Colección física (etapa 3)

- `GET /api/locations?page=0&size=20`: ubicaciones paginadas, ordenadas por ID.
- `POST /api/locations`: `{ "name": "Carpeta", "type": "BINDER" }`.
- `PATCH /api/locations/{id}`: nombre y/o tipo; campos omitidos se conservan.
- `GET /api/inventory/copies?cardId=...&locationId=...&page=0&size=20`:
  copias activas con carta, impresión, imágenes, ubicación, flags y versión.
- `POST /api/inventory/copies`: alta atómica de 1–100 copias individuales.
- `POST /api/inventory/copies/{id}/move`: traslado atómico.
- `PATCH /api/inventory/copies/{id}`: modificar `isProxy` y/o `isFoil`.
- `POST /api/inventory/copies/{id}/archive`: baja explícita de la colección.
- `GET /api/inventory/availability?cardIds=1,2`: resumen en lote por carta lógica.

Primero buscar/importar un print mediante el catálogo; después registrar copias
con su ID interno. Ejemplo de alta:

```json
{
  "printingId": 1,
  "locationId": 1,
  "quantity": 2,
  "isProxy": true,
  "isFoil": false
}
```

Cada fila representa una copia, incluso para impresiones idénticas. Flags
omitidos al adquirir son false. En PATCH, flags omitidos o null se conservan.
Una copia auténtica puede ser foil aunque el proveedor no informe ese acabado:
los acabados de Scryfall son informativos y también se admiten proxies.

Mover requiere `{ "toLocationId": 2, "expectedVersion": 0 }`; archivar requiere
`{ "expectedVersion": 0 }`. PATCH de flags también requiere `expectedVersion`.
Usar la versión devuelta por la última lectura; una versión obsoleta devuelve
409 y no deja cambios ni movimientos parciales. Una transferencia al mismo
lugar y zona es un no-op sin auditoría adicional, pero valida la versión.

Ubicaciones públicas: BINDER, BOX y OTHER. Crear o convertir a DECK devuelve
422 hasta que el módulo de mazos pueda crear ambos registros juntos (etapa 4).
En ubicaciones no DECK, `deckZone`/`toZone` deben omitirse o ser null.
El dominio ya contempla MAIN/COMMANDER para la siguiente etapa.
No hay borrado físico de ubicaciones ni copias mediante esta API.

La baja conserva la última ubicación y no borra el registro físico ni los
movimientos. Una copia archivada no puede moverse ni editarse. Las transferencias
cambian ubicación, nunca impresión o flags. Las altas, traslados y bajas registran
ACQUIRED, TRANSFERRED y REMOVED_FROM_COLLECTION en la misma transacción.
Editar flags no es un movimiento físico. Consulta de historial y valoración
se incorporarán en su etapa; aquí solo se garantiza el registro de movimientos.

Disponibilidad: máximo 100 IDs conocidos por solicitud, repetidos se deduplican;
un ID inexistente devuelve 404. Incluye cantidades poseídas, auténticas, proxies,
libres y ubicadas en mazos, más el desglose por ubicación. Prints y proxies de
una misma carta cuentan juntos; una carta conocida sin copias devuelve cero.
El resumen no contiene una lista parcial de copias: pedir el detalle paginado
con los filtros cardId/locationId. La paginación usa tamaños 1–100 y páginas
basadas en cero. Todos los listados activos excluyen copias archivadas.

Pruebas de inventario: MySQL real, límites/zonas, rollback ante falla de auditoría,
versiones obsoletas, dos transferencias HTTP concurrentes, archivado, flags y
agregación de proxies e impresiones sin consultas por copia en los listados.
