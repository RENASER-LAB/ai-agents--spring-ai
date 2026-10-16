# El Excel de la prueba del puesto usa la prueba vigente
Estado: TERMINADO

## Objetivo

Que el Excel de la prueba del puesto saque **solo las columnas de criterio de la prueba que la
vacante tiene puesta hoy**, y no las de pruebas anteriores que algún candidato rindió antes de
que se cambiara, sin tocar el historial de nadie. Y que, en los dos Excel del ranking, **las
cabeceras se lean enteras al abrir el archivo**, sin agrandar la fila a mano.

## Contexto

- **Comprobado con archivos reales.** Los Excel `ranking-prueba-puesto-vacante-13` del 18/09
  y del 24/09 traen, después de los 7 criterios de la prueba vigente —«Experiencia y magnitud
  de lo administrado» … «Orientación a resultados y plan de crecimiento», que suman 100—,
  cuatro columnas más: «Criterio de priorización (pts /40)», «Qué deja fuera (pts /25)»,
  «Claridad (pts /20)» y «Reacción al cambio (pts /15)». **Ninguna de las 20 filas
  descargadas tiene una sola nota en ellas.** Son la rúbrica de «Prueba de ejecución · demo»,
  que crea `scripts/sembrar-datos-de-prueba.py`.
- **Por qué pasa.** La vacante 13 tenía puesta la versión 1 —la demo— y se cambió a la de
  Administrador cuando al menos dos candidatos ya la habían entregado (lo cuenta
  `scripts/cargar-prueba-de-la-empresa.py`). Cada candidato queda atado a la versión con la
  que abrió su prueba (RF-90), y el ranking le pinta a cada fila la rúbrica de **su** versión.
  El Excel arma sus columnas juntando las de **toda la tanda**, incluso las filas que el
  filtro deja fuera: basta un candidato con la demo para que salgan sus cuatro columnas.
