# Reseñas de empresas a las personas que contrataron
Estado: TERMINADO

## Objetivo

Que la empresa que contrató a alguien por EX pueda dejarle una reseña: de 1 a 5 estrellas y
una opinión escrita. Así la persona reúne una reputación verificable, porque solo opina quien
de verdad la contrató. Además, las demás empresas pueden consultarla cuando esa persona se
postule a sus vacantes.

- **Para el candidato:** ve en su perfil (`/perfil`) su promedio y lo que opinan de él. No
  puede aceptar ni rechazar reseñas, pero sí **responder** a cada una (su versión se ve debajo,
  en todos los sitios donde sale la reseña) y **reportar** la que incumpla las normas.
- **Para la empresa que contrató:** tiene un sitio para dejar su opinión cuando ya tiene
  algo que decir, un mes después de contratar.
- **Para las demás empresas:** en la tabla de la vacante pueden encender la columna
  «Reseñas» y leer las reseñas completas en la ficha del postulante.

## Contexto

- Comportamiento actual:
  - **Contratar solo se puede por la API.** Hay dos caminos:
    - La decisión en verde (`POST /panel/postulaciones/{id}/decision`, permiso
      `decidir_contratacion`).
    - La transición manual a `CONTRATADO` (`POST …/transiciones`, permiso
      `mover_postulacion`).
    - El panel todavía no tiene pantalla para contratar. Llegará con el apartado 1 de
      [la ampliación de RR.HH.](../../docs/AMPLIACION-RRHH.md), y esta spec no lo incluye.
  - **`CONTRATADO` es un estado final y no se puede revertir.** La fecha de contratación se
    saca de la transición a `CONTRATADO` en el historial, que no se puede modificar.
  - **Al contratar no sale ningún aviso** al candidato: ni correo ni campana.
  - **El perfil lo ve solo el candidato.** El endpoint del panel
    `GET /panel/postulaciones/{id}/perfil` existe, pero ninguna pantalla lo usa. La ficha del
    panel (`DetalleDelPostulante`, que se despliega debajo de la fila del ranking) muestra
    datos de contacto, estado, CV e historial.
  - **Multiempresa:**
    - Cada postulación pertenece a la empresa de la vacante.
    - El candidato tiene una sola cuenta, que vale para todas las empresas.
    - Hoy solo existe una empresa real (Renaser). La segunda solo aparece en los tests.
  - **Hay una regla escrita que esta función rompe a propósito:** «Entre empresas viaja solo el
    perfil. Jamás notas, alertas ni decisiones de otro proceso»
    ([diseño del 25/08](../../docs/superpowers/specs/2026-08-25-el-candidato-ante-varias-empresas-design.md)).
    Que las demás empresas vean el promedio y los textos lo decidió el usuario el 28/09/2026.
  - **No existe nada de lo que esta función necesita:** ni estrellas, ni reseñas, ni respuestas,
    ni reportes, ni moderación, ni consentimiento para compartir opiniones entre empresas.
  - **El panel no tiene campana de avisos.** Solo la tiene el portal del candidato.
  - **La columna nueva entra en una tabla que ya funciona así:**
    - La tabla de la vacante (`Ranking` en `src/panel/vacantes/Vacante.tsx` de
      `RenaserOsPostulantes`) tiene un menú «Columnas».
    - Las columnas que se pueden ocultar salen todas encendidas al abrir la vacante. Solo los
      criterios arrancan apagados.
    - La elección no se guarda al recargar.
- Pantallas o procesos afectados:
  - En el portal:
    - Cabecera, índice lateral y una sección nueva de `/perfil`.
    - Descarga de mis datos.
    - Campana.
  - En el panel:
    - Columna nueva en la tabla de las cinco etapas.
    - Dos bloques nuevos en la ficha del postulante.
    - Pantalla nueva de moderación en Configuración.
  - En el backend:
    - Borrado de datos (anonimización).
    - Permisos.
    - Alta de empresas, que copia los permisos a cada empresa nueva.
- Referencias existentes:
  - [plantilla de spec](../plantilla-spec.md)
  - [estados de postulación](../../docs/03-ESTADOS-POSTULACION.md)
  - [roles y permisos](../../docs/04-ROLES-Y-PERMISOS.md)
  - [ampliación de RR.HH.](../../docs/AMPLIACION-RRHH.md)
  - [APIs del perfil](../../docs/APIS-PERFIL-DEL-CANDIDATO.md)
  - El `PRODUCT.md` y el `DESIGN.md` del repositorio `RenaserOsPostulantes`.

## Alcance

- Incluye:
  - **Escribir, editar y borrar una reseña** desde la ficha del postulante en el panel:
    - Solo en postulaciones `CONTRATADO` de la propia empresa.
    - Se puede escribir a partir de los 30 días de la contratación.
    - Se puede editar o borrar durante los 30 días siguientes a publicarla.
  - **En `/perfil` del candidato:**
    - El promedio en la cabecera.
    - La entrada «Reseñas» en el índice «En esta página».
    - Una sección «Reseñas de empresas» con el resumen y las 2 más recientes.
    - Una ventana «Ver todas» con filtros.
  - **Reportar una reseña** desde el perfil. El equipo de la plataforma la revisa y decide
    mantenerla u ocultarla.
  - **Responder a una reseña** desde el perfil: una respuesta por reseña, que se puede editar
    o borrar durante 30 días y que se ve debajo de la reseña en el perfil y en el panel.
  - **Reportar una respuesta:** solo la empresa autora de la reseña, desde su bloque de la
    ficha.
  - **Moderación:** la pantalla «Reseñas reportadas», solo para la plataforma. Recibe los
    reportes de reseñas y los de respuestas.
  - **En el panel, para cualquier empresa:**
    - La columna «Reseñas» en la tabla, apagada por defecto, ordenable y en las cinco etapas.
    - El bloque «Reseñas de empresas» en la ficha, con los textos completos.
  - **Avisos en la campana del portal:** cuando llega una reseña, cuando la empresa edita una
    reseña ya respondida, cuando se resuelve un reporte suyo y cuando la plataforma oculta su
    respuesta.
  - **Tres permisos nuevos:** escribir, ver y moderar.
  - Las reseñas entran en la descarga de datos del candidato y en el borrado de datos.
