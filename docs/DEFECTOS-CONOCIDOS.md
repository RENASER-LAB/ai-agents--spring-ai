# Defectos conocidos

**Lo que está roto, se sabe, y sigue abierto.** No es un historial: en cuanto algo se arregla,
sale de aquí y su explicación se va al documento que corresponda. Si algo está en esta lista es
porque hoy, tal como está desplegado, puede pasarle a alguien.

Cada entrada dice tres cosas: **qué le pasa a una persona de verdad**, **por qué pasa** y **qué
haría falta para arreglarlo**. Sin lo tercero, una lista así solo sirve para preocupar.

> **Por qué existe este documento.** Los defectos que se conocen y no se escriben se vuelven a
> descubrir cada pocos meses, normalmente con un candidato dentro. Lo que **ya está arreglado**
> no vive aquí: vive en el documento de su tema, en `CLAUDE.MD` o en el javadoc de su clase.

Última revisión: **15/09/2026**.

---

## 1 · Una empresa puede leer el examen de otra

**Qué le pasa a alguien.** Quien administra las pruebas de la empresa B abre el catálogo de
preguntas y **ve las preguntas específicas que escribió la empresa A**. Esas preguntas no son
metadatos: **son el texto del examen**. Con dos empresas del mismo rubro compitiendo por la misma
gente, es filtrar el examen antes de tomarlo.

**Por qué pasa.** `pregunta_prueba` es un catálogo **sin `organizacion_id`**. Nació antes del
multiempresa, cuando solo Renaser contrataba, y se quedó igual: el listado (`GET
/plantillas-prueba/preguntas`) hace un `findAll()` sin filtrar por nada. Todo lo demás del módulo
de pruebas —plantillas, versiones, rúbricas— sí tiene dueño y sí lo comprueba; este catálogo es
el agujero que quedó.

**Y hay un segundo daño, más callado.** `codigo` es **único en toda la plataforma**. Así que la
primera empresa que use `ADMIN_Q01` se lo queda **para siempre**: cualquier otra que lo intente
recibe un error de clave duplicada, y **no hay ningún endpoint que borre una pregunta del
catálogo**, así que ni siquiera se puede liberar. Un código escrito por error es permanente.

**Qué haría falta.** Una migración: `organizacion_id` en `pregunta_prueba` —repartiendo las filas
que ya hay a la plataforma—, el único cambiado a `(organizacion_id, codigo)`, y el filtro por
dueño en el listado, con la regla de siempre (una empresa ve las suyas y, si no personalizó, las
de la plataforma en solo lectura). El borrado del catálogo es aparte y hay que pensarlo: una
pregunta ya elegida por una versión publicada tiene respuestas colgando.

---

## 2 · El enlace del enunciado caduca, y puede quedar imposible de renovar

**Qué le pasa a alguien.** Al candidato le llega el correo de la prueba y **el enlace al
enunciado no abre**. No hay enunciado, no hay prueba.

**Por qué pasa.** El enunciado se sube al almacén y **el enlace se firma en ese momento, para 180
días**, y se guarda ya firmado en `version_plantilla_prueba.url_consigna`. El correo no vuelve a
firmar nada: pega lo que hay guardado. Así que la cuenta atrás empieza el día que alguien sube el
archivo, no el día que se invita a nadie. Una convocatoria que recluta más de seis meses —o una
plantilla preparada con mucha antelación— manda un enlace muerto.

**El caso feo, y está comprobado en el código.** Subir el enunciado **solo se permite mientras la
versión está en `BORRADOR`**. Y `CopiadorDeInstrumentosImpl`, cuando una empresa se lleva una
copia de una prueba de la plataforma, copia `urlConsigna` **tal cual** —con la caducidad ya
corriendo del original— y construye la versión nueva con `estado("PUBLICADA")`. Las dos cosas
juntas dan el caso sin salida: esa empresa recibe un enlace que va a morir y **ninguna forma de
volver a subir el archivo**, porque su versión nunca pasa por borrador. La única salida es crear
una versión nueva entera y rehacerla.

