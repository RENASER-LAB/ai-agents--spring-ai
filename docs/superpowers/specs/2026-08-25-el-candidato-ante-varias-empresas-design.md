# Pieza D · El candidato ante varias empresas

Fecha: 2026-08-25 · Estado: aprobado en conversación

Cuarta pieza del multiempresa. Las anteriores decidieron el reparto (A), el aislamiento (B)
y la identidad (C). Esta mira el sistema desde el lado del candidato: **una cuenta, un
perfil, muchas empresas**.

## La decisión de fondo

El candidato es **de la plataforma**, no de una empresa: se registra una vez, tiene un solo
perfil y postula donde quiera. **Su postulación pertenece a la empresa de la vacante** — es
lo que hace que el panel de cada empresa vea a sus candidatos y que el aislamiento
signifique algo.

## 1 · El consentimiento, capa por capa

- **Al crear la cuenta** consiente con la **plataforma**: que Renaser guarde su cuenta y su
  perfil, y que la IA lea sus currículums (nombrando a DeepSeek y Google — deuda legal ya
  conocida, RF-169).
- **Al postular** acepta el texto de **esa empresa**: quién es, qué datos tratará y por
  cuánto tiempo. Una casilla en el formulario de postular; el registro firmado (texto,
  versión, fecha, IP) queda a nombre de esa empresa. Postular a tres empresas son tres
  registros, cada uno con su papel en regla.
- Es lo que la ley 29733 espera: cada quien que trata datos, nombrado y consentido.
- **Consecuencia que encaja con la pieza A**: una empresa no puede publicar vacantes hasta
  tener su texto legal publicado con su nombre — el requisito del día uno ya definido.

## 2 · Lo que ve cada quien

- **El candidato ve todo lo suyo junto**: «mis postulaciones» mezcla sus procesos en todas
  las empresas, cada uno con el nombre de la empresa al lado. Su perfil es uno y lo edita
  en un solo sitio.
- **Cada empresa ve solo su proceso.** No ve que el candidato está en procesos con otras
  empresas, ni cuántos, ni desde cuándo: saber que alguien «está en el mercado» es
  información valiosa que no le pertenece.
- Entre empresas viaja **solo el perfil** (que es del candidato). Jamás notas, alertas ni
  decisiones de otro proceso.

## 3 · El currículum: transcripción compartida, evaluación por puesto

Son **dos lecturas distintas**, y solo una se reutiliza:

- **La transcripción** (`DATOS_CV`) copia lo que el papel dice: nombre, contacto, meses de
  experiencia, dónde trabajó, qué estudió, idiomas, certificaciones. No depende del
  puesto — un título es el mismo se postule a lo que se postule. **Esta se reutiliza**
  entre empresas cuando el archivo es idéntico (huella SHA-256, RF-161): es un dato del
  candidato, no una opinión sobre él.
- **La evaluación** (`EVIDENCIA_CV`) puntúa el currículum contra los criterios de esa
  vacante. **Es del puesto y nunca se reutiliza**: cada empresa evalúa contra su propia
  vacante, con sus pesos, y esa nota se queda en ese proceso. Es la regla de la pieza A —
  *el perfil es de la persona, la nota es del proceso*.

**El matiz aceptado a conciencia:** de los ~15 datos de la transcripción, uno mira el
puesto — «hasta cinco habilidades, las más relevantes para el puesto». Al reutilizar la
ficha, esas cinco reflejan el **primer** puesto para el que se leyó el archivo. Se deja
así: las otras catorce son correctas siempre, las habilidades son un resumen para mirar
rápido (el que quiera precisión abre la experiencia completa, que sí es exacta), y **lo que
decide en este sistema es la evaluación**, que siempre es del puesto. Releer por puesto
costaría una llamada al modelo por postulación para corregir un campo de quince.

Quién asume el coste de la primera lectura: pieza E.

## 4 · El borrado

Lo ejecuta **la plataforma** y arrasa parejo: la cuenta, el perfil y lo que cada empresa
tenía de esa persona. Una persona no puede quedar «medio borrada». (`dato_cv` se conserva
según el contrato ya documentado: sostiene evaluaciones ya hechas.)

## Fuera de esta pieza

El coste de la IA por empresa (E) y las herramientas de Renaser como dueña de la plataforma
(F).
