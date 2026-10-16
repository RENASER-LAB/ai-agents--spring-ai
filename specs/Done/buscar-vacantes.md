# Buscar vacantes en el portal

Estado: TERMINADO

Estado previo (referencia histórica): lista para implementar

## Objetivo

Que quien busca trabajo encuentre el suyo sin leer todas las vacantes: escribe lo que busca,
acota con lo que los datos permiten acotar, elige el orden y ve cuántas quedan. Sirve al
visitante sin cuenta y al candidato con sesión, sobre todo desde el teléfono. Hoy hay 9 vacantes
publicadas; la pantalla tiene que verse igual de bien con 1, con 9 y con 40.

Para que haya algo que filtrar, el equipo deja de escribir a mano la modalidad y la ciudad al
crear una vacante: la modalidad se elige de una lista de tres, y la ciudad del mismo catálogo de
ciudades del Perú que ya usa el registro del candidato.

## Contexto

- Comportamiento actual:
  - Las vacantes son una sección de la portada (`/#vacantes-abiertas`,
    `src/paginas/vacantes/Vacantes.tsx` en `RenaserOsPostulantes`): todas de golpe, en una
    rejilla de tres columnas, sin buscar ni filtrar. La navegación «Vacantes» y los dos botones
    «Ver las vacantes abiertas» saltan a esa sección.
  - El servidor ya las entrega de la más reciente a la más antigua, pero la fecha de publicación
    no viaja en la respuesta. Es una columna que admite nulo y se fija solo al publicar.
  - La ficha (`/vacantes/:id`) tiene «← Volver a las vacantes», que lleva a la portada, y pinta
    modalidad, ubicación y horario en una línea.
  - La cinta de la portada dice «N ciudades» contando textos de ubicación distintos: hoy dice
    «2 ciudades» porque cuenta «Selva Alegre» como ciudad e ignora las cinco sin ubicación.
  - El portal también es app Android (Capacitor, código empaquetado): el botón atrás del
    teléfono recorre el mismo historial que el navegador.
  - **En el panel, la modalidad y la ubicación son texto libre.** El formulario de alta y
    edición (`src/panel/vacantes/CamposDeLaVacante.tsx`) las pide en cajas de texto; la de
    modalidad solo sugiere «(Presencial, Híbrido…)». El backend acepta cualquier texto, y
    `scripts/convocatorias.json` escribe «PRESENCIAL» en mayúsculas: de ahí salen las variantes.
  - `scripts/cargar-convocatoria.py` manda el bloque `vacante` de `scripts/convocatorias.json`
    tal cual al alta del panel. Si la vacante ya está publicada con ese título, la reutiliza sin
    editarla. De las cinco convocatorias del JSON, «talento» dice `PRESENCIAL` y «Arequipa»;
    «asistente-admin» y «administrador», `PRESENCIAL` y «Lima»; «arquitecto» y «civil» no dicen
    modalidad ni ubicación. `enlaces-de-drive.py` y `exportar-para-demo.py` leen el mismo JSON,
    pero solo `carpetaDrive` y `titulo`.
  - Editar una vacante publicada **avisa en su portal a cada postulante en carrera** si cambia
    un campo visible, modalidad y ubicación incluidas (spec 01, `CambiosDeLaVacante`). La
    comparación solo ignora los espacios de los bordes: para el sistema, «PRESENCIAL» →
    «Presencial» es un cambio.
  - **El catálogo de ciudades ya existe** (V47, tabla `ubigeo`, códigos del INEI): 25
    departamentos, 196 provincias y «Fuera del Perú». Se ofrecen las provincias y «Fuera del
    Perú» —la V47 explica por qué provincia y no distrito ni departamento—. El registro del
    candidato lo usa como desplegable agrupado por departamento, el ranking del panel filtra
    candidatos por él, se sirve sin sesión (`GET /api/v1/portal/catalogos/ubigeo`) y el backend
    ya sabe validar un código (`CatalogosDelPerfil.esCiudadElegible`).
- Lo que publica hoy el tablón de producción (consultado el 24/09/2026):

  | Vacante | Modalidad | Ubicación | Horario |
  |---|---|---|---|
  | Especialista en Gestión del Talento | Presencial | Selva Alegre | 9am-6pm |
  | Especialista en Marketing Digital | Presencial | Selva Alegre | 9am-6pm |
  | Ingeniero Civil Builder Junior | — | — | — |
  | Arquitecto Builder Junior | — | — | — |
  | Ingeniero/a de Infraestructura | — | — | — |
  | Líder de operaciones | — | — | — |
  | Desarrollador web | — | — | — |
  | Administrador | PRESENCIAL | Lima | Tiempo completo |
  | Asistente Administrativo | PRESENCIAL | Lima | Tiempo completo |

  Una sola empresa. Las nueve con el sueldo sin publicar. Ninguna dice «remoto» ni «híbrido», y
  «analista» no aparece en ninguna. Sus fechas de publicación no se pueden ver todavía. «Selva
  Alegre» es un barrio, no una ciudad del catálogo.
- Límites fijados por el dueño: área y nivel del puesto no salen al portal; plazas y fecha de
  cierre tampoco. En el backend se tocan dos cosas: exponer la fecha de publicación y dar a la
  vacante una ciudad del catálogo (decidido el 25/09/2026).
- Pantallas afectadas: la portada y su cinta, la pantalla nueva `/vacantes`, la ficha, la
  navegación del portal y el formulario de alta y edición de vacantes del panel.
- Referencias: [DESIGN.md del portal](../../../RenaserOsPostulantes/DESIGN.md) y su `PRODUCT.md`;
  [spec 01 · editar vacantes y avisos](01-editar-vacantes-y-avisos.md);
  `V47__la_ciudad_del_candidato.sql`. **No** el `DESIGN.md` de este repositorio, que describe
  otro sistema.

## Alcance

