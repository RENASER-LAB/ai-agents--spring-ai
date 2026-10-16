# Una vacante, una versión, de principio a fin

**Decisión tomada el 27/08/2026, para el banco CAZATALENTOS.**

Una vacante nace con su versión de banco y su versión de pesos, y muere con ellas. Mientras
esté abierta no se le cambia ninguna de las dos.

> **Desde la `V74` (16/10/2026) la versión de pesos sí se cambia con candidatos dentro, pero para
> todos a la vez**: en la misma operación se recalcula a cada candidato con nota, sin IA y sin
> mover a nadie de etapa. La regla de fondo no cambia —todos los de una vacante medidos con la
> misma vara—; lo que se permite es cambiar la vara entera. Ver «La tercera excepción: los
> pesos», más abajo.

---

## Por qué

Porque el instrumento **no está calibrado y va a cambiar muchas veces con candidatos reales
dentro**. La propia clienta lo declara pendiente (parte 10 de su documento): hasta que Darren
no califique a mano tres candidatos y coincida en 15 de 18, las señales se van a mover.

Si el candidato A rinde con las señales v1 y el B con las v2, y el sistema los ordena en la
misma lista, **sus notas no son comparables**. Eso no es un fallo técnico: es una decisión de
contratación tomada sobre una comparación falsa.

Dentro de una convocatoria, que todos se hayan medido con la misma vara es lo único que hace
falta para decidir a quién se pasa. Entre convocatorias distintas da igual: no compiten.

---

## Las dos garantías — por qué la de siempre no bastaba

`ServicioVacantesPanelImpl.asignarVersionPesos` permitía cambiar la versión de pesos de una
vacante publicada, con candidatos dentro. Y lo justificaba:

> *«Nada se recalcula hacia atrás: cada nota guardada conserva la versión con la que se
> calculó. Cambiar esto solo mueve las propuestas de decisión que aún no se toman.»*

**Esa garantía es cierta, y no es la misma que esta regla.** Conviene no confundirlas:

| | Qué garantiza |
|---|---|
| El comentario | **Hacia atrás**: nada se reescribe. Cada nota conserva la versión con que se calculó |
| Esta regla | **Hacia los lados**: todos los de una misma vacante se midieron con la misma vara |

Lo segundo no se deduce de lo primero:

```
Vacante 13, abierta, con version_pesos v1

lunes      Candidato A responde  →  se califica con v1  →  72   (anotado: v1)
martes     Se ajusta una señal   →  se publica v2 y se asigna a la vacante
miércoles  Candidato B responde  →  se califica con v2  →  68   (anotado: v2)
```

El comentario se cumple: a A no le tocaron la nota y consta que fue con v1. Pero el ranking
enseña `A 72 · B 68` y se pasa a A — y esos dos números **no se midieron igual**. Con v2, A
podría haber sacado 65.

**Saber que dos notas son incomparables no las hace comparables.** La trazabilidad es un
registro; la comparabilidad es una propiedad.

Y ahí está la suposición del comentario: *«solo mueve las propuestas de decisión que aún no se
toman»* es inofensivo cuando el cambio es un ajuste de política que se aplica a todos de ahí en
adelante. Deja de serlo cuando cae **a mitad de una convocatoria**, porque se aplica a unos
competidores sí y a otros no. El comentario asume pesos estables; aquí hay señales en
calibración.

Las dos garantías son compatibles: esta regla **añade** una, no niega la otra.

---

## Cómo quedó implementada (la guarda `exigirVaraQuieta`)

Los dos asignadores de `ServicioVacantesPanelImpl` que quedan —`asignarPlantillaEvaluacion` y
`asignarVersionPlantillaPrueba`— pasan por la misma guarda. Hasta la `V74` también pasaba
`asignarVersionPesos`; ahora la versión de pesos se cambia en `ServicioPesosDeLaVacante`, que
recalcula a todos en vez de frenar (ver «La tercera excepción: los pesos»).

- **La línea es la primera postulación, no la publicación.** Una vacante publicada a la que
  nadie ha postulado todavía puede terminar de configurarse (el flujo sin banco asigna sus
  pesos después de publicar). Desde la primera postulación, la vara no se mueve.
- **Asignar donde no había nada se permite siempre**: nadie fue medido con una vara que no
  existía (es el camino de volver a encender la evaluación).
- El mensaje de error dice qué hacer: estrenar la versión en la siguiente convocatoria, o
  recalibrar señales por la corrección editorial y recalificar a todos.

