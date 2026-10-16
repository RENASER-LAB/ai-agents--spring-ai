# Los puntos del criterio y de las preguntas, explicados donde se escriben
Estado: TERMINADO

## Objetivo

El editor de la prueba técnica ya pide el total de cada criterio (V69): las cerradas restan sus
puntos y lo que queda lo califican la IA o una persona mirando las abiertas y los archivos. Al
revisarlo, la clienta tropezó tres veces con los mismos puntos:

- **El reparto del lápiz** («Sus cerradas suman 0. Los otros 20 los califica la IA…») no deja ver
  que cerradas + abiertas y archivos = el total del criterio.
- **Una opción que vale más que su pregunta** (10 en una pregunta de 5) se guarda sin aviso. Solo
  lo frena la cabecera al publicar, y la tarjeta de la pregunta parece correcta.
- **El aviso «su parte calificada no mira nada»** no explica de dónde salen esos puntos (total −
  cerradas) ni cómo se arregla. Daba a entender que todo criterio necesita una parte de IA.

Además, en una prueba publicada (o para quien no puede editar) el editor deja media pantalla vacía:
los puntos de cada criterio se quedan a mitad de la línea y los textos se cortan a 72 caracteres
dentro de tarjetas que ocupan todo el ancho.

Esta spec cambia esos textos, marca la pregunta con falta donde se ve, aprovecha el ancho en esas
vistas y arregla, de paso, la suma con decimales de «Cambiar los puntos» del banco. Las reglas y la
forma de calificar no cambian.

## Contexto

### Comportamiento actual

- **El lápiz del criterio** (`CriterioDePrueba.tsx`) dice en vivo:
  - «Sus cerradas suman N. Los otros M los califica [La IA ▾] mirando sus abiertas y archivos.»;
  - «Sus cerradas suman N. Todo lo puntúa el sistema.» si no queda nada;
  - y debajo: «Si luego cambian sus cerradas, el criterio sigue valiendo lo mismo: lo que se
    ajusta es la parte que califican la IA o una persona».
- **«Cambiar los puntos»** de la prueba publicada (`PreguntasPublicadas.tsx`) dice «Sus cerradas
  suman 60. Los otros 40 los califica la IA.».
- **El criterio desplegado** dice «Parte calificada: 5 pts · la califica la IA».
- **Las reglas de las opciones** (`ReglasDePuntos.puntuacionDeLaPregunta`) son:
  - en opción única y escala, ninguna opción puede ser negativa ni pasar de los puntos de la
    pregunta, y alguna tiene que darlos todos;
  - en opción múltiple, cada opción va entre −P y P, y las buenas tienen que llegar a P.
- **Dónde se aplican hoy esas reglas.** Solo al publicar (`ReglasDeLaPrueba` y las del banco) y en
  «Cambiar los puntos». La falta «La pregunta 1 («…»): ninguna opción puede pasar de los 5 puntos
  de la pregunta.» sale en la cabecera y lleva al criterio y a la pregunta (`navegacion.ts`).
- **La tarjeta de la pregunta** (`BloqueCriterio.tsx`, común a los dos editores) no muestra nada.
- **El formulario de la pregunta** (`FormularioPregunta.tsx`, común) solo tiene un texto de ayuda
  fijo y guarda sin avisar.
- **El aviso del servidor** (`ReglasDeLaPrueba`, línea ~279) dice «El criterio «X»: su parte
  calificada no mira nada. Agrégale una abierta o pide un archivo en una de sus preguntas.».
  - La cabecera reconoce las faltas de criterio porque empiezan por «El criterio «X»» o «Las
    cerradas de «X»».
  - Una falta que empieza por «X» se toma por un entregable (`navegacion.ts`, líneas 40-45).
- **«Cambiar los puntos» del banco** (`ServicioPreguntasVacanteImpl.cambiarPuntos`, línea ~303)
  solo suma los puntos enteros. Con una pregunta en 25.5, el 400 dice «suman 85: faltan 15» aunque
  lo escrito sume otra cosa. Es el mismo fallo que QA-11 arregló en la prueba.
