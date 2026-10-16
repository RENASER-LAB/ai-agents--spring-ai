# Logros clave en el perfil del candidato
Estado: TERMINADO

## Objetivo
La clienta quiere que el perfil del candidato muestre **tres resultados concretos por los
que deberían contratarlo**. Hoy la cabecera dice quién es la persona (nombre, titular,
señas) pero nada de lo que ha conseguido; lo único parecido es el texto libre de «En pocas
palabras», que casi nunca trae resultados medibles. Se resuelve con un dato nuevo,
**«Logros clave»**: hasta tres frases cortas que escribe el propio candidato en «Acerca de
ti» y que se pintan en dos sitios: la cabecera, arriba, y la sección «Acerca de ti», más
abajo.

## Contexto
- Comportamiento actual: la cabecera (`CabeceraDelPerfil`) muestra, de arriba abajo:
  portada, foto o iniciales, nombre (sale de la sesión), botón «Editar perfil», titular,
  señas (ubicación, experiencia en años y meses, disponibilidad: cada una solo si está
  llena), la línea de reseñas «★ 4,5 · 3 reseñas» (solo si hay reseñas) y los enlaces
  (LinkedIn, GitHub, Portafolio…). La sección «Acerca de ti», en su vista normal, pinta el
  texto de «En pocas palabras», «Lo que sabes hacer» y la pretensión. Su formulario edita
  siete campos —titular, «En pocas palabras», aptitudes, experiencia en meses, dónde
  estás, desde cuándo puedes empezar y pretensión— y guarda con un PUT que **reemplaza los
  siete de golpe**.
- Pantallas o procesos afectados: «Mi perfil» del portal del candidato (web y app de
  Android, que empaqueta el mismo `src/`): cabecera, vista normal y formulario de «Acerca
  de ti». Backend: `PUT /api/v1/portal/perfil` (cabecera), `GET /perfil`,
  `GET /perfil/descarga` y la tabla `perfil_candidato`.
- Referencias existentes:
  - portal (repositorio RenaserOsPostulantes): `src/paginas/perfil/Cabecera.tsx`
    (`CabeceraDelPerfil`), `src/paginas/perfil/Perfil.tsx` (sección «Acerca de ti»; ojo, su
    componente también se llama `Cabecera`, con `CamposCabecera` y `sembrar`),
    `src/ui/campos/Campo.tsx` y el `DESIGN.md` del portal;
  - backend: `perfil/dto/DtosPerfil.java` (`EditarCabecera`, `PerfilCompleto`),
    `perfil/service/impl/ServicioPerfilPortalImpl.java`, `PintorDePerfil.java` y
    `ServicioPropuestaPerfilImpl.java` (la lectura del CV).
  - El `DESIGN.md` de este repositorio es el de RENASER OS, abandonado: no aplica.

## Alcance
- Incluye: el campo nuevo en el formulario «Acerca de ti»; su guardado junto al resto de
  la cabecera; su pintado en la cabecera (versión compacta) y en la vista normal de
  «Acerca de ti» (versión completa); su inclusión en la descarga de «Mis datos».
- Queda fuera:
  - **IA de cualquier tipo.** Los logros los escribe solo el candidato: la lectura del CV
    no los propone ni los rellena, y no entran en ningún prompt.
  - Que puntúen o entren al ranking (el perfil no puntúa).
  - Pintarlos en el panel de la empresa (la ficha del panel todavía no muestra el perfil).
  - El Excel del ranking.

## Comportamiento esperado
1. El candidato abre «Acerca de ti» → «Editar lo tuyo» y ve, justo debajo de «En pocas
   palabras», el bloque **«Tus logros clave»** con la ayuda: «Hasta tres resultados por los
   que deberían contratarte. Mejor si llevan una cifra: «Reduje de 10 a 4 días el cierre
   contable»». Trae tres cajas de una línea, numeradas 1, 2 y 3, todas opcionales, de hasta
   100 caracteres cada una.