- Incluye:
  - Pantalla nueva `/vacantes`: buscador, contador, filtros que solo aparecen cuando separan
    algo (ciudad, modalidad, empresa y fecha de publicación), orden «Relevantes / Recientes» y
    «Publicada hace…» en cada tarjeta.
  - La dirección guarda la búsqueda, los filtros y el orden para poder compartirla, sin llenar
    el historial.
  - Backend: la fecha de publicación viaja en la vacante pública; la vacante gana una ciudad del
    catálogo, que se guarda, se valida, se expone al portal y al panel y cuenta como cambio
    visible al editar.
  - Una migración que rescata la ciudad de las vacantes cuyo texto de ubicación ya es el nombre
    exacto de una ciudad del catálogo.
  - Panel: la modalidad se elige de un desplegable (Presencial, Híbrido, Remoto); la ciudad, del
    catálogo; lo que antes era «Ubicación» pasa a ser «Zona o referencia», opcional.
  - `scripts/convocatorias.json`: la modalidad escrita como en el desplegable y el código de la
    ciudad en cada convocatoria que la dice, para que las vacantes cargadas por script lleguen
    igual que las del panel.
  - Portada: su sección enseña las tres vacantes más recientes y un enlace a todas; los botones
    y la navegación «Vacantes» llevan a la pantalla nueva; la cinta cuenta ciudades de verdad.
  - Ficha: pinta la ciudad y la zona; volver a la lista la deja como estaba.
- Queda fuera:
  - Filtrar por área, familia o nivel del puesto (decisión del dueño: son internos de cada
    empresa).
  - Filtrar u ordenar por sueldo, plazas o fecha de cierre; cualquier otro orden (alfabético,
    por empresa).
  - Buscar dentro de la descripción, las responsabilidades o los requisitos.
  - Tolerancia a errores de tipeo y sinónimos («analsita», «contador» ≈ «contable»).
  - Paginación, «cargar más» y scroll infinito.
  - Distritos: el catálogo solo trae provincias. Con 9 vacantes, cada distrito tendría una y el
    filtro se haría pedazos. El día que hagan falta se cargan en la misma tabla sin tocar nada
    de esto.
  - Búsqueda por distancia, mapa o ubicación del teléfono; cruzar la ciudad de la vacante con la
    del candidato («cerca de ti»).
  - Alertas por correo, búsquedas guardadas, favoritos y vacantes recomendadas.
  - Marcar a qué vacantes ya postuló quien tiene sesión.
  - Validar la modalidad en el backend, y exigir modalidad o ciudad a quien crea vacantes por la
    API o con scripts: solo el panel las exige.
  - Adivinar la ciudad de las vacantes cuyo texto no la nombra exactamente («Selva Alegre»): la
    elige el equipo en el panel.
  - Cambiar la ficha más allá de la ciudad, la zona y el enlace de vuelta.

### Qué se puede filtrar y ordenar de verdad

| Eje | ¿Entra? | Por qué |
|---|---|---|
| Texto libre | **Sí, siempre** | Busca en título, empresa, modalidad, ciudad y zona. |
| Ciudad | **Sí**, cuando separe algo | Sale del catálogo: una vacante en «Lima» y otra en «LIMA» son la misma ciudad. Tras la migración solo Administrador y Asistente Administrativo tienen ciudad (Lima); el filtro aparece en cuanto el equipo ponga ciudad a alguna de las demás (p. ej. las de Selva Alegre, en Arequipa). |
| Modalidad | Solo cuando separe algo; **hoy no aparece** | La única escrita es Presencial. Con el desplegable del panel, las vacantes nuevas llegan con Presencial, Híbrido o Remoto, y el filtro aparece solo en cuanto haya dos modalidades distintas. |
| Empresa | Solo cuando separe algo; **hoy no aparece** | Hay una sola. El backend ya admite varias. |
| Fecha de publicación | **Sí**, cuando separe algo | Últimas 24 horas, 7 días y 30 días. Con pocas vacantes a veces no aparecerá: si todas caen en la misma ventana, no separa nada. Si aparece hoy o no depende de fechas que todavía no se ven. |
| Orden | **Relevantes / Recientes**, con texto en el buscador | Sin nada escrito no hay relevancia que medir: la lista va de la más reciente a la más antigua. |
| Área y nivel | No | Internos (decisión del dueño). Es el filtro central de cualquier bolsa y aquí falta: se dice, no se disimula. |
| Sueldo | No | Nueve de nueve ocultos: vaciaría la pantalla. |
| Plazas y fecha de cierre | No | Internas. |
| Horario | No | Texto libre que mezcla dos cosas distintas: «9am-6pm» y «Tiempo completo». |
| Descripción y requisitos (en la búsqueda) | No | «ingeniero» traería al Administrador y al Asistente Administrativo, que mencionan la ingeniería en sus requisitos: 6 resultados donde el título solo da 2. |

El día del despliegue, la pantalla ofrece el buscador, el filtro de fecha si las fechas lo
permiten y, con texto escrito, el orden. Ciudad, modalidad y empresa aparecen solas a medida que
el equipo complete las vacantes en el panel.

## Comportamiento esperado

### Llega sin saber qué hay

1. La persona entra en `/vacantes` desde la navegación, la portada o un enlace. Ve el titular
   «Vacantes abiertas», el buscador, el contador «9 vacantes abiertas» con «Más recientes
   primero» en la misma fila, los filtros que correspondan y todas las vacantes, de la más
   reciente a la más antigua.
2. Cada tarjeta muestra, si existen: modalidad · ciudad (o la zona, si no hay ciudad), el título,
   la empresa, un resumen (el propósito o, si falta, la descripción; tres líneas como mucho) y
   un pie con el horario, «Sueldo sin publicar» o el sueldo, y «Publicada hace 3 días».
3. Pulsa una tarjeta y abre la ficha. Con atrás o con «← Volver a las vacantes» vuelve a la
   lista con la misma búsqueda, filtros y orden, a la altura de la tarjeta que abrió.

### Busca algo concreto

4. Escribe en el buscador y la lista se reduce con cada letra, sin pulsar nada ni recargar. El
   contador pasa a «2 de 9 vacantes».
5. Coincidencias: no distingue mayúsculas ni tildes («gestion» encuentra «Gestión»); encuentra
   partes de palabra («ingenier» encuentra «Ingeniero/a»); con varias palabras deben estar
   todas («ingeniero civil»); ignora los espacios de los bordes.
