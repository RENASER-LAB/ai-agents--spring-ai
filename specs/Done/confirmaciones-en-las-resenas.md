# Confirmaciones en las reseñas: moderar y descartar borradores
Estado: TERMINADO

## Objetivo
Evitar dos pérdidas por un clic sin querer en las reseñas:
- **Quien modera** resuelve hoy un reporte en cuanto pulsa «Mantener» u «Ocultar». Las dos
  decisiones son definitivas, avisan a terceros y cambian lo que ven la persona y las empresas.
  Antes de resolver, una ventana explicará lo que va a pasar y pedirá confirmarlo.
- **La persona y la empresa** pierden hoy lo que escribieron en «Responder» o «Reportar» si
  cierran la ventana con Escape, el aspa, un clic fuera o «Volver». Si hay texto nuevo, se
  preguntará antes de descartarlo.

## Contexto
- Comportamiento actual:
  - En Panel › Configuración › «Reseñas reportadas», cada reporte pendiente tiene una «Nota de
    la revisión» y los botones «Mantener» y «Ocultar». Con la nota escrita, el botón envía la
    decisión en el acto y el reporte pasa a «Resueltas». No hay forma de deshacerlo.
  - Las ventanas de «Responder» y «Reportar» se cierran sin preguntar, aunque haya texto
    escrito.
- Revisión de todas las acciones de reseñas (01/10/2026):

  | Dónde | Acción | ¿Confirma hoy? | ¿Lo necesita? |
  |---|---|---|---|
  | Panel › Configuración › Reseñas reportadas | Mantener | No | **Sí.** Cierra el reporte para siempre y, si es de una reseña, avisa a la persona |
  | Panel › Configuración › Reseñas reportadas | Ocultar | No | **Sí.** Es definitivo, sin apelación, y cambia lo que ven la persona y las empresas |
  | Mi perfil (persona) | Cerrar «Responder» o «Reportar» con texto escrito | No | **Sí.** Se pierde lo escrito |
  | Ficha del postulante (empresa autora) | Cerrar «Reportar la respuesta» con texto escrito | No | **Sí.** Se pierde lo escrito |
  | Ficha del postulante (empresa autora) | Borrar la reseña | Sí, en línea | Ya lo tiene |
  | Ficha del postulante (empresa autora) | Publicar o guardar la reseña | No | No. Se puede editar o borrar durante 30 días, y si la persona ya respondió, el formulario avisa antes de guardar |
  | Ficha del postulante (empresa autora) | Enviar el reporte de la respuesta | No | No. La ventana del formulario, con el motivo, ya es el paso previo |
  | Mi perfil (persona) | Borrar tu respuesta | Sí, en línea | Ya lo tiene |
  | Mi perfil (persona) | Publicar o guardar la respuesta | No | No. Se puede editar o borrar durante 30 días |
  | Mi perfil (persona) | Enviar el reporte | No | No. La ventana del formulario, con el motivo, ya es el paso previo |

- Pantallas o procesos afectados:
  - la sección «Reseñas reportadas» del panel;
  - los pasos «Reportar» y «Responder» de la ventana de reseñas de «Mi perfil»;
  - la ventana «Reportar la respuesta» de la ficha del postulante.
- Referencias existentes en `RenaserOsPostulantes`:
  - [reseñas de empresas a contratados](resenas-de-empresas-a-contratados.md): puntos 13,
    15, 19, 20 y 22, y AC-20 y AC-21.
  - `src/panel/configuracion/ResenasReportadas.tsx`, `src/paginas/perfil/Resenas.tsx` y
    `src/panel/vacantes/ResenasDeLaFicha.tsx`.
  - Las piezas compartidas `src/ui/resenas/Resenas.tsx` (`FormularioDeReporte` y
    `VentanaDeResenas`) y `src/ui/Modal.tsx`.
  - La ventana de «Eliminar vacante» (`src/panel/vacantes/EliminarVacante.tsx`) sirve de modelo
    para la lista de «lo que pasa».
  - Las confirmaciones en línea de «Borrar» sirven de modelo para la pregunta de descartar.

## Alcance
- Incluye:
  - Una ventana de confirmación para «Mantener» y otra para «Ocultar», en reportes de reseñas
    y en reportes de respuestas. Dice qué se juzga, lo que pasará, quién leerá la nota y que no
    se puede deshacer.
  - Mostrar en la sección el aviso del servidor cuando el reporte ya estaba resuelto o retirado.
  - La pregunta «¿Descartar lo que escribiste?» al cerrar con texto nuevo los pasos «Responder»
    y «Reportar» del perfil y la ventana «Reportar la respuesta» del panel.
  - Actualizar las pruebas de Vitest y E2E que cambian de comportamiento y añadir las nuevas
    (ver «Verificación»).
