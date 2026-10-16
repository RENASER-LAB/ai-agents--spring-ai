# Arreglar el selector de columnas del ranking
Estado: TERMINADO

## Objetivo
Quien revisa el ranking de una vacante en el panel puede ocultar columnas con el botón
«Columnas». Hoy ese menú tiene dos fallos:

- La casilla del ponderado ocupa un párrafo entero en vez de su nombre.
- Al ocultar «Veredicto», la tabla se descuadra: los datos de cada fila quedan bajo la
  cabecera equivocada y a la derecha queda un hueco en blanco.

El resultado debe ser un menú que se lea de un vistazo y una tabla que siga cuadrada oculte
lo que oculte.

## Contexto
- **Comportamiento actual.** Todo está en el repositorio del frontend,
  `~/Documentos/RenaserOsPostulantes`. El backend no se toca.
  - **El menú «Columnas».** Está encima de la tabla del ranking, en las cinco pestañas de la
    vacante (`src/panel/vacantes/Vacante.tsx:1245-1300`). Lista una casilla por cada columna
    que se puede ocultar, en este orden:
    - «#».
    - «Pretensión», si alguien la declaró.
    - «Ponderado», solo en la pestaña de la prueba del puesto.
    - «Veredicto».
    - Los criterios, si «Ver los criterios en la tabla» está encendido.
    - «Estado».
    - «Ciudad», si alguien la tiene.
  - **Fallo 1: el rótulo del ponderado.** El menú escribe `c.completo ?? c.titulo`
    (`Vacante.tsx:1286`). El `completo` del ponderado es `PONDERADO_EXPLICADO`
    (`src/panel/vacantes/ranking.ts:230` y `:2045`): «Ponderado sobre 100 de lo que ya
    rindió… No es la nota final…». Por eso la casilla ocupa seis líneas.
    - El mismo párrafo acaba en el título emergente del botón de ordenar de esa cabecera,
      «Ordenar por Ponderado sobre 100 de…» (`Vacante.tsx:1489`).
    - `completo` está pensado para el nombre entero de una abreviatura: la inicial de un
      criterio da su nombre y «Nota» da «Nota de la prueba del puesto». El ponderado lo usa
      para guardar una explicación.
  - **Fallo 2: la celda del veredicto.** La cabecera sale de la lista de columnas visibles,
    pero las celdas de cada fila se pintan a mano. Cada celda ocultable lleva su condición
    `ve('<clave>')`, salvo la del veredicto (`Vacante.tsx`, el `<td className={estilos.columnaCifra}>`
    que sigue al comentario «El veredicto es el grupo de prioridad…», hacia la línea 1640).
    - **Qué se ve** (captura del usuario): al apagar Veredicto se va la cabecera, pero la
      celda sigue en cada fila. Cada fila tiene una celda más que la cabecera.
      - «No priorizado» queda bajo ESTADO.
      - El estado queda bajo CIUDAD.
      - La ciudad queda en una columna sin cabecera.
      - La fila de cabeceras termina antes que la tabla y deja un hueco en blanco a la
        derecha.
    - Pasa en las cinco pestañas.
    - La leyenda de veredictos de debajo de la tabla sí se oculta bien, porque usa
      `ve('veredicto')` (`Vacante.tsx:1827`).
  - **Por qué no lo detectó ningún test.** Los tests de «elegir qué columnas se ven»
    (`src/panel/vacantes/Vacante.test.tsx:1157-1210`) solo cuentan cabeceras (`thead th`),
    nunca celdas de las filas.
    - El que se titula «la quita de la cabecera y de todas las filas» apaga «Estado», que sí
      tenía su condición.
    - El de «Ver todas» apaga Veredicto, pero también mira solo la cabecera.
- **Pantallas o procesos afectados.** El ranking de cada vacante en el panel del equipo, en
  sus cinco pestañas, en escritorio y en el teléfono. En el teléfono, «Columnas» está dentro
  de «Más» y es el mismo componente.
- **Referencias existentes.**
  - La lista de columnas: `columnasDelRanking` y `columnasVisibles` en `ranking.ts:1983-2107`.
  - El PR #34, que añadió el ponderado.
  - [DESIGN.md](../../../RenaserOsPostulantes/DESIGN.md).

