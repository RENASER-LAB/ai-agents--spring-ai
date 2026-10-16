# El Veredicto solo en la pestaña del perfil integral
Estado: TERMINADO

## Objetivo
En el ranking de cada vacante, la columna «Veredicto» sale en las cinco pestañas. Su valor
se calcula una sola vez, en el perfil integral, así que en las demás pestañas contradice la
nota que tiene al lado. Por ejemplo, alguien puede tener un 95 en la prueba del puesto y
«No priorizado» en la columna de al lado.

Esta spec deja el Veredicto solo en la pestaña «Perfil integral», que es la etapa de la que
sale. En «Prueba del puesto», la columna de resumen pasa a ser «Ponderado», que ya existe.

## Contexto
- **Comportamiento actual.** Todo está en el repositorio del frontend,
  `~/Documentos/RenaserOsPostulantes`. El backend no se toca.
  - **Qué es el Veredicto.** Es el grupo de prioridad (`grupoPrioridad`) de la postulación.
    El backend lo asigna una sola vez, cuando la IA termina de calificar el currículum en el
    perfil integral, y no lo recalcula en ninguna etapa posterior.
    - Lo calcula `grupoDe` en `PuenteCalificacionIaImpl.java:821-849`.
    - «Alta»: la nota del perfil es 80 o más y no hay ningún hallazgo de riesgo crítico.
    - «Con riesgo»: la nota es 65 o más, o el potencial es 80 o más.
    - «No priorizado»: el resto.
    - «Incompatible» está en el catálogo, pero hoy ningún camino del código lo escribe.
  - **Dónde se pinta.** Se pinta igual en las cinco pestañas: «Perfil integral», «Prueba del
    puesto», «Simulación», «Validación» y «Decisión».
    - **La columna.** `columnasDelRanking` la añade sin mirar la etapa, con
      `{ clave: 'veredicto', titulo: 'Veredicto', estrecha: true, ocultable: true }`
      (`src/panel/vacantes/ranking.ts:2073`).
    - **La celda.** Es el `<td className={estilos.columnaCifra}>` con
      `rotuloCortoDelGrupo(fila.grupoPrioridad)` (`src/panel/vacantes/Vacante.tsx`, hacia la
      línea 1677). Desde el 28/09/2026 ya se pinta con `ve('veredicto')`, como su cabecera.
    - **La leyenda de debajo de la tabla.** «Alta Prioridad alta…», con `ve('veredicto')`
      (`Vacante.tsx:1861`).
    - **La casilla «Veredicto» del menú «Columnas».** Sale de la misma lista de columnas.
  - **De dónde viene la confusión.** El informe de la clienta traía un «Fuerte / Sólido /
    Parcial» calculado con la nota de la **prueba**, con cortes en 80 y 65. Al construirlo
    se reutilizó el grupo de prioridad porque usa los mismos cortes, pero se calcula con la
    nota del **perfil** (comentario en `Vacante.tsx:1647-1668`).
    - Un test fija a propósito que el Veredicto esté en todas las pestañas: «Veredicto está
      en las cinco…» (`src/panel/vacantes/ranking.test.ts:1595-1605`). Esta spec revierte
      esa decisión.
  - **El Ponderado ya existe y solo sale en «Prueba del puesto».** Junta la nota del perfil
    integral y la de la prueba, cada una con su peso. Va justo después de «Nota».
- **Pantallas o procesos afectados.** El ranking de cada vacante en el panel del equipo, en
  escritorio y en el teléfono: tabla, leyenda y menú «Columnas», que en el teléfono está
  dentro de «Más».
- **Sin impacto:**
  - **El Excel del ranking** no tiene columna de veredicto (`ServicioExcelRankingImpl`).
  - **El orden de las filas**: el grupo ya no lo gobierna.
  - **Los filtros** no filtran por grupo.
  - **El portal del candidato** nunca muestra el grupo.