- Queda fuera:
  - Las confirmaciones de «Borrar la reseña» y «Borrar tu respuesta». Ya existen y siguen en
    línea como hoy.
  - El formulario de la reseña de la empresa. Está en la ficha, no en una ventana, y solo se
    descarta con «Cancelar».
  - Recargar la página, cerrar la pestaña y el botón «atrás» de Android con una ventana
    abierta.
  - Cambios en el backend, en la API o en las reglas de moderación.
  - Deshacer una decisión de moderación ya tomada.

## Comportamiento esperado

### A. Confirmar la moderación
1. Quien modera escribe la «Nota de la revisión» y pulsa «Mantener» u «Ocultar».
2. Si la nota está vacía o pasa de 1000 caracteres, el error sale junto al campo, como hoy, y no
   se abre ninguna ventana.
3. Con la nota válida, se abre la ventana de confirmación. Todavía no se envía nada.
4. La ventana tiene:
   - **Título y botón de confirmar**, con la decisión y lo que se juzga: «Ocultar la reseña»,
     «Mantener la reseña», «Ocultar la respuesta» o «Mantener la respuesta».
   - **Qué se juzga:** «Reseña de [Empresa] a [Persona]» o «Respuesta de [Persona] a la reseña
     de [Empresa]».
   - **Lo que pasa**, en una lista según el caso:
     - *Ocultar una reseña:* deja de verse en el perfil de la persona y en el panel de las demás
       empresas, y deja de contar en el promedio. La empresa autora la verá atenuada, con tu
       nota. A la persona le llega «Revisamos tu reporte: la ocultamos».
     - *Mantener una reseña:* sigue visible y contando. A la persona le llega «Revisamos tu
       reporte: la mantuvimos». Solo se podrá volver a reportar si la empresa la edita.
     - *Ocultar una respuesta:* deja de verse para todas las empresas. La persona la verá
       atenuada, con tu nota, y no podrá editarla, borrarla ni volver a responder a esa reseña.
       Le llega un aviso a la campana.
     - *Mantener una respuesta:* nada cambia. La empresa autora verá «La plataforma la mantuvo»
       con tu nota. A la persona no se le avisa. Solo se podrá volver a reportar si la persona
       la edita.
   - **Tu nota**, entre comillas y completa, para releerla antes de confirmar.
   - **Cierre:** «No se podrá deshacer.»
   - **Botones:** «Volver» y el de confirmar.
5. «Volver», Escape, el aspa o un clic fuera cierran la ventana sin enviar nada. La nota sigue
   escrita en la tarjeta y el foco vuelve al botón que se pulsó.
6. Al confirmar, la decisión se envía. Mientras se guarda, los dos botones quedan
   deshabilitados, el de confirmar dice «Guardando…» y la ventana no se puede cerrar.
7. Si sale bien, la ventana se cierra, el reporte sale de «Pendientes» y el foco va al título
   de la sección, como hoy.
8. Si el servidor responde que el reporte ya no está pendiente (409), porque otra persona lo
   resolvió o porque se retiró mientras se leía:
   - la ventana se cierra;
   - la lista se refresca;
   - el mensaje del servidor queda visible encima de la lista, no dentro de la tarjeta que
     desaparece.
9. Si falla por otra causa, la ventana sigue abierta con el error dentro y los botones se
   vuelven a habilitar para reintentar. La nota no se pierde.

### B. Preguntar antes de descartar un borrador
10. Hay borrador cuando hay algo escrito que no está guardado. Los espacios de los extremos no
    cuentan.
    - *Responder* por primera vez: el texto no está vacío.
    - *Editar* la respuesta: el texto es distinto del guardado. Si se cambia y se deja como
      estaba, no hay borrador.
    - *Reportar* y *Reportar la respuesta*: «Cuéntanos más» tiene texto. Elegir solo un motivo
      no cuenta, porque rehacerlo es un clic.
11. Sin borrador, «Volver», Escape, el aspa y el clic fuera funcionan como hoy, sin preguntar.
12. Con borrador, cualquiera de esos cuatro cierres no cierra nada. El pie de la ventana cambia
    a la pregunta «¿Descartar lo que escribiste? No se guardará.», con los botones «Seguir
    escribiendo» y «Descartar». El foco va a «Seguir escribiendo».
13. «Seguir escribiendo», o Escape con la pregunta a la vista, quita la pregunta y vuelve el pie
    normal. El texto sigue intacto y el foco vuelve al campo.
