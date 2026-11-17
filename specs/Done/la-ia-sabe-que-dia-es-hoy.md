# La IA sabe qué día es hoy, ve los años de experiencia y arreglos del visor de informes
Estado: TERMINADO

## Objetivo
Que ninguna calificación de la IA castigue a un candidato por un dato de su CV que la IA no pudo
leer bien, y que el visor de informes ponga primero a quienes ya tienen las tres notas, deje
abrir los entregables y enseñe el celular del candidato. Son cinco arreglos:

1. **La fecha de hoy.** Hoy **ningún agente recibe la fecha del día**, así que el modelo razona con
   la de su entrenamiento, que es anterior a 2026. Por eso marca como «futuras» o «imposibles»
   experiencias de febrero o julio de 2026, que ya ocurrieron. Eso baja notas del CV, crea
   hallazgos falsos y, cuando el hallazgo es un riesgo crítico, cambia el grupo de prioridad.
2. **Los años de experiencia tapados.** Para no mandar la edad a la IA, el sistema tapa toda frase
   del tipo «34 años» con `[DATO NO UTILIZABLE]`. Pero la regla no distingue la edad de la
   experiencia: «más de 5 años de experiencia en Recursos Humanos» llega a la IA como «más de
   [DATO NO UTILIZABLE] de experiencia en Recursos Humanos». La IA no ve los años de experiencia,
   que es justo lo que más pide la clienta, y además **copia la marca en textos del panel**: en la
   ficha sale «Tiene más de [DATO NO UTILIZABLE] de experiencia en Recursos Humanos» como afirmación
   «Sin información», con una pregunta para validarla que no haría falta.
3. **El orden del visor de informes.** Hoy la lista de postulantes del visor sigue el orden del
   ranking de la evaluación, así que un candidato sin prueba del puesto puede salir por encima de
   otro que ya la rindió con 93. La clienta quiere ver arriba a quienes tienen las tres notas,
   ordenados por su nota combinada, y debajo a los demás.
4. **Los entregables del informe de la prueba no se abren.** En «Entregables pedidos» el visor
   dice qué entregó el candidato y cuándo («Documento de Recursos Humanos · Subido el 14/11 a las
   10:14 · Entregado»), pero no hay forma de abrirlo desde ahí: hay que ir a la ficha.
5. **El celular del candidato no está a mano.** Ninguna pantalla del panel lo enseña; solo el
   Excel del ranking trae el teléfono escrito en el CV. La clienta quiere verlo en el visor, junto
   al enlace del currículum, para llamarlo o escribirle por WhatsApp.

El fallo de la fecha se detectó el 14/11/2026 al redactar a mano los informes de prueba: en Juan
Pastor, Luis Fernando Arroyo, Briam Medina y Rosa Huayta, en Rosa como su único riesgo crítico. Una
consulta de solo lectura en producción, el 16/11/2026, dio la escala:
- **66 postulaciones** con hallazgos que hablan de fechas «futuras», «imposibles» o que «aún no
  ocurren», **32 de ellas con un riesgo crítico**;
- **73 postulaciones** con alguna justificación de criterio del CV que lo menciona.

Algunos pueden ser casos legítimos, como una fecha de verdad posterior a hoy, pero la mayoría
vienen de este fallo. El de los años tapados se vio en el panel el 16/11/2026; afecta a todo CV
que escriba su experiencia como «N años», que son casi todos.

