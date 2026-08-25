# Propuesta · El perfil del candidato

**Esto es una propuesta, no un requisito del cliente.** Los RF llevan numeración nueva (RF-155
en adelante) pero **no entran en `01-REQUISITOS-FUNCIONALES.md` hasta que Renaser los valide**:
ese documento es suyo.

Va **después del MVP**: el embudo ya está completo y esto es mejora, no cierre de alcance.

---

## Qué se quiere

Hoy el candidato sube un currículum por cada vacante y ahí muere. Si postula a tres, se le lee
el archivo tres veces y sus datos quedan en tres sitios sin relación. No hay forma de decir
«este es Juan y esta es su experiencia».

La idea es que **el candidato tenga un perfil**: se llena solo al subir el currículum, él lo
corrige, y se reutiliza en cada postulación.

## Lo que ya existe y no hay que construir

| Pieza | Dónde está |
|---|---|
| Leer el PDF o el Word | `ServicioTextoCv` |
| Sacar habilidades, experiencia, puesto y educación del texto | El agente `DATOS_CV`, ya implementado |
| Guardar esos datos | `dato_cv` — pero atada a la postulación |
| LinkedIn, GitHub y portafolio | El endpoint de postular ya los recibe; van a `enlace_cv` |
| Nombre, apellidos, teléfono, documento | `persona` |

**El «subes el CV y se llena tu perfil» ya funciona.** Lo que falta es el perfil.

---

## La decisión de fondo: el perfil es de la persona

**El perfil cuelga de `persona`, no de `usuario`.** Es lo que más condiciona todo lo demás.

En este modelo `usuario` existe **una vez por organización** (`UNIQUE (persona_id,
organizacion_id)`): la misma persona que postula a dos empresas tiene dos usuarios. `persona`
es la persona física y **no lleva organización**.

Si el perfil colgara de `usuario`, cada empresa vería un perfil distinto de la misma persona y
el candidato tendría que llenarlo una vez por empresa. Es justo lo que se quiere evitar.

Colgando de `persona` sale el modelo de Computrabajo o Indeed: **una persona, un perfil, muchas
empresas**. Se llena una vez y sirve para postular donde sea.

> ⚠️ Esto encaja con que en el futuro varias empresas creen sus propias vacantes: el perfil ya
> queda en el sitio correcto y no habrá que migrarlo otra vez.

---

## Los requisitos

**RF-155** Cada persona tiene **un perfil y solo uno**, que vive con ella y no con ninguna
postulación ni ninguna empresa. Se conserva entre convocatorias y entre organizaciones.

**RF-156** El perfil recoge: titular, resumen, habilidades, experiencia laboral, formación,
idiomas, certificaciones, enlaces, ubicación, disponibilidad y pretensión salarial. **Nada de
eso es obligatorio para postular**: el currículum sigue siendo lo que se evalúa.

**RF-157** El candidato puede **editar y borrar** cualquier parte de su perfil en cualquier
momento, sin pedir permiso a nadie.

**RF-158** Al subir un currículum, el sistema lee sus datos con el agente `DATOS_CV` y los
**propone** para el perfil. No los escribe directamente.

**RF-159** Lo que el candidato escribió o confirmó **nunca se sobrescribe** con lo que la IA
leyó después. Una extracción que pise un dato corregido a mano convierte una herramienta útil
en una que hay que vigilar.

**RF-160** Cada dato del perfil sabe de dónde vino —**escrito por la persona** o **leído del
currículum**— y si la persona lo confirmó. Un dato leído y no confirmado se enseña como tal.

**RF-161** El currículum se lee **una vez por archivo**, no una vez por postulación. Cada
lectura es una llamada al modelo y se paga.

**RF-162** Del currículum se conserva **solo el último**. Al subir uno nuevo, el anterior deja
de ser el vigente.

**RF-163** La ficha del candidato en el panel muestra su perfil, y distingue a simple vista lo
que la persona escribió de lo que se sacó de su currículum.

