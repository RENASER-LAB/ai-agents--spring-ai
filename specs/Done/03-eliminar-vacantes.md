# 03 · Eliminar vacantes mediante borrado lógico

Estado: TERMINADO

Estado previo (referencia histórica): lista para implementar después de integrar la entrega 02 en ambos repositorios.

## Objetivo
Retirar una vacante que no debió existir conservando sus datos, cerrar las postulaciones en carrera y avisar por campana, y dejar su solicitud disponible para crear una vacante correcta.

## Base y alcance
Parte de [editar y avisar](01-editar-vacantes-y-avisos.md) y [archivar/desarchivar](02-archivar-desarchivar-vacantes.md) integrados en ambos repositorios. Reutiliza formularios, permisos, conteos, avisos y la definición de «en carrera»: estados distintos de `CONTRATADO`, `NO_CONTINUA` y `CERRADA`.

Incluye borrado lógico de vacantes activas o archivadas, cierre de postulaciones, liberación de solicitud, permiso nuevo y exclusión de todas las consultas y procesos del alcance. No permite restaurar desde el panel; solo soporte directamente en la base. Quedan fuera scripts de `scripts/` que consultan la base directamente, avisos de otros hechos y cambios en los correos habituales de la máquina de estados. No rehagas las entregas anteriores.

## Archivo frente a eliminación
Una archivada conserva su detalle y el proceso en modo lectura desde Archivadas. Eliminar es excepcional: conserva registros por borrado lógico, pero retira su consulta normal tanto de la lista habitual como de Archivadas. Para mantener un proceso consultable, se archiva y no se elimina.

Confirmado por el usuario el 21/09/2026: eliminar no exige archivar antes. Se conserva la eliminación de borradores, publicadas, cerradas y archivadas según permiso, con motivo y confirmación.

## Comportamiento esperado
### Eliminar
1. Si el rol tiene `eliminar_vacante`, cada fila tiene una papelera con nombre accesible «Eliminar la vacante {título}», sea cual sea su estado. Va en la misma columna de acciones que editar y archivar, sin añadir otra columna ni un botón de eliminación global en la cabecera:
   - En `BORRADOR` o `PUBLICADA`: lápiz y papelera, según permisos.
   - En `CERRADA` no archivada: archivo y papelera; no hay lápiz.
   - En Archivadas: consulta de solo lectura, desarchivar y papelera, según permisos.
   - El orden de las acciones disponibles es editar, archivar/desarchivar y eliminar al final. Eliminar conserva su carácter destructivo y no se dispara al pulsar el icono: primero se confirma en el modal.
2. Al pulsarlo se abre un modal «Eliminar vacante» que identifica el título de la vacante y explica las consecuencias:
   - La vacante dejará de verse en el panel y en el portal (tablón, detalle público y «Mis procesos»), y no se podrá deshacer desde el panel.
   - Si hay N postulantes en carrera: «Se cerrarán sus N postulaciones y se les avisará en su portal».
   - Pide un motivo escrito obligatorio. El botón destructivo «Eliminar vacante» se activa solo cuando hay un motivo que no sea solo espacios.
   - Ofrece «Cancelar» y un cierre accesible. Cancelar, Escape o cerrar sin confirmar no elimina, no cierra postulaciones, no libera la solicitud ni registra la eliminación. La acción nunca se ejecuta al abrir el modal.
   - Reutiliza el modal compartido, con foco contenido/restaurado y contenido accesible en móvil. Mientras envía, evita confirmaciones duplicadas y cierre accidental; ante error conserva el modal y el motivo para reintentar.
3. Al confirmar, el sistema:
   - Pasa a `CERRADA` cada postulación en carrera, con el motivo de cierre nuevo «vacante eliminada», como transición hecha por una persona y no por el sistema, y sin enviar correo.
   - Publica en la campana de cada una un aviso `VACANTE_ELIMINADA`: «Se retiró la vacante «{título}»» / «La empresa retiró esta vacante y tu postulación quedó cerrada. No tienes que hacer nada». Este aviso no enlaza a su proceso.
   - Marca la vacante como eliminada, con fecha, persona y motivo en la auditoría.
   - Devuelve la solicitud que la respaldaba a `ABIERTA`, para que pueda respaldar la vacante correcta.

   El cierre de las postulaciones, la marca de eliminada y la liberación de la solicitud se aplican en una sola transacción. Si una parte falla, no se aplica ninguna. Un aviso que falla no deshace la eliminación.
