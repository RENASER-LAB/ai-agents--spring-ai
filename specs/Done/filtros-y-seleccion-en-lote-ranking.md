# Botón «Filtros», filtro por fecha y selección en lote con barra de acciones fija

Estado: TERMINADO

Estado previo (referencia histórica): implementada (23/09/2026). Las decisiones que se tomaron al construirla están al final.

## Objetivo

Que el equipo de reclutamiento encuentre más rápido a quién revisar en la tabla de cada
vacante, con una zona de filtros más clara y ordenada, y que pueda avanzar o descartar a
muchas personas sin recorrer la tabla hasta el final para llegar a los botones.

## Contexto

- Comportamiento actual:
  - Cada vacante muestra un ranking por etapa (Perfil integral, Prueba del puesto,
    Simulación, Validación, Decisión). Todas usan el mismo componente `Ranking` de
    `src/panel/vacantes/Vacante.tsx` en el repositorio `RenaserOsPostulantes`.
  - La barra de filtros (`BarraDeFiltros`) mezcla, en una sola fila, la búsqueda por
    nombre, un desplegable llamado «Ciudad, nota y pretensión», el selector de columnas, el
    botón «Ver a todos», el de calificar y el de descargar Excel. No hay un lugar único que
    diga «esto son los filtros», ni se ve de un vistazo cuáles están activos.
  - El filtrado es en el navegador (`filtrarFino` en `ranking.ts`) sobre las filas que ya
    trajo el backend. Lo que queda visible (`visibles`) es también lo que viaja al Excel.
  - Los filtros viven en el estado del componente `Ranking`; se pierden al recargar.
  - No existe filtro por fecha: `FilaRanking` **no expone la fecha de postulación**, aunque
    `Postulacion.creadoEn` sí existe en base de datos.
  - Cada fila tiene una casilla, pero no hay casilla de «marcar todo» en la cabecera.
  - «Avanzar a N personas» y «Descartar» (que abre una ventana con los nombres) están en la
    «mesa» debajo de la tabla, junto al campo «Motivo». Con una tabla larga hay que bajar
    hasta el final para actuar.
- Pantallas o procesos afectados: los cinco rankings de la vista de una vacante; la barra de
  filtros; la mesa de avance; el Excel del ranking (indirectamente, por usar `visibles`);
  el DTO `FilaRanking` del backend.
- Referencias existentes: [plantilla de spec](../plantilla-spec.md),
  [estados de postulación](../../docs/03-ESTADOS-POSTULACION.md),
  [roles y permisos](../../docs/04-ROLES-Y-PERMISOS.md), y el `DESIGN.md` del repositorio
  `RenaserOsPostulantes` (el único acento del panel es el violeta `--activo`, que significa
  «te toca a ti»; el `DESIGN.md` de este repositorio es de un sistema distinto y no aplica
  aquí).

## Alcance

- Incluye:
  - **Botón «Filtros»** (con ese nombre literal) que agrupa todos los filtros en un panel.
    Muestra una insignia con cuántos filtros están activos.
  - **Filtro nuevo por fecha de postulación**: un día fijo o un rango (desde / hasta), con
    atajos «Hoy», «Últimos 7 días» y «Últimos 30 días».
  - **Filtro nuevo por estado de la calificación con IA**: calificada, en curso, fallida.
  - Los filtros que ya existen (ciudad, nota de la etapa, pretensión) pasan al panel
    «Filtros» con el mismo comportamiento. La búsqueda por nombre se queda visible fuera del
    panel, porque es la más usada.
  - **Filtros activos a la vista**: cada filtro activo se muestra como una etiqueta con una
    «×» para quitarlo, debajo de la barra.
  - **Botón «Borrar filtros»** que limpia todos de una vez.
  - **Los filtros se conservan al cambiar de etapa** dentro de la misma vacante.
  - **Marcar todo lo que se ve**: casilla en la cabecera de la tabla, con estado
    intermedio cuando hay algunas marcadas.
  - **Barra de acciones fija abajo**: al haber al menos una persona marcada, «Avanzar» y
    «Descartar» (con el campo «Motivo») aparecen en una barra pegada al borde inferior de
    la ventana, visible sin hacer scroll.
  - Todo lo anterior en las cinco etapas.