**RF-164** El perfil **no puntúa**. No entra en ninguna nota ni en el orden del ranking.
Cambiarlo después de una evaluación **no altera ninguna nota ya puesta**.

**RF-165** La **pretensión salarial** solo la ve quien tenga el permiso `ver_pretension`, y
**nunca aparece en el ranking ni en las listas**. Si apareciera junto a la nota pesaría en la
decisión, que es justo lo que este sistema busca evitar.

**RF-166** Los **enlaces se validan**: que la dirección tenga forma de dirección, y que la de
LinkedIn sea de LinkedIn y la de GitHub de GitHub. Un enlace que no cumple no se guarda.

**RF-167** El candidato puede **descargar sus datos** en un archivo legible: es el derecho de
acceso de la ley 29733 y hoy no tiene por dónde ejercerse.

**RF-168** Un perfil sin actividad se conserva el tiempo que diga el parámetro
`meses_conservar_perfil`, y después se anonimiza. Arranca en **24 meses**.

**RF-169** El perfil es un dato personal permanente y **no cabe en el consentimiento actual**,
que cubre una postulación concreta. Antes de encenderlo hace falta un texto que diga qué se
guarda, cuánto tiempo y para qué.

**RF-170** Al ejercer el derecho de borrado, el perfil se borra con lo demás. La postulación ya
evaluada conserva lo suyo, que es lo que sostiene una decisión tomada.

---

## Las historias de usuario

### HU-1 · Se me llena el perfil con mi currículum

> **Como** candidato que ya subió su currículum,
> **quiero** que mis datos aparezcan solos en mi perfil,
> **para** no volver a escribir lo que ya está en el archivo.

- Al subir un currículum se lee y salen titular, resumen, habilidades, experiencia y formación.
- Lo leído aparece **marcado como sacado del currículum**, no como escrito por mí.
- Si el archivo no se puede leer —escaneado, una imagen— **no se inventa nada**: el perfil se
  queda como estaba y se me dice que no se pudo leer.
- La lectura no me hace esperar: postulo igual y el perfil se completa después.

### HU-2 · Corrijo lo que el sistema entendió mal

> **Como** candidato,
> **quiero** editar o borrar cualquier cosa de mi perfil,
> **para** arreglar lo que se leyó mal y añadir lo que falta.

- Puedo editar y borrar cada campo, cada experiencia y cada estudio, sin pedir permiso.
- Al guardar un cambio, ese dato pasa a estar **escrito por mí**.
- Puedo confirmar un dato que sacó el sistema sin cambiarlo, y entonces también pasa a ser mío.

### HU-3 · Lo que escribí no se me borra

> **Como** candidato que corrigió su perfil,
> **quiero** que subir otro currículum no pise lo que escribí,
> **para** no tener que corregir lo mismo cada vez.

- Si el currículum nuevo trae un dato que yo ya había escrito o confirmado, **se conserva el
  mío**.
- Lo que trae y yo no tenía entra marcado como sacado del currículum.
- **Este es el criterio que más importa**: una herramienta que pisa lo corregido a mano deja de
  ahorrar trabajo y pasa a darlo.

### HU-4 · Enseño mis enlaces

> **Como** candidato,
> **quiero** poner mi LinkedIn, mi GitHub y mi portafolio,
> **para** que se vea lo que he hecho y no solo lo que digo.

- Puedo añadir varios enlaces y decir de qué tipo es cada uno.
- No puedo guardar dos veces el mismo enlace del mismo tipo.
- Si pongo algo que no es una dirección, o una de LinkedIn que no lo es, **no se guarda**.
- Los enlaces que ya mandé al postular aparecen aquí, sin volver a escribirlos.

### HU-5 · No repito nada en la siguiente vacante

> **Como** candidato que ya postuló antes,
> **quiero** postular a otra vacante sin llenar nada de nuevo,
> **para** que me cueste un minuto y no media hora.

