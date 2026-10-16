# 02 · Edición en modal y archivo de vacantes cerradas

Estado: TERMINADO

Estado previo (referencia histórica): actualizada el 21/09/2026. Implementar después de integrar los PR de la entrega 01 en ambos repositorios. Esta entrega incorpora el ajuste de presentación de la edición ya implementada; no vuelve a implementar sus avisos ni reglas de negocio.

## Objetivo
Editar sin desplazar la tabla de `/admin` y retirar de su lista habitual las vacantes cerradas sin postulantes en carrera, conservando su proceso consultable en Archivadas.

## Base y alcance
Parte de la [entrega 01 integrada](01-editar-vacantes-y-avisos.md). Reutiliza sus campos, validaciones, auditoría, conteos, permisos y definición de «en carrera» (todos salvo `CONTRATADO`, `NO_CONTINUA`, `CERRADA`). Cerrar una vacante no cierra sus postulaciones (RF-14).

Incluye edición en modal, confirmación de archivo, acceso a Archivadas desde la cabecera, detalle de solo lectura y rechazo de mutaciones por API. Fuera: eliminación, liberación de solicitudes, avisos nuevos, cambios en correos, rediseño del alta y scripts de acceso directo a la base.

## Comportamiento esperado
### Editar en un modal
1. El lápiz mantiene los permisos y estados de la entrega 01: solo `BORRADOR` o `PUBLICADA` con `editar_vacante`. Al pulsarlo abre «Editar vacante» en un modal sobre la página, sin insertar el formulario encima de la tabla ni desplazar sus filas. Reutiliza el modal compartido del frontend (`src/ui/Modal.tsx`) y la identidad visual existente; no crees otra infraestructura de diálogos.
2. El modal contiene todos los campos editables del formulario vigente, con sus datos actuales, incluida la remuneración y su motivo cuando corresponda. Solicitud y puesto siguen siendo de solo lectura. Se conservan las restricciones del sueldo y el aviso sobre cuántos postulantes serán notificados; no se añaden campos ni se habilita editar la configuración de la tuerca.
3. Tiene «Guardar cambios», «Cancelar» y un botón de cierre con nombre accesible. Cancelar, el cierre, Escape y pulsar el fondo siguen la misma regla: sin cambios cierra; con cambios pide «Descartar cambios» o «Seguir editando», sin perder datos mientras se decide. Cancelar nunca guarda, audita ni avisa. Solo hay un formulario de alta o edición abierto a la vez.
4. Al guardar correctamente, cierra el modal, actualiza la fila y muestra el resultado definido en 01, incluido «No había cambios que guardar». Ante error conserva el modal y lo escrito y muestra el error correspondiente. Mientras guarda evita doble envío y cierre accidental; al terminar o fallar vuelve a permitir cerrar. Si la vacante dejó de ser editable, el servidor rechaza sin efectos y el panel explica el conflicto y actualiza la lista.
5. El foco permanece dentro del modal y vuelve al lápiz al cerrar, o a un punto lógico de la lista si la fila desapareció. El fondo no se puede operar mientras está abierto. En móvil, el contenido largo tiene desplazamiento interno y siguen accesibles el cierre y las acciones; no se recortan campos ni aparece desplazamiento horizontal.

### Archivar y consultar Archivadas
6. En la zona de acciones de cada fila, junto al espacio destinado a editar, aparece un icono de archivo con nombre accesible «Archivar la vacante {título}» si está `CERRADA` y el usuario tiene `cerrar_vacante`. Se conserva la regla de estados: las cerradas no tienen lápiz; las `BORRADOR` y `PUBLICADA` no tienen archivo. No se cierra una vacante automáticamente al intentar archivarla.
7. Al pulsar Archivar se abre un modal de confirmación que identifica la vacante y explica: dejará la lista habitual, se podrá consultar en Archivadas y se podrá desarchivar sin reabrirla. Tiene «Cancelar», cierre y «Archivar vacante». Abrirlo o cancelarlo no cambia nada.
   - Sin nadie en carrera, confirmar archiva y retira la fila de la lista habitual.
   - Si quedan N personas en carrera, muestra «Quedan N postulantes en carrera. Decide cada uno, o descártalos en lote, desde la vacante antes de archivarla», con enlace a la vacante y sin permitir confirmar. El servidor revalida estado y conteo al confirmar, aunque el modal se abriera antes.
   - Ante fallo, mantiene el modal y explica el error; no retira la fila como si hubiera tenido éxito. Durante el envío evita confirmaciones duplicadas.