## Alcance
- **Incluye:**
  1. La casilla del ponderado se lee «Ponderado», a secas.
  2. La explicación del ponderado **no se pierde**: sigue saliendo al pasar el cursor por la
     cabecera «Ponderado» de la tabla.
     - El botón de ordenar dice «Ordenar por Ponderado».
     - Propuesta: separar el nombre de su explicación con un campo nuevo en
       `ColumnaDelRanking` (por ejemplo `ayuda`) para el título emergente de la cabecera, y
       quitar `PONDERADO_EXPLICADO` de `completo`. Queda a criterio de quien implementa,
       siempre que se cumplan AC-01 a AC-03.
  3. La celda del veredicto se pinta con la misma condición que su cabecera,
     `ve('veredicto')`.
  4. **Un test que impida que vuelva a pasar con cualquier columna.** Para cada columna
     ocultable, en cada pestaña, comprueba dos cosas tras apagarla:
     - Cada fila tiene tantas celdas como cabeceras.
     - La celda que queda bajo «Estado» es la del estado.

     Va en `Vacante.test.tsx` y recorre la lista de columnas que da la propia tabla, no una
     lista escrita a mano. Así una columna nueva queda cubierta sin tocar el test.
  5. Corregir los tests actuales que dicen «de todas las filas» para que miren también las
     filas.
- **Queda fuera:**
  - Pintar las celdas desde la lista de columnas en vez de a mano. Evitaría este tipo de
    fallo de raíz, pero cambia el cuerpo entero de la tabla. El test del punto 4 basta como
    protección.
  - Guardar entre recargas o entre pestañas las columnas ocultas: siguen en memoria como
    hoy, y cambiar de pestaña las vuelve a mostrar.
  - Cambiar qué columnas se pueden ocultar, su orden o el diseño del menú.
  - El Excel de la tanda, que no depende de este menú.
  - El texto de la explicación del ponderado y el desglose de cada celda
    (`desgloseDelPonderado`), que no cambian.

## Comportamiento esperado
1. Quien revisa abre la pestaña «Prueba del puesto» y pulsa «Columnas». Ve una lista corta
   y de una línea por casilla: «#», «Ponderado», «Veredicto», «Estado», «Ciudad», etc.
2. Pasa el cursor por la cabecera «Ponderado» de la tabla y lee la explicación de siempre:
   qué mezcla y que no es la nota final.
3. Desmarca «Veredicto».
   - La columna desaparece entera, cabecera y celdas.
   - «Estado» y «Ciudad» siguen con sus datos debajo.
   - La tabla ocupa el mismo ancho sin huecos.
   - La leyenda de veredictos de debajo desaparece.
   - El botón dice «Columnas · 1 ocultas».
4. La vuelve a marcar, o pulsa «Ver todas», y la columna reaparece en su sitio: después de
   Nota o Ponderado y antes de Estado o de los criterios.
5. Lo mismo vale para cualquier otra columna ocultable y para cualquier combinación de
   varias.

## Referencia visual
No hay cambios de diseño: se reutilizan el menú y la tabla tal como están. Solo cambia un
rótulo y desaparece una celda que sobraba. El menú queda así en la pestaña de la prueba:

```
☐ Ver los criterios en la tabla
  6 columnas más, una por criterio, con el fondo
  teñido según lo que cubre cada nota.
────────────────────────────────
☑ #
☑ Ponderado
☑ Veredicto
☑ Estado
☑ Ciudad
```

- **Estados.** El menú no tiene estados nuevos. En la tabla hay que mirar:
  - Con filas y Veredicto apagado.
  - Vacía por un filtro, con Veredicto apagado: el mensaje ocupa todo el ancho.
  - Con la ficha de un candidato abierta, con Veredicto apagado: la ficha ocupa todo el
    ancho.
  - Con los criterios encendidos.
- **Móvil y escritorio.** En el teléfono el menú vive dentro de «Más» y se comporta igual.
  La tabla se desplaza en horizontal como hoy.

## Criterios de aceptación
- AC-01: Dado el ranking en la pestaña «Prueba del puesto», cuando se abre «Columnas»,
  entonces la casilla del ponderado dice exactamente «Ponderado», en una línea, y ninguna
  casilla muestra la explicación.
- AC-02: Dado ese mismo ranking, cuando se pasa el cursor por la cabecera «Ponderado» de la
  tabla, entonces sale la explicación de `PONDERADO_EXPLICADO`, que dice que no es la nota
  final.
- AC-03: Dado ese mismo ranking, entonces el título emergente del botón de ordenar por
  ponderado es «Ordenar por Ponderado», sin el párrafo.
- AC-04: Dado cualquier ranking con filas, cuando se desmarca «Veredicto», entonces:
  - Ninguna fila tiene celda de veredicto.
  - Cada fila tiene tantas celdas como cabeceras.
  - Bajo «Estado» se lee el estado de cada persona y bajo «Ciudad» su ciudad.
  - La fila de cabeceras llega hasta el borde derecho de la tabla.
- AC-05: Dado Veredicto desmarcado, entonces la leyenda de veredictos de debajo de la tabla
  no sale y el botón dice «1 ocultas».
