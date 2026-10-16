# La nota mínima del Perfil Integral para pasar sola a la prueba
Estado: TERMINADO

## Objetivo

Que en las vacantes que avanzan solas solo pase a la prueba del puesto quien lo merece. Hoy,
con «Calificar y avanzar sola», todo el que entrega el banco pasa a la prueba en ese mismo
momento, sin mirar la nota. Desde esta spec, el pase espera a que la IA termine la nota del
Perfil Integral y solo se da si llega a la **nota mínima** de la vacante (60 por defecto). Quien
no llega no se descarta: se queda donde está y una persona del equipo decide.

Es el primer filtro por nota. Más adelante habrá otro sobre la nota ponderada, tras la prueba
del puesto (spec aparte).

## Contexto

- **Comportamiento actual** (`docs/03-ESTADOS-POSTULACION.md`, «La vacante que avanza sola»):
  - El interruptor «Calificar y avanzar sola» está en la configuración de la vacante (la
    tuerca), en «Cómo avanza esta vacante» (`ConfiguracionDeLaVacante`,
    `src/panel/vacantes/Vacante.tsx`). Su texto dice que cada persona «llega hasta la prueba del
    puesto sin que nadie intervenga».
  - **La prueba al instante** (`specs/Done/la-prueba-al-instante-y-avisos-de-etapa.md`, Parte A,
    07/10/2026): al entregar el banco, la postulación pasa en ese momento de «calificando» a «por
    confirmar» y a la prueba (`PaseAutomaticoAlInstante` → `PaseAutomatico.alInstante`). Sin
    banco, igual al postular. La IA califica por detrás.
  - **El pase tras calificar**: cuando la IA termina (`PuenteCalificacionIaImpl.cerrarPerfilIntegral`),
    se guardan la nota y el grupo, la postulación pasa a «por confirmar» si sigue en el Perfil
    Integral y se publica `RetratoTerminado`, que lleva a `PaseAutomatico.avanzarSiToca`. Hoy
    solo sirve a quien no pudo pasar al instante.
  - **Ninguna guarda mira la nota** (`laQueLoDejaAvanzar`): pase encendido, vacante viva, banco
    entregado e instrumento montado.
  - **La nota del Perfil Integral** se guarda en `nota_etapa` (`PERFIL_INTEGRAL`), sobre 100 con
    dos decimales. Al entregar un banco clásico se escribe una nota **provisional** solo con lo
    cerrado; la completa sale de `cerrarPerfilIntegral`. Puede salir nula (abiertas sin nota, o
    pesos que no pesan nada que se pueda llenar).
  - **La nota cambia después** con un ajuste a mano o una recalificación
    (`recalcularSinMover`), que hoy no mueve a nadie ni avisa de nada.
  - **`vacante.nota_minima`** (`numeric(5,2)`, V5) existe y ningún código la lee ni la escribe.
  - **El texto legal** (consentimiento `PLATAFORMA` 1.0, V54, y la página pública
    `src/paginas/privacidad/PoliticaPublica.tsx`) dice que el sistema hace tres cosas solo «y
    ninguna de las tres depende de una nota», la tercera «me pasa a la etapa siguiente sin
    esperar a nadie». Con esta spec, esa tercera sí depende de una nota.
- **Pantallas o procesos afectados:**
  - Panel: la tuerca de la vacante y la ficha del candidato.
  - Portal: el aviso al entregar el banco.
  - Backend: el pase automático, el cierre de la nota, el recálculo y el texto legal.
- **Referencias:** `docs/03-ESTADOS-POSTULACION.md`,
  `specs/Done/la-prueba-al-instante-y-avisos-de-etapa.md`, `specs/novedades-por-whatsapp.md`
  (el «felicidades» por WhatsApp sale del mismo paso a la prueba).

## Alcance

**Incluye:**

- La nota mínima por vacante, configurable en la tuerca, de 0 a 100 y 60 por defecto.
- Que el pase automático a la prueba del puesto espere la nota completa del Perfil Integral y
  exija que llegue a la mínima.
- Que la prueba al instante deje de existir: con el interruptor encendido, el pase siempre
  espera la nota.
- Que quien suba a la mínima por un ajuste o una recalificación, mientras espera, pase solo.
- Los textos que hoy prometen la prueba o dicen que el pase no mira la nota: el interruptor,
  el aviso del portal al entregar, la documentación y el texto legal.
- En la ficha del panel, por qué espera alguien que no llegó.

**Queda fuera:**