8. La cabecera de la lista añade el botón secundario «Archivadas (N)» al lado de «Escribir una solicitud» y «Crear vacante». Sustituye la propuesta anterior del selector «Activas / Archivadas»; no se muestran ambos controles. El contador respeta el alcance del usuario y solo incluye archivadas no eliminadas cuando exista la entrega 03.
9. Al pulsarlo se abre la vista «Vacantes archivadas», identificable en la URL, con «Volver a vacantes». La navegación Atrás y recargar conservan la vista correcta. Muestra fecha de archivo y acceso al detalle; si está vacía dice «No hay vacantes archivadas». Por defecto `/admin` muestra únicamente no archivadas: las archivadas tampoco reaparecen al buscar, filtrar por estado, paginar o recargar esa lista. El filtro y los totales se aplican en el servidor.
10. El detalle archivado es de solo lectura y muestra «Archivada el {fecha}». Conserva la información de la vacante y el proceso: postulantes, etapas, ranking, fichas, resultados, historial y descargas ya disponibles según los permisos del usuario. Archivar no borra registros ni evidencias ni expone información a otros roles. No se puede editar, modificar sueldo, publicar, reconfigurar, mover o reabrir postulaciones. «Desarchivar» es una acción aparte disponible únicamente con permiso; la entrega 03 añadirá la eliminación excepcional.
11. Desarchivar devuelve la vacante a la lista habitual como `CERRADA`, sin reabrirla ni alterar postulaciones. El candidato conserva su proceso en el portal tal como estaba. Archivar no libera la solicitud ni genera avisos.
12. El backend exige `cerrar_vacante` para archivar/desarchivar (403 sin permiso, 404 fuera del alcance). Audita persona y fecha. Archivar una ya archivada o desarchivar una no archivada se rechaza sin efectos duplicados. También rechaza la edición o el cambio de sueldo iniciado antes de que otro usuario la archive.

## Criterios de aceptación
- AC-11: Una `CERRADA` sin nadie en carrera se archiva solo al confirmar su modal; desaparece de la lista habitual, búsquedas y paginación, aparece en Archivadas con fecha y conserva su detalle en modo lectura. Cancelar no cambia ni audita nada.
- AC-12: Una `CERRADA` con 1 postulación en carrera no se archiva: el modal muestra el conteo y enlace; tampoco permite saltarse la regla por API ni si el conteo cambió después de abrirlo.
- AC-13: Desarchivar devuelve la fila a la lista habitual como `CERRADA`, sin cambios en postulaciones ni portal.
- AC-14: La API rechaza sin efectos editar, cambiar remuneración, publicar, reconfigurar o mover/reabrir postulaciones de una archivada; incluye formularios abiertos antes del archivo. Ranking, fichas, historial y descargas existentes siguen consultables según permisos.
- AC-19: Sin `cerrar_vacante` no aparecen archivar/desarchivar y sus APIs responden 403; fuera del alcance del rol responden 404. Consultar Archivadas usa el permiso de lectura existente, sin exigir permiso de archivo.
- AC-22: El lápiz abre un modal con todos los campos actuales y botones para guardar, cancelar y cerrar. La tabla no se desplaza; solicitud/puesto siguen fijos y se conservan las validaciones de 01.
- AC-23: Cerrar sin cambios no escribe nada. Con cambios, descartarlos cierra sin guardarlos y seguir editando los conserva. Un guardado inválido conserva formulario y valores; uno correcto cierra y actualiza la fila. Los avisos, auditoría y el guardado sin cambios mantienen el comportamiento de 01.
- AC-24: «Archivadas (N)» está junto a las acciones de cabecera y abre una vista separada de la lista habitual, con retorno, estado vacío, conteo autorizado y navegación/recarga correctas; no hay un selector redundante.
- AC-25: En escritorio y móvil los modales se operan con teclado, tienen título y nombres accesibles, mantienen/restauran el foco y permiten llegar al cierre y a las acciones con contenido largo. Cancelar confirmaciones de archivo no ejecuta la operación.

## Datos y compatibilidad
- Añade únicamente fecha de archivo; conserva los estados existentes y no introduzcas eliminación en esta entrega. Las vacantes existentes quedan sin archivar y se conservan postulaciones, evidencias y avisos.
- Agrega fecha, booleanos `puedeX` y consulta de archivadas al contrato de lista siguiendo el patrón existente, sin endpoint nuevo de permisos. Filtrado, paginación y conteos respetan archivo y alcance de usuario.
- Archivar/desarchivar tiene verbos propios. Protege las mutaciones y los procesos automáticos pertinentes para que una archivada siga en lectura; no basta con ocultar acciones.
- El cambio a modal reutiliza el guardado de 01, sin nueva API ni migración solo por presentación. Conserva las regresiones ya añadidas sobre campos omitidos y fecha de cierre con hora.
- Antes de numerar una migración Flyway, consulta la base integrada y ramas abiertas. Usa el siguiente número libre sin editar migraciones anteriores. Debe funcionar desde el snapshot autorizado y desde una base con 01 aplicada.

