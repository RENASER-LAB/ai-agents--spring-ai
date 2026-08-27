# La calificación con inteligencia artificial

Cómo funciona la parte en la que la IA lee el currículum, califica las respuestas abiertas y
arma el Perfil de Talento. Al final están también los dos agentes de las etapas siguientes:
el que califica la prueba del puesto y el que prepara la conversación final.

---

## En una frase

**Cuando el candidato entrega su evaluación, el sistema le pone nota solo: primero con
aritmética, después con tres agentes de IA que corren en fila, y al final la postulación
queda esperando a que una persona decida.**

---

## Qué pasa, paso a paso

1. El candidato **entrega** su evaluación.
2. El sistema puntúa **lo cerrado** al momento. Es aritmética contra una clave: no interviene
   ninguna IA y tarda milisegundos.
3. La postulación pasa a **«calificando»** y se encola el primer agente.
4. Corren los tres agentes, uno detrás de otro:

| Agente | Qué hace |
|---|---|
| **Evidencia del currículum** | Puntúa el currículum sobre 100 con los ocho criterios, con el peso que corresponde al nivel del puesto. Y clasifica cada afirmación: demostrada, declarada, contradicha o falta información |
| **Evaluador** | Califica de 0 a 4 las respuestas abiertas, citando la parte de la respuesta en que se basa. Con un banco CAZATALENTOS (método `CRITERIOS`) cambia el contrato: no devuelve puntaje sino **qué criterios vio** (C1 episodio, C2 autoría, C3 dato duro, C4 incomodidad, y si cumple la señal de 0) y el número lo cuenta el código |
| **Potencial y riesgo** | Arma el Perfil de Talento: adecuación, potencial, alto rendimiento, confianza de la evidencia, y los hallazgos |

5. Al terminar el tercero, el sistema **rehace la nota de la etapa** juntando currículum y
   evaluación con los pesos de la vacante, le asigna un **grupo de prioridad** y mueve la
   postulación a **«por confirmar»**, que es donde una persona mira y decide.

**Van en fila y no a la vez** porque el tercero necesita lo que dejaron los dos primeros. Y
así hay un solo trabajo vivo por candidato, que es lo que hace fácil reintentar.

---

## Antes de que la IA lea nada: el currículum recortado

**La IA nunca ve foto, edad, sexo ni estado civil.** Es requisito, no mejora.

El sistema saca el texto del archivo (PDF o Word) y produce **dos versiones**:

- La **completa**, que solo ve el equipo cuando abre la ficha.
- La **recortada**, que es la única que sale hacia el modelo. Donde había un dato prohibido
  queda escrito `[DATO NO UTILIZABLE]`.

Las dos se guardan, y además queda registrado el envío literal que se le hizo al modelo. Así
se puede demostrar después que la regla se cumplió.

La foto no hace falta borrarla: al pasar el archivo a texto las imágenes se quedan fuera
solas.

> **Lo que no cubre.** Un currículum es texto libre y siempre habrá una forma rara de escribir
> la edad que no esté en la lista. Lo que sí se garantiza es que las formas normales no pasan,
> y que la instrucción del agente le prohíbe además puntuar por esos datos.
>
> El **.doc antiguo** (el binario de Word de los noventa) no se puede leer. Si alguien sube
> uno, la calificación queda pendiente con un mensaje claro y hay que pedirle el PDF.

---

## Si la IA falla

**Nunca se inventa una nota.** Ni un cero, ni un aproximado.

- Cada intento fallido queda escrito, con el motivo.
- Se reintenta solo, hasta tres veces.
- Si se agotan los intentos, la postulación **se queda esperando** y sale un error en el
  registro nombrando al candidato. No avanza ni se descarta.
- Si el mensaje se pierde, o si el servidor se cae con un trabajo a medias, un vigilante lo
  vuelve a poner en la cola cada cinco minutos.

El motivo del fallo se distingue, porque no todos se arreglan igual:

| Qué pasó | Qué hacer |
|---|---|
| La clave del proveedor no vale | Ponerla bien. Reintentar no sirve de nada |
| La cuenta no tiene saldo | Recargar |
| El proveedor limita el ritmo | Nada: el reintento lo resuelve |
| Se agotó el tiempo de espera | Mirar si el envío es demasiado grande |
| El modelo se quedó sin espacio para responder | Subir el tope de tokens. **No es que el proveedor esté caído**: es que el modelo razona, y ese razonamiento gasta el mismo presupuesto que la respuesta |

Ese último caso es el que más engaña: desde fuera se ve igual que un proveedor caído, y la
causa es la contraria.

---

## Qué queda escrito de cada llamada

Todas, salgan bien o mal: qué agente fue, con qué versión, qué instrucción usó, qué se le
envió, qué respondió, cuánto tardó, cuántos tokens gastó y, si falló, por qué.

Y **cada nota que se guarda apunta a la llamada que la produjo**. Es lo que permite abrir una
nota de hace seis meses y ver exactamente de dónde salió.

Desde el 25/08 esa puerta existe de verdad: `GET /panel/postulaciones/{id}/evaluacion` devuelve
cada respuesta abierta con su nota de 0 a 4, la explicación, la evidencia que la IA citó de la
propia respuesta y la confianza; si una persona ajustó la nota, también el motivo. De lo cerrado
sale el promedio y cuántas preguntas fueron, no pregunta por pregunta: se corrige solo contra
una clave y no hay nada que explicar.

⚠️ **Cómo se mezclan las dos mitades no lo ha confirmado Renaser.** Hoy se ponderan por cuántas
preguntas produjo cada una, y esa cuenta vive en un solo sitio —`ServicioCalificacion.notaCombinada`—
a propósito: el día que el cliente decida otra cosa tiene que cambiar a la vez para la nota que
entra en la etapa y para la que enseña el panel. Estuvo copiada en dos sitios hasta el 25/08.