- El umbral sobre la nota ponderada tras la prueba del puesto (spec aparte, más adelante).
- Vacantes sin «Calificar y avanzar sola»: el equipo sigue confirmando a mano, como hoy, y la
  mínima no bloquea ni avisa en «Avanzar».
- Cualquier «No continúa» automático por nota. Nadie se descarta solo.
- Mover a quien ya está en la prueba si su nota baja.
- Los avisos por WhatsApp (`specs/novedades-por-whatsapp.md`): el correo y la campana siguen
  como hoy.

## Comportamiento esperado

### El recorrido del candidato (vacante con «Calificar y avanzar sola»)

1. **Responde el banco.** No hay mínimo para responderlo: lo responden todos.
2. **Lo entrega.** La postulación queda en «Estamos calificando tus respuestas» y la IA se
   encola, como hoy. **Ya no pasa a la prueba en ese momento.** El aviso del portal dice
   «Evaluación entregada. Te avisaremos por correo y en la campana cuando haya novedades.».
3. **La IA termina la nota** (normalmente unos minutos). La postulación pasa a «por confirmar»
   como hoy, y entonces:
   - **Con 60 o más** (o la mínima de esa vacante): pasa sola a la prueba del puesto, se le crea
     lo que va a rendir y recibe el correo `PRUEBA_DISPONIBLE` y el aviso de la campana, como
     hoy. El historial dice «Pase automático: nota del Perfil Integral 72,40 (mínima 60)».
   - **Con menos:** se queda en «Tu perfil está en revisión». No recibe nada. Una persona del
     equipo decide si la pasa con «Avanzar», como hoy.
   - **Sin nota completa** (la IA falló, se agotó el saldo, CV ilegible sin banco, abiertas sin
     nota o pesos que no pesan nada): se queda en revisión, igual que con menos de la mínima.
4. **Sin banco** (vacante que solo mira el CV): al postular, si cumple los requisitos, se queda
   en «por confirmar» mientras la IA califica el CV, y al terminar se aplica el punto 3 con esa
   nota.

### La nota que cuenta

5. **Solo la completa.** Cuenta la nota que guarda el cierre del Perfil Integral, con el retrato
   ya hecho. La provisional que se escribe al entregar un banco clásico nunca dispara el pase.
6. **«Llega» es 60 o más**, comparando la nota con sus dos decimales: 59,99 no llega y 60,00 sí.
7. **Si la nota cambia mientras espera.** Si una persona ajusta una respuesta o recalifica, y la
   postulación sigue en «por confirmar» en una vacante con el interruptor encendido, se vuelve a
   mirar: si ahora llega, pasa sola con el mismo correo y la misma campana, y el historial lo
   dice («Pase automático tras recalcular: nota 63,10 (mínima 60)»). Si ya está en la prueba, no
   se le toca aunque la nota baje.
8. **Las demás guardas siguen**: vacante archivada o eliminada, sin prueba montada, postulación
   cerrada o movida por una persona. En todos esos casos no hay pase, como hoy.

### La configuración de la vacante (la tuerca)

9. En «Cómo avanza esta vacante», debajo del interruptor, un campo **«Nota mínima para pasar el
   perfil integral»**:
   - número entero de 0 a 100, con 60 por defecto;
   - ayuda: «Quien llegue a esta nota en el Perfil Integral pasa sola a la prueba del puesto.
     Quien no llegue espera a que alguien del equipo decida.»;
   - solo se puede editar con el interruptor encendido; apagado, se ve desactivado;
   - se guarda al salir del campo, con el mismo aviso de error que el interruptor;
   - con la vacante cerrada no se edita.
10. El texto del interruptor encendido pasa a decir: «Encendido: cada persona que postule se
    califica sola y, si llega a la nota mínima, pasa a la prueba del puesto sin que nadie
    intervenga. Tú decides el resto, cuando ya tienes sus notas».
11. **Cambiar la mínima no mueve a nadie que ya espera.** Vale para las notas que terminen desde
    ese momento y para los recálculos del punto 7. A quienes ya esperaban los mueve el equipo,
    también en lote.

### El panel

12. En la ficha del candidato, cuando espera en «por confirmar» en una vacante con el interruptor
    encendido y su nota no llega, junto al estado: «Nota 58,20: no llega a la mínima de la
    vacante (60)». Sin nota completa: «Sin nota completa: revisa el Perfil Integral».
13. La nota «en camino» de la ficha y el ranking (de la spec de la prueba al instante) sigue
    igual mientras la IA trabaja.

### Textos y documentación