4. La fila desaparece y el panel confirma «Vacante eliminada. Se cerraron N postulaciones y se les avisó en su portal» si los N avisos se publicaron, o «Vacante eliminada» si no había nadie en carrera. Si falla algún aviso, informa por separado N postulaciones cerradas y M avisos realmente publicados, sin afirmar que todos fueron avisados. Conserva el fallo para diagnóstico y no revierte la eliminación.
5. Desde ese momento, la vacante eliminada:
   - Devuelve 404 al consultarla por id en el panel. En el portal, su detalle público y sus procesos dicen «Esta vacante ya no está disponible».
   - Rechaza nuevas postulaciones.
   - No aparece en la lista habitual de `/admin` ni en Archivadas (incluido su contador), el tablón, «Mis procesos», los rankings, las exportaciones, la selección de vacantes de las sesiones de simulación, el Radar ni los conteos del panel.
   - No es procesada por ningún proceso automático: vencimientos, pase automático ni calificación.


6. Las postulaciones ya terminadas no cambian ni reciben aviso, pero sus procesos dejan de verse en el portal junto con la vacante. Los avisos antiguos que apuntaban a ella permanecen sin enlace; no borres historial ni correos enviados.
7. Cerrar por eliminación libera lo mismo que un cierre manual, incluida una sesión de simulación reservada. Se registra la persona que decidió y el motivo «vacante eliminada», sin añadir la coletilla automática «sin avisar al candidato» solo por haber silenciado el correo; el resultado real de los avisos queda distinguido. No cambies textos de consentimiento que enumeran transiciones humanas.
8. Repetir la eliminación de una vacante eliminada responde 404 y no vuelve a cerrar, auditar la eliminación ni avisar. Desactiva el botón mientras se envía, pero valida también en backend. Una vacante eliminada no impide reutilizar su título ni que su solicitud respalde otra.
9. Extiende los guardas de las entregas anteriores: un formulario de edición abierto antes de la eliminación no puede guardar; tampoco puede modificarse la remuneración, publicarse, reconfigurarse, archivarse o desarchivarse una eliminada. No basta con ocultarla en la lista.

## Criterios de aceptación
- AC-15: Dada una vacante publicada con 2 postulaciones en carrera y 1 en `CONTRATADO`, cuando se elimina con motivo, entonces las 2 pasan a `CERRADA` con motivo «vacante eliminada», reciben un aviso `VACANTE_ELIMINADA` sin enlace y ningún correo; la de `CONTRATADO` no cambia ni recibe aviso; la solicitud vuelve a `ABIERTA`, y queda registrada la auditoría con el motivo.
- AC-16: Dada una vacante eliminada, cuando se busca en `/admin`, el tablón, el detalle público, «Mis procesos» de cualquiera de sus postulantes, el ranking o la exportación, entonces no aparece; su URL de panel responde 404 y la del portal dice «Esta vacante ya no está disponible».
- AC-17: Dada una vacante eliminada, cuando alguien intenta postular a ella, por ejemplo con el formulario abierto desde antes, entonces se rechaza sin crear la postulación.
- AC-18: Dado el modal de eliminar, muestra título y consecuencias, exige motivo no vacío y ofrece cancelar/cerrar. Con motivo vacío o solo espacios, el botón no se activa y la API responde 400. Abrir, cancelar o cerrar no ejecuta ningún efecto; ante error se conserva el motivo.
- AC-19: Dado un usuario sin `eliminar_vacante`, cuando abre `/admin`, entonces no ve la papelera y la API de eliminación responde 403; fuera del alcance de su rol responde 404.
- AC-20: Dada una vacante ya eliminada, cuando se repite la eliminación, entonces responde 404 sin volver a cerrar ni avisar a nadie.
- AC-21: Dada una solicitud liberada al eliminar su vacante, cuando se abre «Crear vacante», entonces aparece entre las solicitudes aprobadas disponibles y permite crear una vacante nueva.