**Por qué son 180 días y no cinco minutos.** El enlace corriente de descarga dura cinco minutos a
propósito, porque un currículum que circule por un chat es el dato de una persona. El enunciado
no es el dato de nadie —es el examen que reciben todos los candidatos de esa vacante— y antes se
repartía como un enlace de Drive abierto que no caducaba nunca. Lo de hoy es menos público que
aquello, no más. **El número no es el problema.**

**Qué haría falta.** Guardar el **`archivo_id`** en vez de la URL, y firmar el enlace **al mandar
el correo**. Entonces no caduca nunca porque se emite fresco cada vez, y de paso la copia entre
empresas hereda un archivo y no una cuenta atrás.

---

## 3 · El cambio inesperado puede no aparecer nunca

**Qué le pasa a alguien.** Un candidato rinde la prueba, **nunca ve el cambio inesperado**, y se
le califica igual por cómo se adaptó a él. La instrucción del agente que califica dice que cómo
se reaccionó al cambio forma parte de lo que se mide.

**Por qué pasa.** La versión de la plantilla guarda un rango —«entre el minuto 30 y el 50»— y al
empezar se sortea uno concreto. **Ese sorteo no mira cuánto dura de verdad la prueba.** Si la
vacante fijó 10 minutos para su etapa técnica, el reloj cierra en el 10, el barrido entrega sola
la prueba, y el minuto 30 no llega nunca.

**Qué lo sostenía antes.** El rango de 60 a 120 minutos que se exigía al publicar: con esa
horquilla, un cambio sorteado entre el 30 y el 50 siempre cabía. **Ese rango se retiró el
31/08/2026** —decisión de Renaser— y con él se fue la garantía, sin que nadie se diera cuenta de
que la sostenía.

⚠️ **Y no había red debajo.** El diccionario de datos afirmaba que los minutos del cambio «tienen
que caber dentro de `duracion_minutos`». **Esa restricción no existe** —ni CHECK en ninguna
migración, ni comprobación en el código— y nunca existió. Corregido en el documento el
01/09/2026.

**A quién afecta hoy: a nadie.** Medido el 01/09/2026 contra la base: **una sola versión publicada
tiene cambio configurado, y ningún candidato la ha rendido nunca.** Es un dato de ese día, no algo
que se pueda volver a deducir del código: si alguien configura una prueba corta con cambio, deja
de ser cierto.

**Qué haría falta.** Decidir primero cuál de las dos: rechazar al publicar un rango que no quepa
en la duración —que devuelve el problema a quien escribe la prueba, pero no ve los minutos que
pondrá la vacante— o acotar el sorteo a la duración efectiva al empezar el intento, que es donde
por fin se sabe el número de verdad. Lo segundo parece mejor y hay que confirmarlo con Renaser:
cambia dónde aparece el cambio respecto de lo que la plantilla pidió.

**Está escrito en un test**, saltado a propósito: el método `elCambioInesperadoCabeDentroDelReloj`
de `RelojDeLaEtapaTecnicaQaTest`. ⚠️ **La clase entera no está saltada** —los otros dos casos
pasan y son la red de los dos defectos hermanos que sí se arreglaron—: es ese método y solo ese.
Cuando se decida qué hacer, se le quita el `@Disabled` y tiene que pasar.

---

## 4 · La subida del enunciado no se ha probado contra un almacén de verdad

**Qué no se sabe.** Que el archivo se sirva. Está probado que se sube, que la URL se guarda en la
versión y que se audita; **no** que el enlace, puesto en un navegador, devuelva el PDF.

**Por qué.** En local y en los tests el almacén es un doble en memoria que reparte direcciones
`memoria://…`. Firman, se guardan y todo queda verde sin que ningún byte salga a ninguna parte.
El almacén de verdad es Supabase, y el camino de firmar para 180 días —que es nuevo, no el de
cinco minutos de siempre— no lo ha recorrido nunca un archivo real.

