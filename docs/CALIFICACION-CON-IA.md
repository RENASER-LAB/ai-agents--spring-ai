# La calificación con inteligencia artificial

Cómo funciona la parte del hito 2 en la que la IA lee el currículum, califica las respuestas
abiertas y arma el Perfil de Talento. Es lo último que le faltaba al hito 2.

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
| **Evaluador** | Califica de 0 a 4 las respuestas abiertas, citando la parte de la respuesta en que se basa |
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

---

## Cosas que el sistema hace y conviene saber

- **Una nota sin explicación no se guarda.** Si el modelo devuelve un puntaje suelto, se
  descarta esa nota. No se pone un cero en su lugar: quedarse sin nota y valer cero son cosas
  distintas.
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

## Cómo apagarlo

`renaser.ai.calificacion.habilitada: false` en la configuración. Con eso la postulación se
queda en «calificando» y no se encola nada. Sirve si el proveedor está caído y no se quiere
gastar reintentos.

---

## Enlaces

- [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) — el sistema entero, sin palabras técnicas
- [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) — el currículum, la evaluación y el
  Perfil de Talento, requisito por requisito
- [Estados de la postulación](03-ESTADOS-POSTULACION.md) — la regla de que los estados de
  máquina tienen que avanzar solos
- [Avance del hito 2](AVANCE-HITO2-2026-08-17.md) — qué se construyó antes de esto