14. «Descartar» hace lo que iba a hacer el cierre. En el perfil, «Volver» desde «Ver todas»
    vuelve a la lista y cualquier otro cierre cierra la ventana. El foco va adonde va hoy.
15. Con la pregunta a la vista, otro clic fuera o en el aspa no descarta nada.
16. No se pregunta:
    - tras publicar o enviar con éxito, porque lo escrito ya está guardado;
    - en el paso «Reseña no disponible», que no tiene formulario;
    - mientras se envía, porque esas ventanas se comportan como hoy.

## Referencia visual
- Boceto de la confirmación de la moderación (escritorio):

```
┌─ Ocultar la reseña ───────────────────────────── × ┐
│ Reseña de Constructora Andina a Luis Pérez         │
│                                                    │
│ • Deja de verse en el perfil de la persona y en    │
│   el panel de las demás empresas, y deja de contar │
│   en el promedio.                                  │
│ • La empresa autora la verá atenuada, con tu nota. │
│ • A la persona le llega «Revisamos tu reporte:     │
│   la ocultamos».                                   │
│                                                    │
│ Tu nota                                            │
│ «Menciona un diagnóstico médico de la persona.»    │
│                                                    │
│ No se podrá deshacer.                              │
├────────────────────────────────────────────────────┤
│                     [Volver] [Ocultar la reseña]   │
└────────────────────────────────────────────────────┘
```

- Boceto de la pregunta de descartar. Va en el pie de la misma ventana, no en otra:

```
┌─ Responder a la reseña de Acme ──────────────── × ┐
│ ★★★★☆ Acme                                        │
│ «texto de la reseña…»                             │
│ Tu respuesta                                      │
│ [Gracias por la oportunidad, aprendí mucho…    ]  │
├───────────────────────────────────────────────────┤
│ ¿Descartar lo que escribiste? No se guardará.     │
│                [Seguir escribiendo] [Descartar]   │
└───────────────────────────────────────────────────┘
```

- Estados a dibujar:
  - la confirmación de moderación en sus cuatro casos, guardando, con error y reintento, y con
    datos largos: una nota de 1000 caracteres y nombres de empresa y persona largos. La nota se
    lee completa dentro del cuerpo de la ventana, con su propio desplazamiento si hace falta.
  - la pregunta de descartar en los tres pasos, en escritorio y en móvil a 375 px.
- Móvil y escritorio:
  - La confirmación de moderación es la ventana compartida sin pantalla completa, porque es un
    aviso corto.
  - La pregunta de descartar va dentro de la ventana que ya está abierta. En el perfil, en
    móvil, esa ventana ocupa toda la pantalla y la pregunta va en su pie.
- Reglas visuales: [DESIGN.md](../../DESIGN.md) y las piezas existentes, sin estilos nuevos.
  - «Ocultar la …» usa el estilo peligroso que ya lleva el botón «Ocultar».
  - «Mantener la …» usa el botón principal de las ventanas del panel.
  - «Volver» y «Seguir escribiendo» usan el secundario.
  - «Descartar» usa el mismo estilo que el «Borrar» de las confirmaciones en línea.
  - La lista de «lo que pasa» sigue la de «Eliminar vacante».
- Por qué la pregunta no es otra ventana: la ventana compartida lleva un `id` fijo en su título
  y no admite dos abiertas a la vez. Además, la pregunta en línea repite el patrón de las
  confirmaciones de «Borrar».

## Criterios de aceptación

### Moderación
- AC-01: Dado un reporte pendiente con la nota vacía, cuando se pulsa «Ocultar» o «Mantener»,
  entonces sale el error junto al campo, no se abre la ventana y no se envía nada.
- AC-02: Dado un reporte pendiente de una reseña con la nota escrita, cuando se pulsa «Ocultar»,
  entonces se abre la ventana «Ocultar la reseña» con la empresa, la persona, lo que pasa, la
  nota completa y «No se podrá deshacer», y todavía no se ha enviado nada.
- AC-03: Dada la ventana abierta, cuando se cierra con «Volver», Escape, el aspa o un clic fuera,
  entonces no se envía nada, el reporte sigue en «Pendientes» con la nota escrita y el foco
  vuelve al botón pulsado.
- AC-04: Dada la ventana de ocultar una reseña, cuando se confirma, entonces se envía `OCULTAR`
  con la nota, la ventana se cierra, el reporte pasa a «Resueltas» como «Se ocultó» y el foco va
  al título de la sección.