**La prueba técnica escrita en el editor (`V67`, 01/10/2026) corre la línea a la primera
rendición.** La prueba se rinde en la segunda etapa, así que postular no es haberla visto: hasta
que alguien la abra se puede abrir un borrador y publicar otra versión, que archiva la anterior.
Desde la primera rendición el contenido queda congelado —publicar otra es 409— y cada candidato
queda atado a la versión con que empezó (RF-138). Dos excepciones son las de las preguntas
propias: **las instrucciones de la IA** (recalifica a todos con la guía nueva) y **los puntos**
(recalcula a todos al instante, sin IA); las dos cambian la vara para todos a la vez. La tercera,
**los pesos** (`V74`), tiene sección propia más abajo. **La fecha
límite** (`V68`) tampoco se congela, pero no es de la versión sino de la vacante: se cambia en
cualquier momento, con motivo si alguien ya está en la etapa técnica, y no viaja al copiar la
prueba. Con la misma línea, asignar una plantilla por la API a una vacante nueva la pasa a `PLANTILLA` solo mientras
nadie haya empezado a rendir.

---

## La tercera excepción: los pesos, para todos a la vez (`V74`, 16/10/2026)

Hasta la `V74` la versión de pesos de una vacante no se cambiaba desde la primera postulación
(409): cambiarla solo movía las notas futuras y dejaba a unos medidos con una vara y a otros con
otra. Ahora se cambia **y en la misma operación se recalcula a todos los que tienen alguna nota**,
así que nadie queda con la vara de antes. Es el mismo principio que las instrucciones de la IA y
los puntos. Lo hacen `ServicioPesosDeLaVacante` y `RecalculoConOtrosPesos`; las rutas, en
[Las APIs](09-APIS.md), «Vacantes».

- **La IA nunca hace la cuenta con los pesos**, y por eso no hace falta llamarla. El currículum
  guarda la nota de 0 a 100 que la IA puso a cada uno de sus ocho criterios, y el código la vuelve
  a ponderar con el peso del nivel en la versión nueva. Igual el banco `CRITERIOS` con sus
  dimensiones, el Perfil Integral con su reparto entre currículum y evaluación, y el grupo de
  prioridad con los hallazgos guardados.
- **La simulación y la validación también cambian**: cada nota de criterio va sobre el peso de
  su criterio (un 8 sobre 10) y pasa a la escala nueva (12 sobre 15). Se calcula **siempre desde
  la nota tal como se puso** y su máximo de entonces (`nota_criterio.puntaje_original` y
  `maximo_original`, `V75`), nunca desde la última reescalada: ir y volver a la misma versión
  devuelve la misma nota, aunque por el camino el criterio pase por peso 0.
- **La prueba técnica no cambia de valor**: lleva sus propios puntos. Solo pasa a decir la
  versión nueva. La nota final no se guarda: el ranking y su Excel la calculan al vuelo.
- **No se mueve a nadie**: ni la máquina de estados, ni trabajos de IA, ni **el pase automático**
  (`V72`). Quien espera en «por confirmar» y con la nota nueva llega a la mínima lo mueve el
  equipo, como al cambiar la mínima.
- **Las decisiones ya tomadas no se tocan**: la fila de `decision` conserva la nota y la versión
  con que se decidió, que es lo que se decidió con la vara de entonces.
- **Todo o nada.** Va en una transacción con la vacante bloqueada: si una nota no se puede
  recalcular, la vacante sigue con la versión anterior y ninguna nota cambia. Dos cambios
  seguidos van de uno en uno, y elegir la que ya tiene no hace nada.
- **Lo que llega a mitad** —una nota de la IA en curso, un ajuste a mano, una entrega— espera a
  que el cambio confirme y sale con la versión nueva; si llega antes, es el cambio quien la
  espera. Ver la regla del cerrojo de los pesos en [Reglas del código](REGLAS-DEL-CODIGO.md),
  «Reglas que no se negocian».
- **No con la vacante cerrada, archivada o eliminada** (409): sus notas se quedan con los pesos
  que tenía.
- **Queda en la auditoría** (`asignar_version_pesos`): quién, cuándo, las dos versiones y
  cuántas personas se recalcularon.

Sigue sin hacerse: editar una versión de pesos publicada —se crea otra y se elige— y volver a
«los pesos generales» desde la vacante.

---

## La única excepción: recalificar para calibrar

Recalificar **no** es cambiar la versión de la vacante. Es volver a puntuar las mismas
respuestas con señales nuevas, y sirve justamente para saber si las nuevas señales son
mejores.

