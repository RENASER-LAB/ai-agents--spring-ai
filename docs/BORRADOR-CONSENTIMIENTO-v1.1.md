# Borrador · Textos de consentimiento v1.1

⚠️ **Esto es un borrador para que lo revise el abogado de Renaser. No está aprobado y no se
ha cargado en el sistema.** Los textos que están hoy en producción son la v1.0 y siguen
marcados como provisionales.

---

## Por qué hace falta cambiarlo

El texto que firma hoy un candidato dice que «una inteligencia artificial participa en la
evaluación», y ahí se queda. **No dice que sus datos salen de Renaser, ni hacia quién.**

Cuando se escribió eso era verdad: la inteligencia artificial corría en el propio servidor.
Ya no. Hoy el sistema usa dos servicios de otras empresas, las dos fuera del Perú:

| Empresa | Para qué | Qué se le manda |
|---|---|---|
| **DeepSeek** | Califica el currículum, las respuestas abiertas y la prueba del puesto | El currículum ya recortado y las respuestas que escribió el candidato |
| **Google** | Busca por significado dentro del sistema | Fragmentos de texto convertidos en números |

Mientras la inteligencia artificial no calificara a nadie, esto no tenía efecto. **Deja de ser
así en cuanto funcionen los agentes que califican**, que es justo lo que se está construyendo.
Desde ese momento, el currículum y las respuestas de una persona real salen del país.

Eso es lo que la Ley 29733 llama **flujo transfronterizo de datos personales**, y hay que
decírselo a la persona antes, no después.

---

## Qué cambia respecto de la v1.0

Se añade lo que falta y no se quita nada:

1. **Se nombra a las dos empresas** y se dice qué recibe cada una.
2. **Se dice que los datos salen del país.**
3. **Se dice qué NO se manda:** foto, edad, sexo y estado civil se quitan del currículum antes.
4. **Se dice que la máquina no decide sola.** Ya estaba, pero ahora se explica que puede pedir
   que una persona lo revise.

La v1.0 se queda intacta. Quien ya la aceptó queda ligado a ella, que es lo que exige el
sistema de versiones.

---

## Texto propuesto · Consentimiento del proceso

> Acepto que Renaser use mis datos personales, mi currículum y mis respuestas para evaluar mi
> postulación a esta vacante.
>
> **Una inteligencia artificial participa en la evaluación.** Para eso, Renaser envía mi
> currículum y mis respuestas a **DeepSeek**, una empresa de fuera del Perú que presta el
> servicio de inteligencia artificial. Renaser también usa **Google** para buscar información
> por significado dentro del sistema. Mis datos salen del país únicamente por esos servicios,
> se tratan por medios automáticos y **ninguna persona ajena a Renaser los revisa**.
>
> Antes de enviar mi currículum, Renaser le quita **la foto, la edad, el sexo y el estado
> civil**. Lo que se envía es esa versión recortada, nunca el archivo que subí.
>
> **La máquina no decide sola.** Una persona de Renaser revisa y confirma toda decisión sobre
> mi postulación, y puedo pedir que se revise si no estoy de acuerdo con el resultado.
>
> Mis datos se conservan durante [PLAZO] y después Renaser [POLÍTICA AL VENCER]. Puedo pedir
> en cualquier momento que se borren, y también acceder a ellos, corregirlos u oponerme a su
> tratamiento, escribiendo a [CORREO DE CONTACTO].
>
> Este permiso es solo para esta vacante.

---

## Texto propuesto · Consentimiento de futuros contactos

> Acepto que Renaser conserve mis datos para avisarme de otras oportunidades laborales,
> incluido el perfil que la inteligencia artificial armó durante mi evaluación.
>
> Este permiso es **independiente** de mi postulación actual. Puedo retirarlo cuando quiera,
> sin que eso afecte a ningún proceso en el que esté participando.

---

## Lo que Renaser tiene que completar

Los corchetes del texto no los puede rellenar el equipo técnico:

| Hueco | Qué falta decidir |
|---|---|
| `[PLAZO]` | Cuánto tiempo se conservan los datos. La ley obliga a decir un plazo concreto |
| `[POLÍTICA AL VENCER]` | Qué pasa al cumplirse: borrar, anonimizar, o pedir que renueve el permiso |
| `[CORREO DE CONTACTO]` | A dónde escribe el candidato para ejercer sus derechos |

El plazo y la política **no se escriben en el código**: son configuración, para que cambiarlos
no obligue a un despliegue.

---

## Dos preguntas para el abogado

1. **¿Basta con informar, o hace falta algo más?** La Ley 29733 pide que el flujo
   transfronterizo se informe y consienta. Si además exige un registro ante la autoridad, o un
   contrato con el proveedor, eso está fuera de lo que el sistema resuelve solo.

2. **¿Y quien ya postuló con la v1.0?** Si alguien aceptó el texto viejo y su postulación sigue
   viva cuando se encienda la calificación con inteligencia artificial, su consentimiento no
   cubre el envío al extranjero. Hay que decidir si se les pide aceptar la versión nueva o si
   su proceso se termina sin inteligencia artificial.

---

## Cómo se carga cuando esté aprobado

Una migración nueva de Flyway inserta las dos filas en `texto_consentimiento` con
`version = '1.1'` y su huella. **No se toca ni se borra la v1.0.** A partir de ahí el portal
muestra la versión publicada más reciente, y cada aceptación guarda contra qué versión se
firmó.