- **El ancho en los dos editores** (`EditorDePreguntas.module.css`):
  - la página usa el carril del panel (`--ancho-panel`, 120rem);
  - el nombre del criterio llega como mucho a 40rem (línea ~309), y los puntos van justo detrás;
  - la derecha de la línea queda para los botones ↑ ↓ ✎ ✕;
  - «Qué evalúa», el enunciado, sus opciones y «Qué debe tener» se cortan a `--medida-bloque` (72ch);
  - las tarjetas de pregunta ocupan todo el ancho.

  En una prueba publicada, en un banco publicado o para un usuario sin `editar_vacante` no hay
  botones. Por eso, en 1920 px, la línea acaba hacia los 1400 px y la mitad derecha de cada tarjeta
  queda vacía.

### Pantallas o procesos afectados

- **Panel, editor de la prueba**: el lápiz del criterio, el criterio desplegado, la tarjeta de
  pregunta, el formulario de pregunta y la cabecera.
- **Panel, «Cambiar los puntos»** de la prueba publicada.
- **Panel, editor del banco**: la tarjeta de pregunta, el formulario de pregunta y «Cambiar los
  puntos».
- **Backend**: el texto del aviso de la prueba y la suma de «Cambiar los puntos» del banco.

### Referencias

- `specs/Done/editor-de-la-prueba-mas-simple.md`: el modelo del total (V69), la cabecera fija y las
  faltas que llevan a donde se arreglan.
- `docs/PRUEBA-DEL-PUESTO.md` (backend) y `docs/PANEL.md` (frontend).

### Decisiones tomadas (07/10/2026)

1. **El reparto se escribe como reparto**: «Cerradas: N pts · Abiertas y archivos: M pts, los
   califica [La IA ▾]».
2. **La tarjeta de una pregunta con falta se pone en ámbar** con su falta escrita debajo, igual
   que un criterio. Va en los dos editores.
3. **El formulario de la pregunta avisa mientras se escribe**, pero deja guardar: es un borrador, y
   publicar sigue frenado.
4. **El aviso nuevo** dice cuántos puntos quedan sin quien los califique y las tres salidas.
   - Sin ninguna cerrada tiene un texto propio.
   - Empieza por «El criterio «X»» para que la cabecera lo siga llevando a su criterio.
5. **«Cambiar los puntos» del banco** da la suma de lo escrito, o ninguna si hay decimales, como la
   prueba.
6. **Aprovechar el ancho**:
   - en una línea de criterio sin botones, los puntos se van al borde derecho;
   - en pantallas anchas, los textos de lectura pasan de 72 a unos 100 caracteres por línea.

   Se eligió ensanchar el texto antes que estrechar la tarjeta, porque lo que se pidió es usar el
   espacio.

## Alcance

**Incluye:**

- Los textos del reparto en el lápiz, en «Cambiar los puntos» de la prueba y en el criterio
  desplegado.
- La tarjeta en ámbar y el aviso en vivo del formulario de pregunta, en los dos editores.
- El texto nuevo del aviso del servidor y su paso por la cabecera, la línea del criterio y la
  navegación.
- La suma de «Cambiar los puntos» del banco.
- El ancho de las líneas de criterio sin botones y de los textos de lectura, en los dos editores.

**Queda fuera:**

- Las reglas de puntos: qué se exige para publicar no cambia, solo cómo se avisa.
- La calificación, la IA y la recalificación.
- El mensaje interno con el que se reintenta una recomendación de la IA
  (`RecetaRecomendacionPrueba`, «tiene parte calificada y no mira nada»), que no lo lee nadie del
  panel.
- La línea plegada del criterio, «40 pts (sistema 20 + IA 20)», que se queda igual.

## Comportamiento esperado

1. **El reparto del lápiz del criterio** dice, en vivo:
   - con puntos para abiertas y archivos: «Cerradas: 20 pts · Abiertas y archivos: 20 pts, los
     califica [La IA ▾]» (o [Una persona ▾], o [Elige quién ▾] si falta decidirlo);
   - sin puntos para ellas: «Cerradas: 40 pts · Todo lo puntúa el sistema»;
   - con un total inválido o con cerradas por encima del total: los mensajes de hoy.

   El texto de debajo pasa a: «Si luego cambian sus cerradas, el criterio sigue valiendo lo mismo:
   lo que se ajusta son los puntos de abiertas y archivos».
