# Reporte de cambios — 1 de septiembre de 2026

## Alcance

Este reporte resume los cambios integrados hoy en:

- **Backend:** `RENASER-RECLUTAMIENTO` — rama `main`.
- **Frontend:** `RenaserOsPostulantes` — rama `main`, ubicado en `../RenaserOsPostulantes`.

Seis pull requests entraron a `main` entre las 12:36 y las 17:24: cuatro en el backend y dos en el frontend. Todos con *squash merge*, así que cada PR es un único commit en `main`.

## Resumen ejecutivo

El día se explica mejor por **dos funcionalidades completas entregadas de punta a punta** que por repositorios:

1. **Componer una prueba del puesto desde el panel** (backend #56 + frontend #26). Hasta hoy las pruebas solo existían si alguien las cargaba por API con scripts: no había forma de crear, corregir ni borrar nada desde una pantalla. Se añadieron doce rutas de edición y borrado en el backend, la pantalla `/admin/pruebas` en el frontend, la administración de áreas en Configuración, y la guía de calificación que cada prueba le da a la IA. Además, `minutosEtapaTecnica` dejó de ser un campo que no leía nadie: ahora el tiempo de la etapa técnica lo manda la vacante y alcanza a los dos instrumentos.

2. **La ciudad del candidato y el ranking utilizable** (backend #54 + frontend #25). Se incorporó el catálogo `ubigeo` (25 departamentos, 196 provincias y «fuera del Perú»), la ciudad pasó a pedirse en el alta de la cuenta, y la mesa donde se decide a quién llamar ganó orden, filtros y descarga a Excel. La descarga sale en el orden exacto de la pantalla.

A eso se suman dos arreglos de backend —el sembrador de datos de prueba (#55) y la reentrada a la etapa técnica que atascaba al candidato (#57)— y una consolidación de infraestructura en el frontend: los doce arneses e2e sueltos se migraron al corredor de Playwright y quedaron en una sola suite.

**Un dato transversal del día:** los PR no entraron en orden numérico. El #56 llegó a `main` antes que el #54, y ambos traían su propia migración `V46`; dos migraciones con el mismo número tumban el arranque de Flyway. El #54 tuvo que renumerar la suya a `V47`, y lo detectó `MigracionesSinChoqueTest` en el CI, comparando contra la mezcla con `main`.

### Orden real de integración

| Hora | Repo | PR | Título |
|---|---|---|---|
| 12:36 | Backend | #56 | El tiempo de la etapa técnica lo manda la vacante, y una prueba se compone desde el panel |
| 12:47 | Frontend | #26 | Una prueba del puesto se escribe desde el panel, y el plazo se dice entero |
| 15:15 | Backend | #55 | El sembrador de datos vuelve a funcionar contra el backend de hoy |
| 15:15 | Frontend | #25 | El ranking se ordena y se filtra, la ciudad se pide al crear la cuenta, y nace una suite e2e |
| 15:26 | Backend | #54 | La ciudad entra al ranking, que ahora se ordena, se filtra y se lleva a Excel |
| 17:24 | Backend | #57 | Volver a entrar en la etapa técnica deja de atascar al candidato |

---

## Backend — `RENASER-RECLUTAMIENTO`

### `e2235bb` — 12:36 — PR #56

**El tiempo de la etapa técnica lo manda la vacante, y una prueba se compone desde el panel**

Es el commit más grande del día y agrupa cuatro frentes:

**Administración de áreas.** Antes solo se podían listar y crear. Ahora se renombran, se retiran, se reactivan y se borran; `es_activa` por fin tiene quien la escriba. El borrado dice el precio antes de cobrarlo —cuántas solicitudes y personas cuelgan del área— y exige un destino al que mudarlas; sin destino solo procede si el área está vacía, y si no devuelve un 409 con los recuentos en vez del error crudo de la clave ajena. Se cerraron dos agujeros que encontró QA: se podía dar de alta a alguien o registrar una solicitud apuntando a un área de **otra** empresa, y esa fila quedaba invisible para todo lo que consulta por organización. La última área activa tampoco se puede retirar.

**El tiempo de la etapa técnica.** `minutosEtapaTecnica` era un no-op silencioso: se pintaba, se guardaba y se auditaba, pero su único lector estaba dentro de la rama del cuestionario técnico. Ahora los minutos se resuelven **al abrir** el examen —no al crearlo— para que corregirlos alcance a quien todavía no haya empezado. Entre la fecha de cierre ya fijada y el cronómetro gana la que caiga antes. Se retiró el rango de 60 a 120 minutos al publicar: manda el tiempo que ponga la empresa, con un piso de cinco minutos. La guarda para cambiar instrumento o minutos pasó de «hay alguna postulación» a «alguien ya empezó».

**Composición de la prueba.** En todo el paquete no había un solo `DELETE` ni `PATCH`: se podía añadir una pregunta, un entregable o un criterio, y nada más. Con la rúbrica obligada a sumar exactamente 100, un criterio de 40 puesto por error dejaba esa versión imposible de publicar para siempre. Se añadieron **doce rutas de edición y borrado** sobre versiones en BORRADOR, el listado por plantilla —que faltaba, y por eso el panel tanteaba ids por fuerza bruta— y la subida del enunciado como archivo, que hasta ahora solo se podía poner por SQL. El campo `orden` pasó a derivarse de `max+1` en lugar de `size+1`, porque con borrado lo segundo colisiona con el UNIQUE. Despublicar **no** se permite. Y se cerró una trampa: la tabla `criterio` la comparten las tres etapas, así que quitar uno sin mirar de quién es borraba criterios globales de toda la plataforma.

**Guía de calificación para la IA (V46).** Cada prueba puede decirle a la IA cómo mirarla, sin poder darle órdenes: orienta, no sustituye, y la nota sigue saliendo criterio a criterio con la rúbrica sumando 100. La primera versión de la defensa no servía y QA lo demostró por dos flancos; se cambió de estrategia —enumerar caracteres peligrosos es una carrera que se pierde— y el rótulo que cierra la guía lleva ahora una marca sorteada en cada calificación, que quien escribió el texto no puede reproducir. Los rótulos de la bitácora llevan la misma marca, para los seis agentes.

**Documentación.** Se corrigieron cuatro afirmaciones falsas, tres de ellas anteriores a esta rama: el rango de 60–120 minutos del RF-76 aparecía en **seis sitios**; las once plantillas del RF-78 se daban por sembradas cuando **no hay un solo INSERT** en ninguna migración (queda registrado como incumplido, no como retirado); el diccionario prometía un CHECK que nunca existió; y seis ficheros tenían conteos de tests desfasados.

**Cambio registrado:** 61 archivos, `+6212 / -166` líneas.

#### Registro nuevo: `docs/DEFECTOS-CONOCIDOS.md`

Este PR abrió un registro de lo que sigue roto y a quién le duele. Cuatro entradas vivas:

- **Fuga de preguntas entre empresas** — el catálogo se lee sin filtrar, y las preguntas específicas **son** el texto del examen.
- **El enlace del enunciado caduca** (180 días), y una empresa que copie una prueba no podrá volver a subirlo.
- **El cambio inesperado** puede no llegar a aparecer.
- **La subida de archivos** solo se ha probado contra el almacén en memoria.

### `bb2ed4b` — 15:15 — PR #55

**El sembrador de datos vuelve a funcionar contra el backend de hoy**

`scripts/sembrar-datos-de-prueba.py` llevaba roto desde el PR #53 y fallaba en cuatro puntos seguidos; sin él no se puede levantar un entorno local completo. Se corrigió que la solicitud exige `puestoId`, se quitaron `nivelPuestoCodigo` y `familiaCodigo` —el puesto ya los define, y mandarlos aparte hacía que el backend los comparase contra los suyos y rechazara la solicitud— y se añadió `aceptaTratamiento` al postular, que es el consentimiento de la empresa de esa vacante y no el de la cuenta. Además, las cuentas ya mandan `ciudadUbigeo` repartida en varias provincias, para que el filtro por ciudad del ranking sea probable. Verificado corriéndolo entero contra dos backends y bases limpias.

**Cambio registrado:** 1 archivo, `+26 / -14` líneas.

### `dc9323a` — 15:26 — PR #54

**La ciudad entra al ranking, que ahora se ordena, se filtra y se lleva a Excel**

**El catálogo `ubigeo`.** Es un árbol —código, nivel, padre— y no dos columnas de texto. Siembra los 25 departamentos y las 196 provincias; el día que haga falta el distrito bastará con insertar sus filas, porque `persona.ciudad_ubigeo` ya es `varchar(6)`. «Fuera del Perú» es una fila más del catálogo y no un booleano aparte. La ciudad vive en `persona` y no en `perfil_candidato` porque el perfil se crea perezosamente y en el alta la única fila que existe es la de la persona. `perfil_candidato.ubicacion` **no se borra**: deja de leerse y de escribirse, y la migración la lee una vez para rellenar lo que pueda, en dos pasadas —probado contra siete textos reales, acierta seis—. `UbigeoSemillaTest` vigila la invariante del INEI y se comprobó en negativo.

**El ranking.** `FilaRanking` gana ciudad y pretensión sin consultas nuevas dentro del bucle. La pretensión sigue bajo el permiso `ver_pretension`, que solo tiene Dirección; por eso viaja la señal `puedeVerPretension`, porque una columna vacía tiene dos lecturas opuestas —nadie lo declaró, o tu rol no puede verlo—.

**El Excel.** `POST /vacantes/{id}/ranking/excel` recibe la lista de postulaciones **ya ordenada** y escribe esas filas en ese orden; el endpoint no sabe qué es un filtro, toda esa lógica vive en el panel. Una nota que falta dice «rúbrica incompleta» y nunca se queda en blanco ni pone un cero.

**Correcciones que salieron al escribir la documentación.** El anonimizado se dejaba `ciudad_ubigeo` sin vaciar —la provincia sobrevivía, y con el resto del expediente se vuelve a señalar a una persona concreta—; quedó arreglado y con `AnonimizarNoOlvidaCamposTest` contrastando los campos de `Persona` contra las líneas del servicio de borrado, comprobado en negativo. Y `cargar-convocatoria.py` volvió a funcionar: manda `EXT` en vez de inventar una provincia —un dato ausente se ve, uno inventado no— y dejó de tragarse el error de crear la cuenta, que hacía que el fallo reventara después en el login diciendo algo que no tenía que ver.

**Renumeración.** La migración cedió el número y pasó de `V46` a `V47` por el choque con el PR #56, que llegó antes. `UbigeoSemillaTest` dejó de fijar el nombre del archivo y busca la migración por su nombre: el número depende de quién mergee primero, el nombre no.

También se documentó todo en `01-REQUISITOS-FUNCIONALES`, `05-MODELO-DE-DATOS`, `07-DICCIONARIO-DE-DATOS` y `09-APIS`.

**Cambio registrado:** 41 archivos, `+2839 / -41` líneas.

### `8b51521` — 17:24 — PR #57

**Volver a entrar en la etapa técnica deja de atascar al candidato**

Un candidato quedó sin poder pasar a su prueba del puesto: el panel respondía «ya existe un registro con postulacion_id 735» y no había salida desde la pantalla. Detrás había dos causas:

- **Confirmar el avance creaba el intento sin mirar si ya existía uno.** Volver a entrar en la etapa —lo que pasa al retroceder una postulación y volver a avanzarla— chocaba contra la clave única de `intento_prueba`. Ahora se reutiliza el intento existente: a quien no ha abierto su prueba se le pone la versión que la vacante rinde hoy y su fecha de cierre, salvo plazo propio concedido; a quien ya la abrió no se le toca nada, porque su versión quedó fijada al empezar (RF-90).
- **El agente que cierra el Perfil Integral movía la postulación sin mirar dónde estaba.** Entre que empieza a calificar y termina pasan minutos, y en ese rato una persona puede haberla avanzado desde el panel; el agente la devolvía atrás y dejaba el intento creado pero fuera de etapa. Ahora solo la mueve si sigue en el Perfil Integral, y lo calificado se guarda igual: el trabajo de la IA no se tira por llegar tarde.

**De paso:** la regla de arquitectura que persigue los `findById` sin organización no fallaba, **reventaba** —una lambda sin nombre de método la tumbaba con un `StringIndexOutOfBounds`, y con ella caía la suite entera—. Estaba así en `main`. Al arreglarla aparecieron tres llamadas que nunca se habían mirado; las tres derivan de un padre ya validado y quedaron firmadas con su motivo, por el repositorio al que llaman y no por el número de la lambda.

Según el mensaje del PR, la suite queda en **999 tests, 0 fallos**.

**Cambio registrado:** 8 archivos, `+433 / -8` líneas.

---

## Frontend — `RenaserOsPostulantes`

### `ce70762` — 12:47 — PR #26

**Una prueba del puesto se escribe desde el panel, y el plazo se dice entero**

**Áreas en Configuración.** El panel solo sabía consumirlas para llenar desplegables. Ahora hay sección propia con crear, renombrar, retirar, reactivar y borrar. Ojo con un detalle que rompe la pantalla en silencio: `/areas` trae solo las activas —es la que llena los desplegables— y `/areas/todas` incluye las retiradas; esta pantalla lee la segunda, porque con la primera un área recién desactivada desaparecería sin forma de volver a encenderla. La consulta de impacto antes de borrar **no se cachea**: el panel da por buena cualquier respuesta durante 30 segundos, y con recuentos viejos en cero la pantalla escribía «no cuelga nada de esta área» y mandaba el borrado sin destino. Además, la pestaña Equipo leía solo las áreas activas, así que quien estuviera en un área retirada caía en el guion que significa «no tiene área».

**El plazo se dice entero.** El lateral de la prueba era un `if/else`: con cronómetro **y** fecha de cierre a la vez escribía «90 minutos desde que empieces» y callaba la fecha. Quien abriera a las 17:40 con cierre a las 18:00 leía noventa minutos y tenía veinte. Ahora se dicen los dos y cuál acorta a cuál. En la evaluación, los días restantes solo se veían en la portada; como se responde en varias sesiones a lo largo de dos semanas, ese silencio duraba días.

**Componer la prueba.** La pantalla `/admin/pruebas` compone la prueba entera —datos y tiempos, el enunciado como archivo, la guía de calificación para la IA, preguntas, entregables y rúbrica— con el balance en vivo diciendo si falta algo para publicar, en vez de decirlo solo al fallar. Dos avisos van pegados a su acción, porque son las dos confusiones naturales: el archivo es el **enunciado** y no crea la prueba, y la guía **orienta** a la IA pero no sustituye a la rúbrica. El desplegable «Qué prueba del puesto rendirá» dejó de tantear ids por fuerza bruta —ocho por tanda, dejando 404 en la consola— y esa función pasó de cuarenta líneas a una.

**Lo que queda dicho como no comprobado:** en local los archivos viven en memoria y el enlace es `memoria://`, así que está comprobado que el enunciado **se guarda**, no que se pueda abrir.

**Cambio registrado:** 34 archivos, `+7039 / -111` líneas.

### `86eb949` — 15:15 — PR #25

**El ranking se ordena y se filtra, la ciudad se pide al crear la cuenta, y nace una suite e2e**

**Ordenar.** Por nombre, ciudad, nota y pretensión, con tres estados por cabecera: el sentido natural de cada columna —la nota abre por la mayor—, el inverso, y el orden del backend otra vez. Ese tercer estado importa: el orden de origen es la opinión del producto sobre la tanda, y sin forma de volver a él habría que recargar la página. El orden del cliente es **plano**: manda la columna pulsada y nada más. Se probó a ordenar la nota *dentro* de cada grupo de prioridad y se descartó en la misma rama, porque una mesa que se lee 55, 74, 61, 95 parece rota y un orden que hay que explicar no está ordenando; además el beneficio resultó casi nulo, ya que los grupos que la IA escribe cuelgan de la propia nota, e INCOMPATIBLE —el único que podía contradecirla— no lo escribe nadie. El grupo se sigue pintando en cada fila como contexto. Los vacíos van al final suba o baje el orden.

**Filtrar.** Buscador por nombre sin tildes en los dos sentidos («fatima» encuentra a Fátima), multi-selección de ciudad con su recuento, y rangos de nota y de pretensión. El pliegue lleva contador de filtros puestos: sin él, una tabla filtrada se ve igual que una entera y alguien decide sobre media tanda creyendo que la ve completa. Hay un tercer estado vacío, porque el anterior decía «nadie tiene nota» cuando lo que pasaba era que los filtros no dejaban a nadie. `columnasDelRanking` pasa a ser la única fuente del `colSpan`, que estaba escrito a mano con un 8/6 sobre nueve y siete columnas reales.

**Excel y ciudad.** El botón manda las postulaciones en el orden exacto de la pantalla, así que «lo que ves» es una garantía y no una coincidencia; `puerta.ts` ganó lectura de blobs porque el token es Bearer desde localStorage y un enlace directo iría sin credenciales. En el registro, la ciudad es un `<select>` nativo con `<optgroup>` por departamento —teclado y lector de pantalla gratis—; un `<datalist>` habría dejado escribir texto libre y roto la clave ajena.

**Navegación móvil.** A 375 px la barra medía 379 y el desborde salía del `<body>`: la página entera se movía en horizontal. Lo cazó la suite nueva. El desbordo queda ahora dentro de la barra, con scroll propio.

**La suite e2e.** El proyecto declaraba `playwright` en `package.json` sin un solo test ni configuración: la dependencia estaba muerta. Nació una suite y, en el mismo PR, se migraron los doce arneses sueltos que vivían en `herramientas/` —scripts `node` a pelo, con `throw` a mano y `process.exit`— al corredor `@playwright/test`. Estado final: **22 specs (00–21) en `herramientas/e2e/`, 157 pruebas en verde** según el mensaje del PR, comprobado con tres pasadas seguidas sobre la misma base sin resembrar. Se cambiaron 4.601 líneas viejas por 4.436 nuevas. `e2e-android.mjs` se queda como script, porque conduce Maestro contra un APK y no un navegador.

**Qué encontró la suite:** dos defectos de coherencia entre la mesa y la hoja de cálculo —el «#» significaba una cosa en pantalla y otra en el archivo, y la pretensión se escribía `S/ 4,000 – 5,200` en una y `PEN 4000 – 5200` en la otra, siendo la hoja la que sale por correo—, ya arreglados en el backend. Y un tercero que no se vio mirando sino probando la prueba: al deshacer el arreglo para comprobar que el test fallaba, siguió pasando, porque un JVM viejo seguía sirviendo la versión corregida; al matarlo apareció que la hoja de la prueba del puesto tenía el mismo defecto intacto.

También salió que `tsc --noEmit` no revisaba los specs —el `tsconfig` solo incluía `src`— y Playwright transpila sin comprobar tipos: había quince errores de tipo invisibles. Ahora hay `tsconfig.e2e.json` y `npm run typecheck:e2e`.

**Documentación.** `PRODUCT.md` ponía el grupo de prioridad en «lo que nunca puede llegar a la pantalla» sin decir a qué pantalla, y leído literal prohibía lo que el panel del equipo tiene que hacer. Las fuentes originales sí lo acotan al candidato, así que la prohibición quedó acotada en los cinco sitios donde se repetía. Al candidato sigue terminantemente prohibido.

**Cambio registrado:** 69 archivos, `+9520 / -4709` líneas.

---

## Lo que queda pendiente o sin comprobar

Recogido de los propios mensajes de los PR:

- Los cuatro defectos vivos del nuevo `docs/DEFECTOS-CONOCIDOS.md` (ver arriba).
- Las **once plantillas del RF-78 no están sembradas**: no hay un solo INSERT en ninguna migración. Registrado como incumplido.
- La rama «tu rol no puede ver la pretensión» no está cubierta de punta a punta: `dev-equipo` tiene todos los permisos, hace falta un usuario sin `ver_pretension`. Tiene pruebas unitarias.
- `09-avance.spec.ts` escribe y no revierte, y `12-postular` deja una postulación «e2e.postular.*» en cada corrida: `transicion_estado` y `auditoria` son inmutables por *trigger*, y limpiarlas exigiría apagarlos, que es decisión de quien administra la base.
- A la siembra le faltan datos para que corran los 17 saltos de la suite: un nivel del banco con dos versiones publicadas, una prueba entregada, una rúbrica entera sin nota de etapa, una sesión de simulación con inscritos, y una persona con nota en prueba, simulación y validación a la vez.
- La subida del enunciado solo se ha probado contra el almacén en memoria.

## Estado del árbol de trabajo

Ambos repositorios tienen cambios locales **sin commitear** al momento de este reporte:

- **Backend:** modificaciones en `CLAUDE.MD`, `.claude/launch.json`, `skills-lock.json` y tres scripts (`calificar-pruebas.py`, `excel-de-la-prueba.py`, `invitar.py`), más una docena de scripts nuevos sin seguimiento (carga de pruebas, correos, invitaciones), archivos de datos operativos (`gente.csv`, `invitaciones-enviadas.csv`, `curriculums-vacante-13.xlsx`) y cuatro reportes previos en `docs/` que tampoco están commiteados.
- **Frontend:** solo `.claude/launch.json` modificado y el directorio `graphify-out/`.

Nada de esto forma parte de los seis PR descritos arriba.
