# Contexto de vacantes y resultados por WhatsApp

Estado: lista para implementar

Crear o revisar esta spec no autoriza implementarla. El controlador del harness
solo puede marcarla lista tras `/harness ready <ruta-spec>` y la confirmación real
del usuario `LISTA PARA IMPLEMENTAR <código>`. La autorización no inicia el trabajo.

## Objetivo

Que un postulante vinculado por WhatsApp pueda preguntar por su vacante, sus
etapas y su siguiente paso, y conocer el resultado de su postulación. EX entrega
los hechos públicos y los avisos autorizados; el chatbot del CRM los presenta
con mensajes claros y responde a la pregunta concreta.

## Contexto

- Hoy EX permite vincular un número, consultar novedades breves y enviar avisos
  de prueba disponible o avance. No entrega una ficha ni un procedimiento
  estructurado al chatbot. Al terminar una postulación cierra el vínculo y omite
  avisos pendientes; no existe un aviso final que distinga el resultado.
- La ficha y las decisiones ya existen en EX. Se necesita una consulta segura
  para el bot y ampliar los avisos, conservando las reglas de selección.
- Cerrar una convocatoria no termina sus postulaciones en marcha: cada una se
  decide por separado. El estado de la vacante y el de la postulación deben
  comunicarse por separado.
- Afecta la integración de WhatsApp, la consulta de datos públicos de selección
  y el registro y envío de novedades. No crea pantallas.
- Referencias: [plantilla del harness](../plantilla-spec.md),
  [contrato vigente](../../docs/CONTRATO-NOVEDADES-POR-WHATSAPP.md),
  [estados de postulación](../../docs/03-ESTADOS-POSTULACION.md),
  [reglas del código](../../docs/REGLAS-DEL-CODIGO.md) y
  [spec complementaria del chatbot](../../../CRM/specs/014-postulantes-hacia-eventos/spec.md).
- El contrato vigente y la funcionalidad histórica de
  [novedades por WhatsApp](../Done/novedades-por-whatsapp.md) siguen describiendo v1.
  Este documento propone la ampliación; no declara que ya esté disponible.
