# 01 · Editar vacantes y unificar avisos del portal

Estado: lista para implementar. Primera entrega de tres; no depende de las entregas 02 y 03.

## Objetivo
Corregir una vacante desde `/admin` reutilizando el formulario de alta y avisar en el portal a quienes siguen en carrera cuando cambia información visible, incluido el sueldo.

## Contexto
El backend ya expone `PUT /api/v1/panel/vacantes/{id}` con `editar_vacante`, pero la lista no ofrece edición; ese método ignora la remuneración, no avisa y solo audita título y descripción. El sueldo tiene su propio `POST …/vacantes/{id}/remuneracion`, exige motivo y hoy avisa por correo y campana. Se conservan los estados `BORRADOR`, `PUBLICADA` y `CERRADA`. Esta entrega no incorpora archivo ni borrado.

Frontend afectado: `src/panel/vacantes/Vacantes.tsx`, `Remuneracion.tsx`, `Vacante.tsx` y las vistas del portal que muestran vacante, proceso y campana.

## Alcance
- Incluye editar campos del formulario, permisos de edición, auditoría por campo, conteo de avisos reales y notificaciones solo por campana desde ambas entradas del sueldo.
- Fuera: cambiar solicitud o puesto; requisitos indispensables, prueba, banco, pesos, calificación automática y demás ajustes de la tuerca; archivar/desarchivar/eliminar; avisos por otros hechos y cambios en correos de la máquina de estados; scripts que consultan la base directamente.
- Cambiar el sueldo conserva RF-158/RF-159 salvo la retirada explícita del correo de RF-159. No se crean nuevos permisos.

## Comportamiento esperado
### Editar
1. En cada fila `BORRADOR` o `PUBLICADA`, el usuario ve un botón con icono de lápiz cuyo nombre accesible es «Editar la vacante {título}». En `CERRADA` no aparece. La restricción para archivadas se incorpora en la entrega 02.
2. Al pulsarlo se abre el formulario de alta con el título «Editar vacante» y los datos actuales: responsable, título, descripción, propósito, responsabilidades, requisitos, modalidad, horario, ubicación, forma de cierre (con plazas o fecha) y remuneración. La solicitud y el puesto se muestran como texto fijo, con la frase «El puesto decide el nivel y la familia de la evaluación; para cambiarlo, crea otra vacante». Solo hay un formulario abierto a la vez: abrir la edición cierra el alta, y al revés.
3. La remuneración sigue las reglas actuales. En una vacante publicada no se puede pasar de oculta a publicada ni al revés, y la opción bloqueada dice por qué. El monto sí se puede cambiar; en ese caso se pide un motivo escrito (RF-159).
4. Si la vacante está publicada y tiene postulantes en carrera, bajo el botón se informa, sin pedir confirmación: «Al guardar, avisaremos en su portal a N postulantes en carrera de lo que cambies».
5. Al pulsar «Guardar cambios», el sistema compara cada campo con lo guardado, sin tener en cuenta los espacios al principio y al final. Si nada cambió, cierra el formulario, dice «No había cambios que guardar» y no genera aviso ni auditoría.
6. Si algo cambió, el sistema guarda y audita cada campo modificado con su valor anterior y el nuevo, junto con el motivo si cambió el sueldo. Además:
   - Si la vacante está `PUBLICADA` y cambió al menos un campo que ve el postulante (título, descripción, propósito, responsabilidades, requisitos, modalidad, horario, ubicación o remuneración), publica **un único aviso** en la campana de cada postulante en carrera. En carrera significa que su postulación no está en `CONTRATADO`, `NO_CONTINUA` ni `CERRADA`. No se envía correo.
   - Los campos internos (responsable, forma de cierre, plazas y fecha de cierre) se guardan sin aviso.
   - En `BORRADOR` no hay a quién avisar.
7. El aviso es de tipo `VACANTE_ACTUALIZADA`. Su título es «Se actualizó la vacante «{título actual}»». El cuerpo enumera lo que cambió:
   - Los campos cortos (título, modalidad, horario, ubicación y remuneración) se muestran como «antes → ahora».
   - Los campos largos (descripción, propósito, responsabilidades y requisitos) solo se nombran, por ejemplo: «Se actualizaron la descripción y los requisitos».
   - Termina con «Tu postulación sigue su curso y no tienes que hacer nada».

   Al pulsarlo, el postulante va a su proceso y ve la vacante con los datos nuevos.
8. Después de guardar, la tabla muestra los datos nuevos y el panel confirma «Cambios guardados. Avisamos a N postulantes en su portal». N es la cantidad de avisos publicados realmente, igual que el contador actual del sueldo. Si N es 0, solo dice «Cambios guardados».
9. Si ocurre un error, se muestra junto al formulario y se conserva lo escrito. Esto incluye título o descripción vacíos, sueldo incoherente, falta del motivo del sueldo o validaciones de la forma de cierre. Si la vacante se cerró mientras se editaba, el backend rechaza el guardado sin aplicar cambios, el panel lo explica y recarga la lista. Un aviso que falla para una persona no deshace el cambio ni impide los avisos a los demás, igual que ocurre hoy con el sueldo.

