# El modelo cambió de nombre

Qué pasa cuando el proveedor de la IA renombra su catálogo y **no rompe nada**: el sistema sigue
funcionando, nadie se entera, y el contador del gasto se queda en cero. Lo que se arregló el
17/09/2026 (migración `V57`).

---

## En una frase

**El sistema le pide un modelo al proveedor por su nombre, y el proveedor contesta diciendo cuál
usó de verdad. El precio se busca por el segundo, no por el primero — así que el día que el
proveedor empezó a contestar un nombre nuevo, todo siguió funcionando y el gasto dejó de
contarse.**

---

## Lo que pasó, en orden

**El 10/09/2026 DeepSeek retiró el modelo que el sistema venía usando y publicó el siguiente**,
DeepSeek-V4.1-Flash. Su nombre de API es `deepseek-flash` a secas: **no lleva el «4.1» dentro**,
aunque el modelo se anuncie así. El catálogo vivo pasó a ser dos nombres y nada más —
`deepseek-flash` y `deepseek-v4-pro`—, y se comprueba pidiéndoselo al propio proveedor
(`GET https://api.deepseek.com/models`).

**Los nombres viejos no fallaron: quedaron atendiendo como alias.** Medido contra la API real el
17/09/2026:

| Lo que se pide | Lo que contesta | ¿Piensa antes de responder? |
|---|---|---|
| `deepseek-chat` | `deepseek-flash` | **No** |
| `deepseek-v4-flash` | `deepseek-flash` | Sí |
| `deepseek-flash` | `deepseek-flash` | Sí, por defecto |

Por eso nadie se enteró de nada. **El sistema llevaba una semana hablando con un modelo distinto
sin que ningún archivo del proyecto hubiera cambiado**, y las notas, las lecturas de currículum y
los agentes seguían saliendo.

---

## La trampa: el nombre que se pide no es el nombre que contesta

Cada vez que la IA hace un trabajo —leer un currículum, poner una nota, armar un perfil— queda
apuntado en la bitácora qué modelo lo hizo y cuánto costó. **Y el modelo que se apunta es el que
el proveedor dice haber usado, no el que se le pidió.**

Eso es deliberado y es lo correcto: lo que se paga es lo que de verdad corrió. Pero tiene una
consecuencia que no se ve hasta que muerde: **el precio se busca por ese nombre**, y si el
proveedor empieza a contestar uno que la tabla de precios no conoce, no falla nada. La
calificación sale, el candidato avanza, el panel enseña su nota. Lo único que pasa es que
**la casilla del costo se queda vacía** y se anota un aviso que nadie lee.

El daño no está en la contabilidad, está en el freno:

1. El gasto de IA de cada empresa se suma mes a mes a partir de esos costos.
2. Sobre esa suma trabaja el **tope mensual**: al 80% sale un aviso, y al 100% los trabajos
   nuevos se quedan esperando hasta que la empresa recupere cupo.
3. Con todos los costos vacíos, esa suma no sube nunca. **El tope no mira otra cosa: lo que no
   tiene precio, para él no existió.**

Desde el 10/09, **todas las calificaciones y todas las lecturas de currículum se anotaron sin
costo**, el gasto del mes dejó de sumar y el tope quedó ciego.

**Es el mismo fallo que ya se arregló una vez, entrando por otra puerta.** La `V39` existe porque
a la primera pasada de la criba —una llamada por candidato, la más frecuente del sistema— se le
había olvidado el precio y todo su gasto salía vacío. Aquello fue un nombre que faltaba en la
tabla; esto es un nombre al que el proveedor renombró todo. El síntoma es idéntico: **silencio**.

---

## Cómo se comprobó, para que no sea un relato

No se dedujo del código: se midieron **las dos versiones contra la API real, con el mismo
currículum**.

- **Antes del arreglo**: cinco lecturas seguidas, **las cinco con el costo vacío**, y en el
  registro el mismo aviso cinco veces: no hay tarifa vigente para el modelo que el proveedor
  acababa de contestar.
- **Después**: las mismas cinco lecturas, **las cinco con su costo** —entre 0,0010 y 0,0093 USD—
  y ningún aviso.

---

## Qué se cambió

1. **El modelo por defecto se escribe con su nombre real.** No porque el viejo fallara —no
   fallaba—, sino para que lo que se pide, lo que contesta el proveedor y lo que la tabla tarifa
   sean el mismo nombre. Mientras sean tres nombres distintos, el gasto depende de la buena
   voluntad de un alias.
2. **La primera pasada de la criba sigue pidiendo un nombre retirado**, a propósito. Abajo está
   el porqué.
3. **La `V57` siembra los dos precios que faltaban.** El del modelo nuevo, y el del modelo del
   orquestador — que **no lo tuvo nunca**: no está escrito en la configuración, así que nadie lo
   miraba, y lo que gasta ese agente lleva sin contarse desde agosto. Arreglar el costo a medias
   es dejarlo roto.