2. El candidato escribe de cero a tres logros y pulsa «Guardar». El sistema guarda los no
   vacíos, recortados y en el orden en que quedaron (si llena la 1 y la 3, se guardan como
   primero y segundo).
3. La **cabecera** muestra, debajo de las señas y de la línea de reseñas y antes de los
   enlaces, el rótulo pequeño **«Logros clave»** y la lista numerada de lo guardado. Es la
   versión compacta: cada logro ocupa como mucho dos líneas y, si no cabe, se corta con
   «…».
4. La **vista normal de «Acerca de ti»** muestra los mismos logros, con el mismo rótulo
   «Logros clave» y la misma numeración, entre el texto de «En pocas palabras» y «Lo que
   sabes hacer». Aquí van siempre completos, sin cortar.
5. Sin logros no aparece nada en ninguno de los dos sitios: ni rótulo ni hueco.
6. Al volver a editar, las cajas vienen sembradas con lo guardado. Vaciar las tres y
   guardar borra los logros.
7. Cada caja no admite más de 100 caracteres: lo tecleado o pegado se corta en 100 y un
   contador lo avisa al acercarse, como en «En pocas palabras». El backend rechaza con 400 lo
   que llegue por encima de los topes por API.

## Referencia visual
- Boceto de la cabecera (escritorio):
  ```
  ┌─────────────────────────────────────────────────────────────┐
  │                      [ portada ]                 [Portada]  │
  │  (foto)  Luis Perez                         [Editar perfil] │
  │          Desarrollador Backend                              │
  │          ⏱ 1 año y 8 meses de experiencia                   │
  │                                                             │
  │          LOGROS CLAVE                                       │
  │          1  Reduje de 3 s a 400 ms la respuesta de la API   │
  │          2  Migré 40 servicios a AWS sin caídas             │
  │          3  Automaticé el despliegue de 5 aplicaciones      │
  │                                                             │
  │          in LinkedIn   GitHub                               │
  └─────────────────────────────────────────────────────────────┘
  ```
- Boceto de la vista normal de «Acerca de ti»:
  ```
  Acerca de ti
  Egresado de Ingeniería de Sistemas con experiencia en desarrollo…

  Logros clave
  1  Reduje de 3 s a 400 ms la respuesta de la API de pagos
  2  Migré 40 servicios a AWS sin caídas
  3  Automaticé el despliegue de 5 aplicaciones

  Lo que sabes hacer
  [Java] [Spring] [PostgreSQL]

  [Editar lo tuyo]
  ```
- Espacio en la cabecera: el bloque añade el rótulo y hasta tres logros de como mucho dos
  líneas cada uno, siete líneas como máximo. Si usa el ancho de lectura del titular
  (`--medida`, 50ch), un logro de 100 caracteres ocupa dos líneas también en escritorio. El
  ancho lo elige quien implementa; el texto completo siempre está en «Acerca de ti».
- Formulario: tres campos de texto de una línea con su número delante, dentro de un
  `fieldset` con `legend` «Tus logros clave» y la ayuda debajo, igual que «Lo que esperas
  ganar». Contador de caracteres como el de «En pocas palabras». Hoy el tope y el contador
  solo existen en `AreaTexto` (textarea): corta con `maxLength` y enseña el contador pasado el
  80 %. `Campo` (input de una línea) no los tiene: se le añade una prop `maximo` con el mismo
  comportamiento.
- Estados: vacío (no se pinta el bloque en ninguna de las dos vistas; en el formulario,
  tres cajas vacías); con uno o dos logros (solo esos, sin huecos numerados); con datos
  largos (cabecera: dos líneas y «…», con el texto completo accesible para lectores de
  pantalla; «Acerca de ti»: completo, envolviendo); guardando y con error (los del
  formulario actual).
- Móvil y escritorio: la misma lista vertical en los dos; en móvil ocupa el ancho de la
  tarjeta debajo de la foto, como el resto de los datos. Nada de columnas.