- Queda fuera:
  - Guardar filtros entre recargas o por usuario (siguen en memoria).
  - Filtros por «lleva en esta etapa», riesgos críticos, grupo de prioridad y estado de la
    prueba del puesto (se evaluaron y no entran en esta entrega).
  - Selección por rango con Mayús+clic, vistas guardadas y filtros compartidos por enlace.
  - Cabecera de tabla fija al hacer scroll.
  - Cambiar qué acciones existen, sus permisos, el motivo obligatorio, la ventana de
    confirmación con nombres del descarte, el orden del ranking, las notas o las
    transiciones de estado.
  - Paginación o filtrado en el servidor: se mantiene el filtrado en el navegador.

## Comportamiento esperado

### La zona de filtros

1. La barra superior de la tabla queda ordenada de izquierda a derecha: búsqueda por
   nombre · botón **«Filtros»** (con insignia) · **«Columnas»** · **«Borrar filtros»**
   (solo si hay algún filtro activo) · acciones de la tanda (calificar, Descargar Excel).
2. Al pulsar «Filtros» se abre un panel anclado al botón (en móvil, una hoja que sube desde
   abajo y ocupa el ancho). Se cierra con Esc, con clic fuera o con «Listo». Los cambios se
   aplican al instante, sin botón «Aplicar»; el panel muestra «Se ven X de Y» en su pie.
   - **En escritorio no es un modal**: flota encima de la tabla (posición superpuesta), sin
     oscurecer la pantalla ni bloquearla, y **no empuja ni desplaza la tabla** ni el resto
     de la página. Mientras está abierto, la tabla de detrás se actualiza en vivo con cada
     cambio.
   - **En móvil sí es una hoja modal** (sube desde abajo, con fondo atenuado), porque no hay
     sitio para flotar sin tapar; la tabla tampoco se desplaza y se ve actualizarse al
     cerrarla.
3. El panel organiza los filtros en secciones con título, en este orden: **Fecha de
   postulación**, **Calificación con IA**, **Ciudad**, **Nota** (con el nombre de la nota
   de la etapa) y **Pretensión**. Cada sección con un filtro activo lo indica en su título.
   El pie del panel tiene «Borrar filtros» y «Listo».
4. Debajo de la barra, cada filtro activo aparece como una etiqueta legible («Postulados
   del 12 al 15 sep», «IA: fallida», «Ciudad: Lima», «Nota ≥ 60»), cada una con «×». Si no
   hay filtros activos, esa fila no ocupa espacio.
5. Con **«Borrar filtros»** se limpian todos los filtros, incluida la búsqueda por nombre.
   No cambia el corte (Pendiente / Le toca al candidato / Toda la tanda) ni las marcas.
   Sustituye al actual «Ver a todos».
6. Los filtros se combinan con «y» y se **conservan al cambiar de etapa** de la misma
   vacante. En cada etapa solo se muestran las secciones que aplican a ella. La sección
   «Nota» habla siempre de la nota de la etapa que se está viendo, y su etiqueta activa
   lleva el nombre de esa etapa para que no se confunda al cambiar.
7. Al cambiar de vacante, los filtros se reinician.

### Fecha de postulación

8. El usuario elige «Un día» o «Rango».
   - «Un día»: un selector de fecha; muestra a quienes se postularon ese día calendario.
   - «Rango»: «Desde» y «Hasta», ambos inclusivos y cada uno opcional (solo «Desde» = desde
     esa fecha en adelante; solo «Hasta» = hasta esa fecha).
   - Los atajos rellenan el rango y se pueden editar después.
   - El día calendario es el del navegador de quien mira, igual que el resto de fechas del
     panel.
9. Si «Desde» es posterior a «Hasta», el filtro no se aplica y se avisa junto a los campos.
10. Si el backend no trae la fecha de una fila, esa fila queda fuera de un filtro de fecha
    activo, y la ayuda del filtro lo dice («Quien no tiene fecha queda fuera»).

### Calificación con IA

11. Casillas «Calificada», «En curso» y «Fallida» (varias a la vez; vacío = todas). Cada
    opción muestra cuántas filas tiene en la tanda. Como este dato sale de la cola del
    currículum y no de la etapa que se mira, en las etapas posteriores a la primera la
    sección lo aclara: «Es la calificación del currículum».

### Marcar todo y barra fija

12. La casilla de la cabecera marca **solo las filas que se ven** (después de corte,
    filtros y búsqueda). Si ya están todas marcadas, al pulsarla se sueltan. Con algunas
    marcadas queda en estado intermedio.
