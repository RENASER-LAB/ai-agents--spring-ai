# Defectos conocidos

**Lo que está roto, se sabe, y sigue abierto.** No es un historial: en cuanto algo se arregla,
sale de aquí y su explicación se va al documento que corresponda. Si algo está en esta lista es
porque hoy, tal como está desplegado, puede pasarle a alguien.

Cada entrada dice tres cosas: **qué le pasa a una persona de verdad**, **por qué pasa** y **qué
haría falta para arreglarlo**. Sin lo tercero, una lista así solo sirve para preocupar.

> **Por qué existe este documento.** Los defectos que se conocen y no se escriben se vuelven a
> descubrir cada pocos meses, normalmente con un candidato dentro. Lo que **ya está arreglado**
> no vive aquí: vive en el documento de su tema, en `CLAUDE.MD` o en el javadoc de su clase.

Última revisión: **23/09/2026**.

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
el orden sea descendente, que la columna exista, que el Excel traiga su hoja «Datos»— en vez del
número.
Lo segundo es más barato y más duradero; lo primero conserva la capacidad de comprobar que una
cuenta da lo que tiene que dar. Mientras no se haga, **la cifra de arriba es la referencia**: 19
es el rojo esperado, y cualquier número mayor sí es una regresión.

---

## 7 · Cinco puntos del Perfil Integral están reservados a algo que nadie calcula

**Qué le pasa a alguien.** Quien abra el reparto de pesos de la **versión inicial** ve que el
Perfil Integral vale 40 y que dentro de ese 40 el **psicométrico se lleva 5**. Es mentira: el
módulo psicométrico no existe, nadie pone esa nota y **ninguna cuenta del sistema lee ese peso**.
Quien configure los pesos creyendo que ahí se mide algo —o quien le explique el reparto al
cliente leyendo la tabla— está contando con una medición que no ocurre.

**Por qué pasa.** La nota del Perfil Integral se arma con **dos cosas y solo dos**: el currículum
y la evaluación, cada una por su peso y divididas entre lo que suman esas dos. El psicométrico ni
entra en la suma ni aparece en el divisor, así que esos cinco puntos **no le bajan la nota a
nadie**: simplemente se ignoran. Lo que sí sigue vivo es el componente: la base lo acepta como
valor válido de la columna, el endpoint de pesos lo admite y la comprobación de publicar exige
que los componentes sumen lo mismo que pesa la etapa —con el psicométrico dentro de esa suma—.
Una versión que le dé peso pasa todas las puertas y no la calcula nadie. De la tercera versión de
pesos en adelante las migraciones lo dejaron en **0** y repartieron sus puntos entre el currículum
y la evaluación —que es lo que dicen los documentos—, pero **la versión inicial sembrada sigue
con 10 / 5 / 25**.

**Qué haría falta.** Decidir una de dos, y no las dos a medias: que la versión inicial deje el
psicométrico en 0 como las demás —una migración de datos, sin tocar el esquema—, o que publicar
una versión que le dé peso a un componente que nadie calcula **avise o se niegue**, en vez de
aceptarlo en silencio. Mientras tanto, al leer un reparto de pesos hay que mirar el número del
psicométrico: si no es 0, esos puntos no están midiendo nada.

⚠️ **Esto no es parte del formato nuevo del Excel del ranking** (16/09/2026) ni se tocó con él;
se anota aquí porque salió al verificar los pesos que esa hoja combina.

---

## 8 · La lectura de currículums depende de un nombre que el proveedor ya retiró

**Qué le pasa a alguien.** Depende de cómo caiga el día que el proveedor toque ese nombre, y los
dos finales están comprobados en la configuración:

- **Si lo reenruta**, nadie ve un error: leer cada currículum pasa a tardar varias veces más
  —medido el 17/09/2026, cuatro—, así que quien acaba de cargar una tanda la ve arrastrarse sin
  ninguna explicación en pantalla.
- **Si lo retira de verdad**, la lectura del currículum muere tras los tres intentos y el mensaje
  del registro **no señala la causa**: no casa con ninguna de las conocidas —saldo, clave, exceso
  de llamadas—, así que quien lo mire buscará en el sitio equivocado.

**Por qué pasa.** Al leer un currículum no se quiere que el modelo piense antes de contestar: se
quiere ordenar la tanda rápido. Ese nombre retirado es **hoy la única forma de pedirlo callado**,
porque la forma nueva es un parámetro que la versión de Spring AI del proyecto no expone. Es deuda
tomada a sabiendas, no un descuido.