14. **Texto legal.** Se publica la versión 1.1 del consentimiento `PLATAFORMA` y se actualiza la
    página pública con la tercera cosa reescrita: «me pasa a la etapa siguiente sin esperar a
    nadie cuando la empresa configuró su vacante para avanzar sola y mi nota del Perfil Integral
    llega a la mínima que fijó; si no llega, no me descarta: una persona revisa mi caso». La frase
    «ninguna de las tres depende de una nota» pasa a «solo la tercera depende de una nota, y
    nunca para descartarme». La 1.0 no se toca: quien la aceptó queda ligado a ella.
15. **`docs/03-ESTADOS-POSTULACION.md`**: la tabla de «La vacante que avanza sola», «El pase
    nunca miró la nota» y la sección «La prueba al instante» se reescriben con esta regla.

## Referencia visual

Solo cambia la configuración de la vacante: un campo nuevo dentro de un grupo que ya existe.
Se reutilizan los controles de esa pantalla. Maqueta:
https://claude.ai/artifact/VQpVCBbGzviFHQseZhz7f9, fila «Panel · la nota mínima del Perfil
Integral» (la tuerca encendida y apagada, y la ficha). Aprobada por el usuario el 12/10/2026.

```
Cómo avanza esta vacante
  Calificar y avanzar sola   [● Encendido: cada persona que postule se califica sola y,
                                si llega a la nota mínima, pasa a la prueba del puesto…]
  Nota mínima para pasar el perfil integral   [ 60 ] / 100
  Quien llegue a esta nota en el Perfil Integral pasa sola a la prueba del puesto.
  Quien no llegue espera a que alguien del equipo decida.
```

- **Estados:** interruptor apagado (campo desactivado), guardando, error («No se pudo guardar la
  nota mínima») y valor fuera de rango («Escribe un número de 0 a 100»).
- **Escritorio y móvil:** igual que el resto de la configuración.
- **Diseño:** `docs/PANEL.md` y `DESIGN.md` del frontend.

## Criterios de aceptación

- **AC-01:** Dada una vacante con el interruptor encendido y mínima 60, cuando el candidato
  entrega el banco, entonces queda en «calificando», no tiene prueba y el portal dice
  «Evaluación entregada. Te avisaremos por correo y en la campana cuando haya novedades.».
- **AC-02:** Dado ese candidato, cuando la IA cierra el Perfil Integral con 72,40, entonces pasa
  a la prueba del puesto, se le crea la prueba, sale un solo correo `PRUEBA_DISPONIBLE` y un
  aviso en la campana, y el historial dice el motivo con la nota y la mínima.
- **AC-03:** Dado ese candidato con 59,99, entonces se queda en «por confirmar», sin prueba, sin
  correo ni aviso, y la ficha dice que no llega a la mínima.
- **AC-04:** Dado un cierre con nota nula o sin cierre (IA fallida), entonces se queda en «por
  confirmar» o «calificando», sin prueba, y la ficha lo explica.
- **AC-05:** Dada la nota provisional de un banco clásico de 90, cuando aún no ha terminado la
  IA, entonces no hay pase.
- **AC-06:** Dado un candidato en «por confirmar» con 55 en una vacante con el interruptor
  encendido, cuando un ajuste a mano sube su nota a 63, entonces pasa solo a la prueba con su
  correo y su campana. Si ya estaba en la prueba y la nota baja, no se le toca.
- **AC-07:** Dada una vacante sin banco con el interruptor encendido, cuando alguien postula
  cumpliendo los requisitos, entonces no pasa al instante: espera la nota del CV y se aplica la
  mínima.
- **AC-08:** Dada una vacante con el interruptor apagado, entonces nada cambia: el equipo confirma
  a mano y la mínima no bloquea «Avanzar».
- **AC-09:** Dada la tuerca de una vacante, entonces se ve «Nota mínima para pasar el perfil integral» con
  60, se puede cambiar de 0 a 100 con el interruptor encendido, rechaza otros valores, y queda
  auditado quién la cambió y de cuánto a cuánto.
- **AC-10:** Dados candidatos esperando con 65 y mínima 70, cuando se baja la mínima a 60,
  entonces no se mueve ninguno.
- **AC-11:** Dada una vacante con una mínima de 0, cuando la IA cierra con cualquier nota,
  entonces pasa; sin nota completa, no.
- **AC-12:** Dada una cuenta nueva, cuando acepta el consentimiento, entonces firma la versión 1.1
  de `PLATAFORMA`; la página pública dice lo mismo, y quien firmó la 1.0 sigue ligado a ella.
- **AC-13:** Ninguna nota lleva a nadie a «No continúa».

## Casos límite y errores