- Queda fuera:
  - El texto de consentimiento que mencione las reseñas: lo prepara el usuario aparte. Las
    reseñas se comparten con las demás empresas desde el primer día, sin esperar a ese texto.
  - La pantalla para contratar desde el panel (apartado 1 de la ampliación). Mientras tanto
    se contrata por la API.
  - Una lista de «contratados pendientes de reseña». El contratado se encuentra en la vista
    «Toda la tanda» de su vacante.
  - Un hilo de conversación: la empresa no contesta a la respuesta. Hay una reseña y, como
    mucho, una respuesta.
  - Que la empresa o el candidato apelen una moderación.
  - Avisar a la empresa de una respuesta nueva: el panel no tiene campana. La ve al abrir la
    ficha.
  - Correos: solo campana.
  - Reseñas del candidato a la empresa (el sentido inverso).
  - La columna en el Excel del ranking.
  - Guardar qué columnas están encendidas entre recargas (sigue la convención actual).
  - Que las reseñas influyan en notas, orden del ranking, pase automático o en la IA.
    **No influyen en nada.**
  - La evaluación de desempeño interna (apartado 12 de la ampliación). Es otra cosa: esa es
    privada de la empresa, y esta es pública entre empresas.

## Comportamiento esperado

### Quién puede escribir y cuándo

1. **Una reseña por contratación.**
   - Una empresa puede reseñar una postulación suya en estado `CONTRATADO`.
   - Si contrató a la misma persona en dos vacantes, puede dejar dos reseñas, una por cada
     contratación.
   - Ninguna otra empresa puede reseñar esa contratación.
2. **Quién de la empresa puede escribirla:**
   - Solo quien tenga el permiso `resenar_contratado`.
   - Por defecto lo tienen:
     - Talento, en todas.
     - Responsable de área, solo en sus vacantes.
     - Dirección, en todas.
   - Se puede cambiar en Configuración › Permisos.
   - La reseña la firma **la empresa**, no la persona que la escribió. El sistema guarda quién
     fue, para auditoría.
3. **Cuándo se abre:**
   - Se puede escribir desde que se cumplen **30 días** de la contratación. Cuenta la fecha de
     la transición a `CONTRATADO`.
   - Las contrataciones anteriores a esta función también cuentan. Si ya pasaron 30 días, se
     pueden reseñar enseguida.
4. **Qué contiene la reseña:**
   - Estrellas, obligatorias: un entero de 1 a 5, con rótulo:
     - 1 Muy mala
     - 2 Mala
     - 3 Aceptable
     - 4 Buena
     - 5 Excelente
   - Opinión, obligatoria: de 30 a 1000 caracteres, sin contar los espacios de los extremos.
   - Antes de publicar, el formulario avisa: «La verán la persona, que podrá responderla, y las
     demás empresas donde postule. Podrás editarla o borrarla durante 30 días».
5. **Editar y borrar:**
   - Durante 30 días desde la primera publicación, la empresa puede editarla o borrarla.
   - Si la edita, la reseña sale con la marca «Editada».
   - Pasado ese plazo queda fija.
   - Si la borra, esa contratación vuelve a quedar libre para reseñarla, y el plazo de 30 días
     empieza de nuevo con la reseña nueva.
6. **Aviso al candidato:** al publicarse una reseña le llega a la campana del portal:
   «[Empresa] te dejó una reseña», con enlace a `/perfil#resenas`. No se le avisa de las
   ediciones, salvo si ya la había respondido (punto 20).

### Dónde la escribe la empresa (ficha del postulante)

7. Al abrir la ficha de una postulación `CONTRATADO` de la propia empresa, aparece el bloque
   «La reseña de [Empresa]». Tiene seis estados:
   - **Aún no toca:** «Podrás dejar una reseña desde el [fecha], al cumplir un mes de su
     contratación».
   - **Se puede escribir:** formulario con estrellas, opinión, contador de caracteres, el aviso
     del punto 4 y el botón «Publicar reseña».
   - **Publicada y aún editable:** la reseña, con «Editar» y «Borrar» y la frase «Puedes
     cambiarla hasta el [fecha]». «Borrar» pide confirmación.
   - **Publicada y ya fija:** la reseña y la frase «Ya no se puede cambiar».
   - **Ocultada por la plataforma:** la reseña atenuada, con «La plataforma la ocultó: [nota
     de la revisión]». No se puede editar ni borrar.
   - **Sin el permiso:** el bloque no aparece. La reseña publicada se sigue viendo en el bloque
     de lectura del punto 16.
   - En los estados con reseña publicada, si la persona respondió, la respuesta sale debajo
     con «Reportar la respuesta» (punto 22).

### Lo que ve el candidato en `/perfil`

8. **Cabecera:**
   - Debajo de «X años de experiencia» aparece «★ 4,5 · 3 reseñas». Es un enlace que baja a la
     sección.
   - Si no hay reseñas visibles, esa línea no aparece.
9. **Índice «En esta página»:**
   - Nueva entrada «Reseñas» al final, después de «Enlaces».
   - Lleva el número de reseñas visibles, igual que «Tu trayectoria».
   - No lleva la etiqueta «por revisar». El candidato no edita sus reseñas.
10. **Sección «Reseñas de empresas»:**
    - Va al final de la columna principal.
    - **Explicación:** «Te las escriben las empresas que te contrataron por EX, a partir de tu
      primer mes. Las ven las empresas donde te postulas».
    - **Resumen:**
      - A la izquierda, el promedio en grande, las estrellas (con medias estrellas) y «N
        reseñas».
      - A la derecha, 5 barras de reparto (5★ … 1★) con su número.
    - **Las 2 reseñas más recientes**, en tarjetas. Cada tarjeta muestra:
      - Las estrellas.
      - El nombre de la empresa.
      - «Contratado como [puesto de la vacante]».
      - La fecha y, si toca, «Editada».
      - El texto, cortado a 4 líneas con «Leer más».
      - Debajo, su respuesta si la hay (punto 21).
      - Dos botones discretos: «Responder», solo si aún no respondió, y «Reportar».
    - **Botón «Ver todas las reseñas (N)»:** aparece solo si hay más de 2.
    - **Estado vacío:** «Todavía no tienes reseñas. Cuando una empresa te contrate por EX, podrá
      dejarte una a partir del primer mes». La sección y la entrada del índice aparecen igual,
      para que la persona sepa que existe.
11. **Ventana «Ver todas»** (el `Modal` que ya existe en el frontend):
    - Arriba, el mismo resumen. **Las barras de reparto también se pueden pulsar**: una barra
      filtra por esas estrellas.
    - **Filtros básicos:**
      - **Estrellas:** chips «Todas · 5★ (n) · 4★ (n) · 3★ (n) · 2★ (n) · 1★ (n)». Se pueden
        marcar varios, y los que están en 0 salen deshabilitados.
      - **Empresa:** un desplegable que solo aparece si hay reseñas de 2 o más empresas.
      - **Orden:** Más recientes (por defecto), Más antiguas, Mejor calificadas y Peor
        calificadas.
    - Las tarjetas se ven con el texto completo.
    - **Pie:** «Se ven X de N», y «Quitar filtros» si hay alguno activo.
    - **Si los filtros no dejan ninguna:** «Ninguna reseña con estos filtros» y «Quitar
      filtros».
    - Se cierra con Esc, con la ✕ o tocando fuera, como el resto de ventanas.