**Qué haría falta.** Que Spring AI deje apagar el pensamiento por su cuenta; entonces el alias
sobra y el nombre se cambia en una línea. Hasta ese día, lo único que se puede hacer es mirar de
vez en cuando qué nombres sigue sirviendo el proveedor. El contexto entero está en
[El modelo cambió de nombre](EL-MODELO-CAMBIO-DE-NOMBRE.md).

---

## 9 · El gasto de IA que se muestra no es el importe de la factura

**Qué le pasa a alguien.** Quien mire cuánto lleva gastado una empresa **está viendo una
aproximación**, no la factura, y no hay nada en pantalla que lo diga. Se queda corta en lo que se
haya llamado de madrugada —el proveedor cobra el doble en esa franja— y se pasa en lo que el
proveedor haya reconocido de una consulta repetida, que cuesta cincuenta veces menos. La tabla de
precios **no distingue ninguna de las dos cosas**.

**Por qué pasa.** Hay que elegir un número por modelo, y se eligió el de fuera de punta, que es la
franja donde ocurre casi todo el trabajo real: en horario de oficina peruano la punta ni siquiera
ha empezado. Elegir el caro habría significado equivocarse el doble en el 100% del tráfico para
cubrir una minoría, y el coste de equivocarse por ahí es peor que el del importe: es lo
de abajo.

**Y hay un freno que no avisa.** Cuando una empresa cruza su tope, los trabajos nuevos se quedan
esperando, **no sale ningún correo** y el candidato ve su proceso «en curso», indistinguible de
uno que avanza; no se destraba hasta que alguien sube el tope o empieza el mes. Eso es
independiente del precio, pero es lo que convierte un error de cálculo en una postulación
congelada en silencio. **Hoy no le pasa a nadie porque ninguna empresa tiene tope puesto.**

**Qué haría falta.** Para el importe: cuadrar contra una factura real y registrar entonces la
tarifa buena —una nueva, nunca editando la vieja, o se reescribiría el pasado—. Para el freno: que
avise a alguien, que hoy no lo hace. Y si algún día se programan tandas de madrugada, decidir si
la tabla aprende a distinguir la franja horaria antes de que el desvío importe.

---

## 10 · La diferencia entre «no la terminó» y «falta calificarla» no llega a todas partes

**Qué le pasa a alguien.** Desde el 18/09/2026 la pestaña «Prueba del puesto» separa la prueba que
el candidato no terminó de la que entregó y espera nota. **En dos sitios no.** Quien lleva una
vacante que rinde el **cuestionario técnico** sigue viendo el texto de siempre en todas las filas
sin nota, también en las de quien ya entregó, y no tiene desde el ranking manera de saber cuál es
cuál. Y quien descarga el Excel lee «rúbrica incompleta» en la columna de la nota técnica pase lo
que pase, aunque la pantalla desde la que lo bajó sí lo distinga: la hoja se reenvía y se lee lejos
del panel, que es donde más falta hace la explicación.

**Por qué pasa.** La distinción sale de `intento_prueba`, y el cuestionario técnico no escribe
ahí: se rinde y se califica pregunta a pregunta sobre `Evaluacion`. Esas filas salen con el valor
neutro y conservan el texto anterior. En el volcado, `estadoPrueba` ni se consulta: la celda de la
nota tiene un solo texto para cualquier ausencia.

**No es una regresión, es el alcance que se decidió.** Antes de esa fecha la diferencia no se veía
en ninguno de los dos sitios. Se escribe aquí para que no se vuelva a descubrir con una tanda
delante.

**Qué haría falta.** Para el cuestionario: sacar del examen técnico lo mismo que hoy dice el
intento —si hubo entrega y si la hizo la persona o el reloj— y traducirlo a los mismos cuatro
valores. Para el Excel: llevar `estadoPrueba` al volcado y escribir en la celda el texto que ya
usa la tabla. Antes de eso conviene decidir si la hoja debe decir exactamente lo mismo que la
pantalla, porque hoy el cliente no espera que difieran.

---

## 11 · Cerrar una postulación no suelta su plaza en la sesión de simulación

**Qué le pasa a alguien.** Quien ya eligió fecha para la simulación ocupa una plaza del cupo de
esa sesión. Si su postulación se cierra a mano —o porque se eliminó su vacante (RF-14d)—, la
plaza **sigue ocupada**: la sesión puede figurar llena y otro candidato —de la misma vacante o de
otra que comparta la sesión— se queda esperando fecha sin que haga falta.

