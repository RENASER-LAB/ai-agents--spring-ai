# APIs del perfil del candidato · para quien construya las pantallas

> ✅ **Implementado desde el 25/08/2026.** La referencia viva es **Swagger**
> (`/swagger-ui.html`), que se genera del código y siempre está al día. Este documento cuenta
> lo que Swagger no cuenta: las reglas.

El diseño completo —requisitos, historias de usuario y tablas— está en
[PROPUESTA-PERFIL-DEL-CANDIDATO.md](PROPUESTA-PERFIL-DEL-CANDIDATO.md).

---

## Lo que hay que entender antes de dibujar nada

**El perfil es de la persona, no de la vacante ni de la empresa.** Uno solo, que el candidato
llena una vez y le sirve para postular a cualquier vacante y a cualquier empresa. No hay
«perfil para esta convocatoria».

**Se llena solo con el currículum, pero el candidato manda.** Al subir el CV, el sistema lo lee
y rellena lo que puede. Cada dato sabe si lo escribió la persona o salió del archivo, y **la
pantalla tiene que enseñar esa diferencia**. Es el punto que más se nota en el uso.

**Nada es obligatorio.** Se puede postular con el perfil vacío, y eso no puede bloquear ningún
botón.

**El perfil no puntúa.** No sale en el ranking ni cambia notas. Si la pantalla lo pinta junto a
la nota, sugiere lo contrario: conviene separarlos visualmente.

---

## Las dos puertas

Las mismas de siempre (ver [09-APIS.md](09-APIS.md)):

| Puerta | Quién | Token |
|---|---|---|
| `/api/v1/portal/perfil/**` | El candidato, sobre **su** perfil | El suyo |
| `/api/v1/panel/postulaciones/{id}/perfil` | El equipo, de solo lectura | El de equipo |

Errores en RFC 7807, como el resto: `title`, `status` y `detail` en lenguaje normal.

---

## Portal · lo que usa el candidato

### Ver mi perfil

```
GET /api/v1/portal/perfil
```

Devuelve el perfil entero. **Si nunca ha llenado nada, responde 200 con todo vacío**, no 404: la
pantalla siempre tiene algo que pintar.

```json
{
  "titular": "Analista de procesos",
  "resumen": "Ocho años ordenando operaciones...",
  "habilidades": ["Excel avanzado", "Power BI", "Gestión de procesos"],
  "experienciaMeses": 96,
  "ubicacion": "Arequipa, Perú",
  "disponibilidad": "Inmediata",
  "pretension": { "min": 3500, "max": 4200, "moneda": "PEN" },
  "experiencia": [
    {
      "id": 12,
      "puesto": "Analista senior",
      "empresa": "Clínica San Juan",
      "desde": "2022-03-01",
      "hasta": null,
      "descripcion": "...",
      "origen": "PERSONA",
      "confirmado": true
    }
  ],
  "educacion": [
    {
      "id": 3,
      "titulo": "Ingeniería Industrial",
      "institucion": "UNSA",
      "nivelCodigo": "TITULADO",
      "desde": "2014-03-01",
      "hasta": "2019-12-01",
      "enCurso": false,
      "origen": "CURRICULUM",
      "confirmado": false
    }
  ],
  "idiomas":       [{ "id": 1, "idioma": "Inglés", "nivelCodigo": "B2", "origen": "PERSONA", "confirmado": true }],
  "certificaciones": [
    { "id": 5, "nombre": "BLS", "entidad": "AHA", "emitidaEn": "2024-05-01",
      "venceEn": "2026-05-01", "origen": "PERSONA", "confirmado": true }
  ],
  "enlaces": [{ "id": 8, "tipo": "LINKEDIN", "url": "https://linkedin.com/in/..." }],
  "lecturaCv": { "estado": "LISTA", "actualizadoEn": "2026-08-24T10:00:00Z" }
}
```

**`hasta: null` significa «sigo aquí»**, no que falte el dato. Píntalo como «Actualidad».

### Editar la parte de arriba

```
PUT /api/v1/portal/perfil
```

Manda solo lo que cambia: `titular`, `resumen`, `habilidades`, `experienciaMeses`, `ubicacion`,
`disponibilidad`, `pretension`.

La pretensión es **todo o nada**: o mandas `min`, `max` y `moneda`, o mandas `null`. Un mínimo
suelto sin moneda da **400**.

### Las listas

Mismo patrón para las cinco. `{lista}` es `experiencia`, `educacion`, `idiomas`,
`certificaciones` o `enlaces`:

```
POST   /api/v1/portal/perfil/{lista}          crear
PUT    /api/v1/portal/perfil/{lista}/{id}     editar
DELETE /api/v1/portal/perfil/{lista}/{id}     borrar
```

**Editar un elemento lo convierte en «escrito por mí»** (`origen: PERSONA`,
`confirmado: true`). No hace falta decirlo aparte.

### Confirmar lo que leyó el sistema

```
POST /api/v1/portal/perfil/{lista}/{id}/confirmacion
```

Para cuando el dato está bien y el candidato solo quiere validarlo sin editarlo. Pasa a
`confirmado: true` conservando `origen: CURRICULUM`, que es información útil: se sabe que salió
del archivo y que la persona lo dio por bueno.

### Reordenar

