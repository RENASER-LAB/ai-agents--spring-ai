# Ver y ajustar el plazo de la prueba del puesto

Estado: TERMINADO

Estado previo (referencia histórica): lista para implementar

## Objetivo

Que quien administra una vacante vea con claridad **qué plazo rige hoy** para la prueba de la
etapa técnica (para toda la vacante y para cada candidato) y pueda modificarlo sin toparse
con controles que no aplican o con errores en bruto. Cambiar el plazo ya es posible desde el
panel; lo que falta es verlo y que el control respete cada caso.

## Contexto

- Comportamiento actual:
  - **Ya existen los endpoints y los controles.** `POST /vacantes/{id}/cierre-prueba` fija
    cuándo cierra la prueba para toda la vacante (mueve también los intentos abiertos, salvo
    los de quien tenga fecha propia) y `POST /postulaciones/{id}/prueba/plazo` fija la fecha
    de una sola persona, que manda sobre la de la vacante. En el panel: `CierreDeLaVacante`
    (dentro del desplegable «Plazos de la prueba» de la configuración de la vacante) y
    `PlazoDeUnaPersona` (al final de la ficha del candidato, en la etapa Prueba del puesto),
    ambos en `src/panel/vacantes/CierreDePrueba.tsx` del repositorio `RenaserOsPostulantes`.
    El motivo es obligatorio y queda en la auditoría. La nota «falta frontend» está
    desactualizada.
  - **No se ve la fecha vigente.** La vacante guarda `pruebaCierraEn`, pero `VacantePanel` no
    la trae; y la fecha propia de una persona (`intento_prueba.vence_en`, `plazo_propio`) no
    sale en la ficha. Los dos campos de fecha empiezan vacíos y la pantalla lo admite en un
    comentario. Quien entra no sabe qué está cambiando.
  - **El control de la vacante se ofrece o se oculta con una regla vieja.** Solo se oculta con
    la vacante cerrada o sin plantilla de prueba, con un texto genérico. Pero el backend ya
    distingue más casos: una vacante que rinde el **cuestionario técnico** no tiene fecha de
    cierre (su tiempo son los minutos de la vacante) y responde con un 409 explicativo; una
    plantilla **CRONOMETRADA** rechaza hoy una fecha para todos (400, «anularía el reloj»).
    Esa regla quedó vieja: cuando un candidato empieza su prueba, el servidor ya calcula el
    vencimiento por el reloj y se queda con el plazo **que caiga antes** entre el reloj y la
    fecha de la vacante (`ServicioPruebaImpl.iniciar`), así que la fecha no anula el reloj.
  - **El control por persona no distingue el instrumento.** Se muestra siempre en la etapa
    Prueba del puesto, pero el cuestionario técnico no usa `intento_prueba`: para esas
    postulaciones el backend contestaría «Prueba del puesto no encontrada». Tampoco distingue
    a quien aún no llegó a la etapa (sin intento) ni a quien ya entregó (el backend rechaza
    con «ya se entregó»).
- Pantallas o procesos afectados: configuración de la vacante («Plazos de la prueba»); ficha
  del candidato en la etapa Prueba del puesto; `VacantePanel` y un nuevo dato de plazo por
  candidato en el backend.
- Referencias existentes: [plantilla de spec](../plantilla-spec.md),
  [estados de postulación](../../docs/03-ESTADOS-POSTULACION.md) (etapa 3),
  [prueba del puesto](../../docs/PRUEBA-DEL-PUESTO.md).

## Alcance

- Incluye:
  - **Mostrar el plazo vigente de la vacante:** fecha y hora de cierre si existe, o la regla
    que rige si no (minutos desde que cada candidato empieza, o los días de la plantilla).
  - **Precargar** el campo de fecha con la vigente y mostrar debajo cuántos intentos
    abiertos moverá el cambio y cuántos quedan fuera por tener fecha propia (dato que hoy solo
    se conoce después de guardar).
  - **Mostrar el plazo vigente de cada candidato** en su ficha: cuándo le cierra, de dónde
    sale (la de la vacante, la del reloj o una fecha puesta a mano) y su estado (no llegó a
    la etapa, no ha empezado, en curso, entregada).
  - **Ofrecer solo el control que aplica** a cada vacante y candidato, con el motivo dicho en
    palabras cuando no aplica:
    - vacante cerrada;
    - vacante sin instrumento elegido;
    - vacante con cuestionario técnico (tiempo = minutos de la vacante);
    - candidato sin intento, o que ya entregó.
  - Para el cuestionario técnico, en lugar de la fecha se muestra el tiempo vigente (los
    minutos de la vacante) con el enlace al ajuste que ya existe para cambiarlos.