13. Cuando hay marcadas visibles, aparece una barra pegada abajo con: «N personas
    marcadas», el campo «Motivo (obligatorio)», «Avanzar a N personas», «Descartar…» y
    «Soltar selección». Sin marcadas, la barra no se muestra y no ocupa espacio.
    - «Avanzar» conserva su comportamiento: actúa al pulsarlo, uno a uno, informando quién
      avanzó y quién no.
    - «Descartar…» conserva su ventana con los nombres escritos antes de actuar.
    - Tras terminar, se sueltan las marcas, se limpia el motivo y se refresca la tanda.
    - El resultado («Avanzaron: … / No avanzaron: …») se muestra en la propia barra o
      justo encima, sin obligar a bajar.
14. Si el usuario cambia un filtro y quedan marcadas personas que ya no se ven, esas marcas
    se conservan pero **no cuentan** en los botones. La barra lo dice: «N marcadas · M fuera
    de vista por los filtros», con un enlace «Soltar las ocultas».
15. «Descargar Excel» sigue descargando exactamente lo que se ve, incluidos los filtros
    nuevos.
16. Si los filtros dejan cero filas, la tabla dice cuántas hay sin filtrar y ofrece «Borrar
    filtros». La casilla de marcar todo queda deshabilitada.
17. Sin permiso de mover postulaciones: como hoy, «Descartar…» no se muestra y «Avanzar»
    falla por fila con el mensaje del backend. La barra fija no cambia esa regla.

## Referencia visual

Los bocetos fijan la **disposición**, no medidas ni colores. Rigen el `DESIGN.md` del
repositorio `RenaserOsPostulantes` (acento único violeta `--activo`, «EX · Portal del
candidato») y los componentes existentes del panel. **No** el `DESIGN.md` de este repositorio
(`RENASER-RECLUTAMIENTO`): describe un sistema distinto («RENASER OS», monocromático) y no
gobierna el panel vivo. El botón «Filtros», las etiquetas y la insignia van en
neutro, sin el violeta de «Avanzar» («te toca a ti»).

**1. Barra superior, sin filtros activos**

```
[🔍 Buscar por nombre   ] [Filtros ▾] [Columnas ▾]      [Calificar] [Descargar Excel (78)]
```

**2. Con filtros activos** (insignia con el conteo, «Borrar filtros» y etiquetas)

```
[🔍 Buscar por nombre   ] [Filtros ③ ▾] [Columnas ▾] [Borrar filtros]   [Calificar] [Descargar Excel (12)]
[Postulados del 12 al 15 sep ×] [IA: fallida ×] [Ciudad: Lima ×]
```

**3. Panel «Filtros» abierto en escritorio** (flota sobre la tabla, sin fondo oscuro; la
tabla de detrás no se mueve y se actualiza en vivo)

```
[Filtros ③ ▾]
┌────────────────────────────────────────────┐
│ Fecha de postulación                     ● │
│  (•) Un día   ( ) Rango                    │
│  Desde [12/09/2026]  Hasta [15/09/2026]    │
│  [Hoy] [Últimos 7 días] [Últimos 30 días]  │
│  Quien no tiene fecha queda fuera.         │
├────────────────────────────────────────────┤
│ Calificación con IA                      ● │
│  [ ] Calificada 40   [ ] En curso 3        │
│  [x] Fallida 5                             │
│  Es la calificación del currículum.        │
├────────────────────────────────────────────┤
│ Ciudad                                   ● │
│  (Lima 30) (Cusco 8) (Arequipa 5)          │
├────────────────────────────────────────────┤
│ Nota de la etapa   [desde] – [hasta]       │
│ Pretensión         [desde] – [hasta]       │
├────────────────────────────────────────────┤
│ Se ven 12 de 78    [Borrar filtros] [Listo]│
└────────────────────────────────────────────┘
```

La `●` marca las secciones que tienen un filtro activo. En el modo «Un día» solo aparece un
selector de fecha.

**4. Tabla con marcas y barra de acciones fija abajo**

```
[☑ Marcar todas las que se ven]  Puesto  Candidato  Nota …
[☑]  1   Ana Pérez …
[☑]  2   Luis Soto …
[☐]  3   …
…                                        (la tabla sigue con su scroll)
┌──────────────────────────────────────────────────────────────────┐
│ 4 personas marcadas · 2 fuera de vista   [Soltar las ocultas]    │
│ Motivo (obligatorio): [_____________________________]           │
│ [Avanzar a 4 personas]  [Descartar…]  [Soltar selección]         │
└──────────────────────────────────────────────────────────────────┘
   ↑ pegada al borde inferior de la ventana; sin marcadas no se muestra
```

La casilla de la cabecera puede estar vacía, marcada o en estado intermedio.