6. Si no queda ninguna, ve «Ninguna vacante coincide con «analista»» —con «en Lima» si hay un
   filtro puesto— y debajo «Hay 9 vacantes abiertas.» con el botón «Ver las 9 vacantes», que
   quita la búsqueda y los filtros. El texto sigue en el buscador para corregirlo. **Nunca** se
   muestra el mensaje de «no hay vacantes».
7. Borrar el texto, con el teclado o con la ✕ «Borrar búsqueda», devuelve la lista completa.
8. En el teléfono, la tecla «Buscar» del teclado lo cierra para dejar ver los resultados. No
   recarga ni navega.

### Acota

9. Cada filtro es un grupo de opciones con la cantidad que tiene con la búsqueda y los demás
   filtros puestos («Lima (2)»). En ciudad, modalidad y empresa se pueden marcar varias, y
   suman (Lima **o** Arequipa); filtros distintos se combinan (Lima **y** Presencial). Una
   opción que baja a (0) sigue en su sitio para que el grupo no salte mientras se escribe.
10. «Sin indicar (5)» es la última opción de ciudad, modalidad y empresa: quien marca Lima sabe
    que hay cinco que no dicen dónde, y puede incluirlas. Una vacante con zona pero sin ciudad
    («Selva Alegre») cuenta como «Sin indicar».
11. Los filtros activos aparecen como etiquetas bajo el buscador («Lima ✕», «Últimos 7 días ✕»).
    Quitar una deshace ese filtro; «Quitar filtros» los limpia todos y conserva la búsqueda y
    el orden.
12. Las ciudades se nombran como en el catálogo y en orden alfabético; si dos se llamaran igual,
    llevan el departamento al lado. Las variantes de una modalidad son una opción: «Presencial»
    y «PRESENCIAL» salen como «Presencial»; «Hibrido» e «Híbrido», como «Híbrido»; en orden
    fijo: Presencial, Híbrido, Remoto y después cualquier otra. Lo que el equipo escribió mal
    («test») sale tal cual: la pantalla no adivina ni oculta. La tarjeta usa la misma forma que
    el filtro.
13. Un filtro solo aparece si separa algo. Ciudad, modalidad y empresa: cuando entre las
    publicadas hay al menos dos valores distintos. Fecha: ver el punto 25. Si no, no hay
    rótulo, hueco ni «próximamente».

### Comparte

14. La dirección cambia mientras busca, filtra u ordena (p. ej.
    `/vacantes?q=analista&ciudad=1501&publicada=7d&orden=recientes`; la ciudad va por su código
    del catálogo). Quien la recibe ve la misma búsqueda, los mismos filtros y el mismo orden. Los
    resultados son los de ese momento: si una vacante se cerró, ya no está.
15. Si el enlace trae un filtro que ya no tiene vacantes o que hoy no se muestra (`ciudad=0801`,
    `modalidad=remoto`), aparece igual como etiqueta activa con su nombre («Cusco ✕»,
    «Remoto ✕»), el resultado es «ninguna coincide» y se puede quitar. Nunca se descarta en
    silencio. Lo que la dirección traiga y no se entienda —un código que no es ciudad del
    catálogo, un `publicada` u `orden` con otro valor, parámetros desconocidos— se ignora.

### Vuelve

16. Escribir, filtrar y ordenar no crean entradas en el historial: atrás —del navegador o del
    teléfono en la app— sale de `/vacantes` hacia donde estaba antes.
17. Si alguien entró directo a una ficha por un enlace, «← Volver a las vacantes» lleva a
    `/vacantes` sin búsqueda ni filtros.

### Ordenar: Relevantes o Recientes

18. Con texto en el buscador, la fila del contador muestra «Ordenar por: [Relevantes]
    [Recientes]», dos opciones de las que siempre hay una marcada. Viene marcado «Relevantes».
19. **Relevantes** pone arriba lo que mejor coincide con lo escrito, en este orden: primero las
    vacantes cuyo título empieza por lo buscado; después, aquellas con todas las palabras en el
    título; después, las que tienen parte en el título y el resto en empresa, modalidad, ciudad
    o zona; al final, las que solo coinciden fuera del título. Dentro de cada grupo, la más
    reciente primero.
20. **Recientes** ordena por fecha de publicación, de la más reciente a la más antigua. Las que
    no tienen fecha van al final: el servidor las pondría las primeras.
21. Sin texto en el buscador no hay conmutador: en su sitio se lee «Más recientes primero» y la
    lista va por fecha. Sin nada escrito no hay relevancia que medir, y un botón que no cambia
    nada parece roto. Ocupan el mismo sitio, así que la lista no salta al empezar a escribir.
    Al borrar el texto, la lista vuelve al orden por fecha.
22. Cambiar el orden no mueve el foco ni la búsqueda, y viaja en la dirección solo cuando es
    «Recientes» con texto escrito: «Relevantes» es lo que se muestra si no se dice nada.

### Fecha de publicación

23. El filtro «Publicada» es una elección única: «Cualquier fecha» (marcada de entrada),
    «Últimas 24 horas», «Últimos 7 días» y «Últimos 30 días», cada una con su cantidad.
24. Cuenta con la hora del servidor, como el resto del portal. Las vacantes sin fecha solo
    entran en «Cualquier fecha».
25. Aparece solo si alguna de las tres ventanas deja dentro a unas vacantes y fuera a otras. Si
    todas se publicaron hace más de 30 días, o todas en las últimas 24 horas, el filtro no
    separaría nada y no se muestra.
26. En la tarjeta, la fecha se escribe «Publicada hoy», «ayer», «hace N días» (2 a 13), «hace N
    semanas» (2 a 8) y después «el 12 de agosto» (con el año si no es el actual), en hora de
    Lima. Sin fecha no se escribe nada.

### Cargando, sin conexión y sin vacantes

27. Mientras carga, el buscador ya se puede usar (con el texto del enlace, si lo trae) y se ven
    tarjetas esqueleto. El contador no dice «0».
28. Si falla la conexión: «No pudimos cargar las vacantes.» con «Intentar de nuevo». La búsqueda,
    los filtros y el orden se conservan. No se muestra «ninguna coincide» ni «no hay vacantes».
    Si la lista ya estaba cargada y falla una recarga en segundo plano, la lista sigue a la vista.