## Contexto
- Comportamiento actual:
  - Todos los agentes pasan por un solo punto, `EjecutorAgenteIaImpl.ejecutar`, que arma el
    mensaje con la instrucción de `instruccion_ia`, el formato del agente y los datos en JSON.
    En ninguno de los tres va la fecha de hoy.
  - El agente de datos del CV (`AgenteDatosCv`) ordena la experiencia «del más reciente al más
    antiguo», pero tampoco sabe qué día es «actualidad».
  - `AnonimizadorCv` (RF-41) tapa edad, sexo, estado civil, fecha de nacimiento e hijos. Su patrón
    de la edad es «una cifra de 1 a 3 dígitos + años», sin mirar qué viene después, así que tapa
    también «5 años de experiencia», «3 años en el cargo» o «10 años como supervisor».
  - El visor de informes (`GET /api/v1/panel/vacantes/{id}/informes`,
    `ServicioInformesImpl.visor`) devuelve los postulantes en el orden del ranking de la
    vacante, con su puesto en ese ranking. El zip de toda la vacante numera las carpetas con ese
    mismo puesto. El panel pinta la lista tal cual, con el número, y dice «Puesto N del ranking».
  - Del entregable el informe solo lleva nombre, cuándo llegó y si se entregó
    (`EntregableAnexo`). La ficha ya los abre: `GET /postulaciones/{id}/prueba/entregables` trae
    el id del archivo o el enlace solo a quien tiene `descargar_entregables`, y el archivo se abre
    con `/archivos/{id}/enlace` o `/archivos/{id}/descarga`, que exigen ese mismo permiso.
  - El celular del candidato se guarda en dos sitios: el de su cuenta (`persona.telefono`, el que
    escribe al registrarse, con formato 999 999 999) y el que la IA lee de su CV
    (`dato_cv.telefono`, tal cual venía escrito, que es el que usa el Excel).
  - Ya existe una nota combinada: el **ponderado de lo ya rendido** del RF-155 (Perfil Integral
    —CV y evaluación— y prueba del puesto, reescalados sobre la suma de sus pesos). Solo existe
    cuando están las dos notas; no se guarda.
- Pantallas o procesos afectados: la calificación con IA de todas las etapas (CV, evaluación,
  retrato, prueba del puesto, simulación, redactor) y, a través de ella, el ranking, los grupos
  de prioridad, la ficha y los informes; el visor de informes y el zip de la vacante.
- Referencias existentes:
  - [La calificación con IA](../../docs/CALIFICACION-CON-IA.md): «Qué queda escrito de cada
    llamada», «Recalificar es reencolar de verdad» y «Lo que una persona ajustó a mano, la IA no
    lo pisa».
  - RF-41 (anonimizar el CV) y RF-155 (ponderado de lo ya rendido) en
    [Requisitos funcionales](../../docs/01-REQUISITOS-FUNCIONALES.md).
  - Backend: `ai/service/impl/EjecutorAgenteIaImpl.java`,
    `postulacion/service/impl/AnonimizadorCv.java`,
    `informe/service/impl/ServicioInformesImpl.java` (`visor` y `zipDeLaVacante`).
  - Panel: `src/panel/vacantes/informes/VisorDeInformes.tsx`.
  - Recalificar: `POST /postulaciones/{id}/calificacion-perfil-integral` (`reencolarEvaluador`).

## Alcance
- Incluye:
  - Que **todos los agentes reciban la fecha de hoy**, en un solo sitio: el ejecutor común, con
    una regla breve sobre fechas, igual para todos, y que quede en el registro de cada llamada.
  - Que el anonimizador **tape la edad y no los años de experiencia**.
  - Que el visor y el zip de la vacante **ordenen por la nota combinada** a quienes tienen las
    tres notas, y debajo a los demás.
  - Que los **entregables se puedan abrir** desde el visor y desde el PDF del informe de la
    prueba, para quien tiene permiso.
  - Que el visor enseñe el **celular del candidato**, con un enlace para llamar y otro para
    WhatsApp.
  - **Recalificar a los candidatos afectados por la fecha o por los años tapados** que siguen en
    curso en vacantes abiertas, con un comando de administración y con confirmación.
- Queda fuera:
  - Cambiar las instrucciones de cada agente en `instruccion_ia`. La fecha va aparte, para que
    una edición de la instrucción no pueda quitarla.
  - Recalificar postulaciones de vacantes cerradas o archivadas. Quedan como historia, con la
    nota con que se decidió.
  - Detectar fechas imposibles por código. Sigue siendo juicio de la IA, ahora con la fecha
    correcta.
  - Cambiar el orden del ranking de las pestañas del panel o del Excel. Solo cambia el visor de
    informes y su zip.
  - Una columna nueva con la nota combinada en el visor. La lista sigue con CV, EVA y PRU.
  - El celular en los informes PDF y en la ficha. Los PDF siguen sin datos de contacto, como
    dice la spec del informe.