**5. Estado vacío por filtros**

```
┌───────────────────────────────────────────────┐
│ Ningún resultado con estos filtros.           │
│ Hay 78 sin filtrar.   [Borrar filtros]        │
└───────────────────────────────────────────────┘
```

La casilla «Marcar todas las que se ven» queda deshabilitada.

**6. Móvil**

```
┌──────────────────────────────┐
│ [🔍 Buscar ]  [Filtros ③]    │   «Columnas» y las acciones de la tanda pasan a «Más»
│ [IA: fallida ×] [Lima ×] →   │   etiquetas en una fila con scroll horizontal
│ … tabla …                    │
│ [Avanzar a 4] [Descartar…]   │   la barra fija pasa a dos filas y la tabla deja
└──────────────────────────────┘   espacio bajo la última fila para no taparla

Al abrir «Filtros»: hoja modal que sube desde abajo, con fondo atenuado,
las mismas secciones apiladas y un pie fijo con «Se ven X de Y» y «Borrar filtros».
```

**Estados a dibujar en la implementación:** normal, sin filtros, con muchos filtros (las
etiquetas saltan de línea), vacío, cargando (mientras se refresca la tanda) y error del
backend en la barra fija.

**Queda a criterio de quien implementa:** espaciado, animación de apertura, iconos e
identificadores de accesibilidad, siempre dentro del `DESIGN.md` de `RenaserOsPostulantes` y
sin cambiar el comportamiento descrito arriba.

## Criterios de aceptación

- AC-01: Dado un ranking, cuando el usuario mira la barra superior, entonces ve un botón
  con el texto exacto «Filtros» y ya no existe el desplegable «Ciudad, nota y pretensión».
- AC-02: Dado un ranking sin filtros activos, cuando se mira el botón «Filtros», entonces
  no muestra insignia ni aparece «Borrar filtros» ni la fila de etiquetas.
- AC-03: Dados tres filtros activos, cuando se mira el botón «Filtros», entonces la
  insignia dice 3 y hay una etiqueta con «×» por cada filtro.
- AC-04: Dado el panel «Filtros» abierto, cuando el usuario pulsa Esc o hace clic fuera,
  entonces se cierra sin perder los filtros aplicados; el foco vuelve al botón «Filtros».
- AC-04b: Dado el panel «Filtros» abierto en escritorio, cuando el usuario cambia un
  filtro, entonces la tabla se actualiza detrás sin moverse de su posición (ninguna fila ni
  encabezado cambia de lugar por abrir o cerrar el panel) y no aparece fondo oscuro.
- AC-05: Dado un filtro activo, cuando el usuario pulsa la «×» de su etiqueta, entonces
  solo se quita ese filtro y la tabla se actualiza.
- AC-06: Dados varios filtros activos, cuando el usuario pulsa «Borrar filtros», entonces
  se limpian todos (incluida la búsqueda), se ve toda la tanda del corte actual y las
  marcas no cambian.
- AC-07: Dados filtros activos en la etapa «Perfil integral», cuando el usuario cambia a
  «Prueba del puesto» y vuelve, entonces los filtros siguen aplicados y la insignia lo
  refleja; al cambiar a otra vacante, se reinician.
- AC-08: Dado un ranking con postulaciones de varios días, cuando el usuario elige «Un día»
  y una fecha, entonces solo se ven las postulaciones creadas ese día calendario y el
  contador «Se ven X de Y» es correcto.
- AC-09: Dado «Rango» con «Desde» y «Hasta», cuando se aplica, entonces se ven las
  postulaciones de ambos días inclusive y las intermedias.
- AC-10: Dado «Rango» con solo «Desde» (o solo «Hasta»), cuando se aplica, entonces se ven
  las de esa fecha en adelante (o hasta esa fecha).
- AC-11: Dado un rango con «Desde» posterior a «Hasta», cuando el usuario lo escribe,
  entonces el filtro no se aplica y aparece un aviso junto a los campos.
- AC-12: Dado el atajo «Últimos 7 días», cuando se pulsa, entonces «Desde» = hoy − 6 días y
  «Hasta» = hoy, y los campos quedan editables.
- AC-13: Dado el filtro de calificación con IA en «Fallida», cuando se aplica, entonces
  solo se ven las filas con ese estado; con varias casillas marcadas se ve la unión.
- AC-14: Dado un ranking con filtros activos, cuando el usuario pulsa la casilla de la
  cabecera, entonces quedan marcadas exactamente las filas visibles y ninguna oculta.
