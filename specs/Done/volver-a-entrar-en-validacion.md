# Volver a entrar en Validación sin atascar a la persona
Estado: TERMINADO

## Objetivo
Cuando a alguien se le retrocede y se le vuelve a avanzar a Validación, hoy se queda atascado
con «ya existe un registro con postulacion_id X» y Talento no tiene forma de sacarlo desde el
panel. Esta spec hace que vuelva a entrar sin error, conservando su periodo tal como estaba, y
que quede en el paso de Validación que corresponde a ese periodo.

De paso cierra dos huecos del mismo tipo: entrar a Validación o a la prueba del puesto con un
movimiento manual no le crea a la persona lo que necesita en esa etapa.

## Contexto
- Comportamiento actual:
  - **Volver a Validación con «Avanzar».** Al entrar a «Validación · por habilitar», el
    sistema crea el periodo de validación sin mirar si ya existe. La base admite uno por
    postulación (`UNIQUE (postulacion_id)`, V18): el segundo choca y el panel muestra «No
    avanzaron: [Persona] (… ya existe un registro con postulacion_id X)». La persona se queda
    en Simulación y cada «Avanzar» falla igual.
  - **Cómo se llega ahí.** Hace falta retroceder antes. Desde el panel no se puede: el
    movimiento manual solo se usa para descartar. Sí se puede por la API
    (`POST /panel/postulaciones/{id}/transiciones`) y con los scripts. Así le pasó a Ricardo.
  - **Movimiento manual a Validación.** No crea el periodo. La ficha muestra «todavía no
    hay» y habilitar, iniciar, métricas y cerrar responden 404.
  - **Movimiento manual a la prueba.** Mover a «Prueba · turno del candidato» envía el correo
    «tu prueba está disponible», pero no crea la prueba: el candidato abre el enlace y
    encuentra la pantalla vacía. `scripts/invitar.py` ya avisa de esto en su cabecera.
  - **Cómo funciona hoy el periodo.** Nace «por habilitar». Habilitar fija la modalidad, la
    figura contractual, los días y el responsable, y el periodo sigue «por habilitar».
    Iniciar lo pone «en curso» con fecha de inicio y de fin, y mueve a la persona a «turno
    del candidato» si estaba en «por habilitar». Al vencer, el sondeo pasa a «por confirmar»
    solo a quien está en «turno del candidato». Cerrar calcula la nota con las métricas,
    marca el periodo «terminado» y pasa a la persona a «Decisión · por confirmar». Las
    métricas se guardan aparte del periodo.
- Pantallas o procesos afectados: «Avanzar» de la vacante en el panel, el movimiento manual
  por API y el historial de la ficha. Ninguna pantalla cambia.
