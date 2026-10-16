# Pruebas modelo: escribir una prueba antes de que exista la vacante

Estado: borrador

## Objetivo
Permitir escribir preguntas del Perfil Integral o una prueba del puesto sin tener todavía la
vacante, y usarlas después en una o varias vacantes. Está pensado para la empresa que repite
siempre los mismos perfiles, por ejemplo cajeros o asistentes contables, y quiere tener su
prueba lista antes de abrir la convocatoria.

Hoy la única forma de hacerlo es crear una vacante en borrador que sirva de «molde»: el editor
deja publicar su prueba aunque la vacante esté sin publicar, y luego se copia desde ella. Ese
molde ensucia la lista de vacantes.

## Contexto
- Comportamiento actual:
  - Toda versión del editor pertenece a una vacante (`version_banco.vacante_id` y `proposito`),
    con índices que permiten un solo borrador y una sola publicada por vacante (V66).
  - Reutilizar una prueba consiste en «Copiar de otra vacante», que es una copia y no un enlace.
  - Las recomendaciones de la IA se apoyan en los datos de la vacante (`DatosDeLaVacante`:
    título, descripción, propósito, responsabilidades, requisitos, puesto, nivel, familia,
    resultado principal y capacidades). Un modelo no tiene vacante, así que necesita unos datos
    mínimos propios.
- Esta spec **revisa en parte la decisión del 29/09** («sin catálogo aparte; la biblioteca es
  Copiar de otra vacante»). El modelo sí es un catálogo de la empresa, pero **solo se copia y
  nunca lo rinde nadie**. Por eso no arrastra los problemas del sistema antiguo:
  - No se puede editar algo que ya rinden candidatos, porque nadie rinde un modelo.
  - No hay catálogo compartido entre empresas.
  - Cambiar el modelo no altera las vacantes que ya lo copiaron.
- Pantallas o procesos afectados:
  - La pantalla «Pruebas» de `specs/pruebas-indice-de-la-empresa.md`, que pasa a enseñar
    modelos y gana el botón para crearlos.
  - El editor, que se abre también para un modelo.
  - «Copiar de otra vacante», que pasa a ofrecer también modelos.
- Referencias: `specs/pruebas-indice-de-la-empresa.md`,
  `specs/Done/preguntas-propias-de-la-vacante.md` y
  `specs/Done/prueba-del-puesto-con-el-editor.md`.

## Alcance
- Incluye:
  - El botón «Nueva prueba modelo» en cada pestaña de «Pruebas».
  - Los datos propios de cada modelo: nombre, tipo (preguntas del Perfil Integral o prueba del
    puesto), nivel, puesto (opcional) y «Para qué puesto es», un texto corto que da contexto a
    la IA.
  - El editor de siempre para el modelo, en `/admin/pruebas/modelos/:id`: criterios, preguntas,
    caso opcional, entregables, configuración (tiempo, materiales y herramientas, sin fecha
    límite), guía y recomendaciones por IA.
  - Publicar el modelo con las mismas reglas que en una vacante. Solo los modelos publicados se
    pueden usar.
  - Los modelos en el índice, con la etiqueta «Modelo» y un filtro «Origen: Vacantes / Modelos».
  - «Usar en una vacante» desde el modelo, con el mismo diálogo que define la spec del índice.
  - En el editor de la vacante, «Copiar de otra vacante» pasa a llamarse «Copiar de una vacante
    o un modelo», con los modelos publicados en su propio grupo.
  - «Guardar como modelo» en las filas de vacantes del índice: crea un modelo que copia esa
    versión publicada.
  - Archivar y desarchivar un modelo.
- Queda fuera:
  - Un enlace vivo entre el modelo y sus vacantes: cambiar el modelo no cambia las vacantes que
    ya lo copiaron.
  - Modelos compartidos entre empresas, o modelos de RENASER para todas.
  - Pasar a modelos las plantillas del sistema antiguo.
  - Borrar modelos: se archivan.

## Comportamiento esperado
1. **Crear.** «Nueva prueba modelo» abre un formulario corto con nombre, nivel, puesto opcional y
   «Para qué puesto es». Al guardarlo se abre el editor del modelo con un borrador vacío.
2. **Editar.** Funciona igual que en una vacante. «Recomendaciones por IA» usa el nombre, el
   nivel, el puesto y el texto «Para qué puesto es» del modelo en lugar de los datos de una
   vacante.