12. **Promedio:**
    - Es la media de las reseñas **visibles**, sin contar las ocultadas por la plataforma.
    - Se muestra con un decimal y siempre acompañado del número de reseñas. Con una sola
      reseña se ve, por ejemplo, «★ 5,0 · 1 reseña».
    - No hay un mínimo de reseñas para mostrarlo.

### Reportar y moderar

13. **Reportar:**
    - Al pulsar «Reportar», la misma ventana pasa a un paso «Reportar la reseña de [Empresa]».
      Si se reporta desde la sección, se abre la ventana directamente en ese paso. Nunca hay dos
      ventanas abiertas a la vez.
    - Muestra la reseña y el motivo, obligatorio:
      - Tiene insultos o lenguaje ofensivo.
      - Revela datos personales o de salud.
      - Es discriminatoria.
      - Es falsa: no trabajé ahí, o cuenta hechos que no pasaron.
      - Otro motivo.
    - «Cuéntanos más» es obligatorio si el motivo es «Otro motivo», con un máximo de 500
      caracteres.
    - Aviso: «La reseña sigue visible mientras la revisamos».
    - Al enviar, la tarjeta muestra «Reportada · en revisión» y el botón «Reportar» desaparece.
14. **Límite de reportes:**
    - Una reseña se puede reportar una vez.
    - Si la plataforma la mantiene y después la empresa la edita, se puede volver a reportar.
    - Mientras hay un reporte pendiente, la reseña se sigue viendo y contando. Así, reportar no
      sirve para esconder una reseña a voluntad.
15. **Moderación, en Panel › Configuración › «Reseñas reportadas»:**
    - Solo la ve quien tenga `moderar_resenas`: el Administrador de la empresa plataforma.
      **Este permiso no se copia a las empresas nuevas.**
    - Tiene dos listas:
      - «Pendientes», ordenadas de más antigua a más reciente.
      - «Resueltas».
    - Entran los reportes de reseñas (del candidato) y los de respuestas (de la empresa
      autora, punto 22), en las mismas listas.
    - Cada tarjeta muestra:
      - Qué se reporta: «Reseña» o «Respuesta».
      - Empresa → candidato.
      - Estrellas y texto de la reseña. Si se reporta la respuesta, también la respuesta, que
        es lo que se juzga; la reseña va encima para dar contexto.
      - Motivo y comentario de quien reporta.
      - Fecha del reporte.
    - Las acciones son «Mantener» u «Ocultar», y las dos exigen una «Nota de la revisión».
    - **Si se oculta:**
      - La reseña deja de verse y de contar para el candidato y para las demás empresas.
      - La empresa autora la ve atenuada, con la nota.
      - Ocultarla es definitivo.
    - **En ambos casos** le llega al candidato a la campana: «Revisamos tu reporte: la
      ocultamos» o «… : la mantuvimos». Lo que pasa al resolver el reporte de una respuesta
      está en el punto 22.
    - Si la empresa borra la reseña mientras el reporte está pendiente, el reporte se cierra
      como «Retirada por la empresa».

### Lo que ven las empresas en el panel

16. **Bloque «Reseñas de empresas» en la ficha de cualquier postulante,** esté en el estado que
    esté:
    - Tiene el mismo resumen, las 2 más recientes y «Ver todas», con la misma ventana y los
      mismos filtros que el candidato, pero sin «Reportar» ni «Responder».
    - Cada reseña lleva debajo la respuesta de la persona, si la hay.
    - Incluye las reseñas de todas las empresas, también las de la propia, con el nombre de la
      empresa autora.
    - Solo aparece para quien tenga `ver_resenas_candidato`. Por defecto lo tienen Talento,
      Responsable de área y Dirección.
    - Sin reseñas visibles: «Sin reseñas de empresas».
17. **Columna «Reseñas» en la tabla:**
    - Aparece en el menú «Columnas» de las cinco etapas, **apagada al abrir la vacante**.
    - Al encenderla, cada fila muestra «★ 4,5 (3)», o «—» si no hay reseñas visibles.
    - Se puede ordenar pulsando la cabecera:
      - Primero por promedio y, a igualdad, por número de reseñas.
      - Las filas sin reseñas van siempre al final.
      - Al tercer clic vuelve al orden del servidor.
    - Pulsar la celda abre la ficha de esa fila, que contiene el bloque del punto 16.
    - Solo se ofrece a quien tenga `ver_resenas_candidato`.
    - No cambia el orden por defecto del ranking, ni las notas, ni el Excel.

### Responder a una reseña

18. **Quién y cuándo:**
    - Solo la persona reseñada, desde su perfil. Ninguna empresa puede responder.
    - **Una respuesta por reseña.** No hay hilo: la empresa no contesta a la respuesta.
    - Se puede responder en cualquier momento mientras la reseña esté visible, también si
      está reportada y en revisión. Responder y reportar son independientes.
    - Responder no cambia las estrellas ni el promedio.
19. **Cómo se escribe:**
    - «Responder», en la tarjeta de la sección o de la ventana, abre la ventana directamente en
      el paso «Responder a la reseña de [Empresa]». Funciona igual que el paso de reportar: no
      se abre una segunda ventana.
    - Muestra la reseña y el campo «Tu respuesta», obligatorio: de 30 a 500 caracteres, sin
      contar los espacios de los extremos, con contador.
    - Aviso: «La verán las empresas donde te postules, debajo de la reseña. Podrás editarla o
      borrarla durante 30 días».
    - Botones «Volver» y «Publicar respuesta».
    - Al publicar, la tarjeta muestra la respuesta y el botón «Responder» desaparece.
20. **Editar y borrar la respuesta:**
    - Durante 30 días desde que se publicó, la respuesta lleva «Editar» y «Borrar». Borrar pide
      confirmación. Editar usa el mismo paso de la ventana, con el texto cargado.
    - Si la edita, sale con «Editada».
    - Si la borra, puede volver a responder, y el plazo de 30 días empieza de nuevo.
    - Pasado el plazo queda fija.
    - **Si la empresa edita una reseña ya respondida:**
      - Antes de guardar, el formulario de la empresa avisa: «[Nombre] ya respondió a esta
        reseña. Si la cambias, le avisaremos para que pueda revisar su respuesta».
      - El plazo de 30 días de la respuesta vuelve a empezar desde esa edición. Así la persona
        tiene un mes entero para ajustarla a lo que ahora dice la reseña.
      - Le llega a la campana: «[Empresa] editó su reseña. Puedes revisar tu respuesta hasta el
        [fecha]», con enlace a `/perfil#resenas`.