### El sueldo avisa solo por la campana
10. Cambiar el sueldo, desde el formulario de edición o desde la tarjeta del detalle, deja de enviar correo y queda solo el aviso en la campana. Si el mismo guardado cambia el sueldo y otros campos visibles, sale **un solo aviso** con todo. Se corrigen los textos del panel que prometen un correo, y el texto de correo `REMUNERACION_ACTUALIZADA` deja de ofrecerse para editar, aunque sus datos se conservan.


La entrega 02 extenderá el rechazo de edición a archivadas y la 03 a eliminadas. No crees aquí esas marcas ni acciones. Una validación fallida de cualquier campo, incluido el sueldo, no debe dejar un guardado parcial ni avisos. Desactiva el envío mientras la petición está en curso; reenviar la misma edición ya aplicada no vuelve a auditar ni avisar. Conserva esa comprobación en el backend: desactivar el botón no es suficiente.

## Criterios de aceptación
- AC-01: Dada una vacante `BORRADOR` o `PUBLICADA` y un usuario con `editar_vacante`, cuando abre `/admin`, entonces la fila muestra el botón de lápiz con nombre accesible «Editar la vacante {título}». En `CERRADA` no aparece; la cobertura de archivadas pertenece a la entrega 02.
- AC-02: Dada una vacante, cuando se pulsa el lápiz, entonces se abre el formulario con todos los datos actuales, incluida la remuneración; la solicitud y el puesto no se pueden cambiar, y el formulario de alta queda cerrado.
- AC-03: Dada una vacante publicada con 2 postulaciones en carrera y 1 en `NO_CONTINUA`, cuando se cambian el horario y la descripción, entonces cada una de las 2 recibe exactamente un aviso `VACANTE_ACTUALIZADA` que muestra el horario anterior y el nuevo y nombra la descripción; la de `NO_CONTINUA` no recibe nada y no se envía ningún correo.
- AC-04: Dada una vacante publicada con postulantes en carrera, cuando se guarda sin cambios (o solo con espacios añadidos al principio o al final), entonces no se crea ningún aviso ni registro de auditoría y el panel dice «No había cambios que guardar».
- AC-05: Dada una vacante publicada con postulantes en carrera, cuando solo cambian el responsable, la forma de cierre, las plazas o la fecha de cierre, entonces se guarda y audita sin crear avisos.
- AC-06: Dada una vacante publicada con sueldo RANGO, cuando se cambia el monto con motivo, desde el formulario o desde la tarjeta del detalle, entonces cada postulante en carrera recibe un aviso en la campana y no se envía ningún correo.
- AC-07: Dada una vacante publicada, cuando en el mismo guardado cambian el sueldo y la ubicación, entonces cada postulante en carrera recibe un solo aviso que incluye ambos cambios.
- AC-08: Dada una vacante publicada con sueldo visible, cuando se intenta ocultarlo desde el formulario, entonces se rechaza con la explicación actual y no se guarda ningún campo del formulario.
- AC-09: Dada una vacante en borrador, cuando se edita cualquier campo, entonces se guarda y no se crea ningún aviso.
- AC-10: Dado un cambio guardado, cuando el candidato abre el aviso en su portal, entonces llega a su proceso y ve la vacante con los datos nuevos.
- AC-19: Dado un usuario sin `editar_vacante`, cuando abre `/admin`, entonces no ve el lápiz y llamar a editar por API responde 403. Fuera del alcance del rol, por ejemplo `SUS_VACANTES`, responde 404.

## Datos, compatibilidad y permisos
- Guarda auditoría `editar_vacante` con valor anterior/nuevo de cada campo cambiado y motivo del sueldo, cuando corresponda. Espacios iniciales/finales no cuentan como cambios. Los campos internos no generan aviso.
- Añade `VACANTE_ACTUALIZADA` a los tipos de `aviso_portal`, con texto ya armado; conserva los avisos anteriores y correos enviados. Deja de ofrecer para editar el texto de correo `REMUNERACION_ACTUALIZADA`, conservando sus datos.
- La definición de «en carrera» excluye `CONTRATADO`, `NO_CONTINUA` y `CERRADA` y debe reutilizarse en las entregas siguientes.
- El DTO de lista incluye el conteo en carrera y el booleano de edición siguiendo `puedeX`; no añadas un endpoint de permisos. El backend exige `editar_vacante` y respeta el alcance del rol.
- Reutiliza `PUT /api/v1/panel/vacantes/{id}` y el verbo existente del sueldo; coordina el guardado para que cambiar sueldo y otros campos no produzca dos avisos.
- No introduzcas marcas de archivo/eliminación ni sus tipos de aviso en esta entrega. Antes de numerar una migración Flyway, consulta las migraciones de la base integrada y las ramas abiertas. Usa el siguiente número libre; no fijes V58 ni otro número por anticipado, y no edites migraciones anteriores. Las migraciones posteriores deben funcionar tanto desde el snapshot autorizado como sobre una base que ya tenga la entrega anterior aplicada.