29. Si no hay ninguna vacante publicada: «Ahora mismo no hay vacantes abiertas.» con el texto de
    hoy, sin buscador, filtros ni orden, aunque se llegue con un enlace que traiga una búsqueda.

### Portada y ficha

30. La sección de vacantes de la portada muestra las tres más recientes y «Ver las 9 vacantes»,
    que lleva a `/vacantes`. Los dos botones «Ver las vacantes abiertas» también. Sin vacantes,
    la sección dice lo mismo que hoy. Los enlaces viejos a `/#vacantes-abiertas` siguen cayendo
    en esa sección.
31. En la cabecera del portal (la misma en teléfono y escritorio), **«Vacantes» lleva a
    `/vacantes`** y deja de saltar a la sección de la portada. Pasa a marcarse como activo, como
    «Inicio» y «Mis procesos»: encendido en `/vacantes` y en la ficha de cualquier vacante
    (`/vacantes/:id`), apagado en la portada. «Inicio» se enciende solo en la portada. Hoy
    «Vacantes» no se enciende nunca, a propósito: compartía dirección con «Inicio» y se habrían
    encendido los dos.
32. El título de la pestaña del navegador en `/vacantes` es «Vacantes abiertas», que hoy lleva la
    portada; la portada pasa a «Inicio».
33. La cinta de la portada cuenta las ciudades distintas del catálogo entre las publicadas, sin
    «Fuera del Perú»; las vacantes sin ciudad no suman. Con cero ciudades, no dice nada de
    ciudades, como hoy.
34. La ficha pinta la ciudad y, detrás, la zona: «Presencial · Arequipa · Selva Alegre ·
    9am-6pm». Si la zona repite el nombre de la ciudad (sin mirar mayúsculas ni tildes), no se
    repite. Sin ciudad, solo la zona.

### Teclado y lector de pantalla

35. El buscador vive en una región de búsqueda con etiqueta visible («Buscar vacantes»); el texto
    de ejemplo no hace de etiqueta.
36. El contador se anuncia de forma educada **cuando la persona deja de escribir** (medio
    segundo), no con cada letra: «2 de 9 vacantes», «Ninguna vacante coincide». Al cambiar el
    orden se anuncia «Ordenadas por relevancia» o «Ordenadas por más recientes».
37. Filtrar y ordenar nunca mueven el foco. Al quitar una etiqueta de filtro, el foco pasa a la
    siguiente o, si era la última, al buscador; nunca se pierde.
38. Orden de tabulación: buscador → borrar búsqueda → orden → filtros (o «Filtrar» en el
    teléfono) → etiquetas activas → resultados.
39. «Ordenar por» y «Publicada» son grupos de opciones exclusivas con su nombre: se entra con
    Tab y se cambia con las flechas. Ciudad, modalidad y empresa son grupos de casillas; cada
    opción dice su cantidad.
40. Cada tarjeta es un solo enlace cuyo nombre es el título de la vacante, dentro de una lista con
    encabezados. Las etiquetas activas se leen «Quitar filtro Lima».
41. «Filtrar» del teléfono dice si está abierto o cerrado. El mensaje de «ninguna coincide» y su
    botón quedan justo después del buscador en el orden de lectura.

### En el panel: modalidad y ciudad se eligen de una lista

42. En el formulario de alta y edición de vacantes, «Modalidad» deja de ser una caja de texto y
    pasa a ser un desplegable con **Presencial, Híbrido y Remoto**. Se guarda exactamente ese
    texto, con su tilde. Al crear una vacante es obligatoria: sin elegirla no se guarda, y bajo
    el campo se lee «Elige si es presencial, híbrida o remota».
43. «Ciudad» es un desplegable con **las mismas ciudades que el registro del candidato**,
    agrupadas por departamento, con «Fuera del Perú» al final. Al crear, es obligatoria si la
    modalidad es Presencial o Híbrido: «Elige la ciudad del puesto». Con Remoto es opcional. Si
    se cambia de Remoto a Presencial sin ciudad, vuelve a pedirse.
44. «Ubicación» pasa a llamarse **«Zona o referencia (opcional)»**, con la pista «Barrio,
    distrito o dirección. Se ve en la ficha de la vacante». Sigue siendo texto libre y guarda lo
    que ya había escrito.
45. Al editar, el desplegable de modalidad marca la opción equivalente sin mirar mayúsculas ni
    tildes («PRESENCIAL» → Presencial, «Hibrido» → Híbrido), y el de ciudad, la que tenga
    guardada.
46. **Si nadie toca un desplegable, se guarda lo que había, letra por letra.** Corregir otra cosa
    no puede convertir «PRESENCIAL» en «Presencial»: para el sistema sería un cambio de modalidad
    y avisaría a cada postulante en carrera de un cambio que no existe. Solo si la persona elige
    otra opción se guarda el valor nuevo, y ese sí avisa, como hoy.
47. Una vacante vieja sin modalidad o sin ciudad muestra «Sin indicar» en ese desplegable y se
    puede guardar así, para que corregir otra cosa no obligue a decidirla ni dispare un aviso.
    «Sin indicar» solo se ofrece en ese caso.
48. Una modalidad que no se parece a ninguna opción («test») aparece como opción propia y
    marcada, «test (valor anterior)», con la pista «Elige una de las tres para que el portal
    pueda filtrarla». Si no se cambia, se guarda tal cual.
49. Cambiar la ciudad de una vacante publicada es un cambio visible: sale en el aviso a los
    postulantes en carrera como «Ciudad: — → Arequipa», igual que la modalidad o el horario, y
    queda en la auditoría. La zona también, como «Zona o referencia: antes → ahora».
50. El backend rechaza una ciudad que no sea una de las del desplegable —un departamento como
    «04», un distrito, un código inventado— con «Esa ciudad no está en el catálogo», sin
    guardar nada. Sin ciudad sí acepta: la exigencia es del formulario.

### Las convocatorias que se cargan por script