## Datos, permisos y compatibilidad
- Añade una fecha de eliminación, conservando la fecha de archivo de la entrega 02 y el CHECK de estados existente. Persona y motivo obligatorio quedan en auditoría `eliminar_vacante`; no borres filas.
- Añade `VACANTE_ELIMINADA` a `aviso_portal`, conservando tipos y avisos anteriores. Su texto no enlaza al proceso; conserva también los avisos anteriores a la eliminación sin enlaces a recursos retirados.
- Añade el motivo de cierre «vacante eliminada» y el permiso `eliminar_vacante` («Eliminar una vacante», grupo `VACANTES`), asignado a Talento y Dirección con alcance `TODO` tanto en organizaciones existentes como futuras.
- La lista obtiene su booleano `puedeX` para eliminar, sin endpoint nuevo de permisos; la API aplica 403 por falta de permiso y 404 fuera del alcance del rol.
- Los registros existentes quedan sin eliminar. El mecanismo existente de borrado de datos personales sigue alcanzando las postulaciones de vacantes eliminadas; el borrado lógico no lo debe excluir.
- La eliminación tiene verbo propio. Antes de numerar una migración Flyway, consulta las migraciones de la base integrada y las ramas abiertas. Usa el siguiente número libre; no fijes V58 ni otro número por anticipado, y no edites migraciones anteriores. Las migraciones posteriores deben funcionar tanto desde el snapshot autorizado como sobre una base que ya tenga la entrega anterior aplicada.

## Verificación específica
Datos: usuario Talento/Dirección con permiso nuevo y rol sin él; candidata/candidato con acceso al portal; publicada con 2 postulaciones en carrera, 1 `NO_CONTINUA` y 1 `CONTRATADO`; una archivada, un borrador y una cerrada. Usa datos aislados para la operación destructiva y conserva o recrea otros casos para revisión humana.

E2E repetibles:
1. Eliminar publicada: abrir y cancelar sin efectos antes de confirmar; verificar consecuencias y motivo vacío/espacios, cerrar solo quienes están en carrera, aviso sin enlace y ningún correo nuevo; la fila, vacante y procesos dejan de aparecer en panel/portal.
2. Verificar que la solicitud liberada aparece en Crear vacante y permite crear otra. Reutilizar título sin que el borrado lógico lo impida.
3. Verificar permisos UI/API, eliminación de una archivada y rechazo de eliminación repetida o postulación desde un formulario antiguo.

Integración verifica rollback conjunto de cierres/marca/liberación si falla cualquiera, liberación de recursos del cierre manual y fallo parcial de avisos sin deshacer la eliminación ni mentir sobre el conteo. Prueba solicitudes simultáneas de eliminación y de postulación contra eliminación: no deben duplicar cierres/avisos ni crear una postulación sobre una vacante ya eliminada. La implementación elige el mecanismo de consistencia, sin añadir un sistema genérico ajeno al alcance.

QA explora con Chrome visible permisos, confirmación y motivo, teclado/nombres accesibles, enlaces antiguos de campana, edición abierta antes de eliminar y desaparición de todas las pantallas del alcance. Verifica por integración que vencimientos, pase automático y calificación no procesen vacantes eliminadas; no basta con ocultarlas en la UI. Comprueba ranking/Excel, selección de vacantes de simulación, Radar y conteos con las herramientas y pruebas pertinentes, declarando cualquier limitación real.

Revisión humana: eliminar una vacante preparada para la prueba, ver que desaparece del panel y portal de quien postuló, revisar el aviso y la solicitud disponible.

## Documentación y decisiones
Después de la aprobación, actualiza requisitos afectados, RF-14, roles/permisos y APIs; ajusta arquitectura/datos y referencias de CLAUDE.md donde el nuevo borrado lógico lo requiera. No crees un documento por intento ni reescribas documentos sin cambios pertinentes.

Confirmado en la spec original: borrado lógico, cierre humano de quienes siguen en carrera, avisos por campana, permiso para Talento y Dirección y ausencia de restauración desde el panel. Confirmado por el usuario el 19/09/2026: exigir motivo y liberar la solicitud al eliminar. La distinción entre cierres y avisos reales resuelve el caso de fallo parcial ya contemplado en la spec original; no introduce una nueva política de reenvíos.

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
- [Máquina de estados](../../src/main/java/com/renaser/ai/ai_engine/postulacion/service/MaquinaEstados.java).