- AC-05: Dado un reporte pendiente de una reseña, cuando se pulsa «Mantener» y se confirma en
  «Mantener la reseña», entonces se envía `MANTENER` con la nota y el reporte pasa a «Resueltas»
  como «Se mantuvo».
- AC-06: Dado un reporte pendiente de una respuesta, cuando se pulsa «Ocultar» o «Mantener»,
  entonces la ventana se titula «Ocultar la respuesta» o «Mantener la respuesta» y dice lo que
  le pasa a la respuesta (punto 4), no a la reseña.
- AC-07: Dada la ventana abierta, cuando se pulsa dos veces seguidas el botón de confirmar,
  entonces la decisión se envía una sola vez.
- AC-08: Dado un reporte pendiente, cuando se pulsa «Ocultar» con el teclado y se vuelve a
  pulsar Intro en seguida, entonces no se resuelve nada. El foco inicial de la ventana no está
  en el botón de confirmar.
- AC-09: Dada la ventana abierta, cuando otra persona ya resolvió el reporte y se confirma,
  entonces la ventana se cierra, el reporte sale de «Pendientes», el mensaje del servidor queda
  visible encima de la lista y no se resuelve dos veces.
- AC-10: Dada la ventana abierta, cuando el envío falla por red o por un error del servidor
  distinto del 409, entonces la ventana sigue abierta con el error, los botones se habilitan y
  se puede reintentar sin volver a escribir la nota.
- AC-11: Dada la confirmación, cuando se usa la API directamente, entonces la API se comporta
  como hoy: la confirmación es solo de la pantalla.

### Borradores
- AC-12: Dado el paso «Responder» con texto escrito, cuando se cierra con «Volver», Escape, el
  aspa o un clic fuera, entonces la ventana sigue abierta, el pie pregunta «¿Descartar lo que
  escribiste?» y el foco está en «Seguir escribiendo».
- AC-13: Dada la pregunta a la vista, cuando se pulsa «Seguir escribiendo» o Escape, entonces
  la pregunta desaparece, el texto sigue intacto, el foco vuelve al campo y no se envía nada.
- AC-14: Dada la pregunta a la vista, cuando se pulsa «Descartar», entonces la ventana se cierra
  (o vuelve a la lista, si el paso se abrió desde «Ver todas»), no se envía nada y el foco va
  al botón que abrió el paso. Al volver a abrirlo, el campo está vacío.
- AC-15: Dado «Editar» de una respuesta abierto sin cambiar nada, o cambiado y devuelto a como
  estaba, cuando se cierra, entonces se cierra sin preguntar.
- AC-16: Dado «Reportar» con solo un motivo elegido, cuando se cierra, entonces se cierra sin
  preguntar. Con texto en «Cuéntanos más», pregunta.
- AC-17: Dada la ventana «Reportar la respuesta» del panel con texto en «Cuéntanos más», cuando
  se cierra por cualquiera de las cuatro vías, entonces pregunta igual que en el perfil.
- AC-18: Dado un paso con texto, cuando se publica o se envía con éxito, entonces la ventana se
  cierra sin preguntar.
- AC-19: Dada la pregunta a la vista, cuando se hace clic fuera o en el aspa, entonces no se
  descarta nada y la pregunta sigue.

### Pruebas
- AC-20: Dados los cambios, cuando se corren la suite de Vitest y los E2E
  `41-resenas-de-empresas` y `42-resenas-entre-empresas`, entonces todo pasa. Las pruebas
  actualizadas y las nuevas que se listan en «Verificación» están incluidas.

## Casos límite y errores
- Datos vacíos o inválidos:
  - La nota de la revisión se valida antes de abrir la ventana, con las reglas de hoy:
    obligatoria y de hasta 1000 caracteres. Una nota de solo espacios cuenta como vacía.
  - Un borrador de solo espacios no cuenta como borrador.
- Usuario sin permisos: sin cambios. Sin `moderar_resenas` o fuera de la plataforma, la sección
  no se pinta y la API responde 403.
- Recarga, reintento o doble envío:
  - recargar con la confirmación de moderación abierta la cierra sin enviar nada;
  - recargar con un borrador lo pierde sin preguntar: queda fuera;
  - el doble clic en el botón de confirmar envía una vez;
  - mientras se guarda, la ventana de moderación no se cierra.
- Otros casos relevantes:
  - Si la empresa borra la reseña, o la persona su respuesta, mientras la confirmación de
    moderación está abierta, al confirmar aplica el AC-09: el reporte ya está «Retirado».
  - Si la reseña deja de estar disponible mientras se escribe una respuesta, el envío da 404 y
    el paso pasa a «Reseña no disponible», como hoy, sin preguntar al cerrarlo.

