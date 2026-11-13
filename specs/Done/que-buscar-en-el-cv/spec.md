# Qué buscar en el CV
Estado: TERMINADO

Escrita el 21/10/2026 contra `main` (backend hasta V77 y el #126; frontend hasta el #88).

## Objetivo

Hoy la IA califica cada CV con los mismos ocho criterios generales para cualquier puesto. Para
un chef, «experiencia en comida saludable» no es un criterio general, y quien recluta tiene que
abrir cada CV para comprobarlo.

Esta spec deja que quien crea la vacante escriba, en una casilla privada, hasta seis cosas
concretas que busca en el CV. La IA revisa cada una en cada CV y dice si la persona **la tiene**,
**la menciona sin detalle** o **no aparece**, con la frase del CV que lo prueba. El equipo lo ve
de un vistazo en el ranking (puntos de color bajo el nombre), en detalle en la ficha y en el
Excel. Además, el resultado entra en la nota del CV con un peso que vive en la versión de pesos
(25 % por defecto).

Es para el equipo de selección (Talento, Dirección y responsables de área). **El candidato nunca
ve ni la casilla ni los resultados.**

## Contexto

### Comportamiento actual

- **La vacante** guarda sus textos en columnas sueltas (`Vacante.java:44-48`). `requisitos` es
  un texto libre y público: sale en el portal y en el contexto de WhatsApp
  (`FichaPublica.java:133-144`). Los requisitos objetivos (`requisito_objetivo`) son otra cosa:
  preguntas de sí o no que el candidato contesta al postular.
- **El formulario** de crear y editar es el mismo (`CamposDeLaVacante.tsx`, `CamposComunes`
  :238). Editar manda el formulario entero (`PUT /api/v1/panel/vacantes/{id}`), y un campo que
  falta se guarda vacío (`ServicioVacantesPanelImpl.editar`, :427-434).
- **La calificación del CV** va por la cola de IA:
  - El agente `EVIDENCIA_CV` (`AgenteEvidenciaCv`) pone a cada uno de los ocho criterios una
    nota de 0 a 100. Se guarda en `nota_criterio`.
  - Corre a la vez que otros agentes: `A_LA_VEZ_RAPIDA` en la criba y `A_LA_VEZ` en la pasada
    completa (`ColaCalificacionIaImpl:46-55`).
  - Cuando todos terminan, una barrera lanza `POTENCIAL_RIESGO`
    (`RegistroTrabajosIa.crearElRetratoSiLosDemasAcabaron:251-298`). Ese agente escribe la nota
    del Perfil Integral, y el pase automático decide con ella.
  - Un trabajo `TERMINADO` no se repite nunca. Uno `FALLIDO` se repite si alguien lo vuelve a
    pedir (`situacionDe:872-896`).
- **La nota del CV no se guarda: se calcula cada vez.** La ponderación de los ocho criterios con
  `peso_criterio` está hoy en tres sitios:
  - `PuenteCalificacionIaImpl.notaCurriculum:931-955`, para la nota del Perfil Integral;
  - `ServicioPerfilIntegralPanelImpl.notaCurriculum:1020-1035`, para el ranking;
  - `InsumoPerfil.notaCurriculum`, lo que recibe `POTENCIAL_RIESGO`.
- **La versión de pesos** (`version_pesos` y sus tablas hijas) la elige cada vacante
  (`vacante.version_pesos_id`, nunca nulo).
  - Las versiones se crean y publican por API (`PesosController`, permiso
    `publicar_version_pesos`) o, como hará el usuario, por SQL.
  - Publicada, no se edita.
  - Cambiar la versión de una vacante con candidatos dentro ya funciona desde el 16/10/2026
    (V74, PR #124/#88). Pide confirmación y recalcula a todos sin IA y sin mover a nadie de
    etapa (`RecalculoConOtrosPesos`).
- **El ranking** (`GET /vacantes/{id}/ranking`, `ver_embudo`):
  - Devuelve todas las postulaciones, ordenadas por grupo de prioridad y luego por nota.
  - La «ficha» es una fila desplegada bajo el candidato (`Vacante.tsx:1891-1897`,
    `DetalleDelPostulante`), y su contenido cambia según la pestaña.
  - El Excel solo existe en «Perfil integral» y «Prueba del puesto»
    (`ServicioExcelRankingImpl:146-151`).
- **Los textos de la vacante que llegan a la IA** se juntan en tres sitios. Algunos alimentan
  generadores cuyo resultado ve el candidato (las preguntas recomendadas, la prueba, la
  simulación):
  - `DatosDeLaVacanteParaIa`;
  - `ContextoDeLaVacante.queBuscaLaVacanteDe`;
  - `PuenteCalificacionIaImpl.queBusca`.

### Decisiones tomadas con el usuario (20 y 21/10/2026)

1. **Casilla aparte y privada**, «Qué buscar en el CV», debajo de «Lo que se busca». No se
   reutilizan los requisitos públicos (se eligió la opción 2 de la maqueta).
2. **Tres estados por línea:**

   | Estado | Cuenta |
   |---|---|
   | Lo tiene | 100 |
   | Lo menciona, sin detalle | 50 |
   | No aparece | 0 |

   «Lo tiene» exige una frase literal del CV.
3. **Ranking con puntos de color** bajo el nombre, en todas las pestañas, y un selector
   «Orden: De siempre | Por lo buscado». No se añaden columnas al ranking (opción B).
4. **Entra en la nota del CV, no encima de ella.**
   - Nota del CV = criterios generales × (1 − p) + lo buscado × p.
   - Lo buscado es el promedio de sus líneas.
   - El peso del CV en el Perfil Integral y en la nota final no cambia: los pesos siguen
     sumando 100.
5. **El peso p vive en la versión de pesos**, 25 % por defecto, de 0 a 50.
   - No se edita desde el panel. El usuario crea versiones nuevas por SQL.
   - Para cambiarlo en una vacante, se elige otra versión con el desplegable de siempre, que ya
     recalcula a todos.
   - Una pantalla para editar pesos queda para otra spec.
6. **Sin textos de ayuda en las pantallas.** Etiquetas, chips, estados y errores sí; párrafos
   explicativos bajo los campos, no.

### Decisiones de diseño de esta spec

7. **La búsqueda es un agente nuevo**, no una ampliación de `EVIDENCIA_CV`.
   - Así, editar las líneas vuelve a buscar sin volver a calificar los ocho criterios.
   - La instrucción de `EVIDENCIA_CV` (global y editable desde el panel) no se toca.
8. **La búsqueda corre cuando la IA califica el CV**, como un paso más de la criba y de la
   pasada completa.
   - El Perfil Integral espera a que termine o falle del todo. Así el pase automático decide
     con la nota completa.
   - No hay una búsqueda aparte al postular: no se gasta IA en quien la criba no va a calificar.
9. **«Lo tiene» sin frase comprobable baja a «Lo menciona».** El servidor busca la frase en el
   texto del CV. Si no la encuentra, guarda «Lo menciona» sin frase.
10. **Editar las líneas con candidatos dentro vuelve a buscar en todos los CV**, tras una
    confirmación.
    - Los resultados viejos se archivan, no se borran.
    - Las notas se recalculan sin mover a nadie de etapa, con el mismo principio que el cambio
      de pesos de la V74.

### Decisión tomada con el usuario (21/10/2026)

11. **La maqueta, versión 23, queda como referencia.** La lista de lo que no se puede buscar
    permite los documentos que pide la ley para el puesto, como «Carné de salud vigente».

### Pantallas o procesos afectados

- **Panel**:
  - el formulario de crear y editar vacante;
  - el ranking (las cinco pestañas), su ficha y su Excel;
  - la confirmación del cambio de versión de pesos.
- **Backend**:
  - la vacante (guardar las líneas y su confirmación);
  - la cola de IA (agente nuevo, barrera y modo de rebúsqueda);
  - la nota del CV (los tres sitios);
  - el ranking, la ficha y el Excel;
  - la versión de pesos (un campo nuevo, su copia y la previsión del cambio).

### Referencias

- Maqueta: <https://claude.ai/artifact/RxaAqJcg4eYj26Kg5A1YZy> (fila «Versión final»).
- `specs/Draft/pesos-con-candidatos-y-fin-del-prestamo.md`: el cambio de versión con
  candidatos dentro, ya implementado (#124/#88).
- `specs/Done/selector-de-columnas-del-ranking.md` y `specs/Done/rubrica-vigente-en-el-ranking.md`.
- Patrones a copiar:
  - el modo `RECALIFICA` y `versionGuia` del evaluador (V66, `ColaCalificacionIaImpl:253-266`,
    `PuenteCalificacionIaImpl:482-490`);
  - `EnvolturaDeGuia`, que envuelve el texto escrito por la empresa contra la inyección de
    instrucciones.

## Alcance

**Incluye:**

- La casilla «Qué buscar en el CV» al crear y al editar la vacante: hasta 6 líneas, con bloqueo
  de lo que no se puede usar para elegir a nadie.
- El agente nuevo que busca las líneas en el CV, con su instrucción, su formato y la comprobación
  de la frase.
- Correr la búsqueda junto a la calificación del CV y hacer que el Perfil Integral la espere.
- Volver a buscar en todos los CV al editar las líneas, con confirmación, y recalcular sin mover
  a nadie.
- La nota del CV con lo buscado dentro, igual en los tres sitios donde se calcula.
- El peso en la versión de pesos:
  - 25 en todas las versiones que existen, también las copias de las empresas;
  - copiarlo al copiar una versión;
  - mostrarlo en la confirmación del cambio de versión.
- En el ranking:
  - los puntos de color en las cinco pestañas;
  - el orden «Por lo buscado»;
  - el bloque «Qué buscar en el CV» en la ficha, con la nota del CV y una lista con una fila
    por línea buscada.
- Una columna nueva en el Excel del ranking.
- «Volver a buscar» en la ficha cuando la búsqueda falló.
- Que nada de esto llegue al candidato: portal, «Mis procesos», WhatsApp, correos, avisos de
  campana y agentes que generan lo que el candidato ve.

**Queda fuera:**

- Una pantalla para crear o editar versiones de pesos, o el peso de «Qué buscar en el CV».
  Va en otra spec. Mientras tanto, el usuario crea versiones por SQL.
- Cambiar los ocho criterios generales, sus pesos o la instrucción de `EVIDENCIA_CV`.
- Usar las líneas para descartar a nadie, o para mover a alguien de etapa por sí solas.
- Volver a calificar los ocho criterios al editar las líneas.
- OCR para CV escaneados: siguen sin texto.
- El fallo genérico de `EVIDENCIA_CV` con CV sin texto (no guarda el motivo). Esta spec solo
  hace que la búsqueda lo distinga.
- Copiar las líneas desde otra vacante.

## Comportamiento esperado

### Flujo 1 · El equipo escribe qué buscar

1. En «Nueva vacante» y en «Editar vacante», debajo de «Lo que se busca, un requisito por
   línea», aparece un recuadro con:
   - la etiqueta «Qué buscar en el CV, uno por línea»;
   - el chip «Solo lo ve tu equipo» con un candado;
   - un área de texto.

   No lleva texto de ayuda.
2. Debajo, el recuadro «La IA buscará esto en cada CV que llegue», con el recuento «N de 6
   como máximo», lista las líneas válidas mientras se escribe.
   - Las líneas vacías no cuentan.
   - Una línea repetida (sin distinguir mayúsculas ni espacios) cuenta una vez.
   - Una línea que pase de 6 sale tachada con «No se buscará: el máximo es 6.».
   - Sin líneas sale «Nada todavía. Sin líneas, la ficha no muestra el checklist.».
3. **Lo que no se puede buscar.** Si una línea pide edad, sexo o género, estado civil, hijos o
   embarazo, salud o discapacidad, religión, raza, etnia, nacionalidad u origen:
   - sale en rojo con «No se puede buscar: la edad, el sexo, el estado civil, los hijos, la
     salud, la religión o el origen no sirven para elegir a nadie.»;
   - el botón de guardar se apaga;
   - el servidor la rechaza igual (400) con la misma lista.

   Los documentos que pide la ley para el puesto sí se pueden buscar, por ejemplo «Carné de
   salud vigente» o «Carné sanitario». La lista vive en un solo sitio por repositorio, con
   pruebas de qué pasa y qué no.
4. Cada línea admite hasta 200 caracteres. El servidor recorta los espacios de los extremos.
5. **Guardar en una vacante sin candidatos calificados** guarda las líneas sin preguntar.
6. **Guardar en una vacante con candidatos calificados**, si las líneas cambiaron, abre una
   confirmación antes de guardar (ver Flujo 5).
7. Las líneas se guardan aparte de los textos públicos:
   - no cuentan como cambio visible (no avisan a los candidatos);
   - no tocan la revisión del contexto de WhatsApp (V77);
   - un formulario que no las envía no las borra.
8. Con la vacante cerrada o archivada, la casilla se ve en solo lectura.

### Flujo 2 · La IA busca en el CV

1. **Cuándo.** Cuando la IA califica un CV de una vacante con líneas, encola a la vez que
   `EVIDENCIA_CV` un trabajo del agente nuevo. Pasa tanto en la criba como en la pasada
   completa. Si ese trabajo ya está `TERMINADO` para las líneas vigentes, se respeta igual que
   los demás.
2. **Qué recibe el agente:**
   - el texto anonimizado del CV, el mismo que usa `EVIDENCIA_CV`;
   - el título del puesto;
   - las líneas vigentes, numeradas y envueltas como texto de la empresa.

   Todo va en el mensaje de datos, no en el de sistema, para no romper la caché de prefijo de
   DeepSeek. La instrucción le pide no deducir nada de lo prohibido del Flujo 1 (edad, origen,
   etc.) a partir del nombre, las fechas o la foto.
3. **Qué devuelve, por cada línea:**
   - el estado: `TIENE`, `MENCIONA` o `NO_APARECE`;
   - la frase literal del CV, obligatoria en `TIENE`;
   - una nota breve, de una frase;
   - para `MENCIONA` y `NO_APARECE`, una pregunta para la entrevista.
4. **El servidor comprueba la frase.** Busca la frase en el texto del CV, sin distinguir
   mayúsculas, tildes ni espacios repetidos.
   - Si no está, una línea `TIENE` se guarda como `MENCIONA` y sin frase.
   - Una línea que falte en la respuesta se guarda como `NO_APARECE`.
   - Una línea que sobre se ignora.
5. **El Perfil Integral espera a la búsqueda.** La barrera que lanza `POTENCIAL_RIESGO` cuenta el
   trabajo nuevo como uno más.
   - Si la búsqueda se queda sin reintentos, el perfil se arma igual, con lo que sí terminó, y
     la persona queda con «No se pudo buscar».
6. **CV sin texto.** Si el extractor dice que el CV no tiene texto (menos de 40 caracteres o
   `.doc` viejo):
   - la búsqueda se marca «CV sin texto» al primer intento, sin reintentos y sin llamar a la IA;
   - el motivo queda guardado.
7. **Tope mensual de IA.** El trabajo pasa por `frenarOPublicar` como los demás. Si la empresa
   está sin presupuesto, queda en espera y la persona sigue «Pendiente».
8. Cada llamada queda en `ejecucion_ia` con su costo, como los demás agentes.

### Flujo 3 · La nota del CV

1. **Lo buscado** de una persona es el promedio de sus líneas: Lo tiene = 100, Lo menciona = 50 y
   No aparece = 0.
2. **La nota del CV** se calcula así:
   - con líneas y búsqueda terminada: criterios generales × (1 − p) + lo buscado × p, donde p es
     el peso de la versión de pesos de la vacante, dividido entre 100;
   - sin líneas, con la búsqueda pendiente, fallida o con CV sin texto: los criterios generales
     solos, como hoy;
   - con p = 0, los resultados se ven pero la nota es la de hoy.
3. **Los tres sitios** que calculan la nota del CV usan una sola función con esta regla:
   - la nota del Perfil Integral;
   - el ranking;
   - lo que recibe `POTENCIAL_RIESGO`.
4. **Lo demás no cambia.** El peso del CV dentro del Perfil Integral (`peso_componente_perfil`),
   el de la etapa y la nota final se calculan como hoy, con la nota del CV nueva.
   - Si una versión da 0 al CV, lo buscado cambia la nota del CV que se ve, pero no el Perfil
     Integral.
5. **El grupo de prioridad** sale de la nota como hoy, así que puede cambiar.
   - Ejemplo de la maqueta con p = 25: Mariana sube a «Prioridad alta», Diego baja a «Potencial
     con riesgo» y Jorge a «No priorizado».

### Flujo 4 · El equipo mira los resultados

1. **Puntos de color en el ranking**, en las cinco pestañas, debajo del nombre y el correo:
   - un punto por línea, en el orden de las líneas;
   - verde: lo tiene;
   - ámbar: lo menciona;
   - blanco con borde: no aparece;
   - punteado: pendiente o buscando.

   En lugar de los puntos pueden salir las etiquetas «CV sin texto» o «No se pudo buscar». Sin
   líneas en la vacante no sale nada. Cada grupo de puntos lleva un texto oculto para lectores
   de pantalla, por ejemplo «3 de 4 requisitos» o «Buscando requisitos».
2. **Línea de estado** sobre la tabla, con solo los recuentos de la tanda: «5 de 7 listos ·
   1 buscando · 1 CV sin texto». Sin líneas en la vacante no sale.
3. **Orden** «De siempre | Por lo buscado», un control segmentado junto a la línea de estado,
   como el de «Pendiente | Le toca al candidato | Toda la tanda».
   - «De siempre» es el orden de hoy, el del servidor.
   - «Por lo buscado» ordena por cuántos «Lo tiene», de más a menos. Si empatan, por cuántos «Lo
     menciona». Si siguen empatados, por el orden de siempre. Quien no tiene resultado
     (pendiente, sin texto, falló) va al final, en el orden de siempre.
   - El orden se aplica después de los filtros. Al cambiar de pestaña vuelve a «De siempre».
   - Pulsar una cabecera de columna ordena por ella, como hoy, y el control vuelve a «De
     siempre».
   - El control no sale si la vacante no tiene líneas.
4. **La ficha.** Al desplegar a una persona, en cualquier pestaña, lo primero es el bloque «Qué
   buscar en el CV». A la derecha del título va el recuento «3 de 4». Después:
   - **Nota del CV**, con su número grande y dos filas:
     - «Criterios generales», con «pesan 75 %» y su nota;
     - «Qué buscar en el CV · 3 de 4», con «pesa 25 %» y su nota.

     Los porcentajes salen de la versión de pesos. Con la búsqueda pendiente se lee «por ahora,
     todo» y «aún no suma», con «—» como nota. Sin nota del CV, el bloque no sale.
   - **La lista**, con una fila por línea:
     - el icono (✓ verde, – ámbar, ✕ gris);
     - el texto de la línea;
     - la etiqueta («Lo tiene», «Lo menciona, sin detalle», «No aparece»);
     - la frase del CV entre comillas latinas y en cursiva;
     - la nota breve;
     - la pregunta para la entrevista, con «Para la entrevista:» en negrita.
   - **Estados en lugar de la lista**: «Buscando en su CV…», «CV sin texto: no se pudo buscar.» o
     «No se pudo buscar.».
     - Este último va con el botón «Volver a buscar» si quien mira tiene el permiso
       `ajustar_nota`.
     - El botón encola solo la búsqueda de esa persona.

   Debajo sigue la ficha de siempre de esa pestaña, sin cambios.
5. **Excel**, en «Perfil integral» y «Prueba del puesto»:
   - una columna «Qué buscar en el CV» después de «Teléfono», con «3 de 4», «Pendiente», «CV sin
     texto» o «No se pudo buscar»;
   - sin líneas en la vacante, la columna no sale;
   - las filas siguen el orden de la pantalla. Con «Por lo buscado», el pie dice «Orden: por lo
     buscado» en lugar de «Orden del ranking (grupo de prioridad y nota)».

### Flujo 5 · Editar las líneas con candidatos dentro

1. Al guardar líneas distintas en una vacante donde alguien ya tiene la búsqueda hecha o en
   curso, sale una confirmación:

   ```
   ┌ ¿Volver a buscar en 23 CV? ──────────────────────────────┐
   │ Las notas del CV se recalculan al terminar.               │
   │ No cambia nadie de etapa.                                 │
   │                                                           │
   │ [ Cancelar ]                 [ Guardar y volver a buscar ] │
   └───────────────────────────────────────────────────────────┘
   ```

   El número es el de personas cuyo CV ya se calificó o se está calificando, sin contar las de
   CV sin texto.
2. **Al confirmar, en una sola operación:**
   - las líneas viejas y sus resultados se archivan;
   - las líneas nuevas quedan vigentes con una versión nueva;
   - las notas de esas personas se recalculan sin lo buscado (sin publicar eventos ni mover a
     nadie);
   - se encola la búsqueda de cada una en modo rebúsqueda, que no pasa por la barrera del
     Perfil Integral.
3. **Cuando llega el resultado** de cada persona:
   - si es de la versión vigente de las líneas, se guarda y se recalculan su nota del CV, la del
     Perfil Integral y su grupo, sin publicar eventos: nadie cambia de etapa por esto;
   - si es de una versión vieja, se descarta.
4. **Quitar todas las líneas** también pide confirmación. Archiva los resultados y deja la nota
   como hoy.
5. Queda registrado en la auditoría: quién, cuándo, las líneas de antes y de después, y cuántas
   personas se mandaron a buscar.
6. Quien postula mientras tanto entra ya con las líneas nuevas.

### Flujo 6 · El peso en la versión de pesos

1. Cada versión de pesos lleva el peso de «Qué buscar en el CV», en % de la nota del CV.
   - Es un número de 0 a 50, igual para todos los niveles.
   - Todas las versiones que existen quedan en 25, también las copias de las empresas.
2. Crear una versión por la API acepta el campo opcional; si falta, queda en 25. Al copiar una
   versión (`CopiadorDeInstrumentosImpl`), se copia.
3. La confirmación del cambio de versión («¿Cambiar a «X»?») añade, si la vacante tiene líneas,
   una fila «Qué buscar en el CV» con el porcentaje de antes y el de después. Dos versiones que
   solo difieren en este número cuentan como cambio y recalculan a todos, como cualquier otro
   (`RecalculoConOtrosPesos`).
4. No hay pantalla para cambiar el número. Va en otra spec.

## Referencia visual

- Maqueta: [Requisitos en el CV](https://claude.ai/artifact/RxaAqJcg4eYj26Kg5A1YZy), fila
  «Versión final»:
  - «Final · Ranking con la nota» (interactivo: orden, pestañas y fichas);
  - «Final · Nueva vacante».

  El recuadro punteado «Solo en la maqueta» con el deslizador del peso **no es parte del
  diseño**: solo sirve para ver cómo cambian las notas.
- Versión aprobada: la 23, aprobada por el usuario el 21/10/2026. Usar la misma referencia
  durante la implementación y QA.
- Capturas locales:
  - Ranking, pestaña «Perfil integral», orden «De siempre», ficha de Lucía abierta, 1280 px:
    [ranking-perfil-1280.png](capturas/ranking-perfil-1280.png)
  - Mismo, con «Por lo buscado»:
    [ranking-por-lo-buscado-1280.png](capturas/ranking-por-lo-buscado-1280.png)
  - Ficha con la búsqueda en curso (Renzo, tras editar las líneas), 1280 px:
    [ficha-buscando-1280.png](capturas/ficha-buscando-1280.png)
  - Ficha con CV sin texto (Carla), 1280 px:
    [ficha-cv-sin-texto-1280.png](capturas/ficha-cv-sin-texto-1280.png)
  - Pestaña «Prueba del puesto», con los puntos, 1280 px:
    [ranking-prueba-del-puesto-1280.png](capturas/ranking-prueba-del-puesto-1280.png)
  - Nueva vacante, 1280 px: [formulario-1280.png](capturas/formulario-1280.png)
  - Nueva vacante con una línea prohibida, 1280 px:
    [formulario-linea-prohibida-1280.png](capturas/formulario-linea-prohibida-1280.png)
  - Nueva vacante, 390 px: [formulario-390.png](capturas/formulario-390.png)
- **La maqueta simplifica la tabla.** Sus columnas («Grupo», «Nota del CV», «Estado») no son las
  del panel. En el panel se mantienen las columnas, los filtros y el selector de columnas de
  hoy, y solo se añaden:
  - los puntos bajo el correo;
  - la línea de estado;
  - el control de orden;
  - el bloque de la ficha.
- Estados: normal; vacía (vacante sin líneas: no se ve nada nuevo); pendiente o buscando; CV sin
  texto; no se pudo buscar; línea prohibida; más de 6 líneas; vacante cerrada (casilla en solo
  lectura).
- Escritorio y móvil:
  - El formulario se apila igual que hoy.
  - En el ranking, los puntos van bajo el correo y siguen el ajuste de la celda del candidato.
  - El control de orden pasa debajo de la línea de estado a 390 px.
  - El bloque de la ficha se apila en una columna (etiqueta del estado bajo el texto de la
    línea). La maqueta no es referencia de la ficha en móvil.
- Diseño existente: [DESIGN.md](../../../DESIGN.md) del backend y el `DESIGN.md` del panel.
  Componentes que se reutilizan:
  - el control segmentado del ranking;
  - `Modal`, para las confirmaciones;
  - los estilos de `Vacante.module.css` para la celda del candidato.
- Fidelidad requerida:
  - la casilla privada separada con su chip;
  - la vista previa de las líneas con sus errores;
  - los colores y el orden de los puntos;
  - el control de orden;
  - la jerarquía del bloque de la ficha (recuento, nota del CV con sus dos filas, lista).

  Puede adaptarse: espaciados, tamaños exactos y la forma del punto (círculo o cuadrado
  redondeado), mientras los cuatro estados se distingan sin depender solo del color.

Los textos y reglas de esta spec prevalecen sobre la maqueta.

## Criterios de aceptación

### La casilla

- **AC-01**: Dado quien tiene `crear_vacante`, cuando crea una vacante con tres líneas en «Qué
  buscar en el CV», entonces se guardan las tres y la vista previa dice «3 de 6 como máximo».
- **AC-02**: Dada una línea «Menor de 35 años» o «Que sea casado», cuando se escribe, entonces
  sale en rojo con el motivo y el botón se apaga. Y si se manda igual por la API, el servidor
  contesta 400 y no guarda nada. «Carné de salud vigente» sí se acepta.
- **AC-03**: Dadas 8 líneas, cuando se guarda, entonces solo se guardan las 6 primeras válidas;
  la 7 y la 8 salen tachadas antes de guardar.
- **AC-04**: Dada una vacante con líneas, cuando se edita otro campo y se guarda, entonces las
  líneas no cambian, no se avisa a ningún candidato y la revisión del contexto de WhatsApp no
  sube.

### Privacidad

- **AC-05**: Dada una vacante con líneas y candidatos buscados, entonces ni las líneas ni los
  resultados aparecen en:
  - `GET /api/v1/portal/vacantes` ni en su detalle;
  - «Mis procesos»;
  - el contexto de WhatsApp;
  - ningún correo ni aviso de campana;
  - lo que reciben los agentes de preguntas recomendadas, prueba y simulación.

  Una prueba de integración lo comprueba buscando el texto de una línea en esas respuestas.

### La búsqueda y la nota

- **AC-06**: Dado un CV que dice «Bajé la merma de verduras del 9 % al 4 % en un año» y la línea
  «Controla costos y mermas», cuando la IA (simulada en las pruebas) devuelve `TIENE` con esa
  frase, entonces se guarda «Lo tiene» con la frase.
- **AC-07**: Dada una respuesta `TIENE` con una frase que no está en el CV, entonces se guarda
  «Lo menciona», sin frase.
- **AC-08**: Dada una vacante con p = 25, cuando una persona saca 80 en los criterios generales
  y 62,5 en lo buscado, entonces su nota del CV es 75,625. El ranking, el Perfil Integral y lo
  que recibe `POTENCIAL_RIESGO` usan ese mismo número.
- **AC-09**: Dada una vacante sin líneas, o con p = 0, entonces la nota del CV es exactamente la
  de hoy.
- **AC-10**: Dada una vacante con líneas, cuando la IA califica un CV, entonces el Perfil
  Integral no se cierra hasta que la búsqueda termine o falle del todo, y el pase automático usa
  la nota con lo buscado.
- **AC-11**: Dado un CV escaneado sin texto, entonces la búsqueda queda «CV sin texto» sin
  llamar a la IA y sin reintentos, y la nota del CV es la de los criterios generales (o ninguna,
  si tampoco hay).
- **AC-12**: Dada una empresa sin presupuesto de IA, cuando se califica un CV, entonces la
  búsqueda queda en espera y la persona sale «Pendiente». Cuando vuelve a haber presupuesto, se
  busca sola.

### Editar las líneas

- **AC-13**: Dada una vacante con 23 personas buscadas, cuando se cambian las líneas y se
  confirma, entonces:
  - se archivan las líneas y los resultados viejos;
  - se encolan 23 búsquedas;
  - nadie cambia de etapa;
  - al llegar cada resultado, su nota del CV y su grupo se recalculan.
- **AC-14**: Dado un resultado que llega de las líneas viejas después de editarlas, entonces se
  descarta.
- **AC-15**: Dado que se cancela la confirmación, entonces no cambia nada.

### Ranking, ficha y Excel

- **AC-16**: Dada la vacante de la maqueta, cuando se abre el ranking en cualquier pestaña,
  entonces cada persona tiene un punto por línea con el color de su estado, y la línea de
  estado dice cuántos están listos, buscando y sin texto.
- **AC-17**: Cuando se elige «Por lo buscado», entonces el orden es: Mariana, Lucía, Diego,
  Jorge, Ana, Renzo, Carla.
  - Mariana tiene 4 «Lo tiene». Lucía tiene 3 y 1 «Lo menciona».
  - Jorge y Ana empatan, y Jorge va primero por el orden de siempre.
  - Renzo y Carla no tienen resultado y van al final.
- **AC-18**: Dada una persona con la búsqueda terminada, cuando se despliega su ficha, entonces
  se ven:
  - el recuento;
  - la nota del CV con «pesan 75 %» y «pesa 25 %»;
  - por cada línea: el estado, la frase, la nota y, si no la tiene, la pregunta para la
    entrevista.
- **AC-19**: Dada una búsqueda fallida, cuando quien tiene `ajustar_nota` pulsa «Volver a
  buscar», entonces se encola solo esa búsqueda. Sin el permiso, el botón no sale y la API
  contesta 403.
- **AC-20**: Dado el Excel de «Perfil integral» con «Por lo buscado», entonces:
  - lleva la columna «Qué buscar en el CV» con «3 de 4» y los estados;
  - las filas siguen el orden de la pantalla;
  - el pie dice «Orden: por lo buscado».

### El peso

- **AC-21**: Dadas las versiones que existen antes de la migración, entonces todas quedan con
  25. Una versión creada sin el campo queda en 25. Un valor fuera de 0 a 50 se rechaza en la
  base.
- **AC-22**: Dadas dos versiones que solo difieren en el peso (25 y 30), cuando se cambia la
  vacante de una a otra, entonces la confirmación muestra «Qué buscar en el CV 25 % → 30 %» y,
  al confirmar, se recalculan todos sin IA y sin mover a nadie.

### Pantalla

- **AC-23**: Dado quien tiene `ver_embudo`, cuando abre el ranking y el formulario en 1280 px y
  en 390 px, entonces la casilla privada, los puntos, el control de orden y el bloque de la
  ficha cumplen las capturas de «Referencia visual» y no hay párrafos de ayuda bajo los campos.

## Casos límite y errores

- **Datos vacíos o inválidos:**
  - Sin líneas, no se encola nada y no cambia ninguna pantalla.
  - Las líneas en blanco se ignoran, y las repetidas cuentan una vez.
  - Más de 200 caracteres: el servidor contesta 400.
  - Una respuesta de la IA que no se puede leer cuenta como fallo del intento y se reintenta
    como los demás.
- **Usuario sin permisos:**
  - Escribir las líneas exige `crear_vacante` al crear y `editar_vacante` al editar.
  - Verlas exige `ver_vacantes`.
  - Los puntos y el orden exigen `ver_embudo`.
  - La ficha exige `abrir_ficha_candidato`.
  - «Volver a buscar» exige `ajustar_nota`.
  - Fuera del alcance del rol, 404 como hoy.
- **Recarga, reintento o doble envío:**
  - La confirmación de editar lleva la misma guarda contra el doble clic que la de los pesos
    (`yaSeEnvio`).
  - Encolar dos veces la misma búsqueda no crea dos trabajos (`crearSiHaceFalta`).
  - Un resultado de una versión vieja de las líneas se descarta.
- **Concurrencia con el cambio de pesos:** guardar las líneas y cambiar la versión de pesos
  toman el mismo cerrojo de la vacante. Un resultado que llega mientras tanto espera al cerrojo
  de los pesos (`CerrojoDeLosPesosImpl`), como las calificaciones de hoy.
- **Inyección de instrucciones:**
  - Las líneas van envueltas como texto de la empresa.
  - El CV es texto del candidato. «Lo tiene» exige una frase que exista en el CV, y la
    instrucción pide evidencia concreta (puesto, fechas o resultados), no repetir la línea.
- **Vacante cerrada, archivada o eliminada:** no se pueden editar las líneas (409) ni volver a
  buscar. Lo ya buscado se sigue viendo.
- **Postulaciones que no llegan a calificarse** (no continúan por requisitos objetivos, o la
  vacante no califica automático): no se busca nada y no se gasta IA. Sus puntos salen
  punteados («Pendiente») mientras no se califique su CV.
- **Mismo CV en otra vacante:** no se reutiliza nada; cada vacante busca sus propias líneas.

## Datos y compatibilidad

- **Información que debe guardarse** (migración V78, o la siguiente libre al implementar):
  - **Las líneas de cada vacante**: texto, orden, versión de las líneas y cuándo se archivaron.
    No va en `vacante.requisitos` ni en ninguna columna pública.
  - **El resultado por persona y línea**: estado (`TIENE`, `MENCIONA`, `NO_APARECE`), la frase
    del CV en su propia columna, la nota, la pregunta, la ejecución de IA que lo produjo, la
    versión de las líneas y cuándo se archivó. Como mucho un resultado vigente por persona y
    línea.
  - **El estado de la búsqueda por persona**: pendiente, en curso, terminada, fallida o «CV sin
    texto», con su motivo. Puede ser el `trabajo_ia` del agente nuevo, si guarda el motivo del
    «CV sin texto».
  - **El agente nuevo**:
    - rehacer `agente_codigo_check` (patrón V19, V42, V43, V66);
    - insertar su fila en `agente`;
    - sembrar una `instruccion_ia` activa (global y editable desde el panel, como las demás);
    - su `FORMATO` en código;
    - su modelo en `application.yaml`.
  - **En `version_pesos`**: el peso de «Qué buscar en el CV», `NOT NULL DEFAULT 25`, con
    `CHECK` de 0 a 50.
- **Comportamiento para registros existentes:**
  - Las vacantes que existen no tienen líneas: nada cambia en ellas ni en sus notas.
  - Todas las versiones de pesos quedan en 25, lo que no tiene efecto hasta que una vacante
    tenga líneas.
- **Restricciones de compatibilidad:**
  - **No se añaden filas a `criterio`.** `criteriosDelCurriculum` carga todos los criterios del
    Perfil Integral sin plantilla, así que una fila por vacante entraría en la nota de todas y
    rompería las sumas al publicar.
  - **`GuardarVacante` acepta las líneas como campo opcional.** Si falta, no cambia nada. Si
    llega una lista vacía, se quitan, con confirmación si hay candidatos.
  - **`CambiosDeLaVacante`** las clasifica como `INTERNO` o las deja fuera. La V77 no se toca.
  - **No se añaden a** `DatosDeLaVacanteParaIa`, `ContextoDeLaVacante.queBuscaLaVacanteDe` ni
    `PuenteCalificacionIaImpl.queBusca`.
  - **`FilaRanking`**: lo nuevo va al final del record. Se actualizan el constructor viejo, la
    construcción (:700-754) y la copia campo a campo al renumerar (:771-786).
  - **El Excel**: `cabeceras`, `anchos` y `escribirFila` cambian juntos, y el `pie` también.
  - **`CambioDePesosPrevisto`** gana un campo opcional. El frontend de hoy lo ignora sin
    romperse.

## Verificación

- **Actor y datos de prueba:**
  - Una cuenta de Talento con todos los permisos del ranking y `ajustar_nota`, y otra de
    Responsable de área sin `ajustar_nota`.
  - Una vacante «Chef de cocina» con 4 líneas y 7 postulaciones que reproducen la maqueta:
    - cuatro buscadas con mezclas distintas;
    - una con la búsqueda en curso;
    - una con CV escaneado;
    - una con la búsqueda fallida.
  - La IA simulada del entorno de pruebas devuelve resultados fijos por CV.
  - Dos versiones de pesos iguales salvo el peso (25 y 30).
- **Recorridos E2E repetibles en headless:**
  - crear una vacante con líneas, incluida la línea prohibida que apaga el botón;
  - los puntos y el cambio a «Por lo buscado», con el orden de AC-17;
  - desplegar la ficha de una persona buscada y la de una con CV sin texto;
  - editar las líneas con candidatos dentro: la confirmación y los puntos en estado buscando;
  - exportar el Excel con «Por lo buscado» y comprobar la columna y el pie;
  - cambiar la vacante a la versión de 30 y ver la fila nueva en la confirmación.
- **Riesgos que QA debe explorar con Chrome visible:**
  - que el texto de una línea no aparezca en el portal (detalle de la vacante y «Mis
    procesos»);
  - el ranking a 390 px con 6 puntos y un nombre largo;
  - el orden combinado con filtros y con el orden por cabecera;
  - el doble clic en «Guardar y volver a buscar».
- **Comparación visual de QA:** el formulario (1280 y 390 px), el ranking en «Perfil integral» y
  «Prueba del puesto» (1280 px) y las tres fichas (buscada, buscando, sin texto), frente a las
  capturas de «Referencia visual». Se guarda evidencia, y cualquier diferencia en los elementos
  obligatorios es un hallazgo.
- **Resultado que quiero comprobar personalmente** (lo comprueba el usuario, porque el panel
  necesita login):
  - Crear la vacante del chef, postular con un CV real y ver los puntos y la ficha con las
    frases del CV.
  - Ver que la nota del CV sube o baja según lo buscado, y que con «Por lo buscado» el orden
    tiene sentido.
  - Abrir el portal como candidato y no encontrar las líneas en ningún sitio.

## Dependencias y dudas

- **Requiere:** la V74 (el cambio de versión con candidatos dentro), ya en producción desde el
  16/10/2026.
- **Decisiones pendientes:** ninguna. La maqueta y la lista de lo prohibido quedaron
  confirmadas el 21/10/2026 (decisión 11).