## Comportamiento esperado
1. **La fecha va en cada llamada.** El ejecutor común añade, entre la instrucción y el formato,
   un bloque fijo con la fecha de hoy en la zona horaria de Lima (`America/Lima`):
   > Fecha de hoy: 16 de noviembre de 2026 (2026-11-16, hora de Lima). Toda fecha igual o
   > anterior a hoy ya ocurrió: no es futura ni imposible. «Actualidad» o «a la fecha» en un CV
   > significa hasta hoy. Calcula duraciones y antigüedades con esta fecha.
   - La fecha sale del reloj del sistema (el mismo `Clock` que usa el backend), no del modelo.
   - Es igual para todos los agentes y no depende de la instrucción editable: aunque Dirección
     cambie una instrucción, el bloque sigue ahí.
   - Forma parte del `envio` que se guarda de cada llamada, así que se puede saber meses después
     con qué fecha se calificó.
2. **La edad se tapa; la experiencia no.** Una cifra seguida de «años» se tapa solo cuando es una
   edad:
   - Se tapa: «Edad: 34», «34 años de edad», «Tengo 34 años», «Juan Pérez, 34 años», «34 años» solo
     en su línea o entre separadores de datos personales (por ejemplo «DNI 12345678 · 34 años ·
     Lima»), y todo lo que ya tapaba la regla de nacimiento, sexo, estado civil e hijos.
   - No se tapa: «5 años de experiencia», «más de 10 años en ventas», «3 años en el cargo»,
     «2 años como supervisor», «4 años trabajando en…», «durante 6 años», «6 años de
     trayectoria», «años de servicio», y en general una cifra con «años» seguida de lo que hizo
     la persona.
   - Ante la duda, se tapa: mandar la edad a un tercero es peor que perder un dato. La duda se
     resuelve con la lista de pruebas de abajo, no ampliando la regla a ojo.
   - Se mantiene todo lo que hoy protege la clase: la bandera Unicode, las mayúsculas con tilde
     («AÑOS»), los cuantificadores posesivos y el «Nº34 años».
3. **Ningún texto del panel enseña la marca.** Si un texto que la IA devuelve para mostrar
   (afirmaciones del CV, su pregunta de validación, justificaciones, hallazgos, textos del
   redactor) trae `[DATO NO UTILIZABLE]`, ese texto no se muestra tal cual: la afirmación se
   descarta, porque se refiere a un dato que la IA no vio. Los demás textos no se tocan.
4. **El visor ordena por la nota combinada.**
   - **Arriba**, quienes tienen las tres notas (CV, evaluación y prueba del puesto calificada),
     de mayor a menor por el ponderado de lo ya rendido del RF-155. A igual ponderado, por la
     nota de la prueba y luego por la de la evaluación.
   - **Debajo**, los demás, en el orden del ranking de hoy.
   - El número de la izquierda es el lugar en esta lista, de 1 en adelante. La cabecera del
     informe dice «Puesto N de M» en lugar de «Puesto N del ranking», y el subtítulo dice
     «ordenados por nota combinada» en lugar de «en el orden del ranking».
   - El orden se calcula con las tres notas aunque quien mira no tenga permiso para ver alguna
     de ellas. Esa nota sigue saliendo como «–».
   - El zip de toda la vacante numera sus carpetas con este mismo orden.
5. **Los entregables se abren desde el informe.**
   - En el visor, cada entregable entregado es un enlace: un archivo se abre en una pestaña
     nueva con el enlace firmado de siempre (`/archivos/{id}/enlace`); un entregable de tipo
     enlace abre esa dirección.
   - En el PDF, el nombre del entregable entregado lleva un enlace con el mismo trato que el del
     CV: enlace firmado de 8 horas para un archivo, la dirección tal cual para un enlace.
   - Solo para quien tiene `descargar_entregables`. Sin ese permiso se ve como hoy, sin enlace,
     en el visor y en el PDF que esa persona descargue.
   - Uno no entregado sigue sin enlace. El contenido del entregable sigue sin entrar en el
     informe ni ir a la IA.
