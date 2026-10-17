# Pruebas: el índice de las preguntas y pruebas de la empresa

Estado: borrador

## Objetivo
Ver en un solo sitio todas las preguntas y pruebas que la empresa ha escrito en sus vacantes,
encontrar la que sirve por nombre, nivel o estado, y reutilizarla en otra vacante sin tener que
entrar vacante por vacante. Está pensada para quien arma vacantes: Talento, Dirección y
responsables de área.

La pantalla es de **solo lectura**. Aquí se busca, se mira y se copia; para editar, se entra en
la vacante, que mantiene sus reglas de siempre.

## Contexto
- Comportamiento actual:
  - Las preguntas del Perfil Integral y la prueba del puesto se escriben dentro de cada vacante
    (`/admin/vacantes/:id/preguntas` y `/admin/vacantes/:id/prueba`).
  - La única vista de conjunto es «Copiar de otra vacante», dentro del editor de la vacante
    destino (`CopiarDeOtraVacante.tsx` y `GET .../copiables`). Lista las versiones publicadas
    de la empresa en vacantes activas, cerradas y archivadas (nunca en las eliminadas), con
    buscador, filtro por nivel y vista previa en solo lectura (`VistaDeVersion.tsx`). Ordena por
    la fecha de publicación de la vacante, y su «sin publicar» se refiere a la vacante. Tiene dos
    limitaciones: depende de una vacante destino y no muestra los borradores.
  - «Pruebas» ya no está en el menú (`src/panel/menu.ts`) desde
    `specs/Done/fuga-del-catalogo-de-preguntas.md` (#112 y #73). `/admin/pruebas` y
    `/admin/pruebas/versiones/:versionId` siguen existiendo solo para redirigir a `/admin`
    (`src/rutas.ts` y `src/app/App.tsx`). Esta spec sustituye esa redirección por la pantalla
    nueva y vuelve a añadir `'pruebas'` a `ClaveDeEntrada` y a `entradaActiva`.
- Datos que ya existen:
  - `version_banco` con `vacante_id` y `proposito`: `PERFIL_INTEGRAL` para las preguntas propias
    (fase 1) o `PRUEBA_PUESTO` para la prueba del puesto (fase 2). Sus estados son `BORRADOR`,
    `PUBLICADA` y `ARCHIVADA`.
  - El nivel sale del puesto de la vacante (`puesto.nivel_puesto_codigo`).
  - «Ya la rinde alguien» sigue la misma regla que el editor (`laVaraNoSeMueve`). En las
    preguntas significa que la vacante tiene postulaciones; en la prueba, que alguien ya abrió
    su prueba.
  - La situación de la vacante sale de `vacante.estado` (`BORRADOR`, `PUBLICADA` o `CERRADA`) y
    de `archivada_en`.
  - Lo que rinde cada vacante: `vacante.aplica_evaluacion` con `vacante.origen_preguntas`
    (`NIVEL` o `VACANTE`; «sin evaluación» es `aplica_evaluacion = false`) para el Perfil
    Integral, y `vacante.instrumento_etapa_tecnica` (`PLANTILLA`, `CUESTIONARIO_TECNICO` o
    `PRUEBA_PROPIA`) para la etapa técnica. Una vacante puede tener una versión del editor que
    ya no usa: el origen se cambia antes de que haya postulantes, o se le asigna una plantilla
    por API, y la versión no se borra.
  - El tiempo de la prueba es de la versión: modo `CRONOMETRADA` (con `duracionMinutos`) o
    `PLAZO_ABIERTO`. Las publicadas antes de la V68 pueden traer `plazoDias` y se pintan como
    «N días». La fecha límite es de la vacante (`vacante.prueba_cierra_en`), no de la versión:
    viaja en `EditorDePreguntas.fechaLimite` y `VistaDeVersion` no la enseña.
  - Desde `specs/Done/editor-de-la-prueba-mas-simple.md` hay archivos de pregunta y entregables
    generales. La cabecera del editor los cuenta todos juntos como «N entregables».
  - Por defecto (V9), el responsable de área tiene `ver_vacantes` con alcance `TODO` y no tiene
    `editar_vacante`. El alcance `SUS_VACANTES` solo existe si alguien lo configura en los roles.
- Decisiones del 02/10/2026:
  - Índice de solo lectura y sin pruebas sueltas. Se mantiene la decisión del 29/09: la
    biblioteca son las vacantes de la propia empresa. Las pruebas modelo, sin vacante, van
    aparte en `specs/Draft/pruebas-modelo.md`, que está en borrador.
  - Dos pestañas: preguntas del Perfil Integral y prueba del puesto.
  - Las vacantes del sistema antiguo no aparecen.
  - Hay filtros y un botón «Usar en otra vacante».
- Pantallas o procesos afectados: el menú del panel (`src/panel/menu.ts`), la nueva pantalla
  `/admin/pruebas` y, desde el diálogo de copia, el editor de la vacante destino.
- Referencias:
  - `specs/Done/preguntas-propias-de-la-vacante.md`, `specs/Done/prueba-del-puesto-con-el-editor.md`,
    `specs/Done/editor-de-la-prueba-mas-simple.md` y `specs/Done/reparto-y-avisos-de-los-puntos.md`.
  - `docs/PANEL.md` y `DESIGN.md` del repositorio del frontend (RenaserOsPostulantes; `frontend`
    en el worktree del harness). El `DESIGN.md` de este repositorio es el de RENASER OS y no
    aplica. Desde el #76, `/vacantes` tiene filtros en columna con Tailwind y shadcn/ui, un piloto
    que solo usa esa pantalla.

## Alcance
- Incluye:
  - La entrada «Pruebas» en el menú (familia «Selección») para quien tenga `ver_vacantes`.
  - La pantalla `/admin/pruebas`, con dos pestañas: **«Preguntas del Perfil Integral»** y
    **«Prueba del puesto»**. La pestaña elegida y los filtros quedan en la URL
    (`?tipo=preguntas|prueba&…`), así que se pueden enlazar y sobreviven a una recarga.
  - Una fila por cada vacante que tenga esa versión escrita en el editor, publicada o en
    borrador.
  - Filtros:
    - Buscador por título de la vacante.
    - Nivel.
    - Estado de la prueba: Borrador, Publicada o La rinde alguien.
    - Situación de la vacante: Sin publicar, Publicada, Cerrada o Archivada.
  - La vista previa en solo lectura, con el `VistaDeVersion` de siempre.
  - Tres acciones por fila: «Ver», «Abrir en la vacante» y «Usar en otra vacante».
  - En el backend: un listado de la empresa que no depende de una vacante destino e incluye
    borradores, la vista previa de un borrador y la lista de vacantes destino que dice cuáles
    pueden recibir la copia.
- Queda fuera:
  - Crear, editar o borrar desde esta pantalla: todo eso se hace en la vacante.
  - Las pruebas sin vacante, que van en `specs/Draft/pruebas-modelo.md`.
  - Las vacantes del sistema antiguo: las que responden el banco de RENASER por nivel, las que
    rinden una plantilla (`PLANTILLA`) y las del cuestionario técnico del REDACTOR
    (`CUESTIONARIO_TECNICO`).
  - Las vacantes eliminadas.
  - Las versiones del editor que quedaron en una vacante que ya no las usa (pasó a preguntas por
    nivel, a «sin evaluación» o a una plantilla).
  - Los resultados: notas medias, cuántas personas la rindieron, etc.
  - Cambiar «Copiar de otra vacante» dentro del editor, que sigue igual, incluido su alcance.

## Comportamiento esperado
1. Quien tiene `ver_vacantes` ve «Pruebas» en el menú. Al entrar se abre la pestaña «Preguntas
   del Perfil Integral», o la que indique la URL.
2. Cada pestaña lista las vacantes que esa persona tiene a su alcance y que tienen esa versión
   escrita. El alcance es el de `ver_vacantes`, como en la lista de vacantes: con
   `SUS_VACANTES` solo se ven las suyas. Cada fila muestra:
   - El título de la vacante, su nivel y su situación.
   - El estado de la prueba: `BORRADOR`, `PUBLICADA` y, si corresponde, «La rinde alguien».
   - Un resumen. En las preguntas: «N criterios · N preguntas». En la prueba: «N criterios ·
     N preguntas · N entregables · tiempo», donde el tiempo se escribe como en el chip del
     editor: «90 min», «Sin cronómetro» o «N días» (versiones anteriores a la V68). «N
     entregables» cuenta todos, los de pregunta y los generales, como la cabecera del editor.
     La fecha límite no se enseña: no se copia y en las vacantes cerradas ya pasó.
   - La fecha de publicación de la versión, o «sin publicar».
   - Si la vacante tiene una versión publicada y además un borrador abierto, la fila muestra la
     publicada con la marca «Hay un borrador abierto». No se dice si tiene cambios: el borrador
     nace como copia idéntica de la publicada y no hay comparación entre las dos.
   - El orden: primero las publicadas, por la fecha de publicación de la versión (la más
     reciente arriba), y al final los borradores que nunca se publicaron.
3. Los filtros siguen el patrón de `/vacantes` (#76) y reutilizan sus piezas:
   - desde 1024 px, una columna de filtros a la izquierda, con grupos plegables, «Ver N más» y
     «Limpiar»;
   - de 641 a 1023 px, un botón «Filtros» que los abre en un `Popover`;
   - en el teléfono, plegados tras «Filtrar (n)»;
   - las etiquetas de los filtros activos y «Quitar filtros», justo encima de la lista.

   Los filtros se combinan entre sí. El buscador no distingue tildes ni mayúsculas, como en
   «Copiar».
4. «Ver» abre la vista previa en solo lectura: desde 1280 px, en una tercera columna a la
   derecha de la lista, como el panel de `/vacantes`; por debajo, a pantalla completa con
   `Modal`. Muestra la versión publicada. Si la vacante solo tiene borrador, muestra el borrador
   con la etiqueta «Borrador: todavía puede cambiar», sin sus avisos ni la pastilla «N por
   arreglar», que son del editor.
5. «Abrir en la vacante» lleva al editor de esa vacante (`/admin/vacantes/:id/preguntas` o
   `/admin/vacantes/:id/prueba`), donde rigen sus reglas de siempre.
6. «Usar en otra vacante» solo aparece para quien tiene `editar_vacante` y solo en las filas
   con una versión publicada:
   1. Se abre un diálogo para elegir la vacante destino, con buscador. Se ofrecen las vacantes
      de la empresa que esa persona tiene a su alcance, que usan el editor para eso
      (`aplica_evaluacion = true` y `origen_preguntas = VACANTE` en la pestaña de preguntas;
      `instrumento_etapa_tecnica = PRUEBA_PROPIA` en la de la prueba) y que todavía se pueden
      editar (ni cerradas, ni
      archivadas, ni eliminadas). La vacante de origen no se ofrece. Hoy `copiar` no comprueba
      que la destino use el editor: esta spec añade ese rechazo en el servidor.
   2. Las vacantes donde ya la rinde alguien aparecen deshabilitadas, con el motivo de cada
      pestaña: «Ya tiene postulantes: sus preguntas no se reemplazan» o «Ya la rinden
      candidatos: su prueba no se reemplaza». Si se pide por API, el servidor responde con sus
      textos de siempre («Esta vacante ya tiene postulantes…» o «Alguien ya empezó a rendir esta
      prueba…»).
   3. Si la vacante destino tiene un borrador, se pide confirmar que se reemplaza, con el mismo
      texto que en el editor: «Ya hay un borrador en esta vacante: la copia lo reemplaza
      entero.».
   4. Al confirmar se copia (es una copia, no un enlace) y se abre el editor de la vacante
      destino con el borrador copiado. La original no cambia. La fecha límite no se copia,
      porque es de cada vacante, y el plazo en días de las versiones viejas tampoco.
7. Si una pestaña no tiene nada, se muestra un estado vacío que explica que las preguntas y las
   pruebas se escriben dentro de cada vacante, con un enlace a Vacantes. Si los filtros no dejan
   nada, se muestra «Ninguna coincide con los filtros» y «Quitar filtros».
8. Si falla la carga de la lista, de la vista previa o de la copia, se muestra un aviso con
   «Reintentar», y lo que ya estaba en pantalla se mantiene.

## Referencia visual

### Escritorio

```
Pruebas
Las preguntas y pruebas que tu empresa escribió en sus vacantes. Se editan dentro de cada vacante.

 Preguntas del Perfil Integral │ Prueba del puesto
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┷━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
┌ Filtros ──────────────┐   Ejecución ✕   Publicada ✕                    Quitar filtros
│ Buscar                │   ┌──────────────────────────────────────────────────────────────┐
│ [ Asistente…        ] │   │ Asistente Contable                    Ejecución · Publicada   │
│                       │   │ PUBLICADA · La rinde alguien     4 criterios · 12 preguntas   │
│ Nivel                 │   │ Publicada 09/2026 · Hay un borrador abierto                   │
│ [ Todos los niveles ▾]│   │        [ Ver ]  [ Abrir en la vacante ]  [ Usar en otra vacante ] │
│                       │   ├──────────────────────────────────────────────────────────────┤
│ Estado de la prueba   │   │ Analista de Marketing                 Táctico · Sin publicar  │
│ [ ] Borrador          │   │ BORRADOR                          2 criterios · 5 preguntas   │
│ [x] Publicada         │   │ Sin publicar                 [ Ver ]  [ Abrir en la vacante ] │
│ [ ] La rinde alguien  │   └──────────────────────────────────────────────────────────────┘
│                       │
│ Situación de vacante  │
│ [ ] Sin publicar      │
│ [ ] Publicada         │
│ [ ] Cerrada           │
│ [ ] Archivada         │
└───────────────────────┘
```

En la pestaña «Prueba del puesto», el resumen de la fila es: `3 criterios · 6 preguntas ·
2 entregables · 90 min`.

El reparto es el de `/vacantes`: filtros, lista y, desde 1280 px, la vista previa en una
tercera columna. El boceto simplifica los filtros; su forma real es la de esa pantalla.

La vista previa («Ver») se abre en una columna a la derecha de la lista. Es el `VistaDeVersion`
de siempre, con un encabezado «Asistente Contable · Publicada 09/2026» y un botón «Cerrar».

### Diálogo «Usar en otra vacante»

```
Usar «Asistente Contable» en otra vacante
Se copia: la original no cambia y la copia se edita en la vacante que elijas.

[ Buscar vacante…                                      ]
( ) Auxiliar Contable          Sin publicar
( ) Contador Senior            Publicada · tiene un borrador (se reemplazará)
(–) Cajero                     Publicada · Ya la rinden candidatos: no se reemplaza

                                          [ Cancelar ]  [ Copiar y abrir ]
```

### Teléfono
- Las dos pestañas ocupan todo el ancho, arriba.
- Los filtros, plegados tras «Filtrar (n)», como en `/vacantes`.
- Cada fila ocupa una columna y sus botones pasan a la línea siguiente si no caben.
- La vista previa y el diálogo se abren con `Modal` a pantalla completa.

### Estados a dibujar
- **Normal:** como en los bocetos.
- **Vacío:** sin nada en la pestaña, o sin resultados con los filtros puestos.
- **Cargando:** «Buscando…» en la lista; la vista previa y el diálogo tienen su propia carga.
- **Con error:** aviso con «Reintentar».
- **Datos largos:** los títulos se cortan a dos líneas y el título completo va en `title`; los
  resúmenes no se cortan.

### Reglas visuales
Las de `docs/PANEL.md` y `DESIGN.md` del frontend. Se reutilizan:
- los filtros y el reparto en columnas de `/vacantes`, con Tailwind y shadcn/ui: esta es la
  segunda pantalla del panel que usa ese piloto (decisión del usuario del 12/10/2026);
- las pestañas de la ficha del colaborador: `Pestanas` es hoy una función privada de
  `FichaColaborador.tsx` que guarda la pestaña en `useState`; se extrae a un componente común y
  aquí la pestaña vive en la URL;
- `Modal`, que ya tiene `pantallaCompleta` y `lateral` (panel a la derecha);
- `VistaDeVersion` y la confirmación de reemplazo de `CopiarDeOtraVacante`.

Los textos exactos de los estados quedan a criterio de quien implementa.

## Criterios de aceptación
- AC-01: Dado un usuario con `ver_vacantes`, cuando entra al panel, entonces ve «Pruebas» en el
  menú. Sin ese permiso no la ve.
- AC-02: Dada una empresa con una vacante con preguntas propias publicadas y otra con una prueba
  del puesto en borrador, cuando abre «Pruebas», entonces la primera aparece en «Preguntas del
  Perfil Integral» y la segunda en «Prueba del puesto», cada una con su estado y su resumen.
- AC-03: Dada una vacante del sistema antiguo (banco de RENASER por nivel, `PLANTILLA` o
  `CUESTIONARIO_TECNICO`), cuando se abre cualquiera de las dos pestañas, entonces no aparece.
  Tampoco aparece una versión del editor que quedó en una vacante que ya no la usa.
- AC-04: Dadas dos empresas, cuando una abre «Pruebas» o pide la vista previa de una vacante de
  la otra, entonces no ve nada de la otra y la vista previa responde 404.
- AC-05: Dado un rol con `ver_vacantes` de alcance `SUS_VACANTES` (hay que configurarlo: por
  defecto el responsable de área lo tiene con alcance `TODO`), cuando abre «Pruebas», entonces
  solo ve las vacantes que le corresponden.
- AC-06: Dada la lista con filtros de nivel, estado y situación más un texto de búsqueda,
  cuando se combinan, entonces solo quedan las filas que cumplen todos. Al recargar, la pestaña
  y los filtros siguen puestos.
- AC-07: Dada una vacante con una versión publicada y un borrador abierto, cuando aparece en la
  lista, entonces la fila muestra la publicada con «Hay un borrador abierto», y «Ver» enseña la
  publicada.
- AC-08: Dada una vacante que solo tiene borrador, cuando se pulsa «Ver», entonces se ve el
  borrador en solo lectura con la etiqueta de borrador, sin avisos ni «N por arreglar».
- AC-09: Dada una fila, cuando se pulsa «Abrir en la vacante», entonces se llega al editor de
  esa vacante, en la pestaña que corresponde.
- AC-10: Dado un usuario con `editar_vacante` y una fila publicada, cuando usa «Usar en otra
  vacante» y elige una vacante destino sin candidatos, entonces termina en el editor de la
  destino con un borrador idéntico al de origen, y el origen no cambia.
- AC-11: Dada una vacante destino donde ya la rinde alguien, cuando se abre el diálogo,
  entonces aparece deshabilitada con el motivo de esa pestaña, y el servidor también rechaza la
  copia si se pide por API.
- AC-12: Dada una vacante destino con un borrador, cuando se elige, entonces se pide confirmar
  el reemplazo antes de copiar.
- AC-13: Dado un usuario sin `editar_vacante`, cuando ve la lista, entonces no aparece «Usar en
  otra vacante».
- AC-14: Dada una empresa sin nada escrito en el editor, cuando abre una pestaña, entonces ve el
  estado vacío con el enlace a Vacantes.
- AC-15: Dada una vacante que no usa el editor para esa pestaña (preguntas por nivel o sin
  evaluación, o una plantilla o el cuestionario técnico en la etapa técnica), cuando se abre el
  diálogo de copia,
  entonces no se ofrece como destino, y el servidor rechaza la copia si se pide por API.

## Casos límite y errores
- Datos vacíos o inválidos:
  - Una vacante sin puesto o sin nivel muestra «—» y no coincide con ningún filtro de nivel.
  - En el Perfil Integral, una versión publicada sin preguntas no puede existir, porque publicar
    lo exige. En la prueba sí: se publica con solo entregables, y la fila dice «0 preguntas».
- Usuario sin permisos: sin `ver_vacantes` no hay entrada en el menú, y la URL directa muestra
  el aviso de permiso del panel. Sin `editar_vacante` no hay copia, ni en pantalla ni por API.
- Recarga, reintento o doble envío:
  - La pestaña y los filtros viven en la URL.
  - «Copiar y abrir» se deshabilita mientras copia. Si se repite, vuelve a reemplazar el mismo
    borrador, que es inofensivo.
- La vacante destino empieza a rendirse entre que se abre el diálogo y se confirma: el servidor
  rechaza la copia con el texto de siempre del editor. El diálogo lo muestra y refresca la lista
  de destinos.
- La vacante de origen se elimina mientras se mira: la vista previa responde 404 y se muestra
  «Esta vacante ya no existe»; la lista se refresca.
- Las versiones archivadas (las publicadas que otra republicación sustituyó) no aparecen.
  Solo cuentan la publicada vigente y el borrador.
- Observación: hoy «Copiar de otra vacante», dentro del editor, enseña al responsable de área
  todas las vacantes de la empresa, porque `copiables` filtra por empresa y no por alcance. Esta
  pantalla sí aplica el alcance. Solo importa si se configura `ver_vacantes` con
  `SUS_VACANTES`. Se deja así (decisión del 12/10/2026).
- «Copiar de otra vacante» sigue ofreciendo las versiones que quedaron en vacantes que ya no las
  usan; esta pantalla no las enseña.

## Datos y compatibilidad
- Información que debe guardarse: nada nuevo. No hay migración.
- Lecturas nuevas, todas cortadas por empresa y por alcance en el servidor; lo ajeno responde 404:
  - `GET /api/v1/panel/pruebas?tipo=PERFIL_INTEGRAL|PRUEBA_PUESTO&buscar=&nivel=&estado=&situacion=`
    devuelve las filas.
  - `GET /api/v1/panel/pruebas/{vacanteId}?tipo=…` devuelve la vista previa: la publicada o, si
    no hay, el borrador, con el `VersionDePreguntas` de siempre.
  - `GET /api/v1/panel/pruebas/destinos?tipo=…&excluir={vacanteId}` devuelve las vacantes que
    pueden recibir la copia y, para las que no, el motivo. No depende de una vacante de origen:
    `excluir` es opcional, para que `specs/Draft/pruebas-modelo.md` reutilice el diálogo desde un
    modelo sin vacante.
- La copia reutiliza `POST /api/v1/panel/vacantes/{destino}/preguntas-propias/copia` y
  `.../prueba-propia/copia`, con un solo cambio: rechazar una destino que no usa el editor para
  esa pestaña (`aplica_evaluacion` y `origen_preguntas`, o `instrumento_etapa_tecnica`), que hoy
  no se comprueba.
- Permisos: `ver_vacantes` para ver y `editar_vacante` para copiar. No hay permisos nuevos.
- Registros existentes: aparecen tal como están. Se listan las versiones con `vacante_id` no
  nulo y `proposito` en `PERFIL_INTEGRAL` o `PRUEBA_PUESTO`. Eso ya deja fuera lo antiguo:
  los bancos por nivel tienen `proposito` nulo, el cuestionario técnico es
  `CUESTIONARIO_TECNICO` y las plantillas viven en otra tabla. Además, solo cuentan las
  vacantes que usan el editor para esa pestaña: en las preguntas, `aplica_evaluacion = true` y
  `origen_preguntas = VACANTE`; en la prueba, `instrumento_etapa_tecnica = PRUEBA_PROPIA`. Es el
  mismo criterio que para los destinos.
- `specs/Draft/pesos-con-candidatos-y-fin-del-prestamo.md` (pendiente) no cambia esto: guarda la
  versión fija de las vacantes viejas en una columna de la vacante, apuntando a filas de
  RENASER sin `vacante_id` ni `proposito`. Sí pasa a `PRUEBA_PROPIA` las vacantes viejas en
  borrador, que con ello se vuelven destinos válidos.

## Verificación
- Actor/rol y datos de prueba necesarios:
  - Un usuario TALENTO de una empresa con: la vacante A con preguntas publicadas y postulantes;
    la vacante B con la prueba del puesto en borrador; la vacante C cerrada con la prueba
    publicada; la vacante D del sistema antiguo (`PLANTILLA`), que no debe aparecer; y la
    vacante E con una prueba publicada solo con entregables (0 preguntas).
  - Un rol con `ver_vacantes` de alcance `SUS_VACANTES`, configurado en los roles, dueño de una
    sola de esas vacantes.
  - Una segunda empresa con su propia vacante del editor, que nunca debe aparecer.
- Recorridos que deben quedar como E2E repetibles:
  - Entrar, cambiar de pestaña, filtrar por nivel y abrir «Ver».
  - «Usar en otra vacante» hasta terminar en el editor de la destino con el borrador copiado.
- Riesgos que QA debe explorar con navegador:
  - El teléfono y la tableta: filtros tras «Filtrar (n)» y en `Popover`, vista previa y diálogo
    a pantalla completa.
  - Títulos largos.
  - El destino deshabilitado cuando ya hay candidatos.
  - La vista del responsable de área.
- Resultado que quiero comprobar personalmente: abrir «Pruebas», ver las dos pestañas, filtrar
  por nivel y copiar una prueba a una vacante nueva.

## Dependencias y dudas
- Requisitos ya cumplidos: `specs/Done/fuga-del-catalogo-de-preguntas.md` (#112 y #73) y
  `specs/Done/editor-de-la-prueba-mas-simple.md` (#113 y #75), los dos fusionados.
- Relacionada: `specs/Draft/pruebas-modelo.md` (borrador), que añadiría modelos sin vacante a esta
  misma pantalla y reutilizaría el diálogo de destinos.
- Decidido por el usuario el 12/10/2026:
  1. Los filtros y el reparto en columnas, como en `/vacantes` (#76), con Tailwind y shadcn/ui.
  2. La fila de la prueba no enseña la fecha límite.
  3. Las versiones que quedaron en vacantes que ya no las usan no aparecen.
  4. La vista previa de un borrador no enseña sus avisos ni «N por arreglar».
  5. Los destinos no dependen de una vacante de origen (`GET /pruebas/destinos`).
  6. «Copiar de otra vacante», dentro del editor, sigue con el alcance de hoy.
- Decisiones pendientes: ninguna. La spec sigue en borrador hasta que el usuario decida
  implementarla.