51. En `scripts/convocatorias.json`, cada `vacante` que dice su modalidad la escribe como el
    desplegable («Presencial», «Híbrido», «Remoto») y lleva `ciudadUbigeo` con el código de su
    ciudad en el catálogo:
    - «talento»: `"modalidad": "Presencial"`, `"ciudadUbigeo": "0401"` (Arequipa).
    - «asistente-admin» y «administrador»: `"modalidad": "Presencial"`, `"ciudadUbigeo": "1501"`
      (Lima).
    - En las tres se quita `"ubicacion"`: solo repetía el nombre de la ciudad, y desde ahora ese
      campo es la zona.
    - «arquitecto» y «civil» siguen sin modalidad ni ciudad: el JSON no las dice y no se
      inventan. Se añaden cuando el equipo las decida.
52. El texto `_lea_esto` del JSON explica los tres campos: `modalidad` es uno de los tres
    valores del desplegable; `ciudadUbigeo` es el código de una provincia del catálogo (Arequipa
    `0401`, Lima `1501`; la lista entera, en `GET /api/v1/portal/catalogos/ubigeo`); `ubicacion`
    es opcional y lleva la zona o referencia, no la ciudad.
53. Un código que no está en el catálogo hace fallar la carga con el mensaje del backend, antes
    de publicar nada. Las vacantes que ya existen no cambian: el script las reutiliza sin
    editarlas, así que el JSON nuevo solo cuenta en las cargas nuevas (un clon, una demo, una
    base limpia).

## Referencia visual

Los bocetos fijan la disposición, no medidas ni colores. Las cantidades de «Publicada» son de
ejemplo: las fechas reales todavía no se ven. Los bocetos suponen que el equipo ya puso Arequipa
a las dos vacantes de Selva Alegre.

**Escritorio, con búsqueda**

```
Vacantes abiertas

Buscar vacantes
[ ingeniero                                             ✕ ]
2 de 9 vacantes                        Ordenar por [Relevantes][Recientes]
Ciudad     [ ] Arequipa (0)   [ ] Lima (0)   [ ] Sin indicar (2)
Publicada  (•) Cualquier fecha (2)  ( ) Últimas 24 horas (0)
           ( ) Últimos 7 días (1)   ( ) Últimos 30 días (2)

┌──────────────────┐ ┌──────────────────┐
│Ingeniero Civil   │ │Ingeniero/a de    │
│Builder Junior    │ │Infraestructura   │
│RENASER CONSULTING│ │RENASER CONSULTING│
│Resumen, tres     │ │Resumen, tres     │
│líneas como mucho…│ │líneas como mucho…│
│Sueldo sin        │ │Sueldo sin        │
│publicar          │ │publicar          │
│Publicada hace 3 d│ │Hace 2 semanas    │
└──────────────────┘ └──────────────────┘
```

**Escritorio, sin búsqueda** — la fila del contador dice «9 vacantes abiertas · Más recientes
primero» y no hay conmutador. La tarjeta de Marketing Digital dice «Presencial · Arequipa».

**Teléfono, con búsqueda y un filtro**

```
Vacantes abiertas
Buscar vacantes
[ ingeniero               ✕ ]
1 de 9 vacantes    [Filtrar (1)]
Ordenar por [Relevantes][Recientes]
[Últimos 7 días ✕] [Quitar filtros]
┌───────────────────────────┐
│ Ingeniero Civil Builder   │
│ Junior                    │
│ RENASER CONSULTING S.A.C  │
│ Resumen…                  │
│ Sueldo sin publicar       │
│ Publicada hace 3 días     │
└───────────────────────────┘
```

**Ninguna coincide**

```
[ analista               ✕ ]
Ninguna vacante coincide con «analista» en Lima.
Hay 9 vacantes abiertas.
[Lima ✕]         [Ver las 9 vacantes]
```

**Sin vacantes** — sin buscador, filtros ni orden:

```
Vacantes abiertas
Ahora mismo no hay vacantes abiertas.
Publicamos convocatorias nuevas con frecuencia. Vuelve por aquí en unos días.
```

**Sin conexión** — buscador con su texto, mensaje y «Intentar de nuevo» en lugar de la lista.
**Cargando** — buscador activo, contador oculto y tres tarjetas esqueleto.
**Datos largos** — el título se parte en líneas y no se corta; el resumen se corta a tres líneas;
un nombre de ciudad largo («Mariscal Ramón Castilla») se parte dentro de su opción sin empujar
la fila; la zona larga se parte en la ficha.

**Panel · formulario de la vacante**

```
Modalidad             Ciudad                      Horario
[ Presencial      ▾]  [ Elige la ciudad…      ▾]  [ 9am-6pm        ]
                        Amazonas
                          Bagua
                          …
                        Arequipa
                          Arequipa
                          Camaná
                          …
                        Fuera del Perú
Elige la ciudad del puesto.        ← solo al guardar sin ella, si no es Remoto

Zona o referencia (opcional)
[ Selva Alegre                                            ]
Barrio, distrito o dirección. Se ve en la ficha de la vacante.
```

- Escritorio: rejilla de tres columnas como hoy (dos en tableta). Buscador, contador y orden
  arriba; los filtros en filas bajo el buscador, siempre visibles. **No hay columna lateral**:
  con uno o dos filtros sería una columna medio vacía, que es justo lo que hace parecer rota una
  pantalla con pocas vacantes.
- Teléfono: una columna. El orden queda a la vista bajo el contador. Los filtros se pliegan tras
  «Filtrar (n)», que se despliega en su sitio —sin ventana encima— y la lista responde con el
  panel abierto. Las etiquetas activas se ven siempre. Todo lo que se toca mide 44 px o más; el
  campo mide 48 px con letra de 16 px (evita el zoom automático de iOS). Sin scroll horizontal
  desde 320 px de ancho.
- **El conmutador de orden se adapta al `DESIGN.md`**: la referencia es una píldora azul marino,
  y el portal prohíbe las píldoras y el color en los botones. Aquí lleva esquina de 4 px, sin
  azul; la opción marcada se distingue por su relleno y la otra por el contorno de control.