## Verificación específica
Datos: usuario con lectura/edición/archivo; rol con lectura sin archivo; candidato con proceso terminado; borrador y publicada editables; cerrada sin nadie en carrera y cerrada con 1 en carrera. Prepara los estados mediante fixtures del clon, sin depender de cerrar manualmente una vacante en el navegador.

E2E repetibles, concentrados en el cambio:
1. Abrir edición, cancelar con/sin cambios y guardar desde el modal; comprobar tabla estable, valores conservados ante error y un aviso al modificar un campo visible. Adaptar los tests existentes al modal sin duplicar toda la cobertura de avisos de 01.
2. Abrir/cancelar/confirmar archivo; buscar la vacante en ambas vistas, consultar su proceso en lectura y desarchivarla. Verificar que vuelve `CERRADA` y no cambia el portal.
3. Bloquear archivo con alguien en carrera; permisos UI/API, alcance, contador, recarga y ausencia de archivadas en la lista habitual.

QA explora con Chrome visible teclado, foco, cierre y contenido largo de los modales, móvil, navegación a Archivadas y trazabilidad. Integración cubre las entradas de mutación, revalidación concurrente de estado/conteo y edición abierta antes del archivo. Reutiliza los resultados vigentes y agrupa pruebas nuevas y correcciones conocidas antes de pedir una nueva verificación; no repitas suites completas por cada ajuste visual.

Revisión humana: editar desde el modal, archivar una cerrada de prueba, verla en Archivadas en lectura y devolverla a la lista sin reabrirla.

## Documentación y decisiones
Después de la aprobación, actualiza solo secciones afectadas de RF-14, requisitos, roles/permisos y APIs, además de referencias pertinentes de CLAUDE.md. Documenta el modal sin reescribir la documentación del sueldo.

Confirmado el 19/09/2026: solo archivar cerradas sin nadie en carrera, archivo reversible y permiso `cerrar_vacante`. Solicitado el 21/09/2026: edición en modal, icono y confirmación de archivo, botón de Archivadas en cabecera, trazabilidad en lectura y exclusión de archivadas de la lista habitual. No se amplían estados editables ni archivables por cambiar la ubicación de los iconos.

Integrar los PR de esta entrega antes de iniciar la [entrega 03](03-eliminar-vacantes.md).

## Ejecución y pruebas
- Una spec por conversación de Claude Code. Backend y frontend (`RenaserOsPostulantes`) se implementan en sus worktrees del mismo trabajo; nunca en los checkouts originales.
- Usa el clon temporal y los helpers E2E existentes, parametrizados con los destinos del trabajo. Prepara datos sintéticos reproducibles en ese clon; no accedas a la base principal ni copies credenciales personales.
- QA debe entrar tanto al panel como al portal con cuentas de prueba. La revisión humana posterior no sustituye la exploración visual de QA. Si falta acceso, informa el bloqueo concreto y pide solo lo necesario.
- Pruebas repetibles con el runner Playwright CLI/tests del proyecto; exploración adicional con Playwright MCP y Chrome visible. Verifica permisos por API además de ocultar botones. Usa integración para transacciones, errores y procesos automáticos que el navegador no puede acreditar.
- Reutiliza comprobaciones vigentes del harness y añade regresiones del alcance; no repitas manualmente suites completas ya validadas ni habilites la suite API E2E opcional vacía. Las comprobaciones de API pueden vivir en integración existente o en los tests del runner habilitado, según corresponda.
- Al pedir aprobación, conserva localhost y datos del clon para la revisión del usuario. Documentación, PR, CI y limpieza siguen el flujo del harness; no crees documentos por agente.

## Referencias
- [Índice de las tres entregas](editar-archivar-eliminar-vacantes.md).
- [Controlador de vacantes](../../src/main/java/com/renaser/ai/ai_engine/vacante/controller/VacantesPanelController.java) y [servicio de vacantes](../../src/main/java/com/renaser/ai/ai_engine/vacante/service/impl/ServicioVacantesPanelImpl.java).
- [Requisitos funcionales](../../docs/01-REQUISITOS-FUNCIONALES.md), [roles y permisos](../../docs/04-ROLES-Y-PERMISOS.md) y [APIs](../../docs/09-APIS.md).
