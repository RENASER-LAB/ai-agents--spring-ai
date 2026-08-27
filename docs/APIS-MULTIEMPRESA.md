# APIs del multiempresa · para quien construya las pantallas

> ✅ **Implementado desde el 26/08/2026.** La referencia viva es **Swagger**
> (`/swagger-ui.html`), que se genera del código y siempre está al día. Este documento
> cuenta lo que Swagger no cuenta: qué cambia, qué rompe y qué reglas hay detrás.

El diseño completo está en `docs/superpowers/specs/2026-08-25-*.md` (seis piezas). El
detalle endpoint por endpoint, en [09-APIS.md](09-APIS.md).

---

## ⚠️ Lo que ROMPE el frontend actual

**`POST /portal/postulaciones` ahora exige `aceptaTratamiento=true`.** Sin ese parámetro
responde **400**. El formulario de postular necesita una casilla nueva: «Acepto que
[empresa] trate mis datos», con el texto que devuelve el endpoint nuevo de abajo. Es la ley
29733: el candidato consiente con **cada empresa** a la que postula, no solo con la
plataforma al crear su cuenta.

Es el único cambio que rompe. Todo lo demás es aditivo.

---

## El portal del candidato: qué cambia

**El tablón ahora mezcla empresas — a propósito.** `GET /portal/vacantes` devuelve las
vacantes publicadas de **todas** las empresas activas, y cada una trae `nombreEmpresa`.
Píntalo: el candidato debe saber a quién le está postulando. Las vacantes de una empresa
suspendida desaparecen del tablón **y su detalle da 404** — un enlace guardado puede dejar
de existir; la pantalla debe tolerarlo sin romperse.

**El flujo de postular gana un paso:**

1. `GET /portal/vacantes/{id}/consentimiento` (público) → el texto legal de la empresa de
   esa vacante: quién es, qué datos tratará, cuánto tiempo. Se muestra junto a la casilla.
2. `POST /portal/postulaciones` con `aceptaTratamiento=true` → la aceptación queda firmada
   (texto, versión, fecha, IP, navegador) a nombre de esa postulación.

**«Mis postulaciones» cruza empresas.** `GET /portal/postulaciones` sigue devolviendo todas
las del candidato, ahora cada una con su empresa. La cuenta es una sola (de la plataforma);
los procesos son de cada empresa.

Lo que NO cambia: crear cuenta, login del candidato, la evaluación, el perfil
([APIS-PERFIL-DEL-CANDIDATO.md](APIS-PERFIL-DEL-CANDIDATO.md)) — todo igual.

---

## El panel de empresas: el mundo nuevo

**Dos logins separados, dos pantallas.** El portal del candidato tiene el suyo (con
registro público, como siempre). El panel tiene el suyo propio y **sin registro**: las
cuentas nacen solo por invitación.

| Pantalla | Endpoint | Notas |
|---|---|---|
| Login del panel | `POST /panel/auth/login` `{correo, contrasena}` | Devuelve `{token, usuarioId}`. Mismo 401 genérico exista o no el correo; **401 con mensaje claro si la organización está suspendida**; 429 con `Retry-After` tras demasiados intentos |
| Aceptar invitación | `POST /panel/auth/invitacion` `{token, nombre, apellidos, contrasena}` | El token llega en el enlace del correo (`?token=...`); el frontend lo manda **en el cuerpo**, nunca en la URL de la API. Contraseña mínima **12**. Un solo uso; vencida/usada/revocada dan el mismo error genérico. Devuelve sesión: el invitado entra directo |
| Invitar al equipo | `POST /panel/usuarios/invitaciones` `{correo, roles}` | La respuesta trae la **URL del enlace** por si se quiere mostrar/copiar además del correo que sale solo. `GET` lista, `DELETE /{id}` revoca |

**No hay «olvidé mi contraseña» todavía.** No existe endpoint de recuperación: si alguien
la pierde, hoy el camino es que su administrador lo invite de nuevo. Está anotado como
pendiente — no diseñes la pantalla asumiendo que existe.

**El login de desarrollo (`dev-login`) está apagado en producción.** Ningún flujo del
frontend debe depender de él.

## El panel de la empresa: lo nuevo para su administrador

- **Textos legales**: `GET/POST /panel/textos-consentimiento`. El POST crea la versión
  nueva **y la publica**. Importa porque **publicar una vacante sin texto PROCESO publicado
  da error** con mensaje claro — la pantalla de vacantes debería avisarlo antes, no después.
- **Personalización de instrumentos**: `POST/DELETE /panel/organizacion/personalizacion/{instrumento}`
  (BANCO, PESOS, PLANTILLA_EVALUACION, PRUEBA). Apagada = usa el método de Renaser (solo
  lectura); encendida = copia propia editable. La pantalla debe distinguir «esto es de
  Renaser, se mira» de «esto es tuyo, se edita».
- El parámetro `tope_mensual_ia` **se ve pero no se edita** desde la empresa: lo administra
  la plataforma. No pintar el lápiz.

## La plataforma (solo Renaser)

Sección aparte del panel, visible solo con el permiso `administrar_plataforma` **y** siendo
la organización plataforma:

- `GET /panel/plataforma/empresas` — la ficha de cada empresa: estado, tope, banderas,
  consumo del mes. **No hay ni habrá** una vista de los candidatos de otra empresa: Renaser
  administra el continente, no el contenido.
- `POST /panel/plataforma/empresas` — alta con invitación al primer administrador.
- `POST …/{id}/suspension` y `…/reactivacion` (con motivo), `PUT …/{id}/tope-ia`,
  `POST/DELETE …/{id}/personalizacion/{instrumento}`.
- `GET /panel/plataforma/consumo?mes=YYYY-MM` — el gasto de IA por empresa y agente.

## Reglas transversales que la pantalla debe respetar

1. **Lo ajeno es 404, no 403.** Un recurso de otra empresa responde «no existe». No
   construyas lógica sobre distinguir «prohibido» de «inexistente».
2. **La calificación puede quedar «en espera» sin ser un error.** Si una empresa agota su
   tope mensual de IA, los trabajos nuevos esperan y el candidato ve su proceso «en curso»
   — que es la verdad. Ninguna pantalla debe pintar eso como fallo.
3. **Errores en RFC 7807** como todo el sistema: `title`, `status`, `detail` en lenguaje
   normal.
4. Un mismo correo puede existir como candidato y como equipo sin chocar: **son cuentas y
   puertas distintas**. No unifiques sesiones entre portal y panel.
