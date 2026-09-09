# Mover la base de Oregon a Virginia

**Estado: hecho. La base vive en us-east-1 desde el 09/09/2026, 00:52 UTC.**
Parada real: 12 minutos (00:40 a 00:52 UTC).

## Por qué

El backend corre en `us-east-1` (Virginia) y la base estaba en `us-west-2` (Oregon).
Medido desde la propia EC2:

| Destino | Ida y vuelta |
|---|---|
| `aws-0-us-west-2.pooler.supabase.com` | 58–64 ms |
| `aws-0-us-east-1.pooler.supabase.com` | 2–4 ms |

`GET /api/v1/portal/vacantes` tarda 255 ms medido **dentro** del servidor, y ~230 ms de eso
es el rodeo. No es un problema de consultas: `ServicioTablonPortalImpl.vacantesPublicadas()`
las agrupa a propósito, son 3–4 viajes fijos y no crecen con el número de vacantes.

## Los dos proyectos

| | Origen | Destino |
|---|---|---|
| Nombre | `Renaser-os-ia-agents-develop` | `renaser-reclutamiento` |
| Ref | `jcmjaiwwnbbhflguydnq` | `sjmyhkhivvippngilwiu` |
| Región | us-west-2 | **us-east-1** |

El nombre del origen engaña: pese a decir «ia-agents» y «develop», ahí viven la base de los
candidatos reales y los dos buckets. El proyecto de los agentes IA es otro
(`ikyjurujfxpazygwloja`, us-east-2) y **no** entra en esta migración.

## Lo que había que mover, y lo que no

Inventario del origen, hecho antes de tocar nada:

- 108 tablas, 33 995 filas, 51 MB. 51 migraciones de Flyway, todas correctas.
- 84 secuencias detrás de columnas `identity` — sus valores hay que restaurarlos o el primer
  alta después del cambio choca con un id existente.
- Extensiones: `hstore`, `pg_stat_statements`, `pgcrypto`, `supabase_vault`, `uuid-ossp`,
  `vector` 0.8.2.
- **Cero políticas RLS. Cero vistas. Una función y dos disparadores propios.** Sin `pg_cron`.
  El vault, vacío.
- **No se usa Supabase Auth**: `auth.users` tiene 0 filas. Los usuarios son tablas propias
  con BCrypt. Eso ahorra la parte más difícil de cualquier migración de Supabase.
- Dos buckets, no uno: `Documentos-cv` (382 objetos, 347 MB) y `pruebas-tecnicas`
  (2 PDF de agosto, que ningún código referencia).
- El frontend no habla con Supabase. Todo pasa por el backend, así que el cambio toca solo
  parámetros de SSM: **no hay que tocar `vercel.json`**.

## Las tres trampas que aparecieron

**1. La conexión directa es solo IPv6.** `db.<ref>.supabase.co` resuelve únicamente a AAAA y
una red IPv4 no la alcanza. Todo (volcado y restauración) va por el *pooler* en modo sesión,
puerto 5432.

**2. El volcado de datos trae `auth` y `storage`, y el rol `postgres` no puede escribirlas.**
Revienta con `permission denied for table buckets_vectors`. Se arregla con `--schema public`.
También hay que comentar la línea `GRANT SET ON PARAMETER "log_min_messages"` de `roles.sql`:
exige superusuario y el proyecto nuevo ya lo trae puesto.

**3. `supabase storage cp` antepone el nombre de la carpeta local.** Sube
`Documentos-cv/1/x.pdf` donde el objeto debe llamarse `1/x.pdf`. **Este es el fallo
silencioso**: la aplicación arranca bien y los currículums dan 404 días después. Se
comprobó comparando nombres uno a uno; la subida se hizo por la API REST fijando la ruta.

## Comandos que funcionaron

Volcado (tres ficheros, como documenta Supabase):

```
supabase db dump --db-url "$ORIGEN" -f roles.sql  --role-only
supabase db dump --db-url "$ORIGEN" -f schema.sql
supabase db dump --db-url "$ORIGEN" -f data.sql --data-only --use-copy --schema public
```