2. **«Cambiar los puntos» de la prueba publicada** usa el mismo formato, sin selector: «Cerradas:
   60 pts · Abiertas y archivos: 40 pts, los califica la IA».
3. **El criterio desplegado** dice «Abiertas y archivos: 5 pts · los califica la IA» en lugar de
   «Parte calificada: 5 pts · la califica la IA». Lo mismo para «una persona», «falta decir quién
   los califica» y «Todo lo puntúa el sistema».
4. **La tarjeta de una pregunta con falta**:
   - toma el borde y el punto ámbar de los criterios con falta;
   - escribe debajo cada falta de esa pregunta, sin el prefijo «La pregunta N («…»): »;
   - por ejemplo: «Ninguna opción puede pasar de los 5 puntos de la pregunta.».
   - Las faltas son las que el servidor atribuye a esa pregunta («La pregunta N…»): de puntos, de
     forma (opciones, niveles) y «no está en ningún criterio».
   - Si su criterio está plegado, su línea también se pone en ámbar.
   - Al seguir la falta desde la cabecera se llega a esa tarjeta, como hoy.
5. **El formulario de la pregunta** avisa en vivo, bajo las opciones o niveles, con los mismos
   textos que el servidor y sin el prefijo. Lo hace en cuanto los puntos de la pregunta y de las
   opciones están escritos:
   - opción única y escala:
     - «Ninguna opción puede tener puntos negativos.»;
     - «Ninguna opción puede pasar de los N puntos de la pregunta.»;
     - «Alguna opción tiene que dar los N puntos de la pregunta.»;
     - en escala, «ningún nivel / algún nivel»;
   - opción múltiple:
     - «Cada opción tiene que valer entre −N y N.»;
     - «Marcando todas las opciones buenas no se llega a sus N puntos.».

   Con avisos, «Guardar» sigue funcionando: se guarda y la tarjeta queda en ámbar. Los avisos no
   cambian el foco ni bloquean la escritura.
6. **El aviso de un criterio cuyos puntos para abiertas y archivos no tienen nada que mirar**
   (servidor, `ReglasDeLaPrueba`), con T = total del criterio, C = sus cerradas y M = T − C:
   - con cerradas: «El criterio «Excel» vale 20 y sus cerradas suman 5: nadie puede calificar los
     otros 15. Baja el total a 5, sube sus cerradas o agrégale una abierta o un archivo.»;
   - sin cerradas: «El criterio «Excel» vale 20 y no tiene nada que calificar: agrégale una
     abierta, un archivo o cerradas que sumen 20.».

   Sale en la cabecera, donde lleva a ese criterio abierto como hoy, en la línea del criterio y en
   el 400 de publicar. Si el panel repite el texto (`faltaEnLosPuntosDelCriterio`), usa el mismo.
7. **«Cambiar los puntos» del banco**:
   - el 400 da la suma de lo escrito («suman 225: sobran 125»), o ninguna si algo de lo que suma
     no es entero; en ese caso queda solo la falta de ese campo;
   - el formulario no envía un valor inválido y lleva a su campo, como ya hace en la prueba.
8. **El ancho**, en los dos editores:
   - **Línea del criterio sin botones** (prueba o banco publicados, usuario sin `editar_vacante`):
     el nombre queda a la izquierda y «20 pts (IA 20) · 4 preguntas» y el punto ámbar se alinean
     al borde derecho de la línea. Con botones se queda como hoy: puntos tras el nombre y botones a
     la derecha.
   - **Textos de lectura**: el enunciado, sus opciones o niveles, «Qué evalúa» y «Qué debe tener»
     pasan de 72ch a una medida de unos 100ch.
     - Tienen un mínimo legible y nunca pasan del ancho de su tarjeta.
     - En 1280 px ocupan casi toda la tarjeta, y en 1920 px llegan a unos 100 caracteres.
     - Los puntos de cada opción siguen alineados a la derecha de su texto.
   - **Lo que no cambia**:
     - los formularios y las áreas de texto, que siguen en `--ancho-bloque`;
     - la prosa de explicación, en `--medida`;
     - el móvil.

## Referencia visual

No hay maqueta nueva. Se reutilizan el ámbar de los criterios con falta (borde, punto y texto en
`--duda-tinta`) y los estilos del editor. Bocetos:

```
Lápiz del criterio
  Puntos del criterio   [ 40 ]
  Cerradas: 20 pts · Abiertas y archivos: 20 pts, los califica [ La IA ▾ ]
  Si luego cambian sus cerradas, el criterio sigue valiendo lo mismo: lo que se
  ajusta son los puntos de abiertas y archivos.

Tarjeta con falta (borde ámbar)
  ● Pregunta 1 · Opción única · 5 pts
    Qué harías si…
    1                                                   10
    2                                                    0
    Ninguna opción puede pasar de los 5 puntos de la pregunta.
    Alguna opción tiene que dar los 5 puntos de la pregunta.

Línea de criterio sin botones (publicada, 1920 px)
  › Manejo y control de caja                              20 pts (IA 20) · 4 preguntas
  hoy:
  › Manejo y control de caja        20 pts (IA 20) · 4 preguntas
```

Capturas del estado actual (prueba publicada a 1920 px, aportadas por el usuario el 07/10/2026):
criterios plegados con los puntos a media línea y criterio desplegado con los textos cortados a
72ch dentro de tarjetas a todo el ancho.

- **Escritorio y móvil**: los textos se parten en varias líneas sin desbordar a 1280, 1024, 375 y
  320 px.
- **Diseño**: el `DESIGN.md` del panel; ámbar solo para lo que frena la publicación.

## Criterios de aceptación

- **AC-01**: Dado el lápiz de un criterio de 40 con cerradas de 20, entonces dice «Cerradas: 20 pts ·
  Abiertas y archivos: 20 pts, los califica [La IA ▾]»; con el total en 20 dice «Cerradas: 20 pts ·
  Todo lo puntúa el sistema».
- **AC-02**: Dado un criterio nuevo sin preguntas y total 20, entonces el lápiz dice «Cerradas: 0 pts ·
  Abiertas y archivos: 20 pts, los califica [La IA ▾]».
- **AC-03**: Dada una prueba publicada, cuando se abre «Cambiar los puntos», entonces cada criterio
  dice el reparto con el mismo formato.
- **AC-04**: Dado un criterio desplegado con 5 pts para la IA, entonces dice «Abiertas y archivos:
  5 pts · los califica la IA».
- **AC-05**: Dada una pregunta de opción única de 5 con una opción de 10, en cualquiera de los dos
  editores:
  - entonces su tarjeta está en ámbar y dice «Ninguna opción puede pasar de los 5 puntos de la
    pregunta.»;
  - su criterio plegado está en ámbar;
  - la falta de la cabecera lleva a esa tarjeta.
- **AC-06**: Dado el formulario de esa pregunta, cuando se escribe 10 en una opción, entonces el
  aviso sale en vivo; cuando se guarda, se guarda y la tarjeta queda en ámbar; cuando se corrige a
  5, el aviso desaparece y la tarjeta deja de estar en ámbar al guardar.
- **AC-07**: Dada una pregunta de opción múltiple de 10 cuyas buenas suman 6, entonces el formulario
  y la tarjeta dicen «Marcando todas las opciones buenas no se llega a sus 10 puntos.».
- **AC-08**: Dado un criterio de 20 con una cerrada de 5 y sin abiertas ni archivos, cuando se
  intenta publicar, entonces el 400 y la cabecera dicen «El criterio «Excel» vale 20 y sus cerradas
  suman 5: nadie puede calificar los otros 15. Baja el total a 5, sube sus cerradas o agrégale una
  abierta o un archivo.», y la falta lleva al criterio.
- **AC-09**: Dado un criterio de 20 sin cerradas ni abiertas ni archivos, entonces el aviso dice «El
  criterio «Excel» vale 20 y no tiene nada que calificar: agrégale una abierta, un archivo o
  cerradas que sumen 20.».
- **AC-10**: Dado un banco publicado, cuando «Cambiar los puntos» llega al servidor con una pregunta
  en 25.5 (por API), entonces el 400 no da ninguna suma y nombra esa pregunta. Con 150 da la suma
  de lo escrito. El formulario no envía ninguno de los dos.
- **AC-11**: Dados los dos editores, entonces lo que exige publicar y la calificación no cambian:
  las pruebas de integración del banco y de la prueba pasan cambiando solo los textos.