- Reglas visuales: `DESIGN.md` del portal. Acción en negro; **ningún color en esta pantalla**,
  porque aquí no hay turno de nadie; lo marcado se distingue por la forma, no por el color;
  `tabular-nums` en contadores y fechas; filtrar y ordenar no animan la lista, y todo respeta
  `prefers-reduced-motion`. Se reutiliza la tarjeta de la portada y su adelanto de datos a la
  ficha. En el panel, los desplegables son el mismo `Selector` que ya usa «Cómo se cierra», y el
  de ciudad agrupa por departamento igual que el registro.
- A criterio de quien implementa: iconos, espaciados, forma exacta del esqueleto, el relleno
  concreto de la opción marcada y el ancho a partir del cual los filtros dejan de plegarse.

## Criterios de aceptación

- AC-01: Dado que hay 9 vacantes publicadas, cuando alguien abre `/vacantes` sin sesión,
  entonces ve las 9 de la más reciente a la más antigua, el contador «9 vacantes abiertas» y
  «Más recientes primero», sin conmutador de orden.
- AC-02: Dado que la lista está completa, cuando escribe «GESTION», entonces queda solo
  «Especialista en Gestión del Talento» y el contador dice «1 de 9 vacantes».
- AC-03: Dado que escribe «ingeniero», entonces aparecen las 2 vacantes con «ingeniero» en el
  título y no las que solo lo mencionan en los requisitos.
- AC-04: Dado que escribe «analista», entonces ve «Ninguna vacante coincide con «analista»» y
  «Ver las 9 vacantes»; al pulsarlo, vuelve la lista completa con el buscador vacío.
- AC-05: Dado que no hay vacantes publicadas, cuando alguien abre `/vacantes?q=analista`,
  entonces ve «Ahora mismo no hay vacantes abiertas» sin buscador, filtros ni orden.
- AC-06: Dado que hay «Presencial» y «PRESENCIAL», cuando se muestran las opciones y las
  tarjetas, entonces hay una sola opción «Presencial» y todas las tarjetas la escriben así.
- AC-07: Dado que la única modalidad escrita es Presencial, entonces no se muestra el filtro de
  modalidad; al publicar desde el panel otra vacante con «Remoto», aparece con Presencial y
  Remoto sin cambiar nada más.
- AC-08: Dadas 2 vacantes en Lima, 2 en Arequipa y 5 sin ciudad, cuando marca «Lima», entonces
  ve 2 vacantes y la etiqueta «Lima ✕»; al marcar además «Sin indicar», ve 7.
- AC-09: Dado que escribió «ingeniero», marcó «Arequipa» y eligió «Recientes», cuando copia la
  dirección y la abre en otro navegador, entonces ve la misma búsqueda, el mismo filtro, el
  mismo orden y el mismo resultado.
- AC-10: Dado un enlace con `ciudad=0801` sin vacantes en Cusco, cuando se abre, entonces
  aparece la etiqueta «Cusco ✕» y «ninguna coincide»; al quitarla, vuelve la lista completa. Con
  `ciudad=04` (un departamento), el parámetro se ignora.
- AC-11: Dado que llegó a `/vacantes` desde la portada, escribió «administrativo» letra a letra y
  cambió el orden dos veces, cuando pulsa atrás una vez, entonces vuelve a la portada.
- AC-12: Dado que filtró y abrió una ficha desde la lista, cuando pulsa atrás o «Volver a las
  vacantes», entonces ve la lista con la misma búsqueda, filtros y orden, a la altura de esa
  tarjeta.
- AC-13: Dado que el servidor no responde, cuando abre `/vacantes?q=lider`, entonces ve «No
  pudimos cargar las vacantes», el buscador con «lider» y «Intentar de nuevo»; al volver la
  conexión y reintentar, ve el resultado filtrado.
- AC-14: Dada una vacante publicada hace 3 días y otra sin fecha, entonces la primera dice
  «Publicada hace 3 días», la segunda no dice fecha y, en el orden por fecha, sale la última.
- AC-15: Dadas «Analista de Datos», publicada hace 20 días, y «Coordinador de Analistas»,
  publicada ayer, cuando escribe «analista», entonces con «Relevantes» sale primero «Analista de
  Datos» y con «Recientes», «Coordinador de Analistas».
- AC-16: Dado que hay texto en el buscador, cuando lo borra, entonces el conmutador desaparece,
  en su lugar se lee «Más recientes primero» y la lista vuelve al orden por fecha sin saltar.
- AC-17: Dadas vacantes publicadas hoy, hace 3 días, hace 10 días y hace 40 días, entonces
  «Publicada» muestra Cualquier fecha (4), Últimas 24 horas (1), Últimos 7 días (2) y Últimos
  30 días (3); al elegir «Últimos 7 días» quedan 2 y aparece la etiqueta «Últimos 7 días ✕».
- AC-18: Dado que todas las vacantes se publicaron hace más de 30 días, entonces no se muestra el
  filtro «Publicada».
- AC-19: Dado un teléfono de 375 px, entonces no hay scroll horizontal, los filtros están
  plegados tras «Filtrar», el orden se ve bajo el contador y la tecla «Buscar» del teclado cierra
  el teclado sin recargar.
- AC-20: Dado un lector de pantalla, cuando escribe «ingeniero» y se detiene, entonces se anuncia
  una sola vez «2 de 9 vacantes» y el foco sigue en el buscador; al elegir «Recientes» con las
  flechas se anuncia «Ordenadas por más recientes».
- AC-21: Dado que solo usa el teclado, cuando quita la etiqueta «Lima ✕», entonces el foco pasa
  a la siguiente etiqueta o, si no hay, al buscador.
- AC-22: Dado que hay 9 vacantes, cuando abre la portada, entonces su sección muestra las 3 más
  recientes y «Ver las 9 vacantes» lleva a `/vacantes`; `/#vacantes-abiertas` sigue llevando a
  esa sección. Al pulsar «Vacantes» en la cabecera, desde cualquier pantalla, llega a
  `/vacantes` con «Vacantes» encendido y «Inicio» apagado; en la ficha de una vacante,
  «Vacantes» sigue encendido; en la portada, solo «Inicio».
- AC-23: Dadas 2 vacantes en Lima, 2 en Arequipa y 5 sin ciudad, cuando abre la portada, entonces
  la cinta dice «2 ciudades»; con las de Arequipa sin ciudad, dice «1 ciudad».
