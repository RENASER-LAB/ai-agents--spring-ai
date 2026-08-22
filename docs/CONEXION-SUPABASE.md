# Conectar la base de datos a Supabase — retirado el 21/08/2026

Este documento explicaba cómo arrancar tu backend local contra la base de datos de Supabase,
con el perfil `supabase`. **Ese perfil ya no existe** y el documento se ha vaciado a propósito,
en vez de borrarlo, porque hay enlaces viejos que apuntan aquí.

## Por qué se retiró

Cuando se escribió, la idea era buena: que el equipo entero viera los mismos datos sin levantar
Docker. Eso da por hecho que existe una base de Supabase de desarrollo.

No la hay. **Hay un solo proyecto Supabase, y es el que atiende a los candidatos reales.** Con
eso, «que el equipo vea los mismos datos» pasa a ser «que cualquiera trabaje contra producción
desde su portátil», que es justo lo que no queremos.

Y no era solo cuestión de leer o escribir datos por error. El perfil heredaba
`flyway.enabled: true` de la configuración base, así que arrancarlo desde una rama en curso
**aplicaba migraciones a medio hacer a la base de verdad**, y quedaban registradas allí.

## Qué se borró

- `src/main/resources/application-supabase.yaml`
- El bloque `renaser.supabase.db` de `application-secrets.yaml.example`

Si tu `application-secrets.yaml` todavía tiene `renaser.supabase.db.host`, `.user` y
`.password`, **quítalos**: son las credenciales de la base de producción y ya no los usa nadie.
Borrar el perfil no los toca.

## Qué usar ahora

Para trabajar en tu máquina, nada: `local` es el perfil por defecto y ya deja la base en el
contenedor del `docker-compose`. Está explicado en el [README](../README.md#los-perfiles).

El servidor desplegado sigue guardando en Supabase, pero por otro camino: el perfil `pruebas`,
que recibe la conexión en variables de entorno desde el `.env` de la máquina. Ver
[CI/CD](CI-CD.md).

## Si algún día hace falta de verdad

El caso legítimo —un entorno compartido para el equipo— vuelve a tener sentido en cuanto exista
un **segundo** proyecto Supabase, separado del de producción. Ese día, lo que hay que recuperar
está en la historia de git:

```bash
git log --oneline --diff-filter=D -- src/main/resources/application-supabase.yaml
```

Y con él, dos cosas que la versión anterior no tenía y debería: `flyway.enabled: false`, para
que ninguna máquina pueda tocar el esquema compartido, y un nombre que diga a qué base apunta.