## Verificación específica
Datos: usuario de Talento con edición; rol sin `editar_vacante`; candidato con acceso al portal; borrador sin postulantes y publicada con sueldo RANGO, 2 postulaciones en carrera, 1 en `NO_CONTINUA` y 1 en `CONTRATADO`.

E2E repetibles:
1. Cambiar horario y descripción: exactamente un aviso por cada postulación en carrera, ninguno para las terminadas y ningún correo.
2. Guardar sin cambios o con espacios; después cambiar solo responsable: comprobar mensajes, ausencia de avisos y auditoría que corresponda.
3. Cambiar sueldo con motivo desde la tarjeta; cambiar sueldo y texto en un único guardado desde el formulario: avisos sin duplicados y ningún registro nuevo en `correo_enviado`.
4. Editar borrador sin avisos; rechazar sueldo/forma de cierre inválidos sin guardar parcialmente; permisos de UI y API.

QA explora teclado, nombres accesibles, móvil, alternancia alta/edición, datos conservados ante errores, reintentos y apertura del aviso en el proceso. Integración comprueba que el fallo de un aviso no revierte el cambio ni impide avisar a los demás; no se exige provocar ese fallo mediante el navegador. Comprueba también cierre de vacante durante una edición pendiente.

Revisión humana: editar una publicada con el lápiz y ver los datos nuevos y su aviso desde la cuenta del candidato.

## Documentación y dependencias
Después de la aprobación, actualiza RF-159, las APIs afectadas, auditoría y [el sueldo de los dos lados](../docs/EL-SUELDO-DE-LOS-DOS-LADOS.md); roles solo si necesita describir el uso del permiso existente. Conserva referencias de CLAUDE.md pertinentes, sin documentación nueva redundante.

Decisiones de negocio de esta entrega confirmadas en la spec original: avisar cambios visibles solo por campana y mantener los campos internos sin aviso. Las reglas de archivo/eliminación se confirmaron el 19/09/2026 y pertenecen a sus entregas. Integrar sus PR antes de lanzar la [entrega 02](02-archivar-desarchivar-vacantes.md).

## Ejecución y pruebas
- Una spec por conversación de Claude Code. Backend y frontend (`RenaserOsPostulantes`) se implementan en sus worktrees del mismo trabajo; nunca en los checkouts originales.
- Usa el clon temporal y los helpers E2E existentes, parametrizados con los destinos del trabajo. Prepara datos sintéticos reproducibles en ese clon; no accedas a la base principal ni copies credenciales personales.
- QA debe entrar tanto al panel como al portal con cuentas de prueba. La revisión humana posterior no sustituye la exploración visual de QA. Si falta acceso, informa el bloqueo concreto y pide solo lo necesario.
- Pruebas repetibles con el runner Playwright CLI/tests del proyecto; exploración adicional con Playwright MCP y Chrome visible. Verifica permisos por API además de ocultar botones. Usa integración para transacciones, errores y procesos automáticos que el navegador no puede acreditar.
- Reutiliza comprobaciones vigentes del harness y añade regresiones del alcance; no repitas manualmente suites completas ya validadas ni habilites la suite API E2E opcional vacía. Las comprobaciones de API pueden vivir en integración existente o en los tests del runner habilitado, según corresponda.
- Al pedir aprobación, conserva localhost y datos del clon para la revisión del usuario. Documentación, PR, CI y limpieza siguen el flujo del harness; no crees documentos por agente.

## Referencias
- [Índice de las tres entregas](editar-archivar-eliminar-vacantes.md).
- [Controlador de vacantes](../src/main/java/com/renaser/ai/ai_engine/vacante/controller/VacantesPanelController.java) y [servicio de vacantes](../src/main/java/com/renaser/ai/ai_engine/vacante/service/impl/ServicioVacantesPanelImpl.java).
- [Requisitos funcionales](../docs/01-REQUISITOS-FUNCIONALES.md), [roles y permisos](../docs/04-ROLES-Y-PERMISOS.md) y [APIs](../docs/09-APIS.md).
- [Campana del portal](../src/main/resources/db/migration/V56__el_portal_tiene_campana.sql) y [RNF-13b](../docs/02-REQUISITOS-NO-FUNCIONALES.md).