21. **Cómo se ve:**
    - Debajo de la reseña, en un bloque sangrado. En el perfil se titula «Tu respuesta», y en
      el panel, «Respuesta de [nombre de la persona]». Lleva la fecha y, si toca, «Editada».
    - Sale en todos los sitios donde sale la reseña:
      - la sección y la ventana del perfil;
      - el bloque de la empresa autora (punto 7);
      - el bloque de lectura de cualquier empresa (punto 16).
    - En la sección del perfil y en el bloque de la ficha se corta a 3 líneas con «Leer más».
      En la ventana «Ver todas» sale completa.
    - **La respuesta va con su reseña:**
      - Si la empresa borra la reseña, la respuesta se borra con ella, sin aviso.
      - Si la plataforma oculta la reseña, la respuesta deja de verse con ella.
    - La empresa no recibe aviso de la respuesta, porque el panel no tiene campana. La ve al
      abrir la ficha.
22. **La empresa autora puede reportar la respuesta:**
    - En su bloque (punto 7), debajo de la respuesta, hay un botón discreto «Reportar la
      respuesta». Solo lo ve quien tiene `resenar_contratado` en esa contratación. Las demás
      empresas no pueden reportarla.
    - Los motivos son los del punto 13, salvo que «Es falsa» dice «Es falsa: cuenta hechos que
      no pasaron». Con «Otro motivo», el comentario es obligatorio.
    - Aviso: «La respuesta sigue visible mientras la revisamos».
    - Se puede reportar una vez. Si la plataforma la mantiene y la persona la edita después,
      se puede volver a reportar.
    - El bloque de la empresa muestra el estado del reporte: «Reportada · en revisión», y
      después «La plataforma la mantuvo: [nota]» o la respuesta atenuada con «La plataforma
      la ocultó: [nota]».
    - **Si la plataforma la oculta:**
      - Deja de verse para todas las empresas.
      - La persona la ve atenuada, con «La plataforma ocultó tu respuesta: [nota]». No puede
        editarla, borrarla ni responder de nuevo a esa reseña. Es definitivo.
      - Le llega a la campana: «La plataforma ocultó tu respuesta a la reseña de [Empresa]».
    - **Si la mantiene:** no cambia nada y a la persona no se le avisa, porque ella no
      reportó nada.
    - Si la persona borra la respuesta mientras el reporte está pendiente, el reporte se
      cierra como «Retirada por la persona».

### Errores

23. **Al publicar o editar:**
    - Si falla la validación (estrellas vacías, texto corto o largo), el aviso sale junto al
      campo y no se envía nada.
    - Si la ventana de edición venció mientras el formulario estaba abierto, el backend lo
      rechaza, el bloque se refresca y dice «Ya no se puede cambiar».
24. **Si otra persona de la misma empresa publicó antes:** el backend rechaza el segundo
    intento (una reseña por contratación), y el bloque se refresca y muestra la reseña que
    ya existe.
25. **Si falla la carga de reseñas** en el perfil o en la ficha, la sección muestra «No
    pudimos cargar las reseñas» con «Reintentar». El resto del perfil y de la ficha sigue
    funcionando.
26. **Al responder:**
    - Si falla la validación (vacía, menos de 30 o más de 500 caracteres), el aviso sale junto
      al campo y no se envía nada.
    - Si la empresa borró la reseña, o la plataforma la ocultó, mientras la persona escribía,
      el backend rechaza la respuesta. La ventana dice «Esta reseña ya no está disponible» y
      la sección se refresca.
    - Si el plazo de edición venció con el paso abierto, el backend lo rechaza y la tarjeta
      dice «Ya no se puede cambiar».

## Referencia visual

Los bocetos fijan **qué se ve y en qué orden**, no las medidas ni los colores. Mandan el
`DESIGN.md` y el `PRODUCT.md` del repositorio `RenaserOsPostulantes`, no el `DESIGN.md` de
este repositorio.

- **Se llama «Reseñas» y no «Testimonios».** El `PRODUCT.md` del portal prohíbe inventar
  testimonios, y la palabra suena a publicidad.
- **Las estrellas en el panel nunca van en el violeta `--activo`**, que significa «te toca a
  ti». El color de las estrellas queda a criterio, dentro de cada `DESIGN.md`.
- **Hay que añadir el icono de estrella** a `ui/Iconos.tsx`, en sus versiones llena, media y
  vacía.

**1. Cabecera del perfil**

```
┌─────────────────────────────────────────────────────────────────┐
│ [portada]                                           [Portada]   │
│  (LP)  Luis Perez                                [Editar perfil]│
│        Desarrollador Backend                                    │
│        🕒 1 año y 8 meses de experiencia                        │
│        ★ 4,5 · 3 reseñas        ← enlace a #resenas             │
└─────────────────────────────────────────────────────────────────┘
```

**2. Índice lateral**

```
EN ESTA PÁGINA
Acerca de ti
Tu trayectoria          5
Certificaciones   [4 por revisar]
Idiomas                 1
Enlaces
Reseñas                 3     ← nueva, al final
```

**3. Sección en el perfil** (en el panel, el bloque de la ficha es igual, pero sin «Reportar»
ni «Responder», y la respuesta se titula «Respuesta de [nombre]»)

```
Reseñas de empresas
Te las escriben las empresas que te contrataron por EX, a partir de tu primer mes.
Las ven las empresas donde te postulas.

┌───────────────┬──────────────────────────────────┐
│  4,5          │ 5★ ████████████████   2          │
│  ★★★★⯪        │ 4★ ████████           1          │
│  3 reseñas    │ 3★                    0          │
│               │ 2★                    0          │
│               │ 1★                    0          │
└───────────────┴──────────────────────────────────┘
┌──────────────────────────────────────────────────┐
│ ★★★★★  Constructora Andina                       │
│ Contratado como Desarrollador Backend            │
│ 12 sep 2026 · Editada                            │
│ «Muy responsable con los plazos y con el equipo… │
│  …» (4 líneas)                        Leer más   │
│   ┌──────────────────────────────────────────┐   │
│   │ Tu respuesta · 14 sep 2026               │   │
│   │ «Gracias por la oportunidad, aprendí…»   │   │
│   │ (3 líneas)          Leer más             │   │
│   │                     Editar · Borrar      │   │
│   └──────────────────────────────────────────┘   │
│                                        Reportar  │
└──────────────────────────────────────────────────┘
┌──── segunda reseña más reciente, sin respuesta ──┐
│ …                           Responder · Reportar │
└──────────────────────────────────────────────────┘
[Ver todas las reseñas (3)]
```

**4. Ventana «Ver todas»**

```
┌ Reseñas de empresas ───────────────────────────── ✕ ┐
│ 4,5 ★★★★⯪ · 3 reseñas                               │
│ 5★ ████████ 2   4★ ████ 1   3★ 0   2★ 0   1★ 0      │  ← las barras filtran
│                                                     │
│ Estrellas [Todas] [5★ 2] [4★ 1] [3★ 0] [2★ 0] [1★ 0] │
│ Empresa [Todas ▾]            Orden [Más recientes ▾] │
│ ─────────────────────────────────────────────────── │
│ tarjeta con el texto completo                       │
│ tarjeta …                                           │
│ ─────────────────────────────────────────────────── │
│ Se ven 3 de 3                        [Quitar filtros]│
└─────────────────────────────────────────────────────┘
```

