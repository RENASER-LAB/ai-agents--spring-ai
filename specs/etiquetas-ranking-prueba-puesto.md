# Etiquetas del ranking y estado de la prueba del puesto

Estado: lista para implementar

## Objetivo

Hacer más claros los textos que ve el equipo de reclutamiento en los rankings, diferenciando
una prueba del puesto que el candidato no terminó de una prueba que sí fue entregada pero aún
no tiene su calificación completa.

## Contexto

- Comportamiento actual: los rankings muestran la vista o filtro «Por revisar». En la columna
  de la prueba del puesto, cuando falta `notaEtapa`, la interfaz muestra un guion y el texto
  «sin cerrar».
- Verificación del comportamiento actual: `ServicioPerfilIntegralPanelImpl` obtiene la nota
  de `PRUEBA_PUESTO` desde `NotaEtapa`; si no existe, `notaEtapa` queda nula. El backend no
  persiste «sin cerrar» como estado de la prueba.
- La ausencia de `NotaEtapa` tiene más de una causa. Un intento puede seguir en `PENDIENTE` o
  `EN_CURSO`; también puede haber sido entregado manualmente y no tener nota porque aún faltan
  criterios de la rúbrica por calificar. Cuando vence el plazo, el sistema entrega
  automáticamente lo que exista y marca `esEntregaAutomatica`, por lo que ese caso también
  debe distinguirse de una entrega manual pendiente de calificación.
- Pantallas o procesos afectados: filtros y encabezados de los rankings del panel, ranking de
  la etapa «Prueba del puesto», y textos descriptivos de la exportación del ranking a Excel.
- Referencias existentes: [plantilla de spec](plantilla-spec.md), [prueba del puesto](../docs/PRUEBA-DEL-PUESTO.md), [estados de postulación](../docs/03-ESTADOS-POSTULACION.md), y las imágenes adjuntas del ranking.

## Alcance

- Incluye:
  - Reemplazar visualmente «Por revisar» por «Pendiente» en todos los rankings y en la
    descripción equivalente de la exportación a Excel.
  - Mantener exactamente la lógica actual del filtro «Por revisar»; solo cambia el texto
    visible.
  - Reemplazar «sin cerrar» por «Prueba incompleta» cuando el intento de la prueba del puesto
    todavía no fue entregado (`PENDIENTE` o `EN_CURSO`) o cuando el sistema lo cerró
    automáticamente por vencimiento sin que exista una nota de etapa.
  - Mostrar «Pendiente de calificación» cuando la prueba fue entregada manualmente, pero aún
    no existe `NotaEtapa` porque faltan criterios de la rúbrica por calificar.
  - Mantener la nota numérica cuando `NotaEtapa` sí existe, aunque la entrega haya sido
    automática.
  - Exponer al frontend el dato necesario para distinguir estos casos de forma explícita, sin
    inferirlos únicamente a partir de que `notaEtapa` sea nula.
- Queda fuera:
  - Renombrar códigos internos como `EVALUACION_POR_REVISAR` o los estados de
    `Postulacion`.
  - Cambiar qué filas pertenecen al filtro, el orden del ranking, las notas, la rúbrica, los
    pesos o las transiciones de postulación.
  - Cambiar el funcionamiento del vencimiento, la entrega automática o la calificación con IA.
  - Añadir una migración o guardar un nuevo estado persistente en la base de datos.

## Comportamiento esperado

1. El usuario abre cualquier ranking y ve «Pendiente» donde actualmente aparece «Por revisar».
   El conteo, las filas incluidas y el filtro inicial permanecen iguales.
2. El usuario descarga el ranking a Excel y la descripción del corte usa «Pendiente», sin
   conservar el texto «Por revisar».
3. En el ranking de «Prueba del puesto», el sistema determina el estado de presentación con
   esta prioridad:
   - Si existe `NotaEtapa` para `PRUEBA_PUESTO`, muestra la nota numérica.
   - Si no existe la nota y el intento no tiene `entregadoEn`, muestra «Prueba incompleta».
     Esto cubre los intentos `PENDIENTE` y `EN_CURSO`.
   - Si no existe la nota, el intento tiene `entregadoEn` y `esEntregaAutomatica = true`,
     muestra «Prueba incompleta», porque el sistema cerró el intento por vencimiento antes de
     una entrega completa del candidato.
   - Si no existe la nota, el intento tiene `entregadoEn` y `esEntregaAutomatica = false`,
     muestra «Pendiente de calificación».
4. Si la postulación todavía no llegó a la etapa técnica y no existe un intento de la prueba,
   el sistema no la clasifica como «Pendiente de calificación». Debe conservar el indicador de
   no aplicabilidad o ausencia que use actualmente esa fila.
5. El estado visible se calcula con los datos existentes de la postulación, el intento y la
   nota de etapa. No se modifica ningún registro por abrir el ranking o descargarlo.
6. Si el backend no puede determinar el detalle del intento para una fila, el ranking no falla
   completo: conserva la fila y muestra el estado neutro existente, dejando el caso registrado
   para diagnóstico.

## Criterios de aceptación

- AC-01: Dado un ranking que actualmente muestra el filtro «Por revisar», cuando el usuario lo
  abre, entonces el texto visible es exactamente «Pendiente» y el conjunto de filas y el
  conteo no cambian.
- AC-02: Dado un ranking exportable con el corte equivalente, cuando el usuario descarga el
  Excel, entonces la descripción usa exactamente «Pendiente» y no contiene «Por revisar».
- AC-03: Dado un candidato en la etapa técnica con un intento `PENDIENTE` y sin `NotaEtapa`,
  cuando se consulta el ranking de la prueba del puesto, entonces la celda muestra «Prueba
  incompleta».
