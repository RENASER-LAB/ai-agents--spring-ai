# Conectar la base de datos a Supabase

Hoy el backend guarda todo en un Postgres que corre en tu propia máquina, dentro de Docker,
en el puerto 5433. Este documento explica cómo hacer que en lugar de eso guarde todo en
**Supabase**, que es el mismo Postgres pero alojado en internet.

Sirve para que la base deje de vivir en un solo computador: el equipo entero ve los mismos
datos y no hay que levantar Docker para trabajar.

---

## Qué cambia y qué no

| | Antes | Después |
|---|---|---|
| Base de datos | Docker, `localhost:5433` | Supabase, en internet |
| Quién crea las tablas | Flyway | Flyway, igual |
| RabbitMQ | Docker, local | Docker, local (no cambia) |
| Archivos y currículums | Disco de tu máquina | Disco de tu máquina (no cambia) |
| Los agentes de IA | Leen de otro Supabase por su API | Igual (no cambia) |

Ese último punto confunde y conviene tenerlo claro: **hay dos Supabase distintos en juego**.
El de los agentes de IA (`ai/supabase/`) es de RENASER OS, se lee por su API REST y no tiene
nada que ver con este cambio. El nuevo es la base de datos del backend, y se habla con ella
por el puerto de Postgres de siempre.

**El cambio no es obligatorio para nadie.** Se enciende con un perfil llamado `supabase`.
Quien no lo use sigue con su Docker de siempre, sin enterarse.

---

## Los pasos

### 1. Prepara el proyecto de Supabase

Entra al dashboard de tu proyecto, abre el **SQL Editor**, pega el contenido de
[`scripts/supabase-preparar.sql`](../scripts/supabase-preparar.sql) y ejecútalo.

Ese script hace dos cosas. Instala las extensiones que el proyecto necesita, y **cierra la
puerta pública de Supabase**.

Lo segundo importa mucho. Supabase publica solo, sin que nadie se lo pida, una API REST con
todo lo que viva en el esquema `public`, y la clave que la abre (`anon`) es pública a
propósito. Aquí van a vivir nombres, correos, teléfonos y currículums de candidatos reales.
El backend no necesita esa API para nada, así que se cierra antes de que exista la primera
tabla.

El script termina con dos comprobaciones. La primera tiene que devolver **0**: si devuelve
otra cosa, hay algo creado en `public` y el paso 4 va a fallar, y el propio script explica
cómo vaciarlo. La segunda tiene que devolver **false** en las dos columnas.

Falta un cierre más que no se puede hacer por SQL: **Project Settings → API → Exposed
schemas**, y quitar `public` de la lista. Eso apaga la API pública de raíz, sin depender de
los permisos.

### 2. Copia los datos de conexión

En el dashboard, botón **Connect**, pestaña **Session pooler**. De ahí salen tres cosas: el
host, el usuario y el puerto.

⚠️ **Session pooler, no Transaction pooler.** El de transacciones usa el puerto 6543 y no
soporta las consultas preparadas ni los bloqueos que Flyway necesita para migrar. Con ese, la
aplicación falla al arrancar y el error no dice por qué.

### 3. Rellena el archivo de claves

Si no lo tienes todavía:

```bash
cp application-secrets.yaml.example application-secrets.yaml
```

Y rellena `renaser.supabase.db.host`, `.user` y `.password`. La contraseña es la que elegiste
al crear el proyecto; si no la recuerdas, se cambia en **Project Settings → Database → Reset
password**.

Ese archivo está en `.gitignore` y nunca se sube.

### 4. Arranca con el perfil

```bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=supabase"
```

⚠️ **Las comillas no sobran.** Sin ellas, PowerShell parte el argumento en dos por el guion
de `spring-boot`: le pasa a Maven `-Dspring-boot` por un lado y `.run.profiles=supabase` por
otro, y Maven responde `Unknown lifecycle phase ".run.profiles=supabase"`. En Linux o en Git
Bash el comando es `./mvnw`, y las comillas ahí no estorban.

La primera vez tarda más de lo normal: Flyway crea las 34 tablas y siembra los datos base, y
todo eso viaja por internet en lugar de quedarse en tu máquina.

### 5. Comprueba que funcionó

En el SQL Editor de Supabase:

```sql
select count(*) from information_schema.tables where table_schema = 'public';
```

Tienen que salir alrededor de **35** tablas: las 34 del hito 1 más la de historial de Flyway.
Si sale 0, la aplicación no llegó a conectarse; mira el error en la consola.

---

## Cómo volver atrás

Arranca sin el perfil:

```bash
.\mvnw.cmd spring-boot:run
```

Vuelves al Docker local. No se borra nada de Supabase, y los dos pueden convivir.

---

## Lo que conviene saber antes

**La base ya no está en tu máquina.** Cada consulta cruza internet. Una pantalla que hace
veinte consultas pequeñas se nota lenta de una forma que en local no se notaba. No es un
problema de Supabase: es la distancia.

**El plan gratuito duerme el proyecto.** Después de una semana sin actividad, Supabase lo
pausa y hay que despertarlo a mano desde el dashboard. La aplicación mientras tanto no
arranca.

**Los tests no se ven afectados.** Levantan su propio Postgres desechable con Testcontainers
y seguirán haciéndolo.

**La contraseña de la base abre todo.** No pasa por Supabase Auth ni respeta ninguna regla de
seguridad de filas: es el usuario dueño de la base. Vive solo en `application-secrets.yaml`.