**5. Paso «Reportar»** (dentro de la misma ventana)

```
┌ Reportar la reseña de Constructora Andina ───────── ✕ ┐
│ ★★☆☆☆  «texto de la reseña…»                          │
│ ¿Qué problema tiene?                                  │
│ ( ) Tiene insultos o lenguaje ofensivo                │
│ ( ) Revela datos personales o de salud                │
│ ( ) Es discriminatoria                                │
│ ( ) Es falsa: no trabajé ahí o cuenta hechos que no   │
│     pasaron                                           │
│ ( ) Otro motivo                                       │
│ Cuéntanos más [_____________________________] 0/500   │
│ La reseña sigue visible mientras la revisamos.        │
│                          [Volver] [Enviar reporte]    │
└───────────────────────────────────────────────────────┘
```

**5 bis. Paso «Responder»** (dentro de la misma ventana)

```
┌ Responder a la reseña de Constructora Andina ────── ✕ ┐
│ ★★☆☆☆  «texto de la reseña…»                          │
│ Tu respuesta [_____________________________]          │
│              0/500 · mínimo 30                        │
│ La verán las empresas donde te postules, debajo de la │
│ reseña. Podrás editarla o borrarla durante 30 días.   │
│                       [Volver] [Publicar respuesta]   │
└───────────────────────────────────────────────────────┘
```

**6. Bloque de la empresa autora en la ficha** (el estado «se puede escribir»)

```
La reseña de Constructora Andina
Contratado el 28/08/2026 como Desarrollador Backend
Calificación  ☆ ☆ ☆ ☆ ☆   Buena        ← el rótulo cambia con la estrella
Opinión       [______________________________________]
              0/1000 · mínimo 30
La verán la persona, que podrá responderla, y las demás
empresas donde postule. Podrás editarla o borrarla durante 30 días.
                                         [Publicar reseña]
```

**7. Menú «Columnas» y celda**

```
☐ Ver los criterios en la tabla
─────────────────────────
☑ #
☑ Veredicto
☑ Estado
☑ Ciudad
☐ Reseñas          ← nueva, apagada al abrir la vacante

… | Estado      | Ciudad | Reseñas ▾   |
… | Contratado  | Lima   | ★ 4,5 (3)   |
… | Prueba      | Cusco  | —           |
```

**8. Moderación** (Configuración › Reseñas reportadas)

```
[Pendientes (2)] [Resueltas]
┌──────────────────────────────────────────────────┐
│ Reseña · reportada el 20/10/2026                 │
│ Constructora Andina → Luis Perez      ★☆☆☆☆      │
│ «texto de la reseña…»                            │
│ Motivo: Revela datos personales o de salud       │
│ «comentario del candidato»                       │
│ Nota de la revisión [__________________________] │
│                            [Mantener] [Ocultar]  │
└──────────────────────────────────────────────────┘
```

- **Estados a dibujar:** normal; vacío (sin reseñas);
  cargando; con error y reintento; con datos largos (1000 caracteres, nombre de empresa largo,
  50 reseñas en la ventana); cada uno de los seis estados del bloque de la empresa autora
  (punto 7).
- **Estados de la respuesta:** sin respuesta; respondida y editable; respondida y fija;
  reportada por la empresa y en revisión; ocultada por la plataforma (atenuada, con la nota);
  una respuesta de 500 caracteres en móvil.
- **Móvil y escritorio:**
  - **En móvil:**
    - El resumen se apila: el promedio arriba y las barras debajo.
    - La ventana ocupa toda la pantalla.
    - Los chips de estrellas van en una fila con scroll horizontal.
    - Empresa y Orden van uno debajo del otro.
    - La línea de la cabecera se mantiene en una sola línea.
  - **En el panel**, la columna se comporta como las demás.
- **A criterio de quien implementa:**
  - Espaciado, animaciones, el color de las estrellas y el formato de fechas, siempre que siga
    el que ya usa cada frontend.
  - El formato del número debe ser el mismo que usan las notas del panel.
- **Accesibilidad:**
  - Las estrellas que se muestran llevan un texto alternativo, como «4,5 de 5 estrellas».
  - Las estrellas que se eligen al escribir son un grupo de opciones que se maneja con el
    teclado (flechas) y lleva sus rótulos.

## Criterios de aceptación

- AC-01: Dada una postulación `CONTRATADO` de la empresa A hace 31 días, cuando un
  Responsable de área de esa vacante abre su ficha, entonces ve el formulario de reseña y puede
  publicarla.
- AC-02: Dada una postulación `CONTRATADO` hace 10 días, cuando se abre su ficha, entonces el
  bloque dice «Podrás dejar una reseña desde el [fecha de contratación + 30 días]» y no hay
  formulario. El backend rechaza una publicación directa por la API.
- AC-03: Dada una postulación de la empresa A en cualquier estado distinto de `CONTRATADO`,
  cuando alguien intenta reseñarla por la API, entonces se rechaza.
- AC-04: Dada una postulación `CONTRATADO` de la empresa A, cuando un usuario de la empresa B
  intenta reseñarla por la API, entonces se rechaza **sin confirmar que la postulación
  existe** (404, como el resto del panel).
- AC-05: Dado un usuario sin `resenar_contratado`, cuando abre la ficha de un contratado,
  entonces no ve el formulario. Si llama a la API, recibe el rechazo.
- AC-06: Dado un formulario sin estrellas, o con menos de 30 caracteres de opinión, o con más
  de 1000, cuando se pulsa «Publicar», entonces se avisa junto al campo y no se envía nada. El
  backend aplica las mismas reglas.
- AC-07: Dada una contratación ya reseñada, cuando otra persona de la misma empresa intenta
  publicar otra, entonces se rechaza y su bloque muestra la reseña existente.
- AC-08: Dada una reseña publicada hace 5 días, cuando la empresa la edita, entonces se
  guarda, sale marcada «Editada» y el promedio se recalcula.
- AC-09: Dada una reseña publicada hace 31 días, cuando se abre la ficha, entonces no hay
  «Editar» ni «Borrar», y la API rechaza cambios.
- AC-10: Dada una reseña borrada dentro del plazo, cuando se vuelve a abrir la ficha, entonces
  la contratación se puede reseñar de nuevo, y la reseña borrada ya no cuenta en ningún sitio.
- AC-11: Dada una reseña recién publicada, cuando el candidato mira su campana, entonces tiene
  el aviso «[Empresa] te dejó una reseña», y el enlace lleva a la sección de reseñas.
- AC-12: Dado un candidato con 3 reseñas visibles (5, 5 y 4 estrellas), cuando abre `/perfil`,
  entonces la cabecera dice «★ 4,7 · 3 reseñas», el índice dice «Reseñas 3», y la sección
  muestra el resumen con el reparto y las 2 más recientes.