**Por qué pasa.** El cupo cuenta las inscripciones con `es_vigente = true`
(`SesionSimulacionRepository.disponiblesPara`), y solo dos caminos las marcan como no vigentes:
cancelar la sesión y marcar que la persona no asistió. `MaquinaEstados` no toca inscripciones al
cerrar. La eliminación de vacantes reutiliza ese mismo cierre y tiene, a propósito, exactamente
la misma paridad: el RF-14d pide que libere lo mismo que un cierre manual, incluida esta plaza, y
hoy ninguno de los dos la libera. QA lo registró como limitación previa al aprobar la entrega del
21/09/2026.

**Qué haría falta.** Al cerrar una postulación, marcar no vigente su inscripción y recalcular la
disponibilidad de la sesión, como ya hace la cancelación. Antes, decidir si vale para cualquier
cierre o solo para los que decide una persona, y si a quien esperaba fecha se le avisa.

---

## 12 · Crear cuenta o aceptar una invitación con una contraseña muy larga da un error en inglés

**Qué le pasa a alguien.** Quien crea su cuenta en el portal, o acepta la invitación al panel,
con una contraseña muy larga —o no tan larga, pero con muchas tildes, «ñ» o emojis— recibe un
error bajo el campo que dice, en inglés, «password cannot be more than 72 bytes». No entiende
qué pasó ni cuánto tiene que acortarla.

**Por qué pasa.** BCrypt, con el que se guardan las contraseñas, acepta como mucho 72 bytes, y
una tilde o una «ñ» ocupan dos y un emoji cuatro. Las pantallas de contraseña nueva por enlace
(22/09/2026) lo comprueban antes con `@CabeEnBcrypt` y dan un motivo en español; crear la
cuenta (`POST /portal/cuentas`) y aceptar la invitación (`POST /panel/auth/invitacion`) no
llevan esa validación, y el texto de Spring Security llega tal cual. QA lo registró como fuera
del alcance de esa entrega.

**Qué haría falta.** Poner `@CabeEnBcrypt` en la contraseña de esos dos cuerpos, y en el
frontend la misma comprobación que ya usan las pantallas de contraseña nueva, con el mismo
mensaje: «La contraseña es demasiado larga. Usa como máximo 72 caracteres; las letras con
tilde, la ñ y los emojis cuentan por más de uno.».

---

## 13 · Un texto de consentimiento puede decir «S.A.C..»

**Qué le pasa a alguien.** En un texto de consentimiento donde el nombre de la empresa cierra
la frase, cuando esa empresa es la plataforma se lee «RENASER CONSULTING S.A.C..», con el punto
repetido. No cambia lo que se acepta, pero es un texto legal y se nota.

**Por qué pasa.** El nombre de la plataforma ya acaba en punto («RENASER CONSULTING S.A.C.»,
desde la `V54`), y el texto pone el punto de la frase justo después. Los dos correos de la
contraseña olvidada (`V61`) se escribieron para que ninguna frase termine en el nombre; los
consentimientos de la `V54` no. QA lo vio el 22/09/2026 y quedó fuera de esa entrega.

**Qué haría falta.** Localizar las frases que terminan en el nombre de la empresa y
reescribirlas en una versión nueva del texto —los textos publicados no se editan—, o que quien
compone el texto no añada un punto si el nombre ya acaba en uno. Lo primero pasa por el
abogado que tiene que firmar los textos.

---

## Documentos relacionados

- [El modelo cambió de nombre](EL-MODELO-CAMBIO-DE-NOMBRE.md) — de dónde salen el 8 y el 9
- [La prueba del puesto, por dentro](PRUEBA-DEL-PUESTO.md) — de dónde sale el 10: qué dice la
  pestaña cuando no hay nota, y con qué se decide
- [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) — el rango de duración retirado está en
  RF-76 y el cambio inesperado en RF-77
- [Modelo de datos](05-MODELO-DE-DATOS.md) — qué impide la base y qué no
- [Diccionario de datos](07-DICCIONARIO-DE-DATOS.md) — las columnas que salen aquí, una por una
- [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md) — qué se comprueba solo
- [Trabajar en local](TRABAJAR-EN-LOCAL.md) — cómo se corren las pruebas de extremo a extremo, y
  las dos trampas que hacen que mientan