- Referencias existentes:
  - La prueba del puesto ya reutiliza su intento al volver a entrar:
    `ServicioPruebaImpl.crearAlEntrar` y `crearAlEntrarConPruebaPropia`, con el test
    `VolverAEntrarALaPruebaTest` (PR #57). Es el patrón a seguir.
  - `EntradaEtapaTecnica.crearAlEntrar` crea lo que la vacante rinde en la etapa técnica
    (prueba del editor, plantilla o cuestionario técnico). La usan «Avanzar» y el pase
    automático.
  - Código afectado: `ServicioValidacionImpl.crearAlEntrar` (línea 56) y
    `ServicioPostulacionesPanelImpl.confirmarAvance` y `transicionar`.

## Alcance
- Incluye:
  - Reutilizar el periodo existente al entrar a Validación, sin tocar ninguno de sus datos.
  - Llevar a la persona al paso de Validación que corresponde a su periodo cuando entra a
    «por habilitar», con una coletilla en el historial que lo explica.
  - Crear o reutilizar el periodo cuando se entra a Validación desde otra etapa con un
    movimiento manual.
  - Crear o reutilizar la prueba del puesto o el cuestionario técnico cuando se entra a la
    etapa de la prueba desde otra etapa con un movimiento manual.
  - Tests unitarios, de integración y un E2E.
- Queda fuera:
  - Añadir al panel una forma de retroceder o de mover a mano.
  - Reiniciar o alargar un periodo ya iniciado. Hoy no existe y no se añade.
  - Llevar al paso que corresponde al volver a la prueba. La prueba ya se reutiliza y la
    persona entra a «turno del candidato», como hoy.
  - Movimientos dentro de la misma etapa, por ejemplo de «Validación · turno del candidato»
    a «Validación · por habilitar». No crean ni redirigen nada, como hoy.
  - Qué pasa con el periodo al salir de Validación: se queda como está.
  - Crear de golpe el periodo a quien hoy está en Validación sin él. Se le crea si vuelve a
    entrar.
  - Cambios en la base o en la API.

## Comportamiento esperado

### A. Volver a Validación con «Avanzar»
1. Talento pulsa «Avanzar» para alguien que ya tuvo un periodo de validación.
2. El sistema reutiliza ese periodo tal cual: modalidad, figura contractual, días,
   responsable, quién lo habilitó, fechas y estado. Las métricas tampoco se tocan.
3. La persona entra al paso que corresponde a su periodo:

   | Cómo estaba su periodo | A dónde entra | Qué pasa después |
   |---|---|---|
   | Por habilitar, habilitado o no | Validación · por habilitar | Habilitar e iniciar como siempre. Lo ya habilitado se conserva |
   | En curso, con la fecha de fin en el futuro | Validación · turno del candidato | El reloj sigue sin reiniciarse. Al vencer, el sondeo la pasa a «por confirmar» |
   | En curso, con la fecha de fin ya pasada o igual a ahora | Validación · por confirmar | Completar métricas y cerrar |
   | Terminado | Validación · por confirmar | Revisar las métricas, que siguen ahí, y cerrar. La nota se recalcula con ellas |

4. El historial guarda una sola transición, desde el estado de origen hasta el paso donde
   la persona entró de verdad. El motivo es el que escribió quien avanzó, seguido de una
   coletilla, igual que la de «· sin avisar al candidato»:
   - «· su periodo de validación ya estaba en curso»
   - «· su periodo de validación ya había vencido»
   - «· su periodo de validación ya estaba cerrado»

   Si entra a «por habilitar», no lleva coletilla.
5. El aviso al candidato es el del paso donde entra, como en cualquier transición. Si entra a
   «turno del candidato», recibe el aviso de que es su turno.
6. Si no tenía periodo, se le crea «por habilitar», como hoy.

### B. Entrar a Validación con un movimiento manual
7. Con un movimiento manual desde otra etapa a cualquier paso de Validación, si la persona no
   tiene periodo se le crea «por habilitar». Si ya lo tiene, se reutiliza sin tocarlo.
8. Si el destino es «Validación · por habilitar», se aplica la tabla del paso 3, con su
   coletilla detrás del motivo escrito.
9. Si el destino es «turno del candidato» o «por confirmar», se respeta. Un periodo recién
   creado queda «por habilitar» y se puede habilitar e iniciar desde ahí: iniciar ya funciona
   aunque la persona no esté en «por habilitar». Pone el periodo en curso y no la mueve.

### C. Entrar a la prueba del puesto con un movimiento manual
10. Con un movimiento manual desde otra etapa a cualquier paso de la prueba del puesto, se crea
    o reutiliza lo que la vacante rinde, con las mismas reglas que «Avanzar»
    (`EntradaEtapaTecnica.crearAlEntrar`). El destino elegido se respeta.
11. Si la vacante no tiene listo lo que rinde, el movimiento se rechaza con el mismo mensaje que
    da «Avanzar» (409). La persona no se mueve, no se guarda transición y no sale ningún
    correo.

### Errores
12. Si crear o reutilizar el periodo o la prueba falla, no se guarda nada: ni el periodo, ni la
    transición, ni el correo. La persona sigue donde estaba.
13. Si se envían dos entradas a la vez (dos «Avanzar», o un «Avanzar» y un movimiento manual),
    una gana y la otra recibe un error sin escribir nada. La clave única de la base sigue de
    red: nadie queda con dos periodos.

## Referencia visual
No cambia la interfaz. Tras «Avanzar», el panel ya refresca la tabla y no nombra el destino
(«Avanzaron: …»), así que muestra sin cambios el paso real donde entró la persona. La coletilla
se ve en el historial de la ficha, que ya pinta el motivo de cada paso.

## Criterios de aceptación
- AC-01: Dado alguien con un periodo «por habilitar» ya habilitado (trabajo real, con figura
  contractual, 10 días y responsable) y retrocedido a «Simulación · por confirmar», cuando
  Talento pulsa «Avanzar», entonces entra a «Validación · por habilitar» sin error, con el
  mismo periodo (mismo id) y la habilitación intacta.
- AC-02: Dado un periodo en curso con la fecha de fin en el futuro, cuando la persona vuelve
  con «Avanzar», entonces entra a «Validación · turno del candidato» con la misma fecha de
  inicio y de fin, y el historial muestra «· su periodo de validación ya estaba en curso».
- AC-03: Dado un periodo en curso con la fecha de fin ya pasada, cuando vuelve con «Avanzar»,
  entonces entra a «Validación · por confirmar» con la coletilla «ya había vencido».
- AC-04: Dado un periodo terminado con sus métricas, cuando vuelve con «Avanzar», entonces
  entra a «Validación · por confirmar» con la coletilla «ya estaba cerrado», las métricas se
  ven iguales y, al cerrar, pasa a «Decisión · por confirmar» con la nota de esas métricas.
- AC-05: Dado alguien sin periodo, cuando entra con «Avanzar», entonces se crea «por
  habilitar», como hoy, y el motivo no lleva coletilla.
- AC-06: En los casos AC-01 a AC-04, la postulación tiene un único periodo y ningún dato del
  periodo cambia al volver: la fila es idéntica antes y después.
- AC-07: Dado el caso de AC-02, cuando vence el periodo y corre el sondeo, entonces la persona
  pasa a «Validación · por confirmar» como cualquier otra.
- AC-08: Dado alguien sin periodo, cuando se le mueve a mano desde otra etapa a «Validación ·
  turno del candidato», entonces queda en ese paso con un periodo «por habilitar», y habilitar
  e iniciar funcionan: el periodo pasa a en curso sin mover a la persona.
- AC-09: Dado alguien sin periodo, cuando se le mueve a mano a «Validación · por confirmar»,
  entonces tiene periodo, y completar métricas y cerrar funcionan, sin 404.
- AC-10: Dado alguien con un periodo en curso y la fecha de fin en el futuro, cuando se le
  mueve a mano desde otra etapa a «Validación · por habilitar», entonces entra a «turno del
  candidato» y el motivo escrito lleva detrás la coletilla.
- AC-11: Dado alguien con un periodo terminado, cuando se le mueve a mano desde otra etapa a
  «Validación · turno del candidato», entonces queda en ese paso y el periodo no cambia.
- AC-12: Dado alguien en «Validación · turno del candidato», cuando se le mueve a mano a
  «Validación · por habilitar», entonces se mueve como hoy, sin crear ni redirigir nada.
- AC-13: Dado alguien en Perfil Integral de una vacante con prueba lista, cuando se le mueve a
  mano a «Prueba · turno del candidato», entonces se le crea su prueba (o su cuestionario
  técnico, según la vacante) y el enlace del correo abre esa prueba.
- AC-14: Dado alguien que ya tenía su intento de prueba, cuando se le mueve a mano desde otra
  etapa a la prueba, entonces se reutiliza con las reglas de hoy: si no la abrió, toma la
  versión y la fecha de cierre de la vacante, salvo que tenga plazo propio; si ya la abrió, no
  se toca.
- AC-15: Dada una vacante sin prueba lista, cuando se mueve a alguien a mano a la prueba,
  entonces la API responde 409 con el mensaje de «Avanzar», la persona no se mueve, no hay
  transición y no sale correo.
- AC-16: Si falla la creación del periodo o de la prueba, no queda nada guardado: ni periodo,
  ni transición, ni correo.
- AC-17: `./mvnw verify` pasa, con los tests nuevos. `FlujoSimulacionValidacionIT`,
  `FlujoPruebaIT`, `FlujoPruebaPropiaIT` y `FlujoCuestionarioTecnicoIT` siguen verdes sin
  cambiar lo que comprueban. El E2E nuevo pasa.

## Casos límite y errores
- Datos vacíos o inválidos: el motivo sigue siendo obligatorio, como hoy. La coletilla no
  cuenta como motivo.
- Usuario sin permisos: sin cambios. «Avanzar» exige `confirmar_avance` y el movimiento manual
  `mover_postulacion`. Crear el periodo o la prueba forma parte de entrar y no pide los permisos
  de validación ni de prueba, como hoy.
- Recarga, reintento o doble envío: dos entradas a la vez, ver el paso 13. Si se vuelve a
  pulsar «Avanzar» después de un éxito, avanza desde el paso nuevo, como hoy.
- Otros casos relevantes:
  - Una vacante archivada o eliminada se rechaza como hoy, antes de crear nada.
  - De un estado final (contratado, no continúa, cerrada) no se sale, como hoy.
  - Mientras la persona está fuera de Validación, su periodo en curso no se toca. El sondeo ya
    lo ignora porque ella no está en «turno del candidato».
  - El pase automático solo llega hasta la prueba y no cambia.

## Datos y compatibilidad
- Información que debe guardarse: nada nuevo. Sin migraciones; la clave única de `validacion`
  se queda.
- Comportamiento para registros existentes: quien hoy está en Validación sin periodo sigue sin
  él hasta que vuelva a entrar. Por ejemplo, `scripts/sembrar-datos-de-prueba.py` lleva a
  alguien a «Validación · por confirmar» a mano.
- Restricciones de compatibilidad:
  - La API no cambia: mismos endpoints, cuerpos y respuestas. Sí cambian dos resultados:
    - Un movimiento manual a la prueba en una vacante sin prueba lista pasaba y ahora da
      409. Es justo el caso que hoy deja la pantalla vacía.
    - Un movimiento manual a la prueba o a Validación ahora crea el intento o el periodo.
  - Los tests de integración y el sembrador mueven a mano a la prueba en vacantes con
    plantilla asignada, que cuenta como prueba lista. Con este cambio se les crea el intento y
    siguen funcionando. `cribar-cv-sin-mover.py` devuelve a cada persona a su etapa: si era la
    prueba o Validación, se reutiliza lo que ya tenía.

## Verificación
- Actor/rol y datos de prueba necesarios: Talento con `confirmar_avance` y
  `mover_postulacion`, una vacante con prueba asignada, y postulaciones llevadas hasta cada
  caso por la API.
- Tests unitarios:
  - `ServicioValidacionImplTest`: sin periodo, se crea «por habilitar». Con periodo, un test
    por cada fila de la tabla del paso 3, comprobando el paso devuelto y que el periodo no se
    guarda modificado.
  - `ServicioPostulacionesPanelImplTest`:
    - «Avanzar» entra al paso que devuelve validación, con su coletilla.
    - Un movimiento manual desde otra etapa a Validación crea o reutiliza el periodo y respeta
      el destino, salvo «por habilitar».
    - Un movimiento manual dentro de la misma etapa no crea nada.
    - Un movimiento manual a la prueba llama a la entrada técnica, y sin prueba lista da 409
      sin guardar transición.
- Tests de integración (corren con `verify`, no con `test`):
  - En `FlujoSimulacionValidacionIT`, con una postulación propia que no altere el recorrido
    ordenado de la existente:
    1. Llevarla a Validación, habilitar e iniciar.
    2. Retrocederla a mano a «Simulación · por confirmar» y pulsar «Avanzar».
    3. Comprobar que entra a «turno del candidato» con el mismo id de periodo, las mismas
       fechas, una sola fila y la coletilla en `transicion_estado`.
    4. Forzar el vencimiento y correr el sondeo: pasa a «por confirmar».

    Otro test cubre el periodo terminado: vuelve a «por confirmar» con sus métricas.
  - En `FlujoSimulacionValidacionIT` o en un IT nuevo: un movimiento manual a la prueba crea
    el intento, y en una vacante sin prueba da 409 y la persona no se mueve. No en
    `FlujoPruebaIT` ni en `FlujoPruebaPropiaIT`: la spec paralela de la fuga de preguntas
    puede tocarlos.
- Recorridos que deben quedar como E2E repetibles: uno nuevo en el frontend,
  `herramientas/e2e/51-volver-a-validacion.spec.ts`. El 50 ya está ocupado.
  - Prepara por API: lleva a una persona a Validación, habilita e inicia su periodo, y la
    retrocede a «Simulación · por confirmar».
  - En el panel: la marca en la pestaña de Simulación y pulsa «Avanzar».
  - Comprueba:
    - Sale «Avanzaron: …» y no sale «No avanzaron».
    - La persona aparece en la pestaña de Validación en «turno del candidato».
    - El historial de su ficha muestra la coletilla.
- Riesgos que QA debe explorar con navegador: «Avanzar» repetido sobre la misma persona; los
  cuatro estados del periodo; que el correo «tu prueba está disponible» ya no salga sin prueba.
- Resultado que quiero comprobar personalmente: en el panel, que alguien retrocedido avanza a
  Validación sin error y que su historial lo explica. Requiere iniciar sesión, así que lo
  comprueba el usuario.

## Dependencias y dudas
- Requiere: nada.
- Decisiones pendientes: ninguna; acordadas el 02/10/2026. Lo único fijado por defecto es el
  rechazo del paso 11 (mover a mano a la prueba sin prueba lista), por coherencia con
  «Avanzar».