Restauración, en una sola transacción:

```
cat roles.sql schema.sql; echo "SET session_replication_role = replica;"; cat data.sql
  | psql --single-transaction --variable ON_ERROR_STOP=1 -f - --dbname "$DESTINO"
```

## Lo verificado del ensayo

| Comprobación | Resultado |
|---|---|
| Tablas | 108 = 108 |
| Filas, tabla por tabla | 33 995 = 33 995, sin una diferencia |
| Secuencias | 84 = 84, mismos valores |
| Objetos en los buckets | 384 = 384, 347,0 MB = 347,0 MB |
| Nombres de objeto | idénticos uno a uno |
| 5 currículums al azar | descargados del destino con enlace firmado, **sha256 idéntico** |
| Ciclo completo vaciar→restaurar→verificar | **2 min 51 s**, sin errores, y los buckets intactos |

## El cambio, como salió

Parada de 12 minutos, 00:40 a 00:52 UTC del 09/09/2026.

| Paso | |
|---|---|
| Parar el contenedor | 00:40:29 |
| Volcado definitivo | 484 filas más que en el ensayo |
| Vaciar y restaurar | 4 min 05 s, sin errores, 108 tablas |
| Filas | 34 477 = 34 477, ninguna tabla distinta |
| Secuencias | sin una diferencia |
| Ficheros | faltaba 1 (el CV que entró durante el ensayo); trasladado |
| Objetos finales | 385 = 385, 347,2 MB = 347,2 MB |
| 3 CV al azar | descargados con la clave que usa la aplicación: PDF válidos |
| Arranque | 31 s, cero excepciones, Flyway sin migraciones que aplicar |

### Lo que se ganó

`GET /api/v1/portal/vacantes`, medido dentro del servidor:

| | Antes | Después |
|---|---|---|
| Dentro del servidor | 255 ms | **29–45 ms** |
| Desde Perú, en caliente | ~455 ms | **~240 ms** |

## Tres trampas más que solo aparecen al hacerlo

**4. El despliegue NO sincroniza `despliegue/` con la máquina.** `ci.yml` solo ejecuta
`cd /opt/renaser && ./desplegar.sh`, y ese script baja la imagen y reinicia. La máquina tiene
su propia copia de `docker-compose.yml`. Fusionar el PR del `SUPABASE_URL` dejó el repo bien
y el servidor igual que antes; hubo que copiar el fichero por SSM. **Cualquier cambio en
`despliegue/` necesita ese paso a mano.**

**5. La URL de JDBC se escribió con una barra invertida.** El `&` escapado dentro de un
`bash -c` con comillas dobles dejó `...sjmyhkhivvippngilwiu\&password=...` en Parameter Store.
Se vio al releer el valor antes de arrancar. Los valores con `&` se escriben desde Python o
un fichero, no por el intérprete de órdenes.

**6. El directorio de trabajo temporal se vació a media faena**, con el backend ya parado,
llevándose scripts y volcados. Se recuperó porque la contraseña del proyecto nuevo estaba
guardada en Parameter Store. **Nada que haga falta para terminar puede vivir solo en `/tmp`.**

## Vuelta atrás, si hiciera falta

Parameter Store conserva el historial: la **versión 1** de `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` y `APP_ARCHIVOS_SUPABASE_CLAVE`
son los valores de Oregon. Restaurarlos y redesplegar devuelve el sistema al proyecto viejo,
que **sigue encendido e intacto**.

## Lo que queda pendiente

- **Cambiar la contraseña del proyecto viejo.** Se filtró en un registro
  de sesión, y ese proyecto sigue vivo como vuelta atrás. La cambias tú desde el panel.
- **Apagar o borrar el proyecto de Oregon** cuando haya confianza suficiente. Hasta entonces
  paga compute en la cuenta Pro.
- El nombre del proyecto nuevo es `renaser-reclutamiento`, que sí dice lo que es.