- AC-04: Dado un candidato con un intento `EN_CURSO` y sin `NotaEtapa`, cuando se consulta el
  ranking, entonces la celda muestra «Prueba incompleta».
- AC-05: Dado un candidato cuyo intento venció y fue entregado automáticamente
  (`entregadoEn` informado y `esEntregaAutomatica = true`) sin `NotaEtapa`, cuando se consulta
  el ranking, entonces la celda muestra «Prueba incompleta».
- AC-06: Dado un candidato cuya prueba fue entregada manualmente
  (`entregadoEn` informado y `esEntregaAutomatica = false`) pero cuya rúbrica aún no tiene
  `NotaEtapa`, cuando se consulta el ranking, entonces la celda muestra «Pendiente de
  calificación» y no «Prueba incompleta».
- AC-07: Dado un candidato con `NotaEtapa` de `PRUEBA_PUESTO`, cuando se consulta el ranking,
  entonces se muestra la nota numérica y no ninguno de los mensajes de ausencia.
- AC-08: Dado un candidato que todavía no llegó a la etapa técnica y no tiene intento, cuando se
  consulta el ranking de la prueba del puesto, entonces no se lo etiqueta como «Pendiente de
  calificación» por el solo hecho de que `notaEtapa` sea nula.
- AC-09: Dado cualquier ranking o exportación, cuando se busca la copia anterior de los textos,
  entonces no se muestran «Por revisar» ni «sin cerrar» en los lugares alcanzados por este
  cambio.
- AC-10: Dado un error o ausencia de los datos del intento de una fila, cuando se consulta el
  ranking, entonces se conserva la fila y la respuesta no devuelve un error general por ese
  candidato.

## Casos límite y errores

- Datos vacíos o inválidos: una nota ausente no basta por sí sola para declarar «Prueba
  incompleta»; primero debe revisarse el estado del intento. Una nota igual a cero cuenta como
  nota válida y se muestra como número.
- Usuario sin permisos: se mantienen los permisos actuales de consulta del ranking y de
  descarga; este cambio no amplía acceso a postulaciones, entregas ni rúbricas.
- Recarga, reintento o doble envío: volver a abrir el ranking debe recalcular el texto con el
  estado actual del intento y la nota, sin duplicar datos ni cambiar estados.
- Otros casos relevantes:
  - Una prueba entregada manualmente puede permanecer sin nota porque algunos criterios son de
    revisión humana o porque el agente no pudo puntuar una entrega no legible; debe verse como
    «Pendiente de calificación».
  - Si una prueba entregada automáticamente sí tiene `NotaEtapa`, prevalece la nota numérica.
  - Si la postulación está antes de `PRUEBA_PUESTO`, la ausencia de intento no debe confundirse
    con una prueba incompleta.
  - Los textos deben conservar tildes, mayúsculas y escritura exacta: «Pendiente», «Prueba
    incompleta» y «Pendiente de calificación».

## Datos y compatibilidad

- Información que debe guardarse: ninguna nueva. El estado visible se deriva de
  `NotaEtapa.puntaje`, `IntentoPrueba.entregadoEn`, `IntentoPrueba.iniciadoEn`,
  `IntentoPrueba.esEntregaAutomatica` y el estado actual de la postulación.
- Comportamiento para registros existentes: las notas, intentos, entregas y estados existentes
  se conservan. Al abrir un ranking antiguo, se recalcula el texto con esos datos.
- Restricciones de compatibilidad: el contrato del ranking debe entregar al frontend el estado
  de la prueba o los campos equivalentes para diferenciar `INCOMPLETA`,
  `PENDIENTE_CALIFICACION`, `CALIFICADA` y `NO_APLICA`. No se deben reutilizar códigos de
  estado de postulación como si fueran textos de interfaz.
- Si no aplica, indicarlo: no se requieren nuevas tablas, columnas, migraciones ni cambios en
  las reglas de cálculo de notas.

## Verificación

- Actor/rol y datos de prueba necesarios: usuario del panel con permiso para ver el ranking;
  una vacante con prueba del puesto; candidatos con intentos `PENDIENTE`, `EN_CURSO`, entrega
  manual sin nota, entrega automática sin nota, entrega con nota y una postulación que todavía
  no haya llegado a la etapa técnica.
- Recorridos que deben quedar como E2E repetibles: abrir cada ranking y comprobar el texto
  «Pendiente»; consultar la pestaña «Prueba del puesto» con cada estado de intento; descargar
  el Excel desde el corte y confirmar el texto; recargar después de entregar o calificar una
  prueba y comprobar que la etiqueta cambia sin alterar las demás filas.
- Riesgos que QA debe explorar con navegador: que el filtro «Pendiente» cambie accidentalmente
  su conjunto de filas; que el Excel conserve el texto anterior; que una entrega automática se
  confunda con una entrega manual; que una nota cero se trate como ausente; y que una
  postulación anterior a la etapa técnica reciba «Prueba incompleta» indebidamente.
- Resultado que quiero comprobar personalmente: ver «Pendiente» en los filtros, «Prueba
  incompleta» solo para pruebas no terminadas o cerradas automáticamente, «Pendiente de
  calificación» para entregas manuales aún sin nota y la nota numérica para pruebas ya
  calificadas.

## Dependencias y dudas

- Requiere: ajustar el componente del ranking, el texto de la exportación y el contrato o
  adaptador que entrega al frontend el estado de la prueba del puesto. Requiere datos de prueba
  que permitan reproducir una entrega automática por vencimiento.
- Decisiones pendientes: ninguna para el alcance definido.