**Qué haría falta.** Subir un enunciado contra el entorno de Pruebas, abrir el enlace en un
navegador y comprobar que descarga. Diez minutos. Mientras no se haga, **no invitar a una tanda
con un enunciado subido por el panel** sin abrirlo antes uno mismo.

---

## 5 · Un JSON mal escrito contra la API devuelve 500 en vez de 400

**Qué le pasa a alguien.** Quien consume la API —hoy el frontend, mañana quien integre— manda un
cuerpo con una coma de más, una llave sin cerrar o un número donde iba un texto, y recibe **un 500
sin explicación**. El error dice «fallo del servidor» cuando lo que pasa es que el mensaje venía
mal escrito, así que quien lo recibe busca la avería en el sitio equivocado. Y en el registro del
servidor esos fallos se mezclan con los que sí son averías de verdad.

**Por qué pasa.** `ManejadorErrores` enumera las excepciones del proyecto para traducirlas a un
código y un mensaje, pero **no cubre las que lanza Spring MVC antes de llegar a ningún controlador**
—`HttpMessageNotReadableException` la primera—. Nadie las atrapa, así que salen por el camino por
defecto, que es el 500.

**Es deuda anterior a esta rama**, y es **la causa conocida de casi todos los fallos del fuzzing**
de la API: el barrido manda cuerpos deformes a propósito y cuenta 500 donde debería contar 400.
Comprobado a mano el **15/09/2026**.

**Qué haría falta.** Añadir a `ManejadorErrores` los `@ExceptionHandler` de las excepciones de
Spring MVC —cuerpo ilegible, parámetro que falta, tipo que no convierte, método no permitido— con
el mismo formato de error que ya usa el resto. Es un archivo y ningún cambio de contrato: lo que
cambia es el número que se devuelve, de 500 a 400. Conviene hacerlo antes de volver a medir el
fuzzing, porque hasta entonces su cifra no dice casi nada.

---

## 6 · La suite de extremo a extremo sale con 19 fallos de fondo

**Qué le pasa a alguien.** Quien corre la suite contra una base recién sembrada la ve terminar en
rojo, y **no puede saber si rompió algo**. Sin una cifra de referencia, el rojo no dice nada: ni
«esto estaba así», ni «esto lo rompiste tú».

**La medida, del 15/09/2026: 19 fallan, 153 pasan.** Es un recuento de una corrida completa contra
base recién sembrada, no una suma de lo que fue arrastrando cada rama.

**Por qué pasa.** No son regresiones. Los specs de `03-orden`, `04-filtros`, `05-excel`,
`06-sin-ciudad`, `07-movil`, `08-teclado`, `14-vacante`, `18-ranking-contra-api` y
`20-prueba-y-empresas` **esperan notas y cifras concretas que el sembrador de datos de prueba no
produce**. Se escribieron contra una base que tenía otros datos, y el sembrador siguió su camino.

**Qué haría falta.** Decidir cuál de las dos, y no a medias: que el sembrador produzca los valores
que los specs esperan, o que los specs dejen de esperar cifras exactas y comprueben la forma —que
el orden sea descendente, que la columna exista, que el Excel traiga las hojas— en vez del número.
Lo segundo es más barato y más duradero; lo primero conserva la capacidad de comprobar que una
cuenta da lo que tiene que dar. Mientras no se haga, **la cifra de arriba es la referencia**: 19
es el rojo esperado, y cualquier número mayor sí es una regresión.

---

## Documentos relacionados

- [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) — el rango de duración retirado está en
  RF-76 y el cambio inesperado en RF-77
- [Modelo de datos](05-MODELO-DE-DATOS.md) — qué impide la base y qué no
- [Diccionario de datos](07-DICCIONARIO-DE-DATOS.md) — las columnas que salen aquí, una por una
- [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md) — qué se comprueba solo
- [Trabajar en local](TRABAJAR-EN-LOCAL.md) — cómo se corren las pruebas de extremo a extremo, y
  las dos trampas que hacen que mientan