4. **Los dobles de las pruebas dejaron de mentir.** Contestaban nombres que el proveedor ya no
   responde, y fueron ellos los que dejaron pasar el fallo una semana entera. Está contado en
   [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md).

---

## La primera pasada cuelga de un nombre retirado, y hay que saberlo

Al leer un currículum el sistema **no quiere que el modelo piense antes de responder**: quiere el
orden de la tanda rápido y barato, no una decisión de contratación. Pedir el modelo por su nombre
nuevo lo trae **pensando**; pedirlo por el alias viejo lo trae **callado**. Hoy el alias es la
única palanca que hay: la forma nueva de apagar el pensamiento es un parámetro que la versión de
Spring AI del proyecto **no tiene dónde poner**.

Así que no es un descuido, es **deuda tomada a sabiendas**, y conviene saber cómo se va a notar
el día que el proveedor toque el alias. Hay dos finales posibles y no se parecen:

| Si el proveedor... | Qué pasa | Cómo se nota |
|---|---|---|
| **Lo reenruta** al modelo nuevo sin más | Nada falla. La lectura de cada currículum empieza a pensar antes de contestar | **En silencio**: cada currículum tarda varias veces más y gasta miles de tokens que nadie pidió. Quien carga una tanda la ve tardar muchísimo más sin ninguna explicación en pantalla |
| **Lo retira de verdad** | La llamada devuelve error y la lectura del currículum muere tras los tres intentos | **Ruidoso, pero engañoso**: el error no casa con ninguna causa conocida —ni saldo, ni clave, ni exceso de llamadas—, así que el mensaje no señala el problema |

**Lo que hay que mirar en los dos casos es lo mismo**: si la versión de Spring AI que usa el
proyecto ya deja apagar el pensamiento por su cuenta. Ese día el alias sobra.

Para calibrar cuánto está en juego, **medido el 17/09/2026 contra la API real**: pedir el modelo
por el alias **tarda cuatro veces menos y gasta siete veces menos salida** que pedirlo por su
nombre nuevo. La otra cifra que el proyecto cita —19 segundos por currículum en vez de 48— es de
agosto y compara contra el modelo anterior, así que **no son la misma medición**; las dos apuntan
al mismo sitio y la nueva es la del modelo que corre hoy.

---

## Qué precio se sembró, y por qué ese

**DeepSeek cobra dos tarifas por el mismo modelo según la hora del día**: hay una franja punta que
cuesta el doble que el resto. En hora de Perú esa punta cae de 20:00 a 23:00 y de 01:00 a 05:00,
que es justo cuando el sistema casi no trabaja.

**La tabla de precios del proyecto no distingue franja horaria**, solo desde cuándo rige cada
precio. Hay que elegir un número, y **se eligió el de fuera de punta**, que es el que corresponde
al tráfico de verdad:

- **Las horas de Renaser caen enteras fuera de punta.** Quien postula, quien mira el panel y quien
  manda calificar lo hace en horario de oficina, y la punta no empieza hasta las ocho de la noche.
  No es un promedio optimista: es la franja donde ocurre casi todo lo que este sistema hace.
- **Elegir el caro «por si acaso» no habría sido prudencia.** Sería equivocarse el doble en el
  100% del tráfico para cubrir una minoría: el gasto mostrado saldría sistemáticamente al doble
  del que llega en la factura, y el freno mordería con la mitad del consumo real.
- **Y ese freno de más no se ve.** Cuando una empresa cruza su tope, lo nuevo se queda esperando
  sin que salga ningún aviso: el candidato ve «en curso», que no se distingue de un proceso
  normal, y la cosa no se destraba hasta que Renaser sube el tope o empieza el mes. Una
  postulación congelada en silencio es peor que un dólar mal contado.

**Lo que este número no cubre, dicho sin adornos:** una llamada hecha de madrugada se cobra el
doble de lo que aquí se anota. Hoy eso no pasa —ningún trabajo programado llama al modelo, la cola
se mueve cuando alguien postula—, pero si algún día la criba se pone a correr de noche en tandas
grandes, el consumo del mes se quedará corto en esa parte y hay que volver a esta decisión.

En sentido contrario hay una holgura que compensa: el precio de entrada que se sembró es el de
consulta nueva, y cuando el proveedor reconoce una que ya había visto esa parte cuesta cincuenta
veces menos. La tabla no lo distingue, así que por ese lado el número tira hacia arriba.

**De todas formas hoy no frena a nadie**: ninguna empresa tiene tope puesto, y sin tope no hay nada
que cruzar. Los precios siguen siendo provisionales — el número que manda es el de la factura, y
cuando llegue se registra una tarifa nueva en vez de editar la vieja, para no reescribir el pasado.

### ⚠️ Lo que habría que revisar antes de ponerle un tope ajustado a alguien