- **AC-12**: Dada una prueba publicada a 1920 px, o un usuario sin `editar_vacante`, entonces los
  puntos y el resumen de cada criterio terminan en el borde derecho de su línea. En un borrador con
  botones siguen tras el nombre y los botones a la derecha.
- **AC-13**: Dado un criterio desplegado a 1920 px, entonces el enunciado, las opciones, «Qué evalúa»
  y «Qué debe tener» ocupan unos 100 caracteres por línea en lugar de 72. A 1280 px no pasan del
  ancho de la tarjeta, y a 375 px se ven como hoy.

## Casos límite y errores

- **Datos a medias**: con los puntos de la pregunta vacíos o una opción sin puntos, el formulario no
  avisa de las reglas de puntos, como el servidor.
- **Varias faltas en una pregunta**: salen todas debajo de la tarjeta, en el orden del servidor.
- **Preguntas sin criterio**: su tarjeta en ámbar también dice «No está en ningún criterio.».
- **Usuario sin `editar_vacante`**: ve la tarjeta en ámbar y sus faltas, sin formulario.
- **Prueba publicada**: no hay faltas de publicar, así que las tarjetas no salen en ámbar. En
  «Cambiar los puntos» sigue lo de QA-13 (la falta bajo su campo).
- **Textos largos**: un enunciado o un nombre de criterio largos no rompen la tarjeta ni el aviso.

## Datos y compatibilidad

- No se guarda nada nuevo. No hay migración.
- Los avisos los calcula el servidor como hoy; el formulario repite sus reglas para avisar en vivo.
  Las reglas no cambian: se exige lo mismo de siempre.
- La API no cambia; solo cambian los textos de las faltas.
- Los E2E y los tests que comprueban el texto viejo («su parte calificada no mira nada», «Sus
  cerradas suman», «Parte calificada:») se actualizan solo en esos textos.

## Verificación

- **Datos**: un usuario con `editar_vacante`, otro solo con `ver_vacantes`, una prueba en borrador,
  una publicada y un banco publicado.
- **E2E repetibles en headless**:
  1. Lápiz de un criterio: el reparto con IA, con persona, «Todo lo puntúa el sistema» y sin
     preguntas.
  2. Pregunta de 5 con una opción de 10:
     - el aviso en vivo;
     - se guarda;
     - la tarjeta y el criterio plegado en ámbar;
     - la falta de la cabecera lleva a la tarjeta;
     - se corrige y desaparece.

     Se hace en la prueba y en el banco.
  3. Opción múltiple que no llega a sus puntos.
  4. Criterio de 20 con una cerrada de 5: el aviso nuevo y la navegación. Sin cerradas: el otro
     texto.
  5. «Cambiar los puntos» de la prueba (el reparto) y del banco (25.5 por API: sin suma; 150: suma
     escrita).
  6. El ancho a 1920 px:
     - en la prueba publicada, los puntos de cada línea terminan en el borde derecho, y en un
       borrador con botones no;
     - los textos desplegados superan los 72ch.
- **Riesgos que QA explora con navegador**:
  - el reparto a 375 y 320 px;
  - una tarjeta con tres faltas;
  - el aviso en vivo mientras se escribe rápido;
  - que la falta nueva no se confunda con la de un entregable;
  - el ancho a 1280, 1440, 1657 y 1920 px en la prueba y el banco publicados, en un borrador y con
    el usuario de solo lectura: sin huecos absurdos a la derecha, sin líneas ilegibles, sin scroll
    horizontal, y con la cabecera fija y la guía «!» intactas.
- **Comprobación personal**: el lápiz, la tarjeta en ámbar y el aviso nuevo. Hay inicio de sesión de
  por medio, así que **la hace el usuario**.
- **Avisos para quien verifique**:
  - `verify` corre las pruebas de integración y `test` no;
  - SonarCloud exige en lo nuevo cobertura ≥ 80 % (líneas y ramas);
  - el `PGUSER` del clon de E2E lo da el usuario (es `harness_owner` en los clones del harness).

## Dependencias y dudas

- **Requiere**: `specs/Done/editor-de-la-prueba-mas-simple.md` en producción (PR #113/#75).
- **Decisiones pendientes**: ninguna.