## Datos y compatibilidad
- No hay datos nuevos, migraciones ni cambios en la API.
- Los formularios de «Responder» y «Reportar» guardan su texto dentro. Tienen que decirle a la
  ventana si hay borrador, para que el cierre sepa si debe preguntar.

## Verificación
- Actor y datos de prueba:
  - el Administrador de la plataforma, con `moderar_resenas`;
  - un reporte pendiente de una reseña y otro de una respuesta;
  - una nota de 1000 caracteres;
  - una segunda sesión, o una llamada a la API, que resuelva el mismo reporte para provocar el
    409;
  - una persona con una reseña sin responder y otra con una respuesta editable;
  - una empresa autora con una respuesta que se pueda reportar.
- **Pruebas que se actualizan** (en `RenaserOsPostulantes`):
  - `src/panel/configuracion/ResenasReportadas.test.tsx`:
    - «mantener u ocultar sin nota no se envía; con nota, sí»: tras pulsar «Ocultar» con nota,
      comprueba que todavía no se envía nada; confirma en «Ocultar la reseña» y entonces espera
      el envío.
    - RES-QA-03: el doble clic pasa al botón de confirmar de la ventana. Sigue comprobando un
      solo envío y el foco en el título.
  - `herramientas/e2e/41-resenas-de-empresas.spec.ts`, prueba 3: confirma «Ocultar la reseña»
    en la ventana.
  - `herramientas/e2e/42-resenas-entre-empresas.spec.ts`:
    - prueba 3: confirma «Ocultar la reseña» y «Mantener la reseña»;
    - prueba 7: confirma «Ocultar la respuesta».
- **Pruebas que no se tocan y deben seguir pasando.** Cierran pasos sin escribir nada, así que
  prueban que sin borrador no se pregunta:
  - RES-QA-05 de `src/paginas/perfil/Resenas.test.tsx` y de
    `src/panel/vacantes/ResenasDeLaFicha.test.tsx`;
  - «RES-QA-02 · al volver de un paso o terminarlo…» de `42-resenas-entre-empresas.spec.ts`.
- **Pruebas nuevas de Vitest:**
  - `ResenasReportadas.test.tsx`:
    - AC-01 a AC-10;
    - el texto de cada uno de los cuatro casos;
    - los cuatro cierres sin envío y con la nota conservada.
  - `Resenas.test.tsx` (perfil):
    - AC-12 a AC-16, AC-18 y AC-19, en «Responder», «Editar» y «Reportar»;
    - abiertos desde la sección y desde «Ver todas».
  - `ResenasDeLaFicha.test.tsx` (panel): AC-17 y «Seguir escribiendo» / «Descartar» en
    «Reportar la respuesta».
- **E2E nuevos** (en `42-resenas-entre-empresas.spec.ts` o en un archivo nuevo de la misma
  serie):
  - pulsar «Ocultar», cerrar con «Volver» y comprobar que sigue pendiente y con la nota;
  - mantener una respuesta confirmando;
  - confirmar un reporte que se resolvió antes por la API (409) y ver el aviso encima de la
    lista;
  - escribir una respuesta, pulsar Escape, «Seguir escribiendo» con el texto intacto,
    «Descartar» y el campo vacío al volver a abrir.
- Riesgos que QA debe explorar con navegador:
  - el teclado: Intro repetido, Escape con y sin la pregunta, Tab dentro de la ventana y la
    vuelta del foco;
  - el lector de pantalla: el título, la lista de lo que pasa y el anuncio de la pregunta;
  - la nota larga y la pregunta de descartar en móvil;
  - el doble clic en el botón de confirmar;
  - el clic fuera mientras se guarda y con la pregunta a la vista.
- Resultado que quiero comprobar personalmente:
  - al pulsar «Ocultar» o «Mantener» sale una ventana que me dice lo que va a pasar, y si me
    arrepiento no pasa nada;
  - si cierro una respuesta a medio escribir, me pregunta antes de perderla.
  - La parte del panel exige entrar con la cuenta de la plataforma, así que la comprueba el
    usuario.

## Dependencias y dudas
- Requiere: la sección «Reseñas reportadas», la ventana de reseñas del perfil, la ficha del
  postulante y la ventana compartida, que ya existen.
- Decisiones pendientes: ninguna. Acordadas el 01/10/2026:
  - la confirmación de «Mantener» y de «Ocultar»;
  - la pregunta al descartar borradores de «Responder» y «Reportar»;
  - incluir la actualización de las pruebas.