- Mi perfil es el mismo en todas las convocatorias **y en todas las empresas**.
- Si mi currículum no ha cambiado, **no se vuelve a leer**: cada lectura cuesta dinero.
- Puedo postular con el perfil vacío. **Nada del perfil es obligatorio.**

### HU-6 · Digo cuánto quiero ganar sin que me penalice

> **Como** candidato,
> **quiero** poner mi pretensión como un rango,
> **para** no cerrarme una puerta con una cifra exacta antes de saber del puesto.

- Pongo un mínimo, un máximo y la moneda.
- **Solo lo ve quien tenga permiso**, y no sale en las listas ni en el ranking.
- Puedo dejarlo vacío.

### HU-7 · Leo a un candidato sin abrir su archivo

> **Como** persona del equipo,
> **quiero** ver la trayectoria del candidato en su ficha,
> **para** hacerme una idea sin descargar el currículum.

- La ficha muestra titular, resumen, habilidades, experiencia ordenada, formación, idiomas y
  certificaciones.
- **Se distingue a simple vista** lo que la persona escribió de lo que se sacó de su currículum.
- Un candidato sin perfil se ve igual que hoy: la ficha no se rompe ni queda a medias.

### HU-8 · El perfil no decide nada

> **Como** responsable de una vacante,
> **quiero** que el perfil no cuente para la nota,
> **para** que nadie avance por escribir bien sobre sí mismo.

- El perfil **no entra en ninguna nota** ni cambia el orden del ranking.
- Cambiar el perfil después de ser evaluado **no altera ninguna nota ya puesta**.

### HU-9 · Me llevo mis datos o me borro entero

> **Como** candidato,
> **quiero** descargar lo que tenéis de mí, o borrarlo,
> **para** ejercer lo que la ley me reconoce.

- Puedo descargar mi perfil completo en un archivo legible.
- Al pedir el borrado, el perfil se borra con todo lo demás.
- Lo que ya se evaluó conserva lo suyo: una decisión tomada no se queda sin su sustento.

---

## Las tablas

Seis nuevas, dos catálogos y **ningún cambio en las que ya hay**. Todo cuelga de `persona`.

### `perfil_candidato`

```sql
CREATE TABLE perfil_candidato (
    id                bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    -- De la persona, no del usuario: el usuario existe una vez por organizacion y el
    -- perfil tiene que ser el mismo en todas. Ver «La decision de fondo».
    persona_id        bigint NOT NULL UNIQUE REFERENCES persona(id),
    titular           text,
    resumen           text,
    -- Como en dato_cv: separadas por «|». No se consultan por habilidad, se enseñan.
    -- El dia que haya que buscar «quien sabe Excel», esto pasa a su propia tabla.
    habilidades       text,
    -- Lo declara la persona; no se calcula sumando la experiencia de abajo, porque los
    -- periodos se solapan y restarlos bien es un problema que no paga la pena.
    experiencia_meses integer,
    ubicacion         text,
    disponibilidad    text,
    -- Un rango y no un monto: una cifra exacta se lee como ultimatum y obliga al
    -- candidato a comprometerse antes de saber nada del puesto.
    pretension_min    numeric(12,2),
    pretension_max    numeric(12,2),
    pretension_moneda text CHECK (pretension_moneda IN ('PEN', 'USD')),
    actualizado_en    timestamptz NOT NULL DEFAULT now(),
    creado_en         timestamptz NOT NULL DEFAULT now(),
    CHECK (pretension_max IS NULL OR pretension_min IS NULL OR pretension_max >= pretension_min),
    -- O el rango entero o nada: un minimo suelto sin moneda no significa nada.
    CHECK (num_nonnulls(pretension_min, pretension_max, pretension_moneda) IN (0, 3))
);
```

### `experiencia_perfil`