- AC-13: Dado un candidato sin reseñas, cuando abre `/perfil`, entonces la cabecera no muestra
  la línea de reseñas, y la sección muestra el estado vacío con su explicación.
- AC-14: Dado un candidato con 2 reseñas o menos, cuando mira la sección, entonces no aparece
  «Ver todas». Con 3 o más, sí aparece, y muestra el total.
- AC-15: Dada la ventana «Ver todas» con reseñas de 1, 4 y 5 estrellas de dos empresas, cuando
  el usuario marca «5★» y elige una empresa, entonces solo se ven las que cumplen ambas
  condiciones, y el pie dice «Se ven X de N». Al pulsar la barra «4★» del resumen, el filtro de
  estrellas pasa a 4★.
- AC-16: Dada la ventana con el orden «Peor calificadas», cuando se aplica, entonces las
  reseñas van de menos a más estrellas y, a igualdad, de la más reciente a la más antigua.
- AC-17: Dadas reseñas de una sola empresa, cuando se abre la ventana, entonces no aparece el
  filtro «Empresa».
- AC-18: Dada una reseña visible, cuando el candidato la reporta con motivo «Otro» sin
  comentario, entonces no se envía. Con comentario, se envía, y la tarjeta muestra «Reportada ·
  en revisión», sigue visible y sigue contando en el promedio.
- AC-19: Dada una reseña ya reportada y pendiente, cuando el candidato la mira, entonces no
  puede reportarla otra vez.
- AC-20: Dado un reporte pendiente, cuando el Administrador de la plataforma pulsa «Ocultar»
  con una nota, entonces la reseña desaparece del perfil y del panel de las demás empresas, deja
  de contar en el promedio, la empresa autora la ve atenuada con la nota, y el candidato recibe
  el aviso en la campana.
- AC-21: Dado un reporte pendiente, cuando se pulsa «Mantener» con una nota, entonces nada
  cambia en la reseña y el candidato recibe «la mantuvimos».
- AC-22: Dado un usuario de una empresa que no es la plataforma, o sin `moderar_resenas`,
  cuando busca la pantalla «Reseñas reportadas», entonces no la ve, y la API se la niega.
  Una empresa dada de alta después no recibe ese permiso.
- AC-23: Dado un postulante con reseñas de otras empresas, y un usuario de la empresa B con
  `ver_resenas_candidato`, cuando abre la tabla de su vacante, entonces la columna «Reseñas»
  está en el menú «Columnas», apagada. Al encenderla ve «★ 4,5 (3)», y en las filas sin
  reseñas ve «—».
- AC-24: Dada la columna encendida, cuando se pulsa su cabecera, entonces la tabla se ordena
  por promedio (y, a igualdad, por número de reseñas), con los «—» al final. El tercer clic
  vuelve al orden del servidor.
- AC-25: Dada la columna «Reseñas», cuando se cambia de etapa dentro de la misma vacante,
  entonces la columna sigue disponible en las cinco etapas.
- AC-26: Dado un postulante con reseñas, cuando la empresa B abre su ficha, entonces ve el
  bloque «Reseñas de empresas», con los textos completos y el nombre de cada empresa autora,
  y sin «Reportar».
- AC-27: Dado un usuario sin `ver_resenas_candidato`, cuando abre la tabla o la ficha,
  entonces no hay columna ni bloque de reseñas, y el DTO no trae los datos.
- AC-28: Dada cualquier reseña, cuando se calcula la nota de la persona, su orden en el
  ranking o el pase automático, o cuando se llama a la IA, entonces el resultado es idéntico al
  que habría sin la reseña.
- AC-29: Dado un candidato con reseñas, cuando descarga sus datos desde `/perfil`, entonces la
  descarga incluye sus reseñas visibles, sus respuestas y sus reportes con el resultado.
- AC-30: Dada una persona anonimizada por el borrado de datos, cuando cualquiera busca sus
  reseñas, entonces ya no existen, y tampoco sus respuestas. La auditoría conserva que hubo
  una reseña o una respuesta, pero no su texto.
- AC-31: Dado el móvil, cuando se abre la ventana «Ver todas», entonces ocupa la pantalla, los
  chips se desplazan en horizontal y nada se sale del ancho.
- AC-32: Dado un usuario que solo usa el teclado, cuando escribe una reseña, entonces puede
  elegir las estrellas con las flechas y oye el rótulo. Cuando abre y cierra «Ver todas», el
  foco vuelve al botón que la abrió.
- AC-33: Dada una reseña visible sin respuesta, cuando la persona pulsa «Responder» y publica
  un texto de 30 a 500 caracteres, entonces la respuesta sale debajo de la reseña en su perfil,
  en el bloque de la empresa autora y en el de la empresa B, el botón «Responder» desaparece y
  el promedio no cambia.
- AC-34: Dada una respuesta vacía, de menos de 30 caracteres o de más de 500, cuando se pulsa
  «Publicar respuesta», entonces se avisa junto al campo y no se envía nada. El backend aplica
  las mismas reglas y rechaza una segunda respuesta a la misma reseña.
- AC-35: Dada una cuenta del portal que no es la persona reseñada, o cualquier usuario del
  panel, cuando intenta responder por la API, entonces se rechaza.
- AC-36: Dada una respuesta publicada hace 5 días, cuando la persona la edita, entonces se
  guarda y sale «Editada». Publicada hace 31 días, no hay «Editar» ni «Borrar» y la API rechaza
  cambios. Borrada dentro del plazo, la persona puede volver a responder.
- AC-37: Dada una reseña publicada hace 28 días y respondida hace 27, cuando la empresa la
  edita, entonces su formulario avisa antes de guardar, la persona recibe «[Empresa] editó su
  reseña…» en la campana, y puede editar su respuesta durante 30 días desde esa edición (no 3).
- AC-38: Dada una reseña respondida, cuando la empresa la borra, entonces la respuesta
  desaparece con ella. Cuando la plataforma la oculta, la respuesta tampoco se ve.
- AC-39: Dada una respuesta, cuando la empresa autora la reporta y la plataforma pulsa
  «Ocultar» con una nota, entonces la respuesta deja de verse para todas las empresas; la
  persona la ve atenuada con la nota, sin «Editar», «Borrar» ni «Responder», y recibe el aviso
  en la campana. Con «Mantener», nada cambia y la persona no recibe aviso.
- AC-40: Dada una respuesta, cuando un usuario de la empresa B (que no escribió la reseña)
  abre la ficha, entonces no ve «Reportar la respuesta», y la API le niega el reporte.
- AC-41: Dada una reseña reportada y pendiente, cuando la persona la responde, entonces la
  respuesta se publica y el reporte sigue pendiente.

## Casos límite y errores

