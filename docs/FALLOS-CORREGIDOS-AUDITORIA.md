# Los siete fallos de la auditoría del 18/08

Una revisión del backend encontró siete fallos que las pruebas no veían. **Cuatro tocan
dinero o decisiones de contratación**; los otros tres, la confianza en lo que la pantalla dice.

Ninguno daba error. Todos devolvían un número, un estado o un mensaje que parecían correctos,
que es lo que los hacía difíciles de ver y caros de descubrir.

Cada uno tiene su prueba con nombre propio. Si alguien deshace el arreglo, la prueba lo dice.

---

## Los cuatro graves

### 1 · Una postulación se quedaba clavada para siempre

**Qué pasaba.** A un candidato se le cribaba el currículum. Días después entregaba su
evaluación. Sus respuestas **no se puntuaban nunca** y la postulación se quedaba en
«calificando» de por vida. Ningún botón la rescataba: volver a pedir la calificación no hacía
nada.

**Por qué.** La fila arrancaba siempre por el primer paso. Ese paso —leer el currículum— ya
estaba hecho de la criba, así que no se creaba nada y ahí terminaba todo. Los pasos que sí
faltaban, el evaluador y el retrato, nunca llegaban a pedirse.

**Cómo está ahora.** La fila **se recorre** hasta encontrar el primer paso que de verdad falta,
en vez de mirar solo el primero. En este caso arranca por el evaluador. Y cuando el evaluador
termina, el retrato que se armó en la criba —sin las respuestas— se rehace: se calculó antes de
que existiera lo que ahora tiene que resumir.

> `ColaCalificacionIaImpl.pasoQueToca` · prueba:
> `alCribadoQueDespuesEntregaSuEvaluacionSeLeEncolaElEvaluador`

### 2 · Una pasada fina fallida se contaba como terminada

**Qué pasaba.** Si la segunda pasada fallaba pero la primera había terminado, la pantalla
decía «terminada», el contador de fallidos marcaba cero, y unas **notas provisionales se
presentaban como definitivas**. Nadie se enteraba de que la pasada cara no había corrido.

**Por qué.** El estado se calculaba mirando todos los trabajos juntos. Encontraba el agente que
cierra la etapa terminado —el de la pasada rápida— y contestaba que sí.

**Cómo está ahora.** Solo cuenta **la última pasada**. Lo que hay en pantalla se sigue diciendo
aparte: el estado dice «falló» y la pasada dice «rápida», que es la verdad completa.

> `ColaCalificacionIaImpl.comoVa` · prueba: `unaFinaFallidaSobreUnaRapidaTerminadaEsFallida`

### 3 · La criba resucitaba cerradas y contratadas

**Qué pasaba.** La criba de una tanda barría **toda** la vacante. Quien se había retirado, quien
no continuó y quien ya estaba contratado seguían ahí con su currículum, así que volvían a «por
confirmar» —a la bandeja de alguien— y de paso se pagaba el modelo por cada uno.

**Por qué.** Dos cosas a la vez. La criba no miraba el estado, y la máquina de estados
comprobaba a dónde iba una postulación pero no **de dónde salía**.

**Cómo está ahora.** Las dos. La criba salta a quien ya terminó, y la máquina se niega a mover
una postulación que está en un estado final. Lo segundo es lo que lo hace cierto para siempre:
protege también a quien lo intente mañana desde otro sitio.

> `MaquinaEstados.transicionar` y `ServicioPerfilIntegralPanelImpl.cribaRapida` · pruebas:
> `laCribaRapidaNoResucitaAQuienYaTermino`, `laCribaDeUnCurriculumSeNiegaSiLaPostulacionYaTermino`

### 4 · La nota del currículum mezclaba etapas

**Qué pasaba.** La columna «nota del currículum» **cambiaba sola** en cuanto un facilitador
calificaba una simulación. Dos currículums idénticos mostraban notas distintas sin que nadie
hubiera tocado un currículum.

**Por qué.** La cuenta sumaba cualquier criterio que tuviera peso. Desde que existen la
simulación y la validación, la misma versión de pesos trae también los diez criterios de una y
los nueve de la otra.

**Cómo está ahora.** Solo entran los criterios del currículum, y se dice explícitamente. El
mismo defecto vivía en dos sitios y se arregló en los dos: la columna del ranking y la nota que
se guarda al recalificar.

> `ServicioPerfilIntegralPanelImpl.notaCurriculum` y `PuenteCalificacionIaImpl.notaCurriculum`
> · prueba: `laNotaDelCurriculumNoSeMezclaConLaDeOtrasEtapas`