3. **Publicar.** Se aplican las mismas validaciones que a la prueba de una vacante, según
   `specs/editor-de-la-prueba-mas-simple.md` (los puntos suman 100, el caso nunca es
   obligatorio aunque haya entregables, todo entregable lo mira algún criterio con parte
   calificada, etc.), salvo la fecha límite. Un modelo no tiene vacante, así que no tiene fecha: se
   exige al publicar la prueba de la vacante que lo copia.
4. **Usar en una vacante.** Desde la fila del modelo se abre el diálogo de destinos de la spec
   del índice. Se copia y se abre el editor de la vacante destino.
5. **Desde la vacante.** «Copiar de una vacante o un modelo» enseña dos grupos, «Modelos» y
   «Vacantes», con el mismo buscador, el filtro por nivel y la vista previa.
6. **Cambiar un modelo publicado.** Se abre un borrador y, al publicarlo, sustituye a la versión
   publicada. Antes de publicar se avisa: «Las vacantes que ya lo copiaron no cambian».
7. **Guardar como modelo.** Desde una fila de vacante con versión publicada, pide nombre y
   contexto (y propone el título y el nivel de la vacante), crea el modelo con la copia y lo
   abre en su editor.
8. **Archivar.** Un modelo archivado deja de ofrecerse para copiar. Las vacantes que lo copiaron
   no notan nada y el modelo se puede desarchivar.

## Referencia visual

### El índice con modelos

```
Pruebas                                                   [ + Nueva prueba modelo ]

 Preguntas del Perfil Integral │ Prueba del puesto
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┷━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌ Filtros ──────────────┐   ┌──────────────────────────────────────────────────────────┐
│ Origen                │   │ MODELO  Cajero de tienda               Ejecución          │
│ [x] Vacantes          │   │ PUBLICADA                       3 criterios · 9 preguntas │
│ [x] Modelos           │   │ [ Ver ] [ Editar el modelo ] [ Usar en una vacante ] [ ⋯ ]│
│ …                     │   ├──────────────────────────────────────────────────────────┤
└───────────────────────┘   │ Asistente Contable (vacante)           Ejecución · Cerrada│
                            │ PUBLICADA                     4 criterios · 12 preguntas  │
                            │ [ Ver ] [ Abrir en la vacante ] [ Usar en otra vacante ]  │
                            │ [ Guardar como modelo ]                                   │
                            └──────────────────────────────────────────────────────────┘
```

El menú `⋯` de un modelo tiene «Archivar» o «Desarchivar».

### Crear un modelo

```
Nueva prueba modelo · Prueba del puesto
Nombre                [ Cajero de tienda                       ]
Nivel                 [ Ejecución                            ▾ ]
Puesto (opcional)     [ —                                    ▾ ]
Para qué puesto es    [ Atiende caja en tienda de barrio, cuadra caja al cierre… ]
                      La IA lo usa para proponer preguntas.
                                              [ Cancelar ]  [ Crear y abrir el editor ]
```

### El editor del modelo
Es el editor de siempre. Solo cambia la cabecera, `← Pruebas · Modelo: Cajero de tienda ·
Prueba del puesto`, y desaparece todo lo que solo tiene sentido con candidatos: no hay recuento
de postulantes y nunca está «quieto».

- Teléfono: igual que el índice y el editor actuales.
- Estados: los del índice y del editor. El estado vacío de «Modelos» invita a crear el primero.
- Reglas visuales: las del DESIGN.md del panel. Se reutilizan el editor, `Modal` y
  `VistaDeVersion`.

## Criterios de aceptación
- AC-01: Dado un usuario con permiso, cuando crea un modelo de prueba del puesto con nombre y
  nivel, entonces se abre su editor con un borrador vacío y el modelo aparece en el índice como
  `BORRADOR`.
- AC-02: Dado un modelo en borrador, cuando pide «Recomendaciones por IA», entonces la IA
  propone usando el nombre, el nivel y «Para qué puesto es», sin necesitar una vacante.
- AC-03: Dado un modelo que no cumple las reglas, cuando se intenta publicar, entonces se
  rechaza con los mismos motivos que en una vacante.
- AC-04: Dado un modelo publicado, cuando se usa en dos vacantes, entonces cada una recibe su
  propia copia en borrador y las dos se pueden editar por separado.
- AC-05: Dado un modelo ya copiado a una vacante, cuando se cambia y se republica, entonces la
  vacante conserva la versión que copió.
- AC-06: Dada una vacante, cuando abre «Copiar de una vacante o un modelo», entonces ve los
  modelos publicados de su empresa y nunca los de otra.