- **Datos vacíos o inválidos:**
  - Estrellas fuera de 1–5, o que no sean un número entero → rechazo.
  - Un texto que solo tiene espacios cuenta como vacío, también en la respuesta.
  - En «Otro motivo», el comentario es obligatorio, tanto en el reporte del candidato como en
    el de la empresa.
- **Usuario sin permisos:**
  - Sin permiso, el control no se pinta, porque el DTO lleva `puedeResenar` o
    `puedeVerResenas`. Pero quien decide es el backend.
  - Con alcance `SUS_VACANTES`, un Responsable de área que no es de esa vacante recibe un 404,
    no un 403. El mensaje debe decir que la contratación está fuera de su alcance.
  - El candidato solo puede reportar y responder sus propias reseñas.
  - Solo la empresa autora de la reseña puede reportar su respuesta, y dentro de ella solo
    quien tiene `resenar_contratado` en esa contratación.
- **Recarga, reintento o doble envío:**
  - «Publicar», «Publicar respuesta», «Enviar reporte», «Mantener» y «Ocultar» quedan
    deshabilitados mientras trabajan.
  - La unicidad por contratación y la de una respuesta por reseña las garantiza la base de
    datos, no solo la pantalla.
  - Reportar dos veces la misma reseña, o la misma respuesta, se rechaza.
- **Otros casos relevantes:**
  - **Contrataciones por el camino manual:** el camino de `/transiciones` a `CONTRATADO` no
    crea la decisión. Cuentan igual: lo que manda es el estado de la postulación y la fecha de
    su transición.
  - **La ventana vence mientras se edita:** manda el reloj del servidor.
  - **La empresa cambia de nombre:** las reseñas muestran el nombre actual.
  - **Una persona contratada dos veces por la misma empresa:** tiene dos reseñas, y las dos
    cuentan en el promedio.
  - **Muchas reseñas:** la ventana las trae todas en una sola llamada. Con más de 50, el
    scroll sigue fluido. La paginación queda fuera, porque hoy no hay volumen para
    justificarla.
  - **La tabla con la columna encendida:** el promedio de toda la tanda se carga en bloque, en
    la misma consulta del ranking, no fila por fila.
  - **La columna se apaga al recargar:** es la convención actual del panel, no un error.
  - **Contenido sensible en el texto** (salud, afiliación sindical, datos de terceros): no se
    filtra automáticamente, ni en la reseña ni en la respuesta. La vía es el reporte. Los
    avisos de los dos formularios recuerdan que lo verán otras empresas.
  - **La empresa edita la reseña varias veces:** cada edición vuelve a abrir los 30 días de la
    respuesta y avisa a la persona. Como la empresa solo puede editar durante sus propios 30
    días, esto no se alarga indefinidamente.
  - **La respuesta y el reporte de la misma reseña a la vez:** si la plataforma oculta la
    reseña, la respuesta deja de verse con ella, pero no se borra. Queda en la descarga de
    datos de la persona.
  - **La empresa autora pierde el acceso**, por ejemplo cuando se da de baja a quien la
    escribió: la reseña sigue siendo de la empresa.

## Datos y compatibilidad

- **Información que debe guardarse:**
  - **Reseña:**
    - La postulación. Es única mientras la reseña está viva.
    - La empresa autora y la persona reseñada.
    - Las estrellas y el texto.
    - Quién la escribió.
    - Cuándo se publicó y, si se editó, cuándo.
    - El estado: visible u ocultada, y la nota de la ocultación.
  - **Respuesta:**
    - La reseña a la que responde. Es única por reseña mientras está viva.
    - El texto.
    - Cuándo se publicó y, si se editó, cuándo.
    - Hasta cuándo se puede editar: se mueve cuando la empresa edita la reseña (punto 20).
    - El estado: visible u ocultada, y la nota de la ocultación.
    - Si va en la tabla de la reseña o en una propia queda a criterio de quien implementa.
  - **Reporte:**
    - Qué se reporta: la reseña o su respuesta.
    - Quién reporta: la persona reseñada, o la empresa autora y el usuario que lo hizo.
    - El motivo y el comentario.
    - Cuándo se hizo.
    - El estado: pendiente, mantenida, ocultada o retirada.
    - Quién lo resolvió, cuándo y con qué nota.
  - **Auditoría:** crear, editar y borrar reseñas y respuestas, reportar y resolver. **Sin
    copiar el texto de la reseña ni de la respuesta**: la tabla de auditoría no se puede
    modificar, y un texto guardado allí sobreviviría al borrado de datos.
- **Migración:**
  - Será la **V63**. Si antes entra la `V62__condiciones_del_portal_y_aceptacion.sql` del
    worktree `cumplimiento-legal`, será la **V64**. Hay que comprobarlo contra la base
    compartida antes de arrancar.
  - La migración también:
    - Crea las tablas de reseñas y reportes (y la de respuestas, si va aparte).
    - Siembra los tres permisos nuevos.
    - Concede `resenar_contratado` y `ver_resenas_candidato` a los roles de **todas las
      empresas existentes**, no solo a los de Renaser.
    - Concede `moderar_resenas` solo al Administrador de la plataforma.
- **Alta de empresas:** `moderar_resenas` se excluye de la copia de permisos, igual que
  `administrar_plataforma`.
- **Backend:**
  - **Portal:**
    - Un resumen (promedio, cantidad y reparto) que viaja con el perfil del portal, para la
      cabecera.
    - Lista de reseñas, cada una con su respuesta.
    - Reportar.
    - Responder, editar y borrar la respuesta.
  - **Panel:**
    - Reseñas de la persona de una postulación: resumen, lista con las respuestas, estado de
      «la reseña de mi empresa» y `puedeResenar`.
    - Publicar, editar y borrar.
    - Reportar la respuesta a una reseña propia.
    - `FilaRanking.resenas` (promedio y cantidad, o nulo) y `RankingVacante.puedeVerResenas`.
      Hay que añadirlo también donde se copian las filas al numerarlas.
  - **Plataforma:** lista de reportes y resolución.
  - **Reglas del backend:**
    - Todo pasa por el alcance de la empresa. Hay una regla automática: ningún servicio del
      panel busca por id suelto.
    - La lectura entre empresas es la única excepción, y se hace por la persona de postulaciones
      que ya son visibles.
    - El servicio recibe un `Clock`, para poder probar los plazos de 30 días.
- **Frontend (`RenaserOsPostulantes`):**
  - En `columnasDelRanking`:
    - Añadir la columna nueva, arrancando dentro de `apagadas`.
    - Añadir su celda.
    - Añadir `'resenas'` a `ColumnaOrdenable` y a sus tablas de orden.
  - Componentes compartidos por el portal y el panel:
    - Resumen con reparto.
    - Tarjeta de reseña.
    - Ventana con filtros.
    - Selector de estrellas.
  - Los pasos «Reportar» y «Responder» dentro de la misma ventana, porque el `Modal` tiene un
    `id` fijo y no admite dos abiertas.
  - En el panel, el paso «Reportar la respuesta» del bloque de la empresa autora.