- Reglas visuales: el `DESIGN.md` del portal y los comentarios de `Cabecera.tsx` (los que
  dicen «violeta» son de antes del 25/09: hoy el acento es el índigo `--activo`). **Nada en
  índigo**: por «la regla de la voz única», el índigo es solo lo que se pulsa, dónde estás o lo
  que te toca. Y nada encima de la portada. El
  rótulo usa el estilo de los subtítulos existentes («Lo que sabes hacer»); el número, un
  tono neutro. ⚠️ `Perfil.tsx` dejó de repetir en «Acerca de ti» el titular y las señas
  porque la segunda aparición «parecía otro dato distinto»: por eso los logros llevan el
  **mismo rótulo, numeración y orden** en los dos sitios, para que se lean como el mismo
  dato. El resto, a criterio de quien implementa.

## Criterios de aceptación
- AC-01: Dado un candidato sin logros, cuando abre «Mi perfil», entonces ni la cabecera ni
  «Acerca de ti» muestran el rótulo «Logros clave» ni dejan espacio vacío.
- AC-02: Dado el formulario «Acerca de ti» abierto, cuando el candidato escribe tres logros
  y guarda, entonces la cabecera y la vista normal de «Acerca de ti» muestran «Logros
  clave» con los tres, numerados y en el mismo orden, sin recargar la página.
- AC-03: Dado que el candidato llena solo las cajas 1 y 3, cuando guarda, entonces se
  guardan dos logros y ambas vistas los muestran como 1 y 2.
- AC-04: Dado un candidato con logros guardados, cuando vacía las tres cajas y guarda,
  entonces los logros se borran y el bloque desaparece de las dos vistas.
- AC-05: Dado un logro de 100 caracteres en una pantalla de 375 px, cuando se pinta,
  entonces en la cabecera ocupa como mucho dos líneas terminando en «…» y en «Acerca de
  ti» se lee completo.
- AC-06: Dado un logro de 100 caracteres, cuando el candidato intenta teclear o pegar más,
  entonces la caja no admite más y el contador lo muestra.
- AC-07: Dada una petición directa al PUT de la cabecera con más de tres logros, o con uno
  de más de 100 caracteres, entonces el backend responde 400 y no cambia nada.
- AC-08: Dado un candidato con logros guardados, cuando llega un PUT de la cabecera **sin**
  el campo `logros` (cliente antiguo), entonces los logros guardados se conservan y los
  otros siete campos se actualizan como siempre.
- AC-09: Dado un candidato con logros, cuando descarga «Mis datos», entonces el archivo
  incluye sus logros.
- AC-10: Dado un candidato con logros, cuando edita cualquier otro campo de «Acerca de ti»
  (por ejemplo, la ubicación) y guarda, entonces los logros no cambian.
- AC-11: Dado un candidato que sube o cambia su CV, cuando termina la lectura, entonces sus
  logros siguen exactamente como estaban (la IA no los toca).

## Casos límite y errores
- Datos vacíos o inválidos: cajas con solo espacios cuentan como vacías. Los logros
  repetidos se guardan tal cual (no se deduplican). Saltos de línea pegados en una caja se
  convierten en espacios.
- Usuario sin permisos: los logros solo los edita su dueño, por el mismo endpoint y
  autenticación del resto de la cabecera.
- Recarga, reintento o doble envío: el botón se desactiva mientras guarda (ya ocurre);
  repetir el mismo PUT deja el mismo resultado.
- Otros casos relevantes: candidato sin `perfil_candidato` todavía (se crea perezosamente
  al primer guardado): guardar solo logros debe crearlo igual que hoy con los otros
  campos. El mensaje de sección vacía de «Acerca de ti» («Cuéntale al equipo…») debe
  tener en cuenta los logros: con logros, la sección ya no está vacía.

## Datos y compatibilidad
- Información que debe guardarse: lista ordenada de 0 a 3 textos de hasta 100 caracteres,
  en una columna nueva de `perfil_candidato`. **No** reutilizar el separador « | » de
  `habilidades`: un logro puede contenerlo. Columna `logros jsonb` con un `CHECK` de array
  de como mucho tres; en el proyecto no hay ninguna `text[]` y las `jsonb` se mapean con
  `@JdbcTypeCode(SqlTypes.JSON)` (`RespuestaPrueba`, `PropuestaPreguntas`). Hibernate valida
  el esquema al arrancar.