- **Vacante sin prueba montada y nota que llega:** se queda en «por confirmar», como hoy. Al
  montar la prueba nadie pasa solo: lo mueve el equipo.
- **Una persona la mueve mientras la IA trabaja:** el cierre guarda la nota y no la mueve, como
  hoy.
- **Dos recálculos seguidos:** el pase bloquea la fila, como «Avanzar»: solo pasa una vez.
- **Se apaga el interruptor con gente esperando:** nadie pasa solo desde entonces.
- **Se enciende el interruptor con gente esperando:** solo pasan solos si después termina o se
  recalcula su nota.
- **Mínima con decimales por API:** se rechaza; es un entero.
- **Sin permiso de editar la vacante:** el campo se ve desactivado y la API responde como el
  interruptor.

## Datos y compatibilidad

- **Migración (la siguiente libre, hoy V72):**
  - `vacante.nota_minima`: las nulas pasan a 60; por defecto 60; `NOT NULL`; `CHECK` de 0 a 100
    y entero. **Las vacantes que ya tienen el interruptor encendido empiezan a filtrar con 60 en
    cuanto se despliegue.**
  - El texto `PLATAFORMA` 1.1, con su hash, para la plataforma.
- **API:** la vacante del panel gana `notaMinima`. Un `PUT /api/v1/panel/vacantes/{id}/nota-minima`
  con el mismo permiso y la misma auditoría que el interruptor.
- **Registros existentes:** quien ya está en la prueba sigue ahí. Quien espera en «por confirmar»
  no se mueve al desplegar.
- **Lo que se retira:** el pase al instante (`PaseAutomaticoAlInstante`, `alInstante` y los
  motivos «al entregar» y «al postular»). La criba del CV al postular sin banco se sigue
  encolando.
- **Pruebas que cambian:**
  - `FlujoPruebaAlInstanteIT` (AC-1 a AC-6; el AC-7 sigue valiendo) y `FlujoCalificacionIaIT`
    («llega sola hasta la prueba»);
  - `PaseAutomaticoTest`, `PaseAutomaticoAlInstanteTest`, `ServicioPostulacionPortalImplTest` y
    `ArquitecturaTest`;
  - en el frontend, los E2E 54 y 55, `Evaluacion.test.tsx`, `PostularAlInstante.test.tsx` y
    `PoliticaPublica.test.tsx`.

## Verificación

- **Actor y datos:**
  - una vacante con el interruptor encendido, banco de preguntas propias, prueba montada y mínima
    60;
  - otra sin banco;
  - otra con el interruptor apagado;
  - la IA en modo de pruebas, devolviendo notas fijas (72,40, 59,99 y nula).
- **E2E repetibles en headless** (número siguiente libre):
  - entregar el banco y ver el aviso nuevo, sin prueba;
  - con nota 72,40, ver la prueba disponible en «Mis procesos»;
  - con nota 59,99, ver «Tu perfil está en revisión» y en el panel la ficha con la explicación;
  - cambiar la mínima en la tuerca.
- **QA con navegador:** la tuerca en móvil y escritorio, y el campo desactivado con el
  interruptor apagado.
- **Comprobación personal** (hay inicio de sesión, la hace el usuario): poner la mínima en una
  vacante, postular con dos cuentas y ver que una pasa y la otra se queda en revisión.

## Dependencias y dudas

- **Requiere:** nada nuevo.
- **Relacionadas:** `specs/novedades-por-whatsapp.md` (su «felicidades» sale de este mismo paso
  a la prueba) y la futura spec del umbral de la nota ponderada.
- **Decidido por el usuario el 12/10/2026:**
  1. Quien no llega se queda en su estado y alguien lo pasa a mano si quiere; nunca «No
     continúa».
  2. «Llega» es 60 o más.
  3. La mínima es por vacante, en la tuerca de configuración.
  4. Solo en vacantes con «Calificar y avanzar sola».
  5. Sin nota completa, revisión manual.
  6. Si la nota sube mientras espera, pasa solo; a quien ya está en la prueba no se le toca.
  7. El WhatsApp se suma al correo y la campana, no los sustituye.
  8. El umbral del ponderado va más adelante.
- **Propuestas de esta spec** (cámbialas si no te sirven): la mínima es un entero; cambiarla no
  mueve a nadie que ya espera; las vacantes que ya avanzan solas empiezan con 60 al desplegar.
  9. El texto legal nuevo vale para las cuentas nuevas; a quien firmó la 1.0 no se le pide
     aceptar otra vez.
- **Decisiones pendientes:** ninguna.