- **Contrato v2:** es único y está en la sección «Contrato EX v2» de la
  [spec 014 del CRM](../../../CRM/specs/014-postulantes-hacia-eventos/spec.md#contrato-ex-v2)
  (puntos C-1 a C-10). Esta spec implementa el lado de EX; ante cualquier
  diferencia de rutas, campos o límites, prevalece ese contrato y se cambian
  las dos specs a la vez.

## Alcance

- Incluye: consulta por número y referencia propia; ficha publicada;
  procedimiento real; etapa, instrucciones y plazos; resultado final consultable;
  avisos de resultado y cierre de convocatoria; persistencia, reintentos y
  compatibilidad con el bot actual.
- Queda fuera: frontend de EX, nuevos formularios para publicar condiciones,
  decisiones de contratación, cambios de etapas o calificaciones, contenido de
  las pruebas y edición de sus textos compartidos con el portal.
- La IA, los menús y el formato de los mensajes corresponden al chatbot. Las
  preguntas frecuentes y su pequeño selector en la consola del CRM pertenecen
  a su spec complementaria. Los eventos, inscripciones y campañas del flujo
  general del bot conservan su comportamiento.

## Comportamiento esperado

1. **Acceso propio.** La consulta usa el teléfono normalizado y los vínculos
   verificados de EX. Una referencia escrita en el chat no concede acceso.
   Se comprueban número, postulación, organización y vigencia en cada consulta.
   Las referencias son opacas; se reutiliza `postulacionRef` durante la vida de
   su vínculo, sin exponer identificadores internos ni permitir búsquedas libres.

2. **Listado y selección.** Sin referencia, EX devuelve las postulaciones
   consultables de ese número, con referencia, título, situación y un dato
   público que permita distinguir títulos iguales, cuando exista. Pagina en
   grupos de hasta nueve (la décima fila de la lista del bot es «Ver más»),
   con cursor opaco asociado al mismo número. Con una
   referencia, devuelve únicamente el contexto autorizado de esa postulación.
   Una selección antigua se valida otra vez y puede dejar de estar disponible.

3. **Consulta ampliada.** Nueva ruta firmada
   `POST /api/v2/integraciones/whatsapp/contexto`, con
   `{telefono, postulacionRef?, cursor?}`. Referencia y cursor no se combinan.
   Esquemas, límites y errores exactos: C-3 del contrato v2. Los eventos van a
   la nueva ruta del bot `POST /v1/ex/resultados` (C-4 a C-7). Su contenido es:

   | Bloque | Datos autorizados |
   |---|---|
   | Identificación | Referencia propia, referencia pública de la vacante y título |
   | Vacante | Descripción, propósito, responsabilidades, requisitos, ubicación, modalidad, horario y remuneración publicable |
   | Procedimiento | Etapas públicas aplicables, orden y explicación aprobada |
   | Situación | Estado de la postulación, etapa actual y estado independiente de la vacante |
   | Acción | Si existe una tarea pendiente, instrucciones, acceso normal al portal, duración y vencimiento disponibles |
   | Resultado | Resultado confirmado, fecha y explicación pública, cuando exista |
   | Vigencia | Fuente EX, revisión del contexto, revisión ordenable del resultado, fecha real de actualización y fecha de consulta |

4. **Ficha pública.** Se construye una lista explícita de campos comunicables,
   reutilizando las fuentes publicadas para el candidato. No se exporta la ficha
   administrativa completa. La remuneración respeta `OCULTA`, `FIJA` y `RANGO`:
   en `OCULTA` no se envían importes, aunque existan en la base de datos. No se
   recupera el campo antiguo de compensación que ya no se utiliza.

5. **Información ausente.** Se diferencia un valor disponible, no publicado,
   no aplicable o pendiente de definición cuando EX conoce esa distinción. Un
   campo ausente no se convierte en una condición del puesto. Beneficios, tipo
   de contrato o estimaciones de espera solo se entregan si ya existe una fuente
   pública autorizada; esta spec no exige crear nuevos campos o pantallas para
   rellenarlos. Si no existe esa fuente, se informa «no publicado».

6. **Etapas reales.** El procedimiento refleja la configuración de la vacante
   y las reglas vigentes: evaluación inicial cuando corresponde, instrumento
   técnico elegido y demás pasos aplicables. No se afirma que todos deben
   responder un banco de preguntas ni que toda vacante tiene la misma prueba.
   Se reutilizan las explicaciones públicas de `TextosDelMomento` y las reglas
   existentes, sin cambiar sus textos compartidos con el frontend.

7. **Qué toca ahora.** Se separan el procedimiento previsto, la etapa actual y
   una acción efectivamente habilitada para esa persona. Una entrevista o
   simulación futura no aparece como una cita asignada. Si está en revisión, EX
   indica que no hay una tarea pendiente. Fechas y duraciones proceden del
   proceso real; no se inventa un plazo de respuesta.

8. **Pruebas y acceso.** Se distinguen evaluación inicial, prueba del puesto,
   cuestionario técnico y simulación según corresponda. Se informa de duración,
   vencimiento, posibilidad de retomar o inicio del reloj solo cuando la regla
   está confirmada. Consultar o pulsar «Ver cómo ingresar» en el chat no inicia
   intentos, no confirma asistencia y no dispara un cronómetro. El bot explica
   cómo entrar con las credenciales habituales a «Mis procesos». Con una acción
   habilitada o en curso, EX siempre envía `enlace`: la URL pública fija de
   «Mis procesos» del portal, igual para todas las personas, sin parámetros,
   fragmento, credenciales, tokens ni identificadores. No concede acceso por sí
   sola: el portal pide iniciar sesión como siempre (C-3).
   **Plazos.** La acción distingue el plazo para empezar del plazo para
   terminar, con los datos reales: `empezarAntesDe` (cierre de la prueba de la
   vacante, `prueba_cierra_en`, o vencimiento de la evaluación sin iniciar),
   `modalidad` y `duracionMinutos`/`plazoDias` de la versión de la prueba,
   `iniciadaEn` y `entregarAntesDe` (`vence_en` del intento o de la
   evaluación). Un dato que no existe va `null`; no se estima. Con el intento
   ya entregado o vencido, la acción no queda abierta.

9. **Resultados distintos.** Se conserva el significado de los estados de EX:
   `CONTRATADO` es contratación; `NO_CONTINUA` comunica que no continúa;
   `CERRADA` es una postulación cerrada y no se traduce automáticamente en
   descarte. La explicación se basa únicamente en una causa pública registrada.
   Hoy `postulacion.motivo_cierre` es interno: `explicacion` viaja `null`.
   No se publican notas, puntuaciones, rankings, criterios internos, preguntas
   del banco, respuestas ni datos de otros candidatos.

10. **Convocatoria cerrada.** Se devuelve el estado y la fecha de cierre de la
    vacante por separado. Puede avisarse que la convocatoria cerró y que la
    postulación sigue en curso, si eso confirma EX. Ese cierre no revoca el
    vínculo ni cancela una acción pendiente por sí solo. Se conservan las
    diferencias entre cerrar la convocatoria, cerrar una postulación y eliminar
    una vacante. Un borrado de datos personales nunca genera un aviso final.

11. **Consulta después del final.** Para quien tenía un vínculo activo al
    terminar, EX permite consultar su resultado durante treinta días desde el
    final. La consulta muestra fecha y resultado, sin reabrir la postulación ni
    habilitar nuevos avisos. BAJA, dejar de recibir, cambio de celular o borrado
    invalidan este acceso por el vínculo anterior. Una corrección no extiende
    automáticamente el plazo. Los vínculos legados cerrados no se convierten
    en resultados nuevos sin una fuente y una asociación verificables.

12. **Registro durable.** Al confirmar un resultado o cerrar una convocatoria,
    se guarda el hecho público y, si corresponde, su pendiente de comunicación
    dentro de la misma transacción de negocio. Un reinicio después de confirmar
    no puede perderlo. El mecanismo puede ser una bandeja de salida transaccional;
    una llamada en memoria después de confirmar no es suficiente. Se reutiliza
    la máquina de estados; el bot no origina decisiones de selección.

13. **Identidad y orden.** Cada aviso identifica un evento UUIDv7, vínculo,
    postulación, tipo, fecha, revisión creciente y contenido público. Un mismo
    evento mantiene identificador y contenido en los reintentos. Se distingue
    la revisión del resultado de la revisión de la vacante para no sobrescribir
    una contratación con un aviso antiguo de cierre de convocatoria. Los
    duplicados no generan otro pendiente. Una corrección lleva una nueva revisión
    y se comunica como actualización, sin presentar el dato anterior como vigente.

14. **Cierre y autorización final.** Para una postulación que termina, se
    separan el cierre del vínculo ordinario, el derecho a consultar el resultado
    y la autorización limitada para comunicar ese resultado. Solo se crea esta
    última si el vínculo estaba activo y autorizado al confirmar el hecho.
    Vale para ese evento y sus reintentos, durante un máximo de veinticuatro
    horas desde el hecho; no reactiva avisos de avance ni un consentimiento
    revocado. Las correcciones requieren una autorización vigente propia.

    El nuevo evento incluye el resultado, su revisión, caducidad y la disposición
    de cierre. El bot debe registrar de forma atómica resultado, deduplicación,
    cierre ordinario y, si sigue autorizado, pendiente de aviso. No se depende
    del orden de llegada entre una llamada de aviso y una revocación. Una
    revocación por BAJA, dejar de recibir, cambio de celular o borrado prevalece
    aunque llegue antes que el evento. El cierre ordinario por fin del proceso
    no cancela por error ese único pendiente final autorizado.

15. **Supresión y reintentos.** Antes de entregar al bot se revalidan alcance,
    caducidad y supresión. BAJA o borrado cancelan también los pendientes finales;
    el bot vuelve a comprobarlo al despachar. Se conservan los reintentos
    acotados y el registro de fallos del envío existente. Una espera, reapertura
    o respuesta tardía no resucita una autorización revocada. Aceptado por el bot
    significa aceptado, no entregado al WhatsApp de la persona.

16. **Presentación y canal.** EX entrega hechos y explicaciones aprobadas; el
    bot selecciona lo preguntado y compone el mensaje. Los avisos finales no
    promocionan eventos. El bot conserva sus reglas de ventana, plantillas,
    pausa, atención humana y topes de envío. Sin plantilla aprobada fuera de
    ventana no se sustituye por texto libre. El evento de EX no cambia el modo
    de conversación ni consume la siguiente respuesta de una inscripción.

17. **Compatibilidad y activación.** V1 sigue funcionando con sus mismos campos,
    tipos de aviso y firmas. La nueva consulta y recepción de resultados usan
    rutas nuevas; no se añaden tipos a `/v1/ex/avisos` del bot actual. Se amplían
    los dobles de prueba y los modos falso y apagado. Solo se habilita el envío
    nuevo cuando el receptor compatible, el contrato y las plantillas requeridas
    estén disponibles. No hay envíos masivos retrospectivos al activar la mejora.

18. **Seguridad y fallos.** Se conservan firma por dirección, validación de
    bytes y ruta, tolerancia temporal y tiempos de espera del contrato vigente.
    Los errores distinguen consulta no disponible de lista vacía o campo no
    publicado. Una referencia inexistente o ajena no revela si pertenece a otra
    persona. Las consultas son de lectura y no cambian estados de selección,
    intentos, consentimiento ni relojes. Los registros técnicos evitan guardar
    teléfonos completos o el contenido íntegro de las fichas.

19. **Formato de los avisos actuales.** `TextosDeWhatsapp.pruebaDisponible` y
    `TextosDeWhatsapp.avance` pasan de un párrafo plano a un mensaje con
    negritas de WhatsApp, bloques y el enlace fijo a «Mis procesos» (el mismo
    de la regla 8, sin tokens). Siguen dentro de 1000 caracteres, con los
    mismos tipos, firmas y contrato v1; el bot los envía tal cual. La línea del
    plazo solo aparece si EX conoce `empezarAntesDe`. Las plantillas de fuera
    de ventana no cambian. Formato:

    ```
    ¡Felicidades, {nombre}! 🎉

    📌 *{vacante}*
    Tu *prueba del puesto* ya está disponible.
    ⏰ Puedes empezarla hasta el *{fecha y hora}* (hora de Lima).

    👉 {enlace a Mis procesos}
    Inicia sesión con tu correo y contraseña y abre «Mis procesos».
    ```

    ```
    Hola, {nombre} 👋

    📌 *{vacante}*
    Te toca: *{titulo de TextosDelMomento}*
    {texto de TextosDelMomento}

    👉 {enlace a Mis procesos}
    Inicia sesión con tu correo y contraseña y abre «Mis procesos».
    ```

    Con cuestionario técnico dice «Tu *prueba técnica*». Sin nombre, «¡Felicidades!»
    u «Hola 👋». Los textos compartidos con el portal (`TextosDelMomento`) no cambian.

## Referencia visual

No aplica. Es un cambio del backend de EX, sin pantallas nuevas ni cambios de
diseño. La presentación de WhatsApp y el ajuste de la consola del CRM pertenecen
a la spec del chatbot.

## Criterios de aceptación

- AC-01: Dado un número vinculado, cuando consulta, recibe solo sus postulaciones
  autorizadas. Una referencia ajena, de otra organización o de un vínculo sin
  acceso no devuelve datos ni confirma la existencia de esa postulación.
- AC-02: Dadas más de diez postulaciones consultables, el listado permite
  recorrerlas sin perder ninguna. Una referencia concreta devuelve solo su
  contexto; un cursor de otro número se rechaza.
- AC-03: Dada una vacante con funciones, requisitos, horario y ubicación públicos,
  la consulta entrega esos hechos sin incluir la ficha administrativa completa.
- AC-04: Dadas remuneraciones oculta, fija y de rango, solo se envían los valores
  publicables. Con remuneración oculta no aparece ningún importe en el contexto.
- AC-05: Dado un beneficio sin fuente pública, EX informa que no está publicado;
  no lo toma de otra vacante ni inventa un valor. No aplicable y pendiente se
  distinguen cuando el origen permite afirmarlo.
- AC-06: Dadas vacantes con y sin evaluación inicial y con distintos instrumentos
  técnicos, cada una devuelve sus etapas aplicables y explicaciones públicas.
- AC-07: Dada una etapa en revisión y una entrevista solo prevista, la respuesta
  no incluye una tarea ni una cita confirmada que la persona no tenga asignada.
- AC-08: Dada una prueba habilitada, completada o vencida, la acción y los plazos
  reflejan cada caso. Consultar repetidamente no inicia ni altera el intento o
  cronómetro; las instrucciones de acceso no conceden acceso al examen.
- AC-09: Dados `CONTRATADO`, `NO_CONTINUA` y `CERRADA`, la consulta distingue sus
  resultados, con fecha y solo una explicación pública confirmada.
- AC-10: Dada una convocatoria cerrada con una postulación aún activa, la consulta
  y el aviso conservan esa diferencia y la acción vigente. No cierran la
  postulación ni revocan el vínculo por cerrar la convocatoria.
- AC-11: Dado un resultado propio con acceso vigente, es consultable durante
  treinta días y deja de serlo al vencer el plazo. Con otra postulación activa
  se mantienen ambos contextos separados; consultar no habilita avisos.
- AC-12: Dada una caída después de confirmar el resultado y antes de llamar al
  bot, al reiniciar se conserva el resultado y el pendiente autorizado.
- AC-13: Dado el mismo evento dos veces, solo hay un pendiente. Una revisión
  anterior no reemplaza otra posterior; una corrección se reconoce como
  actualización y un cierre de convocatoria no pisa el resultado personal.
- AC-14: Dado el fin del proceso y su cierre ordinario entregados en distinto
  orden o reintentados tras un reinicio, el bot conserva el resultado y su único
  pendiente final válido sin reabrir los avisos de avance.
- AC-15: Dada una BAJA, cambio de número, solicitud de dejar de recibir o borrado,
  un evento tardío no restablece acceso ni aviso por el vínculo anterior. Si
  ocurre antes del despacho, se cancela también el pendiente final.
- AC-16: Dado un evento final con más de veinticuatro horas o sin autorización
  válida al originarse, no se entrega para un nuevo envío. El resultado propio
  puede seguir consultable si conserva su acceso independiente.
- AC-17: Dado un aviso fuera de ventana sin plantilla aprobada, el bot registra
  que no puede enviarlo y no usa texto libre. La aceptación HTTP no se registra
  como entrega al destinatario.
- AC-18: Dado el receptor actual, las consultas y avisos v1 siguen funcionando.
  La ampliación deshabilitada no envía nuevos tipos ni genera avisos históricos
  al activarse. Los modos falso y apagado no contactan un WhatsApp real.
- AC-19: Dada una firma incorrecta, de otra ruta o fuera de plazo, se rechaza la
  solicitud sin efectos. Una consulta fallida se distingue de no tener procesos.
- AC-20: Dado un resultado mientras la persona completa una inscripción del bot,
  su siguiente respuesta sigue el trámite. Las pruebas de integración confirman
  que un usuario sin vínculo conserva el flujo general y no consulta EX.
- AC-21: Dado un borrado de datos, no se emite un nuevo aviso final personal y
  se eliminan o anonimizan contexto, referencias y pendientes según el flujo
  existente, sin retener una copia personal adicional para esta mejora.
- AC-22: Dada una prueba con cierre de vacante y modalidad cronometrada, la
  consulta entrega `empezarAntesDe` y `duracionMinutos`; ya iniciada, también
  `iniciadaEn` y `entregarAntesDe`. Sin esos datos van `null`.
- AC-23: Los avisos de prueba disponible y de avance usan el formato de la
  regla 19, con el enlace fijo a «Mis procesos» y sin superar 1000 caracteres.

## Casos límite y errores

- Ningún proceso consultable: lista vacía; no equivale a descarte ni contratación.
- Dos títulos iguales sin dato público que los distinga: no inventar empresa o
  ubicación; el bot debe pedir ayuda o selección segura sin mostrar datos internos.
- Cambio de visibilidad de sueldo: la siguiente consulta respeta la visibilidad
  vigente; el bot no reutiliza un contexto antiguo para revelar el valor ocultado.
- Resultado corregido, mensajes fuera de orden y cierre concurrente: preservar
  revisiones independientes, cancelaciones y deduplicación después de reiniciar.
- Error de red o bot incompatible: registrar el fallo y reintentar según el
  contrato; no degradar un resultado final a un aviso genérico de avance.
- Vínculo legado cerrado solo con `PROCESO_TERMINADO`: no inferir un resultado ni
  emitir un aviso retrospectivo. Usar un resultado histórico únicamente si EX
  puede comprobar su origen, asociación y acceso vigente.

## Datos y compatibilidad

- Guardar la proyección pública mínima del resultado, su revisión y fecha; la
  relación autorizada con el vínculo; el evento y su pendiente durable; alcance
  y caducidad de consulta y aviso; estado y motivo de aceptación, fallo u omisión.
  No duplicar una ficha completa ni respuestas o evaluaciones del candidato.
- Se diferencian la revisión del contexto y la del resultado; consultar tiene su
  propia fecha y no se presenta como fecha de modificación del hecho. Una
  revisión del resultado debe poder compararse para descartar eventos antiguos.
- La consulta final dura treinta días; el permiso específico de aviso final,
  veinticuatro horas. Al vencer se retiran los datos auxiliares según la política
  de borrado existente, sin borrar por esta mejora el expediente de selección.
- Cambios de base de datos solo con migraciones nuevas y aditivas, tomando el
  siguiente número libre al implementar. No se editan migraciones publicadas,
  no se inventan estados de selección ni se cambian puntuaciones.
- El contrato v2 está en la spec 014 del CRM (sección «Contrato EX v2»). Al
  implementar se copia a `docs/` junto al contrato vigente, que sigue
  describiendo v1, sin presentar v2 como activa antes de C-10.
- El productor v2 de EX va detrás de su propio interruptor, apagado por
  defecto; con él apagado EX se comporta como v1. Mientras el bot tenga
  `CONVERSATION_EX_VERSION=1`, `/v1/ex/resultados` responde `404`: EX lo
  registra como fallo definitivo, sin reintentos en bucle.
- Se despliegan primero los receptores compatibles, se verifican consultas y
  recepción con el bot falso y luego se habilitan los productores nuevos. Una
  vuelta a v1 desactiva el envío nuevo sin perder hechos ni pendientes, que
  caducan sin recurrir a tipos incompatibles.

## Verificación

- Actor y datos: candidato con una y varias postulaciones, otro número y otra
  organización; vacantes con sueldo oculto/fijo/rango, datos incompletos y
  variantes de evaluación; estados activos y finales; consentimiento vigente,
  revocado y borrado. Usar datos ficticios.
- Automatizar consulta, acceso entre organizaciones, visibilidad, etapas y
  lectura sin efectos; firma y compatibilidad v1; persistencia tras reinicio,
  duplicados, revisiones, caducidades y carreras entre cierre y supresión.
- Integración con base temporal aislada en Docker y bot falso. No ejecutar
  pruebas ni migraciones sobre la base principal ni enviar mensajes reales.
- E2E repetibles en headless con el bot compatible: preguntar por vacante,
  seleccionar entre procesos, consultar etapa, recibir un resultado y volver a
  consultarlo; completar una inscripción con un aviso intercalado. Conservar
  además los recorridos existentes de prueba, cierre y baja.
- Chrome visible: comprobar que entrar a «Mis procesos» sigue usando el acceso
  normal y que preguntar desde el chat no comienza una prueba. La comparación
  visual no aplica al backend; cualquier evidencia de mensajes corresponde al
  QA conjunto del chatbot.
- Resultado que quiero comprobar personalmente: preguntar «¿qué horario tiene
  esta vacante?», «¿en qué etapa voy?» y «¿por qué ya terminó?» y recibir solo
  los hechos autorizados de mi proceso, sin alterar una prueba ni una inscripción.
- Esta spec define la validación de la futura implementación; redactarla no
  implica que estas pruebas de ejecución ya hayan pasado.

## Dependencias y dudas

- La spec 014 del CRM implementa el lado del bot en otro trabajo. Ninguno de
  los dos bloquea al otro: cada uno se prueba con el doble del otro lado y la
  activación conjunta sigue C-10. La ampliación de EX sola no cambia cómo
  responde el bot actual.
- El contrato v2 queda cerrado en la spec 014 (C-1 a C-10): esquemas, límites,
  ruta receptora, errores, revisiones, autorización final y cancelaciones.
- Las plantillas de avisos finales fuera de ventana son trabajo pendiente para
  otra spec; hasta entonces el bot los omite con motivo `sin_plantilla`. No
  bloquean esta implementación.
- Decisiones de este borrador: backend de EX sin cambios visuales; datos públicos
  existentes; cierres de convocatoria y postulación separados; consulta final
  de treinta días y aviso específico de hasta veinticuatro horas; sin envíos
  retrospectivos ni cambios del flujo general de eventos.
- Dudas de producto pendientes: ninguna para revisar este alcance. La aprobación
  de esta spec y la de su compañera corresponde al flujo del harness antes de
  iniciar cualquier implementación.