- AC-24: Dada una vacante en borrador, una archivada y una eliminada, entonces ninguna aparece ni
  cuenta en el contador, en los filtros ni en la cinta.
- AC-25: Dadas vacantes con ubicación «Lima», «LIMA», «Selva Alegre», «test», «Arequipa, Perú» y
  vacía, cuando corre la migración, entonces las dos primeras quedan en la ciudad Lima, las demás
  sin ciudad, y el texto de todas sigue intacto como zona.
- AC-26: Dada una vacante con ciudad Arequipa y zona «Selva Alegre», entonces su tarjeta dice
  «Presencial · Arequipa» y su ficha «Presencial · Arequipa · Selva Alegre · 9am-6pm»; con ciudad
  Lima y zona «Lima», la ficha dice «Lima» una sola vez.
- AC-27: Dado el formulario de alta del panel, cuando se intenta guardar sin modalidad, o con
  Presencial y sin ciudad, entonces no se guarda y cada campo dice qué falta; con Remoto y sin
  ciudad, se guarda.
- AC-28: Dado el desplegable de ciudad del panel, entonces ofrece las mismas ciudades que el del
  registro del candidato, agrupadas por departamento y con «Fuera del Perú» al final.
- AC-29: Dada una vacante publicada con modalidad «PRESENCIAL», ciudad Lima y postulantes en
  carrera, cuando se edita solo el horario, entonces modalidad y ciudad quedan igual y el aviso a
  los postulantes nombra solo el horario.
- AC-30: Dada una vacante publicada sin ciudad, con zona «Selva Alegre» y postulantes en carrera,
  cuando se le elige Arequipa, entonces cada postulante en carrera recibe un solo aviso con
  «Ciudad: — → Arequipa» y la zona no cambia.
- AC-31: Dada una vacante publicada sin modalidad ni ciudad, cuando se edita solo la
  descripción, entonces se guarda con «Sin indicar» en ambos y el aviso nombra solo la
  descripción.
- AC-32: Dada una vacante con modalidad «test», cuando se abre para editar, entonces el
  desplegable marca «test (valor anterior)» con la pista para elegir una de las tres.
- AC-33: Dado un guardado por la API con ciudad «04», «040101» o «999999», entonces responde 400
  con «Esa ciudad no está en el catálogo» y no guarda nada; sin ciudad, guarda.
- AC-34: Dada una base sin la convocatoria «administrador», cuando se carga con
  `scripts/cargar-convocatoria.py` y el JSON nuevo, entonces la vacante queda publicada con
  modalidad «Presencial», ciudad Lima y sin zona, y en el portal cuenta bajo «Lima».
- AC-35: Dada una convocatoria del JSON con un `ciudadUbigeo` que no existe, cuando se carga,
  entonces el script se detiene con «Esa ciudad no está en el catálogo» y no queda ninguna
  vacante nueva publicada.

## Casos límite y errores

- Datos vacíos o inválidos:
  - Sin modalidad ni ciudad ni zona: la tarjeta no lleva línea superior. Sin propósito ni
    descripción: título, empresa y pie. Sin horario: el pie sin él. Un campo con solo espacios
    cuenta como vacío.
  - Modalidades basura («test») salen tal cual en la tarjeta y como opción.
  - Una ciudad desactivada en el catálogo después de guardarse sigue viéndose en la vacante y en
    el filtro; el panel la muestra marcada aunque ya no se ofrezca para vacantes nuevas.
  - Búsqueda con `/`, `?`, `&`, `%` o comillas: se busca tal cual y la dirección no se rompe.
    Una búsqueda de 200 caracteres no rompe la disposición.
  - Con «Relevantes», si ninguna vacante coincide en el título, todas van al último grupo y el
    orden queda por fecha: es lo esperado, no un fallo.
- Usuario sin permisos: la pantalla es pública; con sesión se ve igual. Lo que no está publicado,
  lo eliminado y lo de empresas suspendidas no llega (lo decide el servidor, como hoy). El
  formulario del panel mantiene los permisos que ya tiene.
- Recarga, reintento o doble envío: recargar conserva búsqueda, filtros y orden. «Intentar de
  nuevo» repite la carga sin borrar nada. En el portal no hay nada que enviar dos veces; en el
  panel rige lo que ya hace el formulario (spec 01).
- Otros casos relevantes:
  - Una vacante se cierra mientras la persona mira: la lista no cambia hasta recargar; la ficha ya
    dice «ya no está disponible».
  - Una vacante desarchivada conserva su fecha de publicación original: puede salir abajo con
    «el 3 de julio» y fuera de «Últimos 30 días». Es lo esperado.
  - La pantalla abierta varias horas: las ventanas de fecha y el «hace N días» se calculan al
    cargar; no se recalculan solos.
  - 40 vacantes: sin paginación, y filtrar u ordenar se siente instantáneo en un teléfono modesto.
  - Una sola vacante: buscador y contador «1 vacante abierta» en singular; ningún filtro.
  - Los scripts y la API pueden seguir creando vacantes sin ciudad y con la modalidad escrita a
    su manera («PRESENCIAL»): el portal agrupa mayúsculas y tildes, y lo que no tiene ciudad cae
    en «Sin indicar».
  - Un remoto sin ciudad cae en «Sin indicar» del filtro de ciudad. Es lo esperado: quien busca
    remoto lo encuentra por modalidad.

## Datos y compatibilidad

- Información que debe guardarse:
  - La vacante gana una **ciudad**: un código del catálogo `ubigeo`, opcional, con referencia a
    la tabla (mismo tipo que `persona.ciudad_ubigeo`). Solo se aceptan los códigos que el
    catálogo ofrece (provincias vivas y «Fuera del Perú»), con la validación que ya existe.
  - La columna de texto `ubicacion` no cambia de nombre ni de contenido: desde ahora es la
    «Zona o referencia».
  - La modalidad sigue en su columna de texto; el panel la llena con uno de tres valores.
  - Búsqueda, filtros y orden viven en la dirección; nada de eso se guarda.