```sql
CREATE TABLE experiencia_perfil (
    id                  bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    perfil_candidato_id bigint NOT NULL REFERENCES perfil_candidato(id),
    puesto              text NOT NULL,
    empresa             text NOT NULL,
    desde               date NOT NULL,
    -- Vacio significa «sigo aqui». Es la forma de decirlo sin una bandera aparte que
    -- pueda contradecir a la fecha.
    hasta               date,
    descripcion         text,
    origen              text NOT NULL CHECK (origen IN ('PERSONA', 'CURRICULUM')),
    confirmado_en       timestamptz,
    orden               integer NOT NULL,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    CHECK (hasta IS NULL OR hasta >= desde)
);
```

### `educacion_perfil`

```sql
CREATE TABLE educacion_perfil (
    id                  bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    perfil_candidato_id bigint NOT NULL REFERENCES perfil_candidato(id),
    titulo              text NOT NULL,
    institucion         text NOT NULL,
    nivel_codigo        text REFERENCES nivel_educativo(codigo),
    desde               date,
    hasta               date,
    en_curso            boolean NOT NULL DEFAULT false,
    origen              text NOT NULL CHECK (origen IN ('PERSONA', 'CURRICULUM')),
    confirmado_en       timestamptz,
    orden               integer NOT NULL,
    creado_en           timestamptz NOT NULL DEFAULT now()
);
```

### `idioma_perfil`

```sql
CREATE TABLE idioma_perfil (
    id                  bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    perfil_candidato_id bigint NOT NULL REFERENCES perfil_candidato(id),
    idioma              text NOT NULL,
    nivel_codigo        text NOT NULL REFERENCES nivel_idioma(codigo),
    origen              text NOT NULL CHECK (origen IN ('PERSONA', 'CURRICULUM')),
    confirmado_en       timestamptz,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (perfil_candidato_id, idioma)
);
```

### `certificacion_perfil`

```sql
CREATE TABLE certificacion_perfil (
    id                  bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    perfil_candidato_id bigint NOT NULL REFERENCES perfil_candidato(id),
    nombre              text NOT NULL,
    entidad             text,
    emitida_en          date,
    -- Vacio = no caduca. Muchas si lo hacen —colegiatura, primeros auxilios, seguridad—
    -- y en salud eso decide si alguien puede trabajar o no.
    vence_en            date,
    origen              text NOT NULL CHECK (origen IN ('PERSONA', 'CURRICULUM')),
    confirmado_en       timestamptz,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    CHECK (vence_en IS NULL OR emitida_en IS NULL OR vence_en >= emitida_en)
);
```

### `enlace_perfil`

```sql
CREATE TABLE enlace_perfil (
    id                  bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    perfil_candidato_id bigint NOT NULL REFERENCES perfil_candidato(id),
    tipo                text NOT NULL
        CHECK (tipo IN ('LINKEDIN', 'GITHUB', 'PORTAFOLIO', 'PUBLICACION', 'PRODUCTO', 'OTRO')),
    url                 text NOT NULL,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (perfil_candidato_id, tipo, url)
);
```

### Los dos catálogos

⚠️ **Ninguno existe todavía.** Comprobado contra las 35 migraciones: el único parecido es
`nivel_puesto`, que es otra cosa (el nivel del puesto, no el de estudios).

```sql
CREATE TABLE nivel_educativo (
    codigo    text PRIMARY KEY,
    nombre    text NOT NULL,
    orden     integer NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now()
);
-- SECUNDARIA, TECNICA, UNIVERSITARIA, TITULADO, MAESTRIA, DOCTORADO

-- El marco europeo, de A1 a C2. Mas preciso que «basico / intermedio / avanzado» y es lo
-- que usan las certificaciones de idiomas.
CREATE TABLE nivel_idioma (
    codigo    text PRIMARY KEY,   -- A1, A2, B1, B2, C1, C2, NATIVO
    nombre    text NOT NULL,
    orden     integer NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now()
);
```

> Sobre A1-C2: mucha gente no sabe su nivel en esa escala. **La pantalla tendrá que explicar
> cada uno en una línea** («B2 · me manejo en una reunión de trabajo»), o la mitad elegirá al
> azar y el dato no servirá para nada.

### Índices

