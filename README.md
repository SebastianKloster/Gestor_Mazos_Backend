# MTG Deck Manager — Backend

Etapa 2: infraestructura Spring Boot y catálogo Scryfall. Aún no incluye
inventario, mazos, Fantasía ni diferencias. Diseño: [docs/architecture.md](docs/architecture.md).

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

Surefire ejecuta tests de errores y Failsafe los `*IT` contra MySQL 8.4.8 con
Testcontainers. Docker es obligatorio: la integración no se omite si falta.
No se usa H2 como sustituto de MySQL. Testcontainers usa una base desechable y
no necesita `.env`. El JAR queda en `target/mtg-deck-manager-0.0.1-SNAPSHOT.jar`.
Flyway aplica `V1__create_catalog.sql` y se comprueba su repetibilidad.
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