- **No aparecerán casos nuevos.** Desde el 27/08 (#45), una vacante con postulantes no puede
  cambiar de prueba. El arreglo es para las vacantes que ya quedaron mezcladas.
- **Lo que no está afectado** (comprobado en los archivos):
  - la vacante 14 tiene una sola versión;
  - la vacante 1 tiene la demo como prueba vigente, así que sus 4 columnas son las correctas;
  - el Excel de perfil integral lleva los 8 criterios del currículum, que son globales.
- **Por qué no se borra al candidato.** Se descartó el 25/09/2026 por cuatro motivos:
  - no hay forma de borrar una postulación salvo SQL directo en producción;
  - unas 18 tablas dependen de ella y ninguna en cascada;
  - es una persona real;
  - puede no ser uno solo.

  Descartarlo tampoco sirve, porque el ranking incluye también a los cerrados.
- **Las cabeceras no se leen enteras.** En el Excel del 24/09, la fila 1 tiene el ajuste de
  texto activado pero ninguna altura, así que se abre con la altura de una línea. Las columnas
  de criterio miden 14 caracteres de ancho, y el rótulo más largo, «Orientación a resultados y
  plan de crecimiento (pts /10)», necesita unas 5 líneas. No hay plantilla guardada: el archivo
  se genera por código en cada descarga (`ServicioExcelRankingImpl.encabezar`), y ese código es
  el mismo para los dos Excel del ranking.
- **Pantallas o procesos afectados:** los dos Excel del ranking. En el de la prueba del puesto
  cambian las columnas y la cabecera; en el de perfil integral, solo la cabecera. El panel no
  cambia.
- **Referencias:**
  - [filtros y selección en lote](filtros-y-seleccion-en-lote-ranking.md): la descarga desde
    un recorte;
  - [etiquetas del ranking](etiquetas-ranking-prueba-puesto.md): los textos de nota;
  - RF-90 en [requisitos](../../docs/01-REQUISITOS-FUNCIONALES.md).

## Alcance

- Incluye:
  - que las columnas de criterio del Excel de la prueba del puesto salgan de la rúbrica de la
    prueba vigente de la vacante, y no de juntar las de las filas;
  - que la fila de cabeceras de los dos Excel del ranking salga con altura suficiente para leer
    los rótulos enteros.
- Queda fuera, **decidido el 25/09/2026 para mantenerlo mínimo**:
  - **La tabla del ranking del panel.** Al encender sus columnas de criterio seguirá sacando
    las de la prueba anterior, así que en la vacante 13 el Excel y la pantalla dejarán de
    tener las mismas columnas de criterio.
  - **Marcar de forma especial a quien rindió otra prueba**, o cambiar sus textos de nota, su
    nota combinada o su lugar en el orden.
  - **La ficha del candidato.**
  - **Darle la prueba vigente** a quien quedó apuntando a la anterior sin abrirla.
  - **Cómo se califica.** Cada quien se sigue calificando contra la prueba que rindió.
  - **El Excel de perfil integral**, salvo la altura de su cabecera, y las demás etapas.
  - **Los anchos de las columnas** y la altura de las filas de candidatos.
  - **Tocar datos.** No se borra, repunta ni recalcula nada, y no hay migración.

## Comportamiento esperado

1. La **prueba vigente** es la que la vacante tiene puesta hoy en su ficha.
2. En el Excel de la prueba del puesto, las columnas de criterio son **exactamente** las de la
   rúbrica vigente, en el orden de la rúbrica y con el rótulo de hoy («Manejo y control de
   caja (pts /20)»). Los criterios homónimos se desambiguan como hoy.
3. Esas columnas salen siempre, aunque nadie tenga nota todavía y aunque el filtro deje fuera
   a todos los que tienen nota. No sale ninguna columna de otra prueba.
4. Las celdas se llenan como hoy. La fila de quien rindió la prueba anterior sale con esas
   columnas en blanco, porque sus criterios son otros.
5. **Ninguna nota se pierde del archivo.** Las de la prueba anterior siguen en «Justificación
   detallada», criterio a criterio, como hoy.
6. No cambia nada más del archivo: nombre, hoja «Datos», orden de las filas, resto de
   columnas, textos de nota, nota combinada, enlace del CV y pie. La única excepción es la
   altura de la cabecera (puntos 10–12).
7. Una vacante sin prueba vigente, o que rinde el cuestionario técnico, sale sin columnas de
   criterio.
8. Si la prueba vigente no se puede leer, el Excel sale igual, sin columnas de criterio, y el
   fallo queda en el registro. Las notas siguen en «Justificación detallada». La descarga no
   falla por esto.
9. Descargar el Excel no escribe nada.

### La cabecera se lee entera

10. En los dos Excel del ranking —prueba del puesto y perfil integral—, la fila 1 se abre en
    Excel y en LibreOffice con altura suficiente para leer entero el rótulo que más líneas
    necesita. No hay que agrandarla a mano.
11. La altura sale de los rótulos de ese archivo. Una rúbrica con nombres cortos da una
    cabecera baja, y una con nombres largos, una más alta. Los anchos de columna no cambian.
12. Hay un tope de 8 líneas, para que un nombre de criterio desmesurado no deje una cabecera
    que ocupe media pantalla. Si un rótulo lo pasa, se ve entero al seleccionar la celda. La
    fila sigue fija al desplazarse, como hoy.

## Referencia visual

No cambia ninguna pantalla. Así queda el Excel de la vacante 13.

Antes:

```
# | Candidato | … | Nota Examen Técnico /100 | Nota Perfil Integral /100 | Experiencia… (pts /15) | … 7 criterios … | Criterio de priorización (pts /40) | Qué deja fuera (pts /25) | Claridad (pts /20) | Reacción al cambio (pts /15) | Nota Combinada /100 | Justificación resumida | Justificación detallada
```

Después:

```
# | Candidato | … | Nota Examen Técnico /100 | Nota Perfil Integral /100 | Experiencia… (pts /15) | … 7 criterios … | Nota Combinada /100 | Justificación resumida | Justificación detallada
```

## Criterios de aceptación

- AC-01: Dada una vacante con prueba vigente A (7 criterios) y candidatos que abrieron una
  versión anterior B (4 criterios), cuando se descarga el Excel de la prueba, entonces las
  columnas de criterio son exactamente las 7 de A, en el orden de su rúbrica, y ninguna de B.
- AC-02: Dada la misma vacante, cuando el filtro deja fuera a todos los que rindieron A,
  entonces el Excel sigue teniendo las 7 columnas de A, en blanco.
- AC-03: Dado un candidato que rindió B y tiene notas en sus criterios, cuando se descarga el
  Excel, entonces sus celdas de criterio están en blanco y sus notas de B aparecen en su
  «Justificación detallada».
- AC-04: Dada una vacante cuyos candidatos rindieron todos la vigente (como la 14), cuando se
  descarga el Excel, entonces el archivo es igual al de hoy —mismas columnas, celdas y pie—
  salvo la altura de la cabecera.
- AC-05: Dada una vacante con prueba vigente en la que nadie ha llegado aún a la etapa, cuando
  se descarga el Excel, entonces salen las columnas de la vigente, vacías.
- AC-06: Dada una vacante que rinde el cuestionario técnico, cuando se descarga el Excel,
  entonces no hay columnas de criterio.
- AC-07: Dada una vacante cuya prueba vigente no se puede leer, cuando se descarga el Excel,
  entonces se descarga sin columnas de criterio y sin error.
- AC-08: Dado el Excel de perfil integral de cualquier vacante, cuando se descarga, entonces
  no cambia nada salvo la altura de la cabecera.
- AC-09: Dada cualquier vacante, cuando se abre el ranking del panel, entonces la respuesta y
  la pantalla son las de hoy.
- AC-10: Dado el Excel de la prueba de la vacante 13, cuando se abre en Excel o en
  LibreOffice sin tocar nada, entonces «Orientación a resultados y plan de crecimiento (pts
  /10)» y todas las demás cabeceras se leen enteras.
- AC-11: Dado el Excel de perfil integral de cualquier vacante, cuando se abre sin tocar nada,
  entonces todas sus cabeceras se leen enteras.
- AC-12: Dada una rúbrica con un criterio cuyo rótulo necesitaría más de 8 líneas, cuando se
  descarga el Excel, entonces la cabecera mide 8 líneas, el rótulo completo está en la celda y
  la descarga no falla.
- AC-13: Dado cualquier Excel del ranking, cuando se desplaza hacia abajo, entonces la fila de
  cabeceras sigue fija y los anchos de columna son los de hoy.

## Casos límite y errores

- **Datos vacíos o inválidos:** están cubiertos en los puntos 3, 7 y 8. Una nota 0 sigue
  siendo una nota.
- **Usuario sin permisos:** los mismos que hoy. La prueba vigente se obtiene por el mismo
  camino que ya aplica el filtro por organización y el permiso; el Excel no consulta nada
  fuera de él.
- **Recarga, reintento o doble envío:** cada descarga se calcula de nuevo, sin escribir nada.
- **Limitaciones aceptadas** en esta versión mínima:
  - **Un criterio de B con el mismo nombre, código y techo que uno de A** llenaría la celda de
    A, porque las celdas se emparejan así desde el 16/09. En la vacante 13 no pasa: ningún
    criterio de la demo coincide con los de Administrador.
  - **Si alguien que rindió B tiene nota de etapa**, su «Nota Examen Técnico» y su «Nota
    Combinada» siguen mostrando esa cifra, que es de otra prueba. En las 20 filas descargadas
    el 24/09 no hay ninguna nota en los criterios de la demo; en el resto de la tanda no se
    sabe (ver la consulta en «Dependencias»).

## Datos y compatibilidad

- **Información que debe guardarse:** ninguna. No hay migración.
- **Registros existentes:** quedan intactos. En cuanto se despliegue, el Excel de la vacante
  13 deja de sacar las cuatro columnas de la demo.
- **Restricciones de compatibilidad:**
  - Si el ranking tiene que llevar la rúbrica vigente para que el Excel la use, lleva un
    campo nuevo, y el panel actual lo ignora.
  - El comentario de `ServicioExcelRankingImpl` que promete «las mismas columnas que la
    pantalla» tiene que decir la excepción: el Excel usa la prueba vigente y la tabla junta
    las de las filas.

## Verificación

- **Actor/rol:** un usuario del panel que hoy puede ver el ranking y descargar el Excel.
- **Datos de prueba:**
  - una vacante con prueba vigente A y candidatos que abrieron una versión anterior B, uno de
    ellos con notas en B;
  - de control: una vacante de una sola versión, una que rinde el cuestionario técnico y el
    Excel de perfil integral.
  - ⚠️ **La mezcla ya no se puede fabricar por la API**, porque la regla del 27/08 impide
    cambiar la prueba con postulantes dentro. Se siembra directamente en la base, en un test
    de integración, igual que en producción llegó desde antes de esa regla.
- **Recorridos repetibles:** la mayor parte se cubre con tests de integración que leen el
  archivo generado (cabeceras, celdas y justificación). E2E opcional: descargar el Excel desde
  el panel y comprobar las cabeceras.
- **Riesgos que QA debe explorar:**
  - que el Excel de una vacante de una sola versión cambie en algo;
  - que se pierda una nota de la prueba anterior;
  - que un fallo al leer la prueba vigente tumbe la descarga;
  - que la cabecera se lea entera en LibreOffice pero no en Excel, o al revés. La altura tiene
    que ir escrita en el archivo, no depender de que el programa la recalcule al abrir.
- **Resultado que quiero comprobar personalmente:** después del despliegue, descargar el
  Excel de la prueba de la vacante 13 y ver solo las 7 columnas de Administrador, sin
  «Criterio de priorización», «Qué deja fuera», «Claridad» ni «Reacción al cambio», y con las
  cabeceras legibles enteras sin agrandar la fila. Necesita sesión en el panel, así que lo
  comprueba el usuario.

## Dependencias y dudas

- **Requiere:** solo el backend de este repositorio.
- **Opcional**, para saber cuántas vacantes y candidatos están mezclados y si alguno tiene
  nota en la prueba anterior (la segunda limitación aceptada): una consulta de solo lectura
  en producción.

  ```sql
  select p.vacante_id,
         v.version_plantilla_prueba_id as vigente,
         i.version_plantilla_prueba_id as rendida,
         count(*)                      as candidatos,
         count(i.iniciado_en)          as abiertas,
         count(i.entregado_en)         as entregadas,
         count(ne.id)                  as con_nota
  from intento_prueba i
  join postulacion p on p.id = i.postulacion_id
  join vacante v     on v.id = p.vacante_id
  left join nota_etapa ne on ne.postulacion_id = p.id and ne.etapa_codigo = 'PRUEBA_PUESTO'
  where i.version_plantilla_prueba_id is distinct from v.version_plantilla_prueba_id
  group by 1, 2, 3
  order by 1;
  ```

- **Decisiones pendientes:** ninguna. Si más adelante molesta lo que quedó fuera —la tabla
  del panel, marcar a quien rindió otra prueba, sacar su nota del orden—, es otra spec.
