# Una vacante, una versión, de principio a fin

**Decisión tomada el 27/08/2026, para el banco CAZATALENTOS.**

Una vacante nace con su versión de banco y su versión de pesos, y muere con ellas. Mientras
esté abierta no se le cambia ninguna de las dos.

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

Los tres asignadores de `ServicioVacantesPanelImpl` —`asignarVersionPesos`,
`asignarPlantillaEvaluacion` y `asignarVersionPlantillaPrueba`— pasan por la misma guarda:

- **La línea es la primera postulación, no la publicación.** Una vacante publicada a la que
  nadie ha postulado todavía puede terminar de configurarse (el flujo sin banco asigna sus
  pesos después de publicar). Desde la primera postulación, la vara no se mueve.
- **Asignar donde no había nada se permite siempre**: nadie fue medido con una vara que no
  existía (es el camino de volver a encender la evaluación).
- El mensaje de error dice qué hacer: estrenar la versión en la siguiente convocatoria, o
  recalibrar señales por la corrección editorial y recalificar a todos.

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
| Pesos de pilar · cortes | **No** | versión de pesos nueva (en la siguiente vacante) → **recalcular** |
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

Toda la maquinaria de versionado está, y las guardas también: `exigirVaraQuieta` en los tres
asignadores, la recalificación en lote (`scripts/recalificar-banco.py`, que además se niega a
tocar una vacante donde ya se contrató) y el reencolado forzoso del evaluador
(`ColaCalificacionIa.reencolarEvaluador`).