- AC-07: Dada una vacante con versión publicada, cuando se pulsa «Guardar como modelo»,
  entonces se crea un modelo con el mismo contenido y la vacante no cambia.
- AC-08: Dado un modelo archivado, cuando se busca para copiar, entonces no aparece. Se ve en
  el índice con el filtro de archivados y se puede desarchivar.
- AC-09: Dada otra empresa, cuando pide un modelo ajeno por id, entonces recibe 404.
- AC-10: Dado el banco de RENASER por nivel, cuando se crean modelos, entonces las vacantes que
  responden ese banco siguen tomando el mismo banco que antes (ver «Datos y compatibilidad»).

## Casos límite y errores
- Datos vacíos o inválidos:
  - Nombre y nivel son obligatorios.
  - El nombre es único por empresa y tipo entre los modelos que no están archivados.
  - «Para qué puesto es» admite como mucho 1000 caracteres y pasa por la envoltura
    anti-inyección, como la guía.
- Usuario sin permisos: sin el permiso de modelos no ve el botón ni las acciones de editar,
  archivar o guardar como modelo, y el servidor responde 403.
- Recarga, reintento o doble envío: «Crear» y «Guardar como modelo» se deshabilitan mientras se
  ejecutan. Un nombre repetido devuelve el error de nombre único.
- Un modelo sin publicar no se ofrece para copiar.
- La consigna adjunta (PDF o Word) se copia igual que hoy entre vacantes: la copia apunta al
  mismo archivo (`consignaArchivoId`, `urlConsigna`).

## Datos y compatibilidad
- Información que debe guardarse:
  - Una tabla nueva `prueba_modelo` con `organizacion_id`, `tipo`, `nombre`,
    `nivel_puesto_codigo`, `puesto_id`, `contexto`, `archivado_en`, quién lo creó y cuándo.
  - Una columna nueva `version_banco.prueba_modelo_id`, que excluye a `vacante_id` (una versión
    pertenece a una vacante o a un modelo, nunca a los dos).
  - Índices de un borrador y una publicada por modelo, como los de la vacante.
- ⚠️ **Riesgo principal:** hoy una versión sin `vacante_id` significa «banco por nivel de la
  plataforma» (`VersionBancoRepository`, la consulta con `vacanteId is null`). Toda consulta
  que lea ese significado debe excluir los modelos. Hay que revisarlas todas antes de crear el
  primer modelo.
- Comportamiento para registros existentes: no cambia nada. Las vacantes y sus versiones se
  quedan como están.
- La copia reutiliza `copiarContenido`, el mismo código que la copia entre vacantes.

## Verificación
- Actor/rol y datos de prueba necesarios: un usuario TALENTO, dos vacantes sin candidatos, una
  vacante con versión publicada (para «Guardar como modelo») y una segunda empresa.
- Recorridos que deben quedar como E2E repetibles: crear un modelo, publicarlo, usarlo en una
  vacante y ver la copia en el editor de esa vacante; y guardar una vacante como modelo.
- Riesgos que QA debe explorar con navegador:
  - Las recomendaciones de la IA sin vacante.
  - El aviso al republicar un modelo ya copiado.
  - Que el banco por nivel no cambie para las vacantes antiguas.
- Resultado que quiero comprobar personalmente: crear «Cajero de tienda» sin vacante, y después
  crear una vacante y cargarle ese modelo con un clic.

## Dependencias y dudas
- Requiere: `specs/pruebas-indice-de-la-empresa.md` desplegada, y
  `specs/editor-de-la-prueba-mas-simple.md`, porque el modelo reutiliza ese editor (entregables
  por pregunta o generales, «Mira» deducido y configuración sin fecha límite).
- Decisiones pendientes:
  1. ¿Se implementa? Está en borrador hasta decidirlo.
  2. ¿Qué permiso crea y edita modelos? Recomendado: `editar_vacante`. La otra opción es un
     permiso nuevo.
  3. ¿El responsable de área ve y usa todos los modelos de la empresa? Recomendado: sí, porque
     un modelo no pertenece a ninguna vacante.
  4. ¿Se muestra «Usado en N vacantes»? Haría falta guardar de qué modelo salió cada copia;
     `version_banco.copiada_de_version_id` ya existe y podría servir.
  5. ¿Cómo se llama en pantalla: «Prueba modelo» o «Plantilla»? «Plantilla» se confunde con el
     sistema antiguo.