- Queda fuera:
  - El plazo de la etapa **Perfil integral** (14 días de la evaluación): no tiene endpoint
    para cambiarlo; sería otra spec.
  - Cambiar cómo se calcula o se hace cumplir un plazo, el barrido de vencidos, la entrega
    automática o el desbloqueo de intentos ya entregados.
  - Cambiar el permiso de quien mueve fechas (`elegir_plantilla_prueba` en la vacante;
    `mover_postulacion` en la persona) ni el motivo obligatorio.
  - Cambiar el modelo de datos o añadir migraciones.
  - Hacer editable a la vez el plazo de un lote de candidatos (el de la vacante ya cubre el
    caso común).

## Comportamiento esperado

### Configuración de la vacante

1. Al abrir «Plazos de la prueba», la pantalla muestra una línea con lo que rige hoy, según
   el caso:
   - **Plantilla PLAZO_ABIERTO con fecha:** «Cierra el domingo 21 sep 2026, 23:59
     (hora de tu equipo)».
   - **Plantilla PLAZO_ABIERTO sin fecha:** «Sin fecha para todos: a cada persona le cierra
     N días después de que empieza».
   - **Plantilla CRONOMETRADA sin fecha:** «Cronometrada: N minutos desde que cada persona
     empieza, sin fecha límite para empezar».
   - **Plantilla CRONOMETRADA con fecha:** «Cronometrada: N minutos desde que cada persona
     empieza, y nadie puede seguir después del domingo 21 sep 2026, 23:59. Rige lo que caiga
     antes». El campo de fecha general **sí se ofrece** (ver «Datos y compatibilidad»).
   - **Cuestionario técnico:** «Esta vacante rinde el cuestionario técnico: cada persona tiene
     N minutos desde que lo abre». No se ofrece fecha; se ofrece el enlace al ajuste de
     minutos.
   - **Vacante cerrada o sin instrumento elegido:** se explica por qué no se puede fijar (sin
     texto genérico) y no se ofrece el control.
2. Con fecha vigente, el campo de fecha llega precargado con ella. Al cambiarla, antes de
   guardar se ve: «Se moverá el cierre de X intentos abiertos; Y quedan como están porque
   tienen fecha propia».
3. Se puede **quitar** la fecha (vuelve a contarse desde que cada uno empieza) con motivo,
   como hoy.
4. Tras guardar, la línea de «lo que rige hoy» se actualiza sin recargar la página y muestra
   el resultado (movidos / con fecha propia) como ahora.

### Ficha del candidato (etapa Prueba del puesto)

5. Al abrir la ficha, junto a «Plazo», se ve el estado y el plazo de esa persona:
   - **Aún no llegó a la etapa:** «Todavía no tiene prueba. El plazo se fija cuando llegue»;
     no hay control.
   - **Sin empezar:** «Le cierra: fecha/hora (la de la vacante)» o, si no hay, «su plazo
     empieza a contar cuando la abra».
   - **En curso:** «Le cierra: fecha/hora» + origen («de la vacante», «por el reloj» o «fecha
     puesta a mano el …»).
   - **Ya entregó:** «Entregó el …. El plazo ya no cambia»; el control queda deshabilitado
     con esa razón.
6. Con la prueba sin entregar, el campo de fecha llega precargado con la vigente. Fijar una
   fecha nueva sigue exigiendo motivo, y la fecha queda marcada como propia («no se moverá si
   cambia la de la vacante»), cosa que la pantalla dice antes de guardar.
7. Para vacantes con cuestionario técnico, la ficha no muestra el control de fecha por
   persona y explica que el tiempo del cuestionario son los minutos de la vacante.