```
PUT /api/v1/portal/perfil/experiencia/orden
{ "ids": [12, 8, 3] }
```

Solo en experiencia y educación. El orden que mande la pantalla es el que se guarda.

### Descargar mis datos

```
GET /api/v1/portal/perfil/descarga
```

Un JSON con todo, para el derecho de acceso de la ley 29733. La pantalla lo ofrece como
descarga de archivo.

---

## La lectura del currículum · lo que más afecta a la pantalla

El CV **se sube al postular**, como hoy (`POST /api/v1/portal/postulaciones`). No hay endpoint
aparte para subirlo al perfil.

**La lectura es asíncrona.** Postular responde al momento; la lectura tarda decenas de segundos
y va por una cola. Así que la pantalla **no puede esperarla**: postular tiene que terminar y
avisar después.

El campo `lecturaCv.estado` dice en qué punto está:

| Estado | Qué pinta la pantalla |
|---|---|
| `SIN_CV` | Aún no ha subido ninguno |
| `EN_CURSO` | «Estamos leyendo tu currículum» — conviene refrescar cada pocos segundos |
| `LISTA` | Ya está: si aparecieron datos nuevos sin confirmar, invítale a revisarlos |
| `NO_LEGIBLE` | **El archivo no se pudo leer** (escaneado, una imagen). El perfil se queda como estaba |

`NO_LEGIBLE` **no es un error**: el sistema prefiere no leer nada antes que inventarse datos. La
pantalla debe decirlo sin alarmar y sugerir llenarlo a mano.

---

## Lo que la pantalla tiene que respetar

**1. Distinguir el origen de cada dato.** Es la regla que más importa. Un dato con
`origen: CURRICULUM` y `confirmado: false` **no lo ha dicho la persona**: lo dedujo un modelo de
lenguaje y puede estar mal. Enséñalo distinto —una marca, un color, lo que sea— y ofrécele
confirmarlo o corregirlo.

**2. Nunca pises lo que él escribió.** Si vuelve a subir un CV, lo que ya tenía escrito o
confirmado se conserva. Eso lo garantiza el backend, pero la pantalla no debe sugerir lo
contrario con un mensaje tipo «se reemplazarán tus datos».

**3. La pretensión salarial es delicada.** En el portal es suya y la ve siempre. **En el panel
solo la ve quien tenga permiso**, y nunca aparece en listas ni rankings. Si construyes el panel,
no la pintes junto a la nota.

**4. Explica los niveles de idioma.** El catálogo es A1-C2 y **mucha gente no sabe cuál es el
suyo**. Cada opción necesita una línea que lo explique («B2 · me manejo en una reunión de
trabajo»), o la mitad elegirá al azar y el dato no valdrá nada.

**5. El perfil vacío es normal.** Ningún botón se bloquea por no tener perfil, y postular sigue
funcionando igual que hoy.

**6. Avisa de las certificaciones vencidas.** `venceEn` en el pasado importa de verdad en salud
—colegiatura, primeros auxilios—, y es más útil verlo en la pantalla que descubrirlo tarde.
`venceEn: null` significa que no caduca.

---

## Panel · lo que ve el equipo

```
GET /api/v1/panel/postulaciones/{id}/perfil
```

Solo lectura, con el permiso `ver_perfil_candidato`. Devuelve lo mismo que el portal **menos la
pretensión salarial**, salvo que además se tenga `ver_pretension`.

**Un candidato sin perfil devuelve 200 con todo vacío**, no 404: la ficha no puede romperse por
eso.

---

## Los catálogos

```
GET /api/v1/portal/catalogos/niveles-educativos
GET /api/v1/portal/catalogos/niveles-idioma
```

Devuelven `codigo`, `nombre` y `orden`. **No escribas estos valores a mano en el frontend**: es
lo que ya se desincronizó una vez en este proyecto.

- Educativos: `SECUNDARIA`, `TECNICA`, `UNIVERSITARIA`, `TITULADO`, `MAESTRIA`, `DOCTORADO`
- Idioma: `A1`, `A2`, `B1`, `B2`, `C1`, `C2`, `NATIVO`

---

## Errores que vas a ver

| Código | Cuándo | Qué hacer |
|---|---|---|
| **400** | Pretensión a medias, fecha `hasta` anterior a `desde`, enlace que no es una dirección o un LinkedIn que no es de LinkedIn | Enseñar el `detail`, que viene en lenguaje normal |
| **401** | Token vencido | Volver a entrar |
| **403** | Sin permiso (panel) | No pintar la sección |
| **404** | El elemento de la lista no existe o no es suyo | Refrescar |
| **409** | Enlace repetido del mismo tipo | Decir que ya lo tiene |

---

## Lo que **no** va a existir

Para que no se diseñe encima:

- **Buscar candidatos por habilidad.** Las habilidades son texto libre; no hay endpoint de
  búsqueda y no lo habrá en esta versión.
- **Foto de perfil.** Va en contra de que el currículum se anonimice antes de que lo lea la IA,
  que existe para no sesgar por edad, sexo o aspecto.
- **Referencias laborales.** Son datos personales de terceros que no han consentido nada.
- **Varios currículums.** Solo se conserva el último.
- **Que el perfil sustituya al currículum al postular.** El archivo sigue siendo obligatorio.