---

## Cosas que el sistema hace y conviene saber

- **Una nota sin explicación no se guarda.** Si el modelo devuelve un puntaje suelto, se
  descarta esa nota. No se pone un cero en su lugar: quedarse sin nota y valer cero son cosas
  distintas.
- **En un banco CAZATALENTOS, media rúbrica no es una nota.** Si el evaluador dejó respuestas
  sin calificar, el trabajo entero falla y se reintenta: la nota de etapa (el índice por
  pilares que escribe `CalificacionCriterios`) solo se calcula con la tanda completa.
- **Recalificar es reencolar de verdad.** `POST /postulaciones/{id}/calificacion-perfil-integral`
  rehace al evaluador aunque ya esté terminado (`reencolarEvaluador`): es la palanca de la
  calibración — cambia una señal, se recalifica, las mismas respuestas se puntúan de nuevo.
- **Lo que una persona ajustó a mano, la IA no lo pisa.** Aunque se vuelva a calificar.
- **Lo que el modelo se inventa, se descarta.** Un criterio que no existe, un tipo de hallazgo
  que no está entre los cinco, una nota para la respuesta de otro candidato: nada de eso entra.
- **Las contradicciones las detecta el código, no el modelo**, comparando dos números. Al
  modelo solo se le pide el aviso de «demasiado ideal».
- **Una alerta no descarta a nadie.** Queda en la ficha como pregunta para la conversación
  final.

---

## Los cuatro grupos de prioridad

Al final, cada candidato cae en uno:

| Grupo | Cuándo |
|---|---|
| **Alta prioridad** | Llega a la nota y no arrastra ningún riesgo crítico |
| **Alto potencial con riesgo** | Llega a la nota pero arrastra un riesgo crítico, o se queda corto en nota pero tiene potencial alto |
| **No priorizado** | Ni una cosa ni la otra |
| **Incompatibilidad objetiva** | **Esto no lo pone la IA.** Sale de los requisitos objetivos, que se comprueban al postular |

Los números que separan un grupo de otro (80 y 65) **son un parámetro editable, no están en el
código**. Salieron de las bandas del Banco Maestro y **Renaser todavía no los ha confirmado**.

---

## Los otros dos agentes: la prueba del puesto y la conversación final

Todo lo de arriba es del **Perfil Integral**, que es la primera etapa. Semanas después el
candidato pasa por otras dos en las que también ayuda la IA, y funcionan distinto en algo
importante: **no arrancan solas**. Alguien las pide desde el panel, igual que la criba de
currículums, porque cada llamada al modelo cuesta dinero y a quién se califica lo decide
quien lleva la vacante.

| Agente | Qué hace |
|---|---|
| **Prueba del puesto** | Lee la entrega del candidato y le pone nota a los criterios de la rúbrica. Al terminar, la prueba pasa a «por confirmar», que es donde una persona la revisa |
| **Conversación final** | Escribe entre tres y cinco preguntas para los quince minutos que cierran la simulación. **No pone ninguna nota** |

### Quién mira cada criterio de la prueba lo dice la rúbrica

Cada criterio de una prueba declara cómo se comprueba: con el sistema, con un agente o con
una persona. **El agente solo ve los suyos.** Si una prueba se califica mirando un video y
todos sus criterios son de persona, el agente ni siquiera llama al modelo, y quien apriete el
botón recibe esa respuesta.

### Lo que no se puede leer no se puntúa

Una prueba del puesto se entrega en un archivo, en un video o en un enlace a un repositorio.
De varios de esos no sale texto, y de lo que no se puede leer **el agente deja el criterio sin
nota** para que lo mire una persona.

Esto es lo contrario de lo que hace un modelo por su cuenta: a un modelo al que se le exige
una nota, siempre da una nota. El daño no es que se equivoque —una persona también se
equivoca—, es que después nadie puede distinguir la nota fundada de la inventada. Quien abra
la ficha ve la rúbrica con unos criterios puestos por el agente y otros vacíos, y eso es
exactamente lo que ocurrió.

### De dónde salen las preguntas de la conversación final

De una **contradicción** entre lo que el candidato dijo y lo que se le vio hacer. Por eso al
agente no se le da un texto, se le dan piezas sueltas: el retrato, las alertas, lo que dijo en
el currículum, las notas de la simulación y las horas de los eventos que el facilitador marcó
durante la sesión. La contradicción vive *entre* dos de ellas.

Una pregunta útil nombra el hecho: «lo viste a las 10:41 y lo informaste a las 10:49, ¿qué
pasó en esos ocho minutos?». Una genérica —«cuéntame de una vez que fallaste»— no aporta nada
que no estuviera ya en el currículum.

Se pueden volver a pedir las veces que haga falta. **Las preguntas que ya se hicieron y se
contestaron no se tocan**: lo que se dijo en la sala es un hecho ocurrido, y una segunda tanda
no puede borrarlo. Solo se rehacen las que nadie llegó a hacer.

---

## Cómo apagarlo

`renaser.ai.calificacion.habilitada: false` en la configuración. Con eso la postulación se
queda en «calificando» y no se encola nada — tampoco los dos agentes de las etapas siguientes. Sirve si el proveedor está caído y no se quiere
gastar reintentos.

---

## Enlaces

- [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) — el sistema entero, sin palabras técnicas
- [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) — el currículum, la evaluación y el
  Perfil de Talento, requisito por requisito
- [Estados de la postulación](03-ESTADOS-POSTULACION.md) — la regla de que los estados de
  máquina tienen que avanzar solos
- [Avance del hito 2](AVANCE-HITO2-2026-08-17.md) — qué se construyó antes de esto