8. Los errores del backend se muestran en español y con su causa («Esa fecha ya pasó: al
   candidato se le entregaría la prueba sola», «Esta prueba ya se entregó…»), sin mensajes en
   inglés ni «no encontrada».

## Referencia visual

Los bocetos fijan la **disposición**, no medidas ni colores. Rigen el `DESIGN.md` del
repositorio `RenaserOsPostulantes` (acento único violeta `--activo`, «EX · Portal del
candidato»), además de los componentes de `CierreDePrueba.tsx` ya existentes. **No** el
`DESIGN.md` de este repositorio (`RENASER-RECLUTAMIENTO`): describe un sistema distinto
(«RENASER OS», monocromático) y no gobierna el panel vivo.

**1. Configuración de la vacante · «Plazos de la prueba» (PLAZO_ABIERTO con fecha)**

```
▾ Plazos de la prueba
Cierra el domingo 21 sep 2026, 23:59 (hora de tu equipo)

Fecha de cierre  [21/09/2026 23:59]
Motivo (obligatorio)  [                    ]
                                    [Guardar]  [Quitar fecha]
Se moverá el cierre de 8 intentos abiertos; 2 quedan como están (fecha propia)
```

**2. PLAZO_ABIERTO sin fecha**

```
▾ Plazos de la prueba
Sin fecha para todos: a cada persona le cierra 5 días después de que empieza
Fecha de cierre  [                    ]
```

**3. CRONOMETRADA, sin fecha y con fecha**

```
▾ Plazos de la prueba
Cronometrada: 30 minutos desde que cada persona empieza,
sin fecha límite para empezar
Fecha límite  [                    ]
```

```
▾ Plazos de la prueba
Cronometrada: 30 minutos desde que cada persona empieza,
y nadie puede seguir después del domingo 21 sep 2026, 23:59
Fecha límite  [21/09/2026 23:59]
```

**4. Cuestionario técnico (sin campo de fecha)**

```
▾ Plazos de la prueba
Esta vacante rinde el cuestionario técnico:
cada persona tiene 60 minutos desde que lo abre
[Ajustar los minutos →]
```

**5. Vacante cerrada o sin instrumento elegido (sin control)**

```
▾ Plazos de la prueba
La vacante está cerrada, así que su prueba ya no admite una fecha nueva.
```

**6. Ficha del candidato, los cuatro estados**

```
Plazo: Todavía no tiene prueba. El plazo se fija cuando llegue.

Plazo: Le cierra el 21 sep 2026, 23:59 (de la vacante)
Fecha propia  [                    ]  Motivo  [            ]  [Guardar]

Plazo: Le cierra el 21 sep 2026, 23:59 (fecha puesta a mano el 14 sep)

Plazo: Entregó el 20 sep 2026, 18:32. El plazo ya no cambia.
```

**Estados a dibujar:** cada uno de los seis bloques de arriba, más el error del backend
(fecha pasada, prueba ya entregada) y el botón deshabilitado mientras guarda.

**Móvil:** el desplegable «Plazos de la prueba» y la ficha del candidato se apilan a ancho
completo; el campo de fecha usa el selector nativo del sistema.

**Queda a criterio de quien implementa:** espaciado, iconos y animaciones, siempre dentro del
`DESIGN.md` de `RenaserOsPostulantes` y sin cambiar los textos ni el comportamiento de la
spec.

## Criterios de aceptación

- AC-01: Dada una vacante con plantilla PLAZO_ABIERTO y fecha de cierre, cuando se abre
  «Plazos de la prueba», entonces se lee esa fecha y el campo llega precargado con ella.
- AC-02: Dada una vacante PLAZO_ABIERTO sin fecha, cuando se abre el desplegable, entonces
  dice que no hay fecha para todos y cuántos días tiene cada persona desde que empieza.
- AC-03: Dada una vacante con plantilla CRONOMETRADA (30 minutos), cuando se le fija una
  fecha de cierre, entonces se guarda sin error y el desplegable muestra los minutos y la
  fecha.