- **Referencias existentes.**
  - [selector-de-columnas-del-ranking.md](selector-de-columnas-del-ranking.md): ya está
    implementada y fusionada (PR #65 del frontend, 28/09/2026). Tocó la misma celda y dejó
    tests que apagan Veredicto en «Prueba del puesto» (ver «Dependencias»).
  - [DESIGN.md](../../../RenaserOsPostulantes/DESIGN.md).

## Alcance
- **Incluye:**
  1. La columna Veredicto solo existe en la pestaña «Perfil integral». Allí no cambia nada:
     cabecera, píldora con el rótulo corto, nombre entero en el título emergente, leyenda y
     casilla en «Columnas».
  2. En «Prueba del puesto», «Simulación», «Validación» y «Decisión» desaparecen:
     - la cabecera;
     - la celda de cada fila, también el guion de quien no tiene grupo;
     - la leyenda de veredictos;
     - la casilla del menú «Columnas».
  3. En «Prueba del puesto», «Ponderado» queda como la columna de resumen después de «Nota».
     No cambia de sitio, de texto ni de comportamiento, y se puede seguir ocultando.
  4. **La tabla sigue cuadrada.** Cada fila tiene tantas celdas como cabeceras en las cinco
     pestañas, y la ficha de un candidato y el mensaje de tabla vacía ocupan todo el ancho.
     - La celda del veredicto ya se pinta con `ve('veredicto')` desde la spec del selector,
       así que en las cuatro pestañas sin columna no queda ninguna celda suelta.
     - La red «ninguna columna apagada descuadra la tabla» (`Vacante.test.tsx:1558`) recorre
       las casillas del propio menú en cada pestaña. Debería adaptarse sola a que Veredicto
       ya no esté; si falla, algo se ha descuadrado.
  5. **Las pruebas se ajustan a la nueva regla:**
     - «Veredicto está en las cinco» pasa a comprobar que solo está en «Perfil integral» y
       que las otras cuatro no lo tienen.
     - Se ajustan las cuentas de columnas de «Prueba del puesto», como «el ponderado se
       puede apagar, y entonces la tabla vuelve a ocho».
     - Los tests del selector que apagan Veredicto en «Prueba del puesto» pasan a «Perfil
       integral», la única pestaña donde seguirá habiendo casilla:
       - En `Vacante.test.tsx`, dentro de «elegir qué columnas se ven»: los de las líneas
         1377, 1393, 1408, 1429 y 1440. El de la 1408 espera Veredicto tras «Ponderado»; en
         «Perfil integral» va tras «Nota».
       - «cambiar de pestaña con columnas apagadas no deja rastro» (`:1531`) espera
         Veredicto en «Decisión»: pasa a esperar que no esté.
       - En `herramientas/e2e/42-selector-de-columnas.spec.ts`, los recorridos que
         desmarcan Veredicto (AC-04/05, AC-06, AC-07/10, AC-08/09 y AC-11) se hacen en
         «Perfil integral» o apagan otra columna. La comprobación de la casilla «Ponderado»
         (AC-01..03 y la de AC-11) se queda en la prueba.
  6. **Los comentarios que justifican el Veredicto fuera del perfil integral se actualizan**,
     para que no defiendan una regla que ya no existe:
     - `ranking.ts:2067-2073`;
     - `Vacante.tsx:218-226` y `:1647-1668`;
     - `Vacante.test.tsx:1732` y `:1860-1864`;
     - `ranking.test.ts:1595-1600`.
- **Queda fuera:**
  - **Un veredicto propio de la prueba o de otra etapa** («Fuerte / Sólido / Parcial»):
    repetiría con palabras la nota que ya está al lado.
  - **Llevar el Ponderado a «Simulación», «Validación» o «Decisión»**, u ordenar por él por
    defecto.
  - **Enseñar los hallazgos de riesgo crítico del currículum en la ficha de la prueba.** Se
    mantiene la decisión actual de no mezclar las dos fuentes.
  - **El backend.** `grupoPrioridad` se sigue calculando y enviando en todas las pestañas.
    Solo se deja de pintar.
  - **El Excel del ranking.**

## Comportamiento esperado
1. Quien revisa abre la vacante. En «Perfil integral» ve la columna Veredicto como hoy:
   «Alta», «Con riesgo» o «No priorizado», con la leyenda debajo.
2. Pasa a «Prueba del puesto». La tabla muestra:
   - «#», «Candidato», «Pretensión», «Nota» y «Ponderado»;
   - los criterios, si están encendidos;
   - «Estado» y «Ciudad».

   No hay columna Veredicto ni leyenda de veredictos.
3. Abre «Columnas» en esa pestaña. La lista no tiene casilla «Veredicto».
4. Pasa a «Simulación», «Validación» o «Decisión» y ve lo mismo, sin Ponderado, que nunca
   estuvo en esas pestañas.
5. Quiere saber si alguien de la prueba arrastra un riesgo del currículum. Lo mira en
   «Perfil integral», en su fila de «Toda la tanda» o abriendo su ficha.

## Referencia visual
No hay componentes nuevos ni cambios de diseño: solo desaparece una columna en cuatro
pestañas. Las columnas de la derecha se desplazan a la izquierda junto con sus cabeceras.

```
Perfil integral (sin cambios)
☐ │ # │ Candidato │ Pretensión │ Nota │ Veredicto │ [criterios] │ Estado │ Ciudad

Prueba del puesto
Antes: ☐ │ # │ Candidato │ Pretensión │ Nota │ Ponderado │ Veredicto │ [criterios] │ Estado │ Ciudad
Ahora: ☐ │ # │ Candidato │ Pretensión │ Nota │ Ponderado │ [criterios] │ Estado │ Ciudad

Simulación, Validación y Decisión
Antes: ☐ │ # │ Candidato │ Pretensión │ Nota │ Veredicto │ [criterios] │ Estado │ Ciudad
Ahora: ☐ │ # │ Candidato │ Pretensión │ Nota │ [criterios] │ Estado │ Ciudad
```

El menú «Columnas» en «Prueba del puesto» queda así:

```
☐ Ver los criterios en la tabla
────────────────────────────────
☑ #
☑ Pretensión
☑ Ponderado
☑ Estado
☑ Ciudad
```

- **Estados que hay que mirar en las cuatro pestañas sin Veredicto:**
  - con filas;
  - vacía por un filtro;
  - con la ficha de un candidato abierta;
  - con los criterios encendidos;
  - con datos largos: nombres y ciudades largas, y criterios con notas de tres cifras.
- **Móvil y escritorio.** En el teléfono, «Columnas» vive dentro de «Más» y se comporta
  igual. La tabla se desplaza en horizontal como hoy.

## Criterios de aceptación
- **AC-01:** Dada una vacante con candidatos que tienen grupo, cuando se abre «Perfil
  integral», entonces la columna Veredicto, sus píldoras con título, su leyenda y su casilla
  en «Columnas» se ven exactamente como hoy.
- **AC-02:** Dada esa vacante, cuando se abre «Prueba del puesto», entonces:
  - no hay cabecera «Veredicto»;
  - ninguna fila muestra «Alta», «Con riesgo», «No priorizado» ni «Incompatible»;
  - cada fila tiene tantas celdas como cabeceras;
  - «Ponderado» va justo después de «Nota».
- **AC-03:** Dada «Prueba del puesto», cuando se abre «Columnas», entonces no hay casilla
  «Veredicto» y sí hay casilla «Ponderado».
- **AC-04:** Dada «Prueba del puesto» con filas que tienen grupo, entonces no sale la
  leyenda de veredictos: ni «Prioridad alta» ni «Potencial con riesgo».
- **AC-05:** Dadas «Simulación», «Validación» y «Decisión», entonces se cumple lo de AC-02 a
  AC-04 en cada una, sin exigir la columna Ponderado.
- **AC-06:** Dada una persona con grupo «No priorizado» y un 95 en la prueba, entonces:
  - en «Prueba del puesto» su fila muestra el 95 y ningún rótulo de grupo;
  - en «Perfil integral» → «Toda la tanda» su fila sigue mostrando «No priorizado».
- **AC-07:** Dada cualquiera de las cuatro pestañas sin Veredicto, cuando se abre la ficha
  de un candidato o un filtro deja la tabla vacía, entonces la ficha o el mensaje ocupan el
  ancho entero de la tabla.
- **AC-08:** Dada cualquiera de las cuatro pestañas sin Veredicto, con los criterios
  encendidos, entonces cada criterio queda bajo su letra, y «Estado» y «Ciudad» bajo sus
  cabeceras.
- **AC-09:** Dado «Veredicto» apagado en «Perfil integral», cuando se pasa a «Prueba del
  puesto», entonces el botón dice «Columnas», sin contar ocultas. Al volver a «Perfil
  integral», el Veredicto se ve de nuevo, que es lo que pasa hoy al cambiar de pestaña.
- **AC-10:** Dada cualquier pestaña, entonces el orden de las filas es el mismo que antes
  del cambio.
- **AC-11:** Dado un teléfono de 360 px, cuando se abre «Más» → «Columnas» en «Prueba del
  puesto», entonces no hay casilla «Veredicto» y la tabla no se descuadra.

## Casos límite y errores
- **Datos vacíos o inválidos.**
  - En «Perfil integral», quien todavía no tiene grupo, porque la IA no ha terminado,
    muestra un guion como hoy. Si nadie tiene grupo, no sale la leyenda.
  - En las otras cuatro pestañas no sale nada, tampoco el guion.
- **Usuario sin permisos.** No cambia. Sin el permiso de pretensión, esa columna no existe
  en ninguna pestaña, como hoy.
- **Recarga, reintento o doble envío.** Al recargar en cualquier pestaña se ve la misma
  tabla. Cambiar de pestaña rápido varias veces no puede dejar celdas del veredicto sueltas.
- **Otros casos relevantes:**
  - **Quien vuelve al perfil integral.** Una postulación que pasó de la prueba al perfil
    para recalificar el currículum aparece en «Perfil integral» con su grupo.
  - **Riesgo crítico.** Quien tiene un riesgo crítico en el currículum deja de mostrar «Con
    riesgo» en «Prueba del puesto». Es una decisión tomada: se consulta en «Perfil
    integral».
  - **Excel.** El Excel de «Prueba del puesto» sale igual que antes del cambio.

## Datos y compatibilidad
No aplica: no se guarda nada, no hay migraciones y el backend no cambia. `grupoPrioridad`
sigue llegando en todas las pestañas.

## Verificación
- **Actor, rol y datos de prueba.** Una cuenta del panel con rol Talento o Dirección, y una
  vacante con candidatos repartidos así:
  - al menos dos candidatos en «Prueba del puesto» con nota de la prueba, ponderado y
    grupo, uno de ellos «No priorizado» con nota alta;
  - al menos uno en «Perfil integral» con grupo y otro sin grupo;
  - ciudad y notas por criterio, para las columnas Ciudad y los criterios.
- **Recorridos que deben quedar como E2E repetibles.** Van en un spec nuevo en
  `herramientas/e2e/`, con el siguiente número libre (el 41 es `41-logotipo` y el 42,
  `42-selector-de-columnas`). Además se ajusta el 42 (ver «Alcance», punto 5).
  `03-orden.spec.ts` comprueba el veredicto en «Perfil integral», que es la pestaña por
  defecto, y no debería cambiar.
  1. «Perfil integral»: cabecera Veredicto, píldoras y leyenda visibles (AC-01).
  2. «Prueba del puesto»:
     - sin cabecera, sin rótulos de grupo en las filas y sin leyenda;
     - `td` por fila igual a `thead th`;
     - «Ponderado» tras «Nota»;
     - «Columnas» sin «Veredicto».

     Cubre AC-02 a AC-04.
  3. «Validación» o «Decisión»: lo mismo del punto 2 (AC-05).
  4. Lo mismo del punto 2 a 360 px, dentro de «Más» (AC-11).
- **Riesgos que QA debe explorar con navegador:**
  - Las cinco pestañas con y sin criterios.
  - Apagar columnas en cada una.
  - Abrir fichas y dejar la tabla vacía con un filtro.
  - Comprobar que «Toda la tanda» de «Perfil integral» incluye a quien ya está en la prueba
    y muestra su grupo.
  - Capturas en escritorio y a 360 px.
- **Resultado que quiero comprobar personalmente.** En el panel:
  - la pestaña «Prueba del puesto» sin Veredicto y con «Ponderado» tras «Nota»;
  - las otras pestañas sin Veredicto;
  - «Perfil integral» igual que siempre.

  Hay que entrar con sesión al panel, así que esta comprobación visual la hace el usuario.

## Dependencias y dudas
- **Requiere:** nada pendiente.
  - La celda del veredicto ya se pinta con `ve('veredicto')`. Lo hizo
    [selector-de-columnas-del-ranking.md](selector-de-columnas-del-ranking.md), fusionada el
    28/09/2026 (PR #65 del frontend).
  - Esa spec dejó tests y un E2E que apagan Veredicto en «Prueba del puesto». Esta los pasa
    a «Perfil integral» («Alcance», punto 5).
- **Decisiones pendientes:** ninguna.
- **Decisiones tomadas (28/09/2026):**
  - Se quita el Veredicto en vez de sustituirlo por un veredicto de la prueba.
  - Se quita en las cuatro pestañas posteriores al perfil integral, no solo en la prueba.
  - En la prueba, «Ponderado» es la columna de resumen.