- **Comportamiento para registros existentes:**
  - Las contrataciones ya hechas se pueden reseñar si pasaron 30 días desde su transición a
    `CONTRATADO`.
- **Restricciones de compatibilidad:**
  - Los campos nuevos en los DTO son opcionales y compatibles hacia atrás.
  - El Excel no cambia.
  - Hay que actualizar el
    [diseño del candidato ante varias empresas](../../docs/superpowers/specs/2026-08-25-el-candidato-ante-varias-empresas-design.md)
    para dejar escrita la excepción.

## Verificación

- **Actor/rol y datos de prueba necesarios:**
  - **Empresa A (Renaser):**
    - Un Responsable de área de la vacante.
    - Un Responsable de área de otra vacante, para probar el alcance.
    - Dirección.
  - **Empresa B:** creada con el alta de empresas, con un usuario Talento.
  - **Plataforma:** el Administrador de la plataforma.
  - **Candidatos:** uno con 3 reseñas de dos empresas y otro sin ninguna.
  - **Contrataciones:** hay que sembrarlas insertando la transición a `CONTRATADO` con una
    fecha pasada, de 10 y de 31 días. La tabla no deja modificar una transición, pero sí
    insertarla.
  - **Una reseña publicada hace 31 días**, para el plazo de edición.
  - **Una reseña publicada hace 28 días y respondida hace 27**, para el aviso al editarla,
    y **una respuesta publicada hace 31 días**, para su plazo de edición.
  - **El mismo candidato postulando a una vacante de la empresa B**, para la vista entre
    empresas.
- **Recorridos que deben quedar como E2E repetibles:**
  1. La empresa A publica una reseña, la edita y la ve marcada «Editada». El candidato recibe
     el aviso en la campana y la ve en su perfil (cabecera, índice y sección).
  2. Un candidato con 3 reseñas abre «Ver todas», filtra por estrellas y por empresa, cambia
     el orden y quita los filtros.
  3. El candidato reporta una reseña. El Administrador de la plataforma la oculta. El
     promedio cambia en el perfil y en la tabla de la empresa B.
  4. La empresa B enciende la columna «Reseñas», ordena por ella y abre la ficha con el bloque
     de reseñas.
  5. Los rechazos por la API: contratación de 10 días, empresa ajena, reseña fuera de plazo y
     doble publicación.
  6. La persona responde a una reseña. La empresa A y la empresa B ven la respuesta en la
     ficha. La empresa A edita la reseña y la persona recibe el aviso y edita su respuesta.
  7. La empresa A reporta la respuesta y la plataforma la oculta: la empresa B deja de verla
     y la persona la ve atenuada con el aviso en la campana.
  8. Los rechazos de respuestas por la API: otra cuenta del portal, un usuario del panel,
     segunda respuesta, respuesta a una reseña borrada, edición fuera de plazo y reporte de
     la respuesta por la empresa B.
- **Riesgos que QA debe explorar con navegador:**
  - La ventana en el móvil, con textos de 1000 caracteres y nombres de empresa largos.
  - Los pasos de reportar y de responder dentro de la ventana, y a dónde vuelve el foco.
  - La tarjeta con respuesta en móvil: reseña de 1000 caracteres y respuesta de 500.
  - La cabecera del perfil con y sin reseñas.
  - El índice lateral, que marque bien la sección al hacer scroll.
  - El orden de la columna con empates y con «—».
  - Los seis estados del bloque de la empresa autora.
  - Que ninguna estrella del panel use el violeta `--activo`.
- **Resultado que quiero comprobar personalmente:**
  - Ver mi perfil de candidato con reseñas, responder a una y ver la respuesta desde el panel.
  - En el panel, encender la columna «Reseñas» en una vacante real y leer las reseñas desde
    la ficha.
  - Hay inicio de sesión de por medio, así que la comprobación visual la hace el usuario.

## Dependencias y dudas

- Requiere:
  - **Contrataciones hechas por la API.** El botón para contratar llega con el apartado 1 de la
    ampliación y no bloquea esta spec.
  - **El texto de consentimiento** que mencione las reseñas lo prepara el usuario aparte, con
    el abogado. Esta spec no depende de él.
  - **Coordinar el número de migración** con el worktree `cumplimiento-legal`.
  - **Cambios en los dos repositorios:** el backend `RENASER-RECLUTAMIENTO` y el frontend
    `RenaserOsPostulantes`.
  - **Numerar los RF desde RF-171.** Los números del 155 al 170 ya se usan en dos documentos
    distintos.
  - Esta función **no es un apartado de la ampliación de RR.HH.** Cuándo se programa lo decide
    el usuario, sin alterar el orden de la ampliación.
- Decisiones pendientes: ninguna.

### Decisiones tomadas en esta spec

- **Confirmadas por el usuario el 28/09/2026:**
  - Contratar sigue por la API.
  - El texto de consentimiento queda fuera: las reseñas se comparten con las demás empresas
    desde el primer día.
  - Las demás empresas ven el promedio y los textos, con el nombre de la empresa autora.
  - El candidato puede reportar una reseña, y la plataforma la modera.
  - El candidato no acepta ni rechaza reseñas, pero puede responder a cada una.
  - La reseña se puede escribir a los 30 días de la contratación, y se puede editar o borrar
    durante 30 días.
  - Una reseña por contratación, no por empresa.
  - La opinión es obligatoria, de 30 a 1000 caracteres.
  - La firma la empresa, no la persona que la escribe.
  - La reseña muestra el puesto: «Contratado como …».
  - Pueden escribirla Talento y Dirección (en todas las vacantes) y Responsable de área
    (solo en las suyas).
  - Los avisos van solo por la campana, sin correo.
- **Propuestas en la spec, sin objeción:**
  - Pueden verla Talento, Responsable de área y Dirección.
  - Solo modera el Administrador de la plataforma.
  - Mientras hay un reporte pendiente, la reseña sigue visible.
  - Ocultar una reseña es definitivo, y la empresa no puede apelar.
  - La sección va al final del perfil.
  - Filtros de la ventana: estrellas, empresa y orden.
  - La columna no va en el Excel.
  - **Sobre las respuestas** (añadidas el 28/09/2026):
    - Una respuesta por reseña, de 30 a 500 caracteres, que se puede editar o borrar durante
      30 días. No hay hilo.
    - Si la empresa edita una reseña ya respondida, se avisa a la persona y su plazo para
      editar la respuesta vuelve a empezar.
    - La empresa autora puede reportar la respuesta, y la plataforma la modera en la misma
      pantalla. Ocultarla es definitivo.
    - La empresa no recibe aviso de la respuesta: el panel no tiene campana.