- AC-03b: Dada esa vacante con fecha de cierre el domingo, cuando un candidato abre la prueba
  el sábado, entonces le cierra a los 30 minutos; y cuando otro la abre a 10 minutos del
  domingo 23:59, entonces le cierra a esa hora (rige lo que caiga antes); y cuando alguien
  intenta abrirla después de la fecha, entonces se le dice que el tiempo se agotó.
- AC-04: Dada una vacante con cuestionario técnico, cuando se abre el desplegable, entonces
  se ven los minutos de la vacante, no se ofrece fecha y hay un enlace al ajuste de minutos.
- AC-05: Dada una vacante cerrada, cuando se abre el desplegable, entonces se explica que ya
  no admite cambios y no hay control.
- AC-06: Dada una vacante sin instrumento elegido, cuando se abre el desplegable, entonces
  se explica qué hay que elegir antes y no hay control.
- AC-07: Dada una vacante con 10 intentos abiertos, 2 de ellos con fecha propia, cuando se
  escribe una fecha nueva, entonces antes de guardar se ve «se moverán 8; 2 quedan por
  tener fecha propia», y tras guardar el resultado coincide.
- AC-08: Dada una fecha vigente, cuando se quita con motivo, entonces la línea de «lo que
  rige hoy» pasa a decir que no hay fecha para todos.
- AC-09: Dado un candidato que aún no llegó a la etapa, cuando se abre su ficha, entonces se
  dice que todavía no tiene prueba y no hay control de fecha.
- AC-10: Dado un candidato con la prueba en curso y fecha de la vacante, cuando se abre su
  ficha, entonces se ve cuándo le cierra y que es «de la vacante».
- AC-11: Dado un candidato al que se le puso una fecha a mano, cuando se abre su ficha,
  entonces se ve «fecha puesta a mano» con su fecha, y guardar una nueva pide motivo.
- AC-12: Dado un candidato que ya entregó, cuando se abre su ficha, entonces se ve cuándo
  entregó y el control está deshabilitado con la razón.
- AC-13: Dado un candidato de una vacante con cuestionario técnico, cuando se abre su ficha
  en la etapa Prueba del puesto, entonces no se ofrece fecha por persona y se explica por qué.
- AC-14: Dada una fecha ya pasada, cuando se intenta guardar, entonces se avisa en el campo
  con el texto del backend y no se modifica nada.
- AC-15: Dado un usuario sin el permiso correspondiente, cuando intenta guardar, entonces ve
  el aviso de permiso ya existente y la fecha vigente no cambia.
- AC-16: Dado el cambio de fecha en cualquiera de las dos pantallas, cuando se consulta la
  auditoría, entonces existe el registro con fecha anterior, nueva y motivo (como hoy).
- AC-17: Dada la zona horaria del navegador, cuando se muestra o se guarda una fecha,
  entonces la hora escrita, la mostrada y la guardada en UTC son la misma (se conservan las
  pruebas con fechas literales de `CierreDePrueba.test.tsx`).

## Casos límite y errores

- Datos vacíos o inválidos: fecha vacía sin quitar, motivo vacío, fecha inexistente o pasada:
  error en el campo, sin llamada o con el texto del backend; ya funciona así y se mantiene.
- Usuario sin permisos: vacante `elegir_plantilla_prueba`; persona `mover_postulacion`.
  Quien no puede **cambiar** el plazo igualmente **ve** el vigente, en modo solo lectura, si
  puede abrir la ficha o la vacante.
- Recarga, reintento o doble envío: el botón se deshabilita mientras guarda; una segunda
  pestaña que ya cambió la fecha se corrige al recargar, porque la fecha vigente sale del
  servidor y no de la memoria.
- Otros casos relevantes:
  - **Dos personas cambiando el plazo a la vez:** gana la última; la pantalla muestra la
    vigente que devolvió el servidor tras guardar.
  - **Candidato con fecha propia y luego cambia la de la vacante:** su fecha no se mueve, y
    la ficha debe seguir diciendo «puesta a mano».
  - **Vacante que cambia de instrumento** después de haber fijado una fecha: la fecha
    guardada no se muestra como vigente si el instrumento actual no la usa.
  - **Datos antiguos** sin `plazo_propio` o con `vence_en` nulo se muestran como «sin fecha
    todavía», no como un error.