6. **El celular en el visor.**
   - En la cabecera del informe, en la misma fila que «Currículum» y las redes, va el celular del
     candidato con el icono de teléfono: «☎ 987 654 321». Al hacer clic llama (`tel:`). A su
     lado, «WhatsApp», que abre la conversación con ese número (`https://wa.me/51…`) en una
     pestaña nueva.
   - Se usa el celular de su cuenta. Si no tiene, el del CV. Si no hay ninguno, no sale nada.
   - Se muestra como 999 999 999. El enlace de WhatsApp lleva el prefijo 51 cuando el número
     tiene 9 cifras y empieza por 9; si no se puede armar un número válido (un fijo, uno
     extranjero raro), sale solo el número para llamar, sin WhatsApp.
   - Lo ve quien puede abrir el visor, igual que el enlace del currículum, que ya trae el
     número dentro.
7. **Recalificar a los afectados**, con un **comando de administración de una sola vez**
   (decisión del 16/11/2026), no un botón del panel. Lo ejecuta alguien de la plataforma:
   - **Solo los que siguen en curso** (decisión del 16/11/2026): postulaciones de vacantes
     abiertas que no estén contratadas, descartadas ni cerradas.
   - **Afectados son** las postulaciones en curso que cumplan una de estas:
     - un hallazgo o una justificación de criterio del CV que hable de fechas «futuras»,
       «posteriores a hoy», «imposibles» o que «aún no ocurren»;
     - un CV en el que la regla vieja del anonimizador tapa algo que la nueva deja ver (es
       decir, que perdió años de experiencia).
   - **Lista previa, sin llamar a la IA** (modo de prueba del comando): cuántas son, de qué
     vacantes, por cuál de los dos motivos y el coste estimado en llamadas.
   - **Al confirmar**, se reencolan con el mecanismo de siempre: se rehace la calificación del
     CV y el retrato, y la evaluación solo si hace falta para rehacer el retrato.
   - **Lo que una persona ajustó a mano no se pisa**, como hoy.
   - La nota y el grupo nuevos quedan con su fecha. El cambio queda en la auditoría con quién
     lo pidió.
   - **Nadie cambia de etapa por esto:** si alguien queda por encima de la nota mínima del pase
     automático, no avanza solo; lo decide una persona.
8. **Si algo falla al recalificar**, ese candidato conserva su calificación anterior y queda en
   la lista de fallidos para reintentar. Los demás siguen.

## Referencia visual
- Comando: no tiene pantalla. Primero se ejecuta en modo de prueba y saca la lista y el coste
  estimado. Después se ejecuta de verdad con una confirmación explícita, por ejemplo un parámetro
  `--confirmar`.
- Visor: la misma pantalla de hoy, sin cambios de diseño. Solo cambian el orden de la lista, el
  número de cada fila, el subtítulo y la etiqueta «Puesto N de M». Con la vacante de la captura
  del 16/11/2026: Robert Villegas (60 · 82 · 93), Mayra Garcia-Godos (58 · 73 · 88) y Ruth Suri
  (52 · 70 · 94) quedan arriba, ordenados por su nota combinada; Ximena Aleman, Alex Aquise,
  Christian Casahuaman y Diego Tejada, sin prueba, bajan.
- Entregables: la misma fila de hoy; el nombre pasa a ser un enlace con el estilo de enlace del
  panel (como «Ver perfil de… →»), con el icono de abrir afuera. La pastilla «Entregado» no cambia.
- Celular: en la fila de enlaces de la cabecera, después de «Currículum» y antes de las redes, con
  el mismo estilo que «Currículum»: «☎ 987 654 321» y «WhatsApp» con su icono. En el móvil la
  fila se parte en dos líneas como hoy.

## Criterios de aceptación
- AC-01: Dada cualquier llamada a un agente, cuando se revisa su registro, entonces el `envio`
  contiene el bloque «Fecha de hoy» con la fecha de Lima del día de la llamada, fuera de la
  instrucción editable.
- AC-02: Dada una instrucción editada que no menciona fechas, cuando se califica, entonces el
  bloque de la fecha sigue presente.