- AC-15: Dadas todas las visibles marcadas, cuando el usuario pulsa la casilla de la
  cabecera, entonces se sueltan; con algunas marcadas, la casilla está en estado
  intermedio.
- AC-16: Dado que hay al menos una fila marcada y la tabla es más alta que la ventana,
  cuando el usuario hace scroll por la tabla, entonces «Avanzar» y «Descartar…» siguen
  visibles sin bajar al final.
- AC-17: Dado que no hay filas marcadas, cuando se ve la tabla, entonces la barra fija no
  se muestra.
- AC-18: Dadas cinco filas marcadas y visibles, cuando el usuario escribe el motivo y pulsa
  «Avanzar», entonces avanzan las cinco con el comportamiento actual (resultado por
  persona, refresco de la tanda, marcas y motivo limpios).
- AC-19: Dadas filas marcadas, cuando el usuario pulsa «Descartar…», entonces se abre la
  ventana con los nombres escritos y no se descarta a nadie hasta confirmar.
- AC-20: Dadas seis filas marcadas y un filtro que oculta dos de ellas, cuando se mira la
  barra, entonces dice «4 personas marcadas · 2 fuera de vista» y los botones actúan sobre
  cuatro.
- AC-21: Dados filtros activos, cuando el usuario descarga el Excel, entonces el archivo
  contiene exactamente las filas visibles y en el mismo orden.
- AC-22: Dados filtros que dejan cero filas, cuando se mira la tabla, entonces se explica
  cuántas hay sin filtrar, se ofrece «Borrar filtros» y la casilla de cabecera está
  deshabilitada.
- AC-23: Dado un usuario sin permiso de mover postulaciones, cuando marca filas, entonces
  no ve «Descartar…» y el resto del comportamiento es el actual.
- AC-24: Dada una pantalla de móvil, cuando se abre «Filtros», entonces el panel es una hoja
  usable a ancho completo; y cuando hay filas marcadas, la barra fija no tapa la última fila
  y sus botones son accionables con el pulgar.
- AC-25: Dado un usuario que navega solo con teclado, cuando recorre la barra y el panel,
  entonces puede abrir «Filtros», cambiar cada control, cerrarlo y marcar todo con la
  casilla de cabecera; el contador de marcadas se anuncia a lectores de pantalla.

## Casos límite y errores

- Datos vacíos o inválidos: fechas vacías = filtro no aplicado; «Desde» > «Hasta» = aviso y
  sin aplicar (AC-11); filas sin `postuladoEn` quedan fuera del filtro de fecha y se avisa.
- Usuario sin permisos: sin cambios respecto a hoy; la barra fija no otorga acciones nuevas.
- Recarga, reintento o doble envío: las marcas y filtros siguen en memoria y se pierden al
  recargar (convención actual). Mientras «Avanzar» o «Descartar» trabajan, los botones
  quedan deshabilitados y el texto lo dice, para evitar doble pulsación.
- Otros casos relevantes:
  - **Marcar todo con filtro activo no marca lo oculto.** Es el error más caro (una carta
    de rechazo a quien no se quería), y por eso la ventana de descarte sigue listando los
    nombres.
  - Al conservar filtros entre etapas, una etapa puede dejar la tabla vacía (por ejemplo,
    un rango de nota que en otra etapa no tiene filas). El estado vacío y las etiquetas
    activas explican por qué y ofrecen quitarlas (AC-22).
  - Cambio de zona horaria del navegador: el día calendario se calcula con la del navegador
    actual; no hay garantía entre dos equipos en zonas distintas.
  - Tanda de cientos de filas: marcar todo debe seguir siendo instantáneo y la barra fija
    no debe reescribir la tabla al hacer scroll.
  - La barra fija no debe tapar el detalle abierto de una fila ni el pie de página.

## Datos y compatibilidad

- Información que debe guardarse: ninguna. Los filtros y las marcas viven en memoria.
- Backend: añadir a `FilaRanking` (DTO de perfil integral) el campo `postuladoEn`
  (`Instant`, anulable, desde `Postulacion.creadoEn`). No hay migración: la columna ya
  existe. `estadoCalificacion` ya viaja en la fila.