Con el precio doblado, **el freno se dispara con la mitad del gasto real**, y el aviso del 80%
llega cuando la empresa va por el 40%. Eso sería tolerable si frenar se notara. **No se nota:**

- Al llegar al tope, los trabajos nuevos **se quedan esperando y no sale ningún correo** — solo
  queda una línea en el registro del servidor.
- **El candidato ve su proceso «en curso»**, que es indistinguible de un proceso que avanza con
  normalidad. No hay nada que le diga que su currículum no se está leyendo.
- Solo se despierta cuando la empresa recupera cupo: o alguien le sube el tope, o empieza el mes
  siguiente.

Es decir: **quien ponga un tope ajustado estará parando candidatos en silencio al doble de
velocidad de lo que cree.** Antes de hacerlo hay que decidir si se baja el precio a la franja
normal, si la tabla aprende a distinguir la hora, o si frenar pasa a avisar a alguien.

---

## Por qué los precios llevan la fecha que llevan

Un precio nuevo **no pisa al anterior**: se registra con la fecha desde la que rige, y lo ya
ejecutado conserva el suyo. Sin eso, un cambio de precios reescribiría el pasado — el gasto de
agosto cambiaría solo porque el proveedor subió su lista en setiembre.

Por eso las dos filas nuevas **no se fechan el día que se escribieron**:

- La del modelo nuevo rige **desde el día real en que DeepSeek cambió sus precios**, no desde hoy.
  Si algún día se vuelve a calcular lo ejecutado esa semana, cae en la tarifa que de verdad
  estaba corriendo.
- La del modelo del orquestador rige **desde el día en que nació el control del gasto**, porque su
  precio no cambió esta semana: lo que pasó es que nunca se registró. Fecharla hoy dejaría sin
  tarifa todas sus corridas anteriores, que son justamente las que llevan sin contarse.

**Los dos precios son provisionales**, igual que los que había: el número que manda es el de la
factura, y cuando llegue **se registra una tarifa nueva, no se edita esta**.

---

## Lo que esto deja aprendido

- **Un cambio de nombre de modelo no es cosmético.** Mientras el gasto se anote por el nombre que
  contesta el proveedor, renombrar es un cambio de contabilidad disfrazado de cambio de texto.
- **Un costo vacío no es un hueco en una tabla: es el freno del gasto mirando a otro lado.** Y no
  hace ruido, así que hay que ir a buscarlo.
- **Que algo siga funcionando no significa que siga siendo lo que era.** El sistema pasó una
  semana hablando con un modelo distinto del que decía su configuración, y todo salió bien porque
  el proveedor fue amable. La próxima vez puede no serlo.
- **La prueba que debía cazarlo miraba los nombres que la aplicación pide, no los que el proveedor
  responde.** Siguió en verde toda la semana. Ahora mira también los modelos que eligen los
  agentes —así apareció lo del orquestador—, pero el descase con el proveedor **ninguna prueba
  puede cazarlo sola**: hay que mirar el catálogo del proveedor de vez en cuando.

---

## Dónde está cada cosa

| Qué | Dónde |
|---|---|
| Qué modelo usa el sistema para cada cosa | [README del repositorio](../README.md) — «Modelos usados» |
| Por qué la tabla de precios es así, y el tope mensual | [Modelo de datos](05-MODELO-DE-DATOS.md) — «Agentes de inteligencia artificial» |
| Cómo se anota el costo y cómo frena el tope | [APIs del multiempresa](APIS-MULTIEMPRESA.md) — «Para quien toca el código» |
| Qué comprueba sola la compilación, y qué no pudo comprobar | [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md) |
| Con qué está hecho el sistema, y qué no se toca a ojo | [Trabajar en local](TRABAJAR-EN-LOCAL.md) |
| Qué le pasa a un candidato cuando la IA no responde | [Calificación con IA](CALIFICACION-CON-IA.md) — «Si la IA falla» |
| Lo que sigue abierto de todo esto | [Defectos conocidos](DEFECTOS-CONOCIDOS.md) |

Los porqués línea a línea están en la propia migración (`V57`) y en los comentarios de la
configuración: **cuando este documento y el código se contradigan, manda el código.**

---

## Lo que no está hecho

- **La primera pasada de la criba sigue pidiendo un nombre retirado.** Queda así hasta que la
  versión de Spring AI del proyecto deje apagar el pensamiento por su cuenta.
- **Nadie ha cuadrado estos precios contra una factura.** Son los del catálogo publicado, y se
  desvían por dos vías que la tabla no distingue: se quedan cortos de madrugada y se pasan en las
  consultas repetidas.
- **Nada avisa si el proveedor vuelve a renombrar su catálogo.** Hoy la única forma de enterarse
  es preguntárselo a él o ver costos vacíos en la bitácora.
- **Frenar por tope sigue sin avisarle a nadie.** Es anterior a este cambio y no lo arregla.