- Migración (siguiente número libre al integrar, comprobando antes las ramas abiertas; no se fija
  aquí):
  - Añade la columna de ciudad a la vacante.
  - Rescata la ciudad cuando el texto de ubicación, sin espacios de los bordes y sin mirar
    mayúsculas ni tildes, es **exactamente** el nombre de **una sola** provincia. Si no casa o
    casa con varias, la deja vacía. No toca el texto.
- Lo que viaja:
  - Vacante pública (listado y ficha): `publicadaEn` (instante, puede ser nulo) y `ciudad`
    (código, nombre y departamento, o nulo). `ubicacion` sigue viajando con la zona. Los
    clientes actuales ignoran los campos nuevos.
  - Vacante del panel: la ciudad (código y nombre) para marcar el desplegable al editar.
  - Guardar vacante (alta y edición): acepta la ciudad por su código en `ciudadUbigeo`, el mismo
    nombre que usa el registro del candidato. Es el nombre que lleva `scripts/convocatorias.json`,
    porque el script manda su bloque `vacante` tal cual.
- Comportamiento para registros existentes:
  - Las publicadas por el panel ya tienen fecha; si alguna no la tiene, se trata como en los
    puntos 20 y 24.
  - Tras la migración, de las 9 publicadas hoy solo Administrador y Asistente Administrativo
    tienen ciudad (Lima). Las de Selva Alegre y las cinco vacías quedan sin ciudad hasta que el
    equipo las edite; ese cambio avisa a sus postulantes en carrera, porque para ellos es
    información nueva.
  - Ninguna modalidad guardada se corrige sola (punto 46).
  - El detector de cambios de la spec 01 suma la ciudad como campo visible corto y renombra
    «Ubicación» a «Zona o referencia» en los avisos nuevos. Los avisos ya enviados no cambian.
- Restricciones de compatibilidad:
  - `/`, `/#vacantes-abiertas` y `/vacantes/:id` siguen funcionando igual.
  - El catálogo es el mismo del registro: nada nuevo que sembrar, y si algún día se cargan
    distritos, esta pantalla no cambia porque solo ofrece lo que el catálogo ofrece.
  - La app Android empaqueta el código: recibe la pantalla con su próxima compilación, no antes.

## Verificación

- Actor/rol y datos de prueba necesarios:
  - Portal: visitante sin sesión. **No hay login de por medio**, así que QA y el usuario pueden
    comprobarlo todo a la vista. Una pasada con sesión de candidato para confirmar que se ve igual.
  - Panel: una cuenta de equipo con permiso para crear y editar vacantes. Esa parte está detrás
    del login.
  - En el clon, cinco juegos: **como producción** (las 9 de la tabla de arriba, con Lima en las
    dos de Lima, Arequipa y zona «Selva Alegre» en las dos de Selva Alegre, y las cinco sin
    ciudad); **sucio** (añade «Híbrido» e «Hibrido», una «Remoto» sin ciudad, un «test», un
    título de 150 caracteres, una sin fecha, «Analista de Datos» hace 20 días y «Coordinador de
    Analistas» ayer, fechas de hoy, 3, 10 y 40 días, y una en borrador y otra archivada que no
    deben salir); **migración** (los textos de AC-25, antes de migrar); **muro** (40 publicadas
    con fechas y ciudades repartidas); **vacío** (0 publicadas). Al menos una vacante publicada
    con postulantes en carrera, para AC-29 a AC-31.
- Recorridos que deben quedar como E2E repetibles: AC-01 a AC-04, AC-08 a AC-18, AC-22 a AC-24,
  AC-26, AC-27 y AC-29 a AC-31, más AC-19 con el viewport de un teléfono. AC-25 y AC-33 como
  pruebas del backend. AC-34 y AC-35 se comprueban una vez en el clon, lanzando el script contra
  su backend; no hace falta automatizarlos.
- Riesgos que QA debe explorar con navegador:
  - Qué anuncia el lector de pantalla y cuántas veces mientras se escribe y se cambia el orden
    (AC-20), y el foco al quitar etiquetas (AC-21).
  - Que la lista no salte cuando aparece o desaparece el conmutador de orden.
  - Zoom al 200 % y 320 px de ancho: sin scroll horizontal ni etiquetas cortadas.
  - Red lenta y red caída a mitad de uso: que nunca aparezca «no hay vacantes» por error.
  - Enlaces con parámetros raros o repetidos, y el juego «muro» en un teléfono de gama baja.
  - En el panel: editar vacantes viejas sin tocar los desplegables y comprobar que no sale ningún
    aviso de modalidad ni de ciudad; el desplegable de ciudad con teclado (escribir para saltar).
- Resultado que quiero comprobar personalmente:
  - En mi teléfono, buscar «ingeniero», cambiar a «Recientes», marcar una ciudad, compartir el
    enlace por WhatsApp y abrirlo en otro teléfono.
  - Que con las 9 de hoy la pantalla no parezca vacía, y con una sola coincidencia no parezca
    rota.
  - Que el botón atrás del teléfono salga de la pantalla (en la app, tras compilarla).
  - En el panel (con mi sesión): crear una vacante y ver los desplegables de modalidad y ciudad;
    abrir una vieja y ver que respeta lo que tenía.

## Dependencias y dudas

- Requiere: en el mismo trabajo, frontend y backend (harness con los dos repositorios): la fecha
  y la ciudad en el backend, con su migración, antes de que el portal las lea.
- Decisiones pendientes: ninguna que bloquee. Fuera de esta spec, dos cosas que cambian lo que
  la pantalla enseña:
  - **Tras desplegar, el equipo tiene que poner ciudad a las vacantes que la migración no
    rescata**: las dos de Selva Alegre (probablemente Arequipa, con «Selva Alegre» como zona) y
    las que sigan publicadas sin ninguna. Hasta entonces el filtro de ciudad no aparece, porque
    solo habría una ciudad. Cada edición avisa a los postulantes en carrera de esa vacante.
  - «Desarrollador web», «Líder de operaciones» e «Ingeniero/a de Infraestructura» parecen de
    prueba: dos comparten la misma descripción genérica y el mismo requisito («Disponibilidad en
    Arequipa»), y ninguna tiene modalidad, ubicación ni responsabilidades. Si no son reales,
    archivarlas desde el panel.