| Sobre una vacante… | ¿Se puede recalificar? |
|---|---|
| en curso, aún sin decidir | **Sí** |
| cerrada sin contratar | **Sí** |
| donde ya se contrató a alguien | **No.** Esa nota sustentó una decisión y se queda |

Y hay que dejar rastro: quién recalificó, cuándo y con qué versión. La auditoría ya existe
para las asignaciones; esto va por el mismo sitio.

---

## Qué cambia y qué no, según lo que toques

La regla solo muerde en lo que invalida la comparación. Por eso conviene tener claro qué
clase de cambio es cada cosa:

| Lo que cambia | ¿Invalida lo ya respondido? | Qué se hace |
|---|---|---|
| Señal de 0 · C3 esperado · C4 esperado | **No** — el texto del candidato sigue ahí | corrección editorial (auditada) → **recalificar** |
| Pesos de pilar · cortes | **No** | versión de pesos nueva, elegida en la misma vacante aunque tenga candidatos (`V74`) → **se recalcula a todos** |
| Añadir o quitar una pregunta | **Sí** — falta la respuesta | versión de banco nueva |
| Cambiar un enunciado | **Sí** — mide otra cosa | versión de banco nueva. **Línea roja** |

Las dos primeras filas son el 90% de lo que va a cambiar durante la calibración.

**Dónde viven las señales (decisión final, distinta de un borrador anterior de este doc):**
como columnas de `pregunta` (`c3_esperado`, `c4_esperado`, `senal_de_cero`), importadas del
Excel. Se barajó colgarlas de `version_pesos` y se descartó: `peso_dimension` llavea por
dimensión, no por pregunta, y una tabla nueva habría que registrarla en el copiador de
multiempresa. La iteración barata se consigue por otro camino: **la corrección editorial de
una publicada** (`PUT /panel/banco-preguntas/preguntas/{id}` con `CorregirTextoPregunta`)
admite cambiar la guía del evaluador —el candidato nunca la ve—, queda auditada, y quien la
toque debe recalificar a la vacante entera (`scripts/recalificar-banco.py`), que es lo que
restaura el invariante. El enunciado, en cambio, sigue congelado: cambiarlo es otro
instrumento y exige versión nueva del banco.

---

## Lo que esto nos da gratis

- `vacante.version_pesos_id` ya existe y es obligatoria. No hace falta otro puntero.
- `nota_etapa.version_pesos_id` **ya se guarda al calificar**: queda registrado con qué
  señales se midió a cada persona, sin construir nada.
- `version_banco` ya se archiva en vez de borrarse (RF-138).

Toda la maquinaria de versionado está, y las guardas también: `exigirVaraQuieta` en los dos
asignadores que quedan (el de pesos recalcula a todos desde la `V74`), la recalificación en lote (`scripts/recalificar-banco.py`, que además se niega a
tocar una vacante donde ya se contrató) y el reencolado forzoso del evaluador
(`ColaCalificacionIa.reencolarEvaluador`).

---

## La excepción del 04/09: la V50 editó una versión publicada en sitio

La versión de pesos de Administrador y Asistente Administrativo daba el 100 a la prueba y nada al
Perfil Integral. La V50 la corrigió a 45/55 **sin crear otra versión**, y esta regla dice lo
contrario. Las dos razones, dichas en la propia migración:

1. **Crear una versión nueva rompe algo más grande.** El sistema toma «la última publicada» como
   la que hereda toda vacante nueva y la que el copiador replica a cada empresa que se da de
   alta. Publicar cualquier versión hoy convertiría ese 45/55 en el reparto por defecto de todo
   el mundo; es el mismo motivo por el que la V49 dejó las dos versiones del cazatalentos en
   BORRADOR.
2. **La inmutabilidad protege el pasado, y ahí no había pasado que proteger.** Nadie había sido
   contratado ni descartado en esas dos vacantes con el reparto anterior (comprobado con el
   equipo). Las notas ya calculadas conservan su versión (RF-139); lo que cambia es cómo se
   combinan de aquí en adelante.

No es un precedente: con alguien ya contratado o descartado, el reparto nuevo se estrenaría en la
siguiente convocatoria. Desde la `V74` hay además un camino sin editar nada en sitio: publicar otra
versión y elegirla en la vacante, que recalcula a todos y deja las decisiones tomadas como
estaban (ver «La tercera excepción: los pesos»). Publicarla sigue teniendo el problema del punto
1. Y el criterio de «la última publicada» hay que cambiarlo antes de publicar
ninguna versión más; está en [Estado del proyecto](ESTADO-DEL-PROYECTO.md).