- AC-06: Dado Veredicto desmarcado, cuando se vuelve a marcar o se pulsa «Ver todas»,
  entonces la columna reaparece, con cabecera y celdas, en su posición de siempre.
- AC-07: Dada cualquiera de las cinco pestañas, con los criterios encendidos o apagados,
  cuando se apaga cualquier columna ocultable (o varias a la vez), entonces cada fila tiene
  tantas celdas como cabeceras y cada celda cae bajo su propia cabecera.
- AC-08: Dado Veredicto desmarcado, cuando se abre la ficha de un candidato, entonces la
  ficha ocupa el ancho entero de la tabla.
- AC-09: Dado Veredicto desmarcado y un filtro que deja la tabla vacía, entonces el mensaje
  de «no hay» ocupa el ancho entero.
- AC-10: Dados los criterios encendidos, cuando se abre «Columnas», entonces cada criterio
  sigue saliendo con su nombre entero y no con la inicial.
- AC-11: Dado un teléfono de 360 px, cuando se abre «Más» → «Columnas», entonces la casilla
  dice «Ponderado», y al desmarcar «Veredicto» la tabla no se descuadra.

## Casos límite y errores
- **Datos vacíos o inválidos.** Si nadie tiene veredicto todavía, la columna muestra
  guiones. Al apagarla deben desaparecer también los guiones.
- **Usuario sin permisos.** No cambia. Sin el permiso de pretensión, esa columna no existe y
  no aparece en el menú, como hoy.
- **Recarga, reintento o doble envío.** Al recargar vuelven todas las columnas, que es lo de
  hoy. Marcar y desmarcar rápido varias veces no puede dejar la tabla descuadrada.
- **Otros casos relevantes:**
  - Veredicto, Estado y Ciudad apagadas a la vez: quedan Nota, Ponderado y los criterios
    alineados.
  - Con los criterios encendidos y Veredicto apagado: la columna del candidato sigue clavada
    a la izquierda al desplazar, y los criterios quedan bajo sus letras.
  - Cambiar de pestaña con Veredicto apagado: la pestaña nueva lo muestra, porque la tabla
    se monta de nuevo en cada pestaña.
  - Apagar Ponderado y cambiar a otra pestaña: no puede quedar ninguna celda ni hueco del
    ponderado.

## Datos y compatibilidad
No aplica: no se guarda nada, no hay migraciones y el backend no cambia.

## Verificación
- **Actor, rol y datos de prueba.** Una cuenta del panel con rol Talento o Dirección y una
  vacante con, al menos:
  - dos candidatos en la pestaña «Prueba del puesto», con veredicto y ponderado;
  - ciudad, para que exista la columna Ciudad;
  - notas por criterio, para poder encender los criterios.
- **Recorridos que deben quedar como E2E repetibles.** En un spec nuevo,
  `herramientas/e2e/` con el siguiente número libre (el 41 ya es `41-logotipo`), o sumados a
  `34-filtros-y-seleccion-qa.spec.ts`:
  1. Abrir «Columnas» en la pestaña de la prueba y comprobar que la casilla dice
     «Ponderado» (AC-01).
  2. Desmarcar «Veredicto» y comprobar AC-04 y AC-05: se cuentan `thead th` y los `td` de
     cada fila, y se comprueba el texto bajo «Estado».
  3. «Ver todas» y comprobar AC-06.
  4. Lo mismo a 360 px dentro de «Más» (AC-11).
- **Riesgos que QA debe explorar con navegador:**
  - Apagar cada columna ocultable, una a una y combinadas, en las cinco pestañas, con y sin
    criterios.
  - Abrir una ficha con columnas apagadas.
  - Dejar la tabla vacía con un filtro.
  - Hacer capturas en escritorio y a 360 px.
- **Resultado que quiero comprobar personalmente.** Repetir lo de la captura: en el panel,
  pestaña «Prueba del puesto», abrir «Columnas», ver «Ponderado» a secas y apagar
  «Veredicto». La tabla debe quedar alineada y sin hueco a la derecha. Hay que entrar con
  sesión al panel, así que esta comprobación visual la hace el usuario.

## Dependencias y dudas
- **Requiere:** nada.
- **Relacionada:** [veredicto-solo-en-el-perfil-integral.md](../veredicto-solo-en-el-perfil-integral.md)
  quita el Veredicto de todas las pestañas salvo «Perfil integral». Conviene implementar esta
  primero. Si aquella ya está hecha, los recorridos que apagan Veredicto se hacen en «Perfil
  integral» y el menú de la prueba ya no lleva esa casilla.
- **Decisiones pendientes:** ninguna.
- **Decisión tomada:** la explicación del ponderado se queda en el título emergente de la
  cabecera, y no en el menú ni en un texto visible.
