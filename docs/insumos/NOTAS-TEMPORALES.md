# Notas temporales — Proyecto Reclutamiento Renaser

> ⚠️ **Revisar contra el documento nuevo.** Varios de estos pendientes ya los resolvió el
> cliente el 14/08/2026: el currículum no descarta, el plazo de conservación es configurable y
> la zona dudosa fue reemplazada por grupos de prioridad. Ver
> [Qué cambia con el documento nuevo](CAMBIOS-DEL-DOCUMENTO-NUEVO.md).

> **Documento de trabajo. Aquí solo vive lo que sigue sin respuesta.**
> Lo que ya está decidido está en los documentos numerados y en
> [Qué documento manda](ANALISIS-DOCUMENTOS.md).
> Este archivo se vacía solo: cada vez que algo se resuelve, sale de aquí.
>
> Última actualización: 2026-08-14

---

## 1. Insumos que hay que pedirle al cliente

Ninguno bloquea el diseño. Todos bloquean la puesta en marcha.

| Insumo | Para qué hace falta |
|---|---|
| 2 o 3 descripciones reales de oferta de empleo de Renaser | Calibrar cómo la IA puntúa un CV. Sin ejemplos reales, la rúbrica se escribe a ciegas |
| Ejemplos de pruebas del puesto **con su corrección hecha a mano** | Comparar la nota de la IA contra la de una persona. Es la única forma de saber si califica bien antes de usarla con gente de verdad |
| 10 a 20 CVs reales sin datos personales, marcados como apto o no apto por el reclutador | Lo mismo, para la etapa del CV |
| El marco de Renaser sobre no victimismo, no culpa y no vergüenza, por escrito | El cliente lo menciona pero nunca lo mandó. Se necesita el texto original para convertirlo en criterios observables |
| Cuántas postulaciones recibieron por vacante hasta ahora | Dimensionar el gasto en IA. Hoy se está diseñando para 500 por vacante sin saber si son 50 o 5000 |

---

## 2. Preguntas abiertas

Ningún documento las responde todavía.

**¿Cuánto tiempo se guardan los datos de un candidato que no fue contratado?**
La ley 29733 obliga a fijar un plazo y a decirlo en el texto de consentimiento. Hoy el
sistema dice que los datos «quedan para futuras convocatorias», sin plazo. Eso no se
sostiene legalmente.

**¿Desde qué dirección de correo sale todo?**
Hace falta el dominio de Renaser y quién administra su correo. Si los avisos salen de un
dominio mal configurado, caen en spam y se pierden candidatos que sí querían seguir. Es
un problema de configuración del dominio, no del sistema.

**¿Dónde se despliega y cuánto se puede gastar?**
Ya no hay modelo en local: desde el 18/08/2026 califica **DeepSeek** y busca por significado
**Google Gemini**, los dos de fuera. Así que el gasto ya no es una máquina, es **cada
consulta**, y sigue faltando el número que lo dimensiona: cuántas postulaciones llegan por
vacante. Falta también un tope de gasto para las dos cuentas.

**¿Cuánta gente de Renaser va a usar el panel y con qué frecuencia?**
Cambia cuánto trabajo humano tolera el sistema. Si hay una sola persona operando, la
zona dudosa tiene que arrancar estrecha o se le acumula el trabajo.

**¿Qué tasa de candidatos que terminan la evaluación consideraría buena el cliente?**
Si de 100 que postulan la terminan 15, no hay forma de saber si eso es bueno o malo para
ellos. Sin ese número no se puede decir si el sistema funciona.

---

## 3. Alcance que quedó fuera, pero el cliente lo quiere

**Plataforma pública de empleo.** El cliente describió una web y una aplicación móvil
donde cualquiera busque «empleos Arequipa» y encuentre las vacantes de Renaser. Eso es un
portal de empleo con necesidades de posicionamiento en buscadores, un producto distinto
del que se está construyendo. Lo que sí existe hoy es el portal donde postula quien ya
llegó al enlace.

**Avisos por WhatsApp.** Decidido: por ahora solo correo.