- AC-03: Dado un CV con «Supervisor de planta, febrero 2026 – actualidad» calificado el
  16/11/2026 con un modelo simulado, cuando se arma el mensaje, entonces el bloque dice
  2026-11-16 y la regla de que lo anterior a hoy ya ocurrió.
- AC-04: Dado el reloj del sistema fijado a otra fecha en una prueba, cuando se arma el mensaje,
  entonces el bloque usa esa fecha y no la del equipo.
- AC-05: Dado un CV con «Tengo más de 5 años de experiencia en Recursos Humanos», cuando se
  anonimiza, entonces la frase llega entera a la IA.
- AC-06: Dados «Edad: 34», «34 años de edad», «Tengo 34 años», «Juan Pérez, 34 años», «34 AÑOS»
  solo en su línea y «Nº34 años», cuando se anonimizan, entonces todos quedan tapados.
- AC-07: Dados «10 años en ventas», «3 años en el cargo», «2 años como supervisor», «durante 6
  años» y «6 AÑOS DE EXPERIENCIA», cuando se anonimizan, entonces ninguno se tapa.
- AC-08: Dada una afirmación del CV devuelta por la IA con `[DATO NO UTILIZABLE]`, cuando se
  guarda la calificación, entonces esa afirmación no aparece en la ficha ni en los informes.
- AC-09: Dada una vacante con tres candidatos con las tres notas y otros sin prueba, cuando se
  abre el visor, entonces los tres van primero, de mayor a menor nota combinada, numerados 1, 2
  y 3, y los demás debajo en el orden del ranking.
- AC-10: Dado un usuario que no puede ver la nota del CV, cuando abre el visor, entonces el orden
  es el mismo que para Dirección y la columna CV sale «–».
- AC-11: Dado el zip de toda la vacante, cuando se descarga, entonces sus carpetas van numeradas
  en el mismo orden que el visor.
- AC-12: Dado un usuario con `descargar_entregables` en el informe de la prueba de un candidato
  que subió un archivo, cuando hace clic en su nombre en el visor, entonces el archivo se abre en
  una pestaña nueva; y en el PDF descargado ese nombre abre el archivo durante 8 horas.
- AC-13: Dado un usuario sin `descargar_entregables`, cuando ve el visor o descarga el PDF,
  entonces los entregables salen sin enlace, como hoy.
- AC-14: Dado un candidato con celular en su cuenta y otro distinto en el CV, cuando se abre
  su informe en el visor, entonces sale el de la cuenta como 999 999 999, con enlace para llamar
  y enlace de WhatsApp con el prefijo 51.
- AC-15: Dado un candidato sin celular en su cuenta pero con teléfono en el CV, cuando se abre
  su informe, entonces sale el del CV; y si no tiene ninguno, no sale nada.
- AC-16: Dado un teléfono fijo o que no tiene 9 cifras, cuando se abre el informe, entonces sale
  el número para llamar y no el enlace de WhatsApp.
- AC-17: Dado el comando en modo de prueba, cuando se ejecuta, entonces lista las postulaciones
  afectadas en curso de vacantes abiertas, sus vacantes, el motivo y el coste estimado, sin
  llamar a la IA. Una postulación contratada, descartada o cerrada no aparece.
- AC-18: Dada la confirmación, cuando termina, entonces cada afectado tiene su CV y su retrato
  recalificados con la fecha correcta y los años de experiencia a la vista, las notas ajustadas
  a mano siguen iguales y nadie cambió de etapa por sí solo.
- AC-19: Dado un candidato cuya recalificación falla, cuando termina la tanda, entonces conserva
  su calificación anterior y aparece en la lista de fallidos.

## Casos límite y errores
- Datos vacíos o inválidos: un CV sin fechas ni años no cambia nada.
- Usuario sin permisos: el comando solo lo ejecuta la plataforma, en el servidor. No hay botón
  ni endpoint público. El visor sigue con sus permisos de hoy.
- Recarga, reintento o doble envío: confirmar dos veces no encola dos veces a la misma persona;
  se usa el trabajo vivo de siempre por postulación.