```sql
CREATE INDEX experiencia_perfil_idx   ON experiencia_perfil   (perfil_candidato_id, orden);
CREATE INDEX educacion_perfil_idx     ON educacion_perfil     (perfil_candidato_id, orden);
CREATE INDEX idioma_perfil_idx        ON idioma_perfil        (perfil_candidato_id);
CREATE INDEX certificacion_perfil_idx ON certificacion_perfil (perfil_candidato_id);
CREATE INDEX enlace_perfil_idx        ON enlace_perfil        (perfil_candidato_id);
```

---

## La migración de lo que ya hay

`dato_cv` tiene datos de quienes ya postularon. **Se migran**, con un cuidado: `dato_cv` es
**por postulación**, así que una misma persona puede tener varias filas.

Regla: por cada persona se toma **la más reciente**. Lo que entra queda con
`origen = CURRICULUM` y **sin confirmar**, porque nadie lo ha revisado.

```sql
INSERT INTO perfil_candidato (persona_id, resumen, habilidades, experiencia_meses)
SELECT DISTINCT ON (u.persona_id)
       u.persona_id, d.perfil_resumen, d.habilidades, d.experiencia_meses_total
  FROM dato_cv d
  JOIN postulacion p ON p.id = d.postulacion_id
  JOIN usuario     u ON u.id = p.usuario_id
 ORDER BY u.persona_id, d.actualizado_en DESC;
```

Y la experiencia, que en `dato_cv` es un solo empleo:

```sql
INSERT INTO experiencia_perfil (perfil_candidato_id, puesto, empresa, desde, origen, orden)
SELECT pc.id, d.ultimo_puesto, d.ultima_empresa,
       -- dato_cv no guarda fechas, solo cuantos meses duro: se reconstruye hacia atras
       -- desde la fecha del dato. Es una aproximacion, y por eso entra sin confirmar.
       (d.actualizado_en - make_interval(months => coalesce(d.ultima_meses_duracion, 0)))::date,
       'CURRICULUM', 1
  FROM dato_cv d
  JOIN postulacion p       ON p.id = d.postulacion_id
  JOIN usuario     u       ON u.id = p.usuario_id
  JOIN perfil_candidato pc ON pc.persona_id = u.persona_id
 WHERE d.ultimo_puesto IS NOT NULL AND d.ultima_empresa IS NOT NULL;
```

> ⚠️ **La fecha de inicio se deduce de la duración**, porque `dato_cv` no guarda fechas. Por eso
> todo lo migrado entra **sin confirmar**: el candidato lo verá como «sacado de tu currículum» y
> podrá corregirlo. **No se puede presentar como si lo hubiera escrito él.**

`dato_cv` **no se borra**: la criba y el ranking la siguen usando, y las postulaciones ya
evaluadas dependen de ella.

---

## Lo que se propone y aún no se guarda

| | Cómo | Qué cuesta |
|---|---|---|
| **A. Con `origen` y `confirmado_en`** (lo de arriba) | Lo leído entra sin confirmar; el candidato confirma o borra | Nada nuevo. Mientras no confirme, su perfil tiene filas que él no escribió |
| **B. Una tabla de propuestas aparte** | Lo leído espera en `propuesta_perfil` hasta aceptarse | Una tabla más y el doble de endpoints. A cambio, el perfil solo tiene lo aceptado |

**Se propone la A.** La B solo compensa si el equipo fuera a decidir mirando el perfil, y el
RF-164 dice justo lo contrario.

---

## Lo que no entra

- **Buscar candidatos por habilidad.** Obliga a sacar las habilidades a su tabla y a acordar un
  vocabulario; es otro trabajo.
- **Que el perfil valga como postulación** sin currículum. Hoy el currículum es lo que se
  califica, y quitarlo cambia el embudo entero.
- **Foto y referencias laborales.** La foto va contra la anonimización del currículum, que
  existe para no sesgar por edad, sexo o aspecto. Las referencias son datos personales de
  terceros que no han consentido nada.
- **Las pantallas.** Van aparte, con su propio documento de APIs para quien las construya.