## Datos y compatibilidad

- Información que debe guardarse: ninguna nueva; se leen datos que ya existen
  (`vacante.prueba_cierra_en`, `intento_prueba.vence_en`, `plazo_propio`, `iniciado_en`,
  `entregado_en`, modalidad de la versión de plantilla, minutos de la vacante).
- Backend:
  - Añadir a `VacantePanel` (o a la consulta de la configuración) `pruebaCierraEn`
    (`Instant`, anulable), `modalidadPrueba` (`CRONOMETRADA` | `PLAZO_ABIERTO` | nulo) y los
    minutos/días vigentes, con la información de intentos abiertos con y sin fecha propia.
  - Nuevo `GET /postulaciones/{id}/prueba/plazo` (permiso `abrir_ficha_candidato`, como la
    lectura de respuestas) que devuelve: `existeIntento`, `venceEn`, `origen`
    (`VACANTE` | `RELOJ` | `PROPIO`), `iniciadoEn`, `entregadoEn`, `instrumento`.
  - En `definirCierrePrueba` (`ServicioVacantesPanelImpl`), **quitar la regla que rechaza una
    fecha sobre una plantilla CRONOMETRADA** y su prueba
    (`ServicioVacantesPanelImplTest`, «sobre una prueba cronometrada no se fija fecha»). Se
    conservan las demás validaciones (vacante cerrada, fecha pasada, vacante sin plantilla).
    Ese es el único cambio en los `POST` existentes; el cálculo de `iniciar` no se toca.
  - Actualizar `EntradaEtapaTecnica` / documentación donde se diga que una cronometrada no
    admite fecha.
- Frontend (`RenaserOsPostulantes`): consumir lo anterior en `CierreDePrueba.tsx`, sustituir
  la regla de visibilidad de `Vacante.tsx` (cerrada / sin plantilla) por la de los cinco
  casos y ocultar `PlazoDeUnaPersona` según instrumento y estado; actualizar el comentario
  de «no hay GET de la fecha vigente» y las pruebas.
- Comportamiento para registros existentes: sin cambios; solo pasan a mostrarse.
- Restricciones de compatibilidad: campos nuevos solo añaden al contrato; el panel debe
  tolerar su ausencia (respuesta de un backend anterior) mostrando «sin dato».

## Verificación

- Actor/rol y datos de prueba necesarios: un rol con `elegir_plantilla_prueba` y
  `mover_postulacion` y otro sin ellos; cuatro vacantes de prueba: PLAZO_ABIERTO con y sin
  fecha, CRONOMETRADA, y con cuestionario técnico; una cerrada; candidatos en cada estado
  (sin intento, sin empezar, en curso, entregado, con fecha propia).
- Recorridos que deben quedar como E2E repetibles:
  1. Fijar fecha a una vacante PLAZO_ABIERTO, comprobar que se lee vigente y que la ficha de
     un candidato en curso muestra esa fecha «de la vacante».
  2. Ponerle fecha propia a un candidato, cambiar la de la vacante y comprobar que la suya
     no se movió y que el aviso previo contó bien.
  3. Vacante cronometrada con fecha: el candidato que abre temprano tiene sus minutos
     completos y el que abre cerca de la fecha cierra a la fecha. Vacante con cuestionario
     técnico: no se ofrece fecha y se lee la razón.
  4. Candidato entregado: control deshabilitado.
- Riesgos que QA debe explorar con navegador: zona horaria alrededor de medianoche y cambio
  de horario; precarga del campo con hora local exacta; el desplegable con la vacante
  recién guardada; permisos de solo lectura.
- Resultado que quiero comprobar personalmente: en una vacante real, abrir «Plazos de la
  prueba» y ver la fecha que rige, cambiarla y ver la ficha de un candidato mostrar la
  nueva; hay login de por medio, así que lo mira el usuario.

## Dependencias y dudas

- Requiere: cambios en los dos repositorios (backend `RENASER-RECLUTAMIENTO`, frontend
  `RenaserOsPostulantes`). Se apoya en los `POST` de plazo que ya existen.
- Decisiones pendientes: ninguna.