- Otros casos relevantes:
  - Una llamada que cruza la medianoche usa la fecha del momento en que se arma el mensaje.
  - Un CV con una fecha de verdad posterior a hoy (por ejemplo, «inicio previsto en marzo de
    2027») puede seguir marcándose. Es lo correcto.
  - Una prueba del puesto empezada pero sin calificar no cuenta como tercera nota.
  - Una vacante sin pesos de etapa no tiene ponderado: el visor queda en el orden del ranking,
    como hoy.
  - Un CV que dice «34 años, 10 años de experiencia» tapa el primero y deja el segundo.

## Datos y compatibilidad
- Información que debe guardarse: nada nuevo. La fecha queda dentro del `envio` que ya se guarda.
  El ponderado se sigue calculando al vuelo, sin guardarse.
- Comportamiento para registros existentes: las calificaciones antiguas no cambian, salvo las que
  se recalifiquen con el comando del paso 7. Las afirmaciones antiguas con la marca dejan de
  mostrarse desde el despliegue.
- Restricciones de compatibilidad: no hace falta migración. Las instrucciones de `instruccion_ia`
  no se tocan. El backend y el panel van en pareja por los textos del visor.

## Verificación
- Actor/rol y datos de prueba necesarios: un usuario de Dirección y otro sin permiso para ver la
  nota del CV; un CV con fechas de 2026 ya pasadas y con «N años de experiencia»; una vacante
  abierta con dos candidatos afectados (uno con una nota ajustada a mano), tres candidatos con
  prueba calificada y dos sin ella; una vacante cerrada con otro afectado.
- Recorridos que deben quedar como pruebas automáticas:
  - el bloque de la fecha en el mensaje, con el reloj fijado, para varios agentes, y que
    sobrevive a una instrucción editada;
  - la batería del anonimizador: lo que se tapa y lo que no (AC-05 a AC-07), incluidas las
    mayúsculas con tilde;
  - que una afirmación con la marca no se muestra;
  - el orden del visor y del zip, con y sin permiso para ver todas las notas;
  - los entregables con enlace en el visor y en el PDF, con y sin `descargar_entregables`;
  - el celular en el visor: de la cuenta, del CV, ninguno y uno fijo;
  - la lista previa del comando (solo vacantes abiertas, los dos motivos);
  - la tanda que respeta los ajustes a mano y no mueve de etapa, y el fallo de uno sin frenar
    a los demás.
- Riesgos que QA debe explorar:
  - lanzar el comando en una base con muchos afectados y ver el coste estimado antes de
    confirmar;
  - CV reales con la edad escrita de formas raras, para comprobar que no se escapa ninguna.
- Resultado que quiero comprobar personalmente:
  - tras recalificar, que Rosa Huayta ya no tenga el riesgo crítico por fechas y que el grupo de
    prioridad de los afectados refleje su nota real;
  - que en la ficha ya no salga ningún «[DATO NO UTILIZABLE] de experiencia»;
  - que en el visor de la vacante de la captura salgan arriba los que tienen las tres notas;
  - que el «Documento de Recursos Humanos» se abra desde el visor y desde el PDF;
  - que el celular salga en el visor y que «WhatsApp» abra la conversación.

## Dependencias y dudas
- Requiere: el visor de informes, ya fusionado (spec `informe-pdf-por-etapa`). Conviene
  implementarla cuanto antes, en su propia sesión, porque el redactor del informe y el retrato
  también se benefician de la fecha correcta y de los años de experiencia.
- Decisiones pendientes: ninguna.
  - Decidido el 16/11/2026: solo se recalifica a quienes siguen en curso en vacantes abiertas.
  - Decidido el 16/11/2026: comando de administración, no botón.
  - Decidido el 16/11/2026: el visor ordena por la nota combinada a quienes tienen las tres
    notas y deja debajo a los demás; sin columna nueva.
  - Decidido el 16/11/2026: los entregables se abren desde el visor y el PDF, con el permiso
    de siempre y el enlace de 8 horas del CV.
  - Decidido el 16/11/2026: el celular sale en el visor, con llamar y WhatsApp, para quien puede
    abrirlo; no va en los PDF.