---

## Los tres medios

### 5 · Los botones mentían al contar

**Qué pasaba.** Los botones de criba respondían «43 en cola» y **lo escribían en la auditoría**
aunque no se hubiera encolado ni un trabajo. Un segundo clic daba el mismo número que el
primero. Y si la cadena se rompía a mitad, la respuesta seguía siendo «encolada» sin haber
hecho nada, así que no había forma de reintentar.

**Cómo está ahora.** Encolar dice si encoló. Se cuenta lo que quedó en la cola, no lo que se
intentó, y a quien no se encoló tampoco se le mueve el estado. Cuando no había nada que hacer,
la respuesta lo dice: `SIN_CAMBIOS`.

> `ColaCalificacionIa.encolar*` devuelve `boolean` · prueba:
> `laCribaRapidaSoloCuentaAQuienDeVerdadQuedoEnLaCola`

### 6 · La ficha del candidato solo se sacaba en la pasada rápida

**Qué pasaba.** Quien se calificaba por la fila normal o por la fina salía **sin teléfono, sin
años de experiencia y sin último puesto**, y no había manera de rellenarlo después.

**Cómo está ahora.** Ese paso está en las dos filas, y se salta solo cuando la ficha ya existe:
son datos copiados del currículum, no notas, así que no cambian salvo que cambie el archivo.
Por eso reemplazar un currículum ahora borra la ficha, para que se vuelva a sacar del archivo
nuevo en vez de enseñar los datos del viejo.

> `ColaCalificacionIaImpl.seSalta` · pruebas: `laPasadaFinaEmpiezaSacandoLosDatosDelCandidato`,
> `siLaFichaDeDatosYaEstaSacadaNoSeVuelveAPagar`

### 7 · Las cribas filtraban con el alcance de otro permiso

**Qué pasaba.** El endpoint exige `ajustar_nota`, pero el filtro de vacantes miraba siempre el
alcance de `ver_embudo`. Un rol con `ajustar_nota` limitado a sus vacantes y `ver_embudo` sin
límite podría cribar una convocatoria ajena.

**Por qué importa aunque hoy no pase.** Ningún rol sembrado tiene esa forma. Pero **los roles se
configuran desde el panel**: esto dejaría de ser cierto sin que nadie tocara una línea de
código, y nada avisaría.

**Cómo está ahora.** El permiso llega por parámetro. Cada endpoint filtra con el suyo.

> `ServicioPerfilIntegralPanelImpl.vacanteVisible` · prueba:
> `laCribaFiltraConElAlcanceDeSuPropioPermisoYNoConElDeOtro`

---

## Lo que tienen en común

Cinco de los siete son la misma historia: **algo que ya está hecho no vuelve a hacerse**. Es una
regla sensata —evita pagar dos veces al proveedor— y estaba aplicada con demasiada mano dura.
«Ya está hecho» no es lo mismo que «sigue estando bien»: un retrato armado sin las respuestas
del candidato está hecho y está incompleto.

La regla nueva lo dice entero: se rehace lo que se calculó **antes** de aquello de lo que
depende. Y lo que no depende de nada nuevo —la ficha de datos del currículum— no se toca.

---

## De paso: dos arreglos que no venían en la lista

**Los dos controladores que hablaban con la base.** `CatalogoController` y `PanelAuthController`
inyectaban repositorios y tocaban entidades, saltándose la regla de que entre la petición y la
base va un servicio. Estuvieron nombrados como excepción en las pruebas de arquitectura, con su
motivo escrito, y eso fue lo que hizo que se arreglaran: una desviación a la vista se decide,
una escondida tras un patrón genérico se olvida. Ahora hay `ServicioCatalogo` y
`ServicioAccesoEquipo`, y **la regla ya no tiene excepciones**.

**Las pruebas dependían del archivo de secretos de cada uno.** Al apuntar el broker local a un
servicio con TLS, seis pruebas de integración empezaron a fallar: heredaban esa configuración
en vez de hablar con su propio contenedor. Ya no: cada prueba fija su broker, y la tanda da lo
mismo en cualquier máquina.

---

## Enlaces

- [Los cinco fallos de la criba](FALLOS-CORREGIDOS-CRIBA.md) — los que aparecieron al pasar 190
  currículums reales
- [La criba de currículums](CRIBA-DE-CURRICULUMS.md) — cómo funciona el recorrido entero
- [Lo que el proyecto se comprueba solo](COMPROBACIONES-AUTOMATICAS.md) — qué mira la
  compilación por su cuenta y qué falta
