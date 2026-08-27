# Pieza A · Qué es de cada empresa y qué es de Renaser

Fecha: 2026-08-25 · Estado: aprobado en conversación, pendiente de revisión final

Primera pieza del cambio a plataforma multiempresa (modelo Indeed: cada empresa se
registra, crea sus vacantes y ve solo a sus candidatos). Esta pieza decide **de quién es
cada dato** — el reparto que da sentido a todo lo demás. Las piezas siguientes: B
(aislamiento), C (identidad y alta de empresas), D (el candidato ante varias empresas),
E (coste de la IA por empresa), F (Renaser como dueña de la plataforma).

## El contexto en dos frases

Hoy el sistema entero asume una sola empresa: Renaser contrata para Renaser. El esquema
ya reparte por organización los instrumentos (banco, plantillas, pesos, pruebas), pero
solo existe una organización y el equipo entra por RENASER OS, que solo conoce a Renaser.

---

## 1 · El reparto

### Con bandera de personalización (4 instrumentos)

Por defecto la bandera está **apagada**: la empresa no tiene nada propio y **lee el
instrumento de Renaser** (cero duplicación; una mejora de Renaser llega sola a todas).
Al encenderla se le copia el instrumento y pasa a ser suyo.

| Bandera | Instrumento que arrastra |
|---|---|
| `banco_propio` | El banco de preguntas completo (versiones, preguntas, opciones, rangos, casos, pares de consistencia, umbrales) |
| `pesos_propios` | Los pesos: por dimensión, criterio, componente y etapa |
| `plantillas_evaluacion_propias` | Las plantillas de evaluación y sus cuotas |
| `pruebas_puesto_propias` | Las pruebas del puesto (plantillas, versiones, preguntas, rúbricas) |

Las cuatro son independientes: una empresa puede tener pesos propios y seguir usando el
banco de Renaser. Eso funciona porque los pesos se calculan **por dimensión** y las
dimensiones son un catálogo global.

**Restricción que sostiene la independencia:** quien personaliza el banco puede añadir,
quitar y reescribir preguntas, pero solo **sobre las dimensiones que ya existen**. No se
inventan dimensiones nuevas — si se pudiera, los pesos compartidos dejarían de cubrir el
banco personalizado.

### Siempre propio, sin bandera

No admite compartirse entre empresas:

- Su equipo: usuarios, roles y permisos.
- Su organigrama: áreas y puestos.
- Sus parámetros (plazos, retención).
- Sus textos legales: consentimiento y política de conservación — la ley 29733 obliga a
  nombrar a quien trata los datos, y ese no es Renaser.
- Sus correos a candidatos: un correo firmado «Renaser» a un candidato de otra empresa
  es un error. Se siembran con copia al dar de alta.

### Nunca personalizable

- Los catálogos cerrados: niveles de puesto, familias, dimensiones, etapas, estados de
  la postulación, permisos.
- **Las instrucciones de la IA.** Los prompts llevan dentro las reglas que protegen al
  candidato (no inventar notas, ignorar edad/sexo — RF-41). Dejar que una empresa los
  edite es dejarle apagar esas reglas.

---

## 2 · La mecánica de la bandera

**Dónde viven.** Cuatro columnas booleanas en `organizacion`, `false` por defecto. Y
`organizacion.es_plataforma` (índice único parcial: solo una puede serlo) — Renaser deja
de estar quemada como texto en el código y pasa a ser un dato.

**Cómo se lee.** Un servicio único —el resolutor, `DuenoDelInstrumento`— contesta una
sola pregunta: «para esta organización y este instrumento, ¿de quién son las filas?».
Bandera apagada → las de la plataforma; encendida → las suyas. **Todas** las consultas
de instrumentos pasan por ahí; ningún repositorio decide por su cuenta.

**Encender = copiar, en una transacción.** Se copia la última versión PUBLICADA del
instrumento de la plataforma como versión 1 propia, también PUBLICADA — sin limbo. Cada
copia guarda `copiada_de_version_id` para saber de qué versión salió. Las evaluaciones
en vuelo no se ven afectadas: sus preguntas están fijadas por id (`orden_pregunta`).

**Apagar = volver a leer la de la plataforma.** Las filas propias se **archivan, no se
borran** (RF-138): las notas de los ya evaluados siguen apuntando a preguntas que
existen. Reencender copia desde la plataforma actual, no resucita la copia vieja.

**Quién enciende.** Permiso nuevo `personalizar_instrumentos`, por defecto solo en el
rol ADMINISTRADOR de cada empresa. Que Renaser pueda encenderlo desde arriba es de la
pieza F.

---

## 3 · Los candidatos cuando cada empresa evalúa distinto

- **Cada evaluación queda amarrada al examen exacto que rindió la persona.** Cambiar el
  banco después no mueve las notas de los ya evaluados. (Ya funciona así; se respeta.)
- **Las notas no se comparan entre empresas.** Ningún ranking ni pantalla mezcla
  candidatos de dos empresas.
- **El perfil es de la persona; la nota es del proceso.** El perfil del candidato viaja
  con él entre empresas; sus resultados de evaluación se quedan en cada proceso.
- **Si repite el examen en otra empresa, lo repite.** No se reutilizan resultados entre
  empresas, aunque ambas usen el banco de Renaser. (El currículum sí se lee una sola
  vez: es dato del perfil, no nota.)
- Renaser mejora su banco → la mejora llega al instante a toda empresa sin personalizar.

---

## 4 · El día uno de una empresa nueva

**Lee compartido, sin hacer nada:** el método completo de Renaser (banco, pesos,
plantillas, pruebas). Puede evaluar con un método probado desde el primer día.

**Se le copia como punto de partida:** correos y textos legales — como **borradores**,
porque nombran a Renaser y nadie puede publicar una vacante con un consentimiento ajeno.

**Nace vacío y lo llena ella:** su equipo (mínimo un administrador), sus áreas, sus
puestos.

**Requisitos para publicar la primera vacante** (el sistema los muestra como lista de
tareas, no como errores sueltos):

1. Un administrador dado de alta.
2. Textos legales revisados y publicados con su nombre.
3. Al menos un área y un puesto definidos.
4. Un responsable asignado a la vacante.

**Fuera de esta pieza a propósito:** cómo se registra la empresa y quién aprueba su alta
(pieza C); qué paga por la plataforma y la IA (pieza E).

---

## Consecuencias para la implementación (resumen)

- Migración: 4 banderas + `es_plataforma` en `organizacion`; `copiada_de_version_id` en
  las tablas de versión de los 4 instrumentos.
- Código: el resolutor `DuenoDelInstrumento` y el paso de todas las consultas de
  instrumentos por él; el servicio de copia por instrumento; el permiso nuevo.
- Nada de esto cambia el comportamiento actual con una sola organización: con las
  banderas apagadas y Renaser como plataforma, resolver siempre devuelve Renaser.