- Migración: **V71**. La V70 la usa `specs/la-prueba-al-instante-y-avisos-de-etapa.md`, en
  implementación; Flyway no admite fuera de orden, así que esta se fusiona después de
  aquella. Si aquella se abandona, se renumera.
- Comportamiento para registros existentes: arrancan sin logros (nulo o lista vacía). No se
  rellenan con IA ni a partir del CV.
- Restricciones de compatibilidad: el PUT de la cabecera reemplaza todo, pero la app de
  Android instalada y cualquier pestaña abierta antes del despliegue mandan el PUT sin
  `logros`. Por eso, **`logros` ausente o nulo = no tocar lo guardado; lista vacía =
  borrar**. Es una excepción deliberada a la semántica del PUT y debe quedar comentada en
  el código. `PerfilCompleto` gana el campo `logros` (lista, nunca nula en la respuesta),
  con lo que viaja también por `GET /perfil/descarga` y por el endpoint del panel
  (`GET /api/v1/panel/postulaciones/{id}/perfil`), aunque ninguna pantalla del panel lo pinte
  todavía.
- Trampas al implementar:
  - `PerfilCompleto` se reconstruye en seis sitios: su constructor, `conResenas` y
    `conMisResenas` (`DtosPerfil.java`), y en `PintorDePerfil.java` el perfil vacío (debe dar
    `List.of()`), el pintado normal, `sinPretension` y `sinLoDelCandidato`. Este último es el
    del panel: si no copia `logros`, el panel los pierde sin avisar.
  - Tests que fijan la forma exacta y hay que ampliar: `Perfil.test.tsx` (compara el PUT con
    `toEqual`), `ServicioPerfilPortalImplTest` (tres `new EditarCabecera(`),
    `PintorDePerfilTest` y `ServicioPerfilPanelImplTest`.
  - La lectura del CV no toca los logros, pero guarda la fila entera. Si el candidato guarda
    logros justo cuando termina la lectura, el guardado de la lectura puede pisarlos con el
    valor viejo. Es la misma carrera que ya existe hoy con el titular; AC-11 se prueba sin
    esa coincidencia.

## Verificación
- Actor/rol y datos de prueba necesarios: un candidato con sesión en el portal, con perfil
  y sin logros; otro sin `perfil_candidato` creado.
- Recorridos que deben quedar como E2E repetibles: escribir tres logros y verlos en la
  cabecera y en «Acerca de ti»; dejar uno vacío en medio y comprobar la renumeración;
  vaciar todos y ver que el bloque desaparece de las dos vistas; el tope de 100 caracteres.
  El número de E2E es el siguiente libre tras los de las specs en curso.
- Riesgos que QA debe explorar con navegador: logros de 100 caracteres en móvil (375 px) y
  escritorio, con el corte a dos líneas en la cabecera; convivencia con reseñas y enlaces
  en la cabecera; foco y lectura del `fieldset` con lector de pantalla; PUT sin el campo
  `logros` (por API) que no los borre.
- Resultado que quiero comprobar personalmente: escribo mis tres logros en «Acerca de ti»
  y los veo en la cabecera y en la sección «Acerca de ti», bien colocados, en web y en
  móvil.

## Dependencias y dudas
- Requiere: nada nuevo; es backend de este repositorio y frontend de
  `RenaserOsPostulantes`. Se puede programar en paralelo con
  `specs/la-prueba-al-instante-y-avisos-de-etapa.md` y `specs/reparto-y-avisos-de-los-puntos.md`
  (no tocan el perfil), pero se fusiona después de la primera por la migración.
- Decisiones pendientes: ninguna. Acordado con el usuario el 01/10/2026: nombre «Logros
  clave»; formulario con tres cajas opcionales; aparecen en la cabecera y en «Acerca de
  ti»; tope de 100 caracteres con corte a dos líneas solo en la cabecera; sin IA.