- Frontend:
  - Añadir `postuladoEn` a `FilaRanking` en `api/tipos.ts`.
  - Ampliar `Filtros` / `SIN_FILTROS` / `hayFiltroPuesto` / `filtrarFino` en `ranking.ts`
    con fecha (modo día o rango) y calificación con IA, con pruebas unitarias por cada
    regla (patrón de `ranking.test.ts`).
  - Elevar el estado de los filtros por encima del cambio de etapa para conservarlo, y
    reiniciarlo al cambiar de vacante.
  - Nuevo componente del panel «Filtros» (anclado en escritorio, hoja en móvil) y la fila de
    etiquetas activas; nueva barra fija de acciones que sustituye a la mesa inferior.
  - Respetar el `DESIGN.md` de `RenaserOsPostulantes`: el botón «Filtros», las etiquetas y
    la insignia de conteo son neutros, sin el violeta `--activo` que ya significa
    «te toca a ti» en «Avanzar».
- Comportamiento para registros existentes: postulaciones con `creadoEn` nulo quedan fuera
  del filtro de fecha y el control lo indica.
- Restricciones de compatibilidad: añadir un campo al DTO es compatible hacia atrás. El
  contrato del Excel no cambia. `estadoCalificacion` es de la cola del currículum, no de la
  etapa (advertencia de `ranking.ts`); por eso la sección lo aclara.

## Verificación

- Actor/rol y datos de prueba necesarios: un usuario con permiso de mover postulaciones y
  otro sin él; una vacante con al menos 30 postulaciones creadas en 5 o más días distintos,
  con estados de calificación variados (incluida una fallida) y ciudades, notas y
  pretensiones distintas.
- Recorridos que deben quedar como E2E repetibles:
  1. Abrir «Filtros», filtrar por un día y por un rango; comprobar conteos, etiquetas e
     insignia; quitar una etiqueta y luego «Borrar filtros».
  2. Filtrar por calificación con IA «Fallida».
  3. Aplicar filtros, cambiar de etapa y volver; comprobar que se conservan.
  4. Marcar todo con un filtro activo y avanzar; comprobar que solo avanzaron las visibles.
  5. Marcar varias, hacer scroll y descartar desde la barra fija; comprobar la ventana con
     nombres.
  6. Repetir marcar todo en al menos dos etapas distintas.
- Riesgos que QA debe explorar con navegador: panel «Filtros» en escritorio y móvil (Esc,
  clic fuera, foco); barra fija sobre el pie de página y con el detalle de una fila
  abierto; conservar filtros entre etapas con resultado vacío; foco y teclado; zona horaria
  en el borde de medianoche; aspecto de la insignia y las etiquetas frente al `DESIGN.md`
  de `RenaserOsPostulantes`.
- Resultado que quiero comprobar personalmente: en una vacante real con tabla larga, abrir
  «Filtros», filtrar por fecha, marcar todo y llegar a «Avanzar» y «Descartar…» sin bajar
  hasta el final. Hay login de por medio, así que lo mira el usuario.

## Dependencias y dudas

- Requiere: cambios en los dos repositorios (backend `RENASER-RECLUTAMIENTO`, frontend
  `RenaserOsPostulantes`). El backend solo añade un campo; el resto es del frontend.
- Decisiones pendientes: ninguna.

## Decisiones tomadas al implementar

Aprobadas por el usuario con la entrega. No cambian los criterios de arriba; dicen cómo se
leyeron donde la spec no entraba en detalle.

- La búsqueda por nombre no cuenta en la insignia ni lleva etiqueta, pero «Borrar filtros» sí
  la limpia (punto 5).
- Un rango de fechas al revés («Desde» posterior a «Hasta») no cuenta como filtro activo: no
  suma en la insignia ni sale como etiqueta.
- Si un filtro esconde a todas las marcadas, la barra fija no se muestra (punto 13, tal cual).
  Las marcas se conservan y vuelven a contar al quitar el filtro.
- En «Pretensión», cuando la vacante no publica su remuneración, la ayuda dice «La vacante no
  publicó pretensión».
- El resultado de avanzar o descartar se queda en la barra hasta cerrarlo con «×».
- Foco: Esc, «Listo» y un clic fuera sobre algo que no es un control devuelven el foco a
  «Filtros». Un clic fuera sobre un control (casilla, fila, botón) no se pierde: un clic en una
  fila cierra el panel y abre su ficha. Salir del panel con Tab lo cierra. Los «Borrar filtros»
  de la barra y de la tabla vacía llevan el foco a «Filtros»; el del pie queda apagado con
  `aria-disabled` y conserva el foco. En ventanas estrechas el panel se corre para no
  desbordar.

Cómo quedó en pantalla: `docs/PANEL.md` del repositorio `RenaserOsPostulantes`, «Filtrar la
tanda y actuar sobre muchas a la vez».
