# Pieza E · El coste de la IA por empresa

Fecha: 2026-08-25 · Estado: aprobado en conversación

Quinta pieza del multiempresa. Cada llamada al modelo cuesta dinero real y, con varias
empresas dentro, hace falta saber **de quién es cada gasto** y que ninguna genere una
factura que nadie vio venir.

## El punto de partida (ya construido)

`ejecucion_ia` guarda por llamada: `organizacion_id` (la de la postulación, o sea la de la
empresa dueña de la vacante), `agente_codigo`, `modelo`, `proveedor`, `tokens_entrada`,
`tokens_salida`, `duracion_ms` y si salió bien. La columna `costo numeric(10,4)` **existe
desde V11 y nunca se ha rellenado**. Es exactamente la materia prima que esta pieza pone a
trabajar.

## 1 · Poner precio a lo que ya se mide

**Tarifa por modelo, con vigencia por fecha**: precio por millón de tokens de entrada y de
salida, que Renaser mantiene. Cuando el proveedor cambia sus precios se registra una tarifa
nueva y **lo ya ejecutado conserva el precio que tenía** — sin vigencia, un cambio de
precios reescribiría el pasado.

Con la tarifa, cada ejecución guarda su `costo` al terminar. Renaser ve el consumo **por
empresa, por mes y por agente**; lo último responde la pregunta útil: ¿el dinero se va en
leer currículums o en calificar exámenes?

## 2 · El tope que evita la factura sorpresa

Cada empresa tiene un **tope mensual configurable** (parámetro suyo, con valor por defecto
sembrado al darla de alta):

- **Al 80 %**: aviso a Renaser y al administrador de la empresa.
- **Al 100 %**: no se aceptan **trabajos de IA nuevos** de esa empresa.

**El proceso del candidato no se rompe.** Lo que ya está en marcha termina; lo nuevo queda
**en espera**, no falla. El candidato no ve un error: ve que su calificación está en curso,
que es la verdad. Cuando Renaser sube el tope o empieza el mes siguiente, la cola arranca
sola con lo que quedó esperando.

**El tope no frena lo que el sistema ya prometió**: las vacantes de una empresa sin cupo
siguen publicadas y la gente sigue postulando. Lo que espera es la calificación, no la
puerta.

## 3 · Fuera de esta pieza, a propósito

Ni planes, ni saldo, ni facturas, ni pasarela de pago. Renaser mira el consumo y cobra
fuera del sistema como acuerde con cada empresa. El día que haya modelo de negocio
definido, **los números para facturarlo ya estarán todos ahí** — que es lo que esta pieza
garantiza.

## Consecuencias para la implementación (resumen)

- Tabla `tarifa_modelo` (modelo, proveedor, precio entrada y salida por millón, vigente
  desde/hasta) mantenida por Renaser.
- Rellenar `ejecucion_ia.costo` al cerrar cada ejecución, con la tarifa vigente en ese
  momento.
- Parámetro `tope_mensual_ia` por organización + consulta de consumo del mes.
- Comprobación del tope al encolar (no al ejecutar): supera → el trabajo queda en espera,
  no fallido. Reintento cuando el tope o el mes cambien.
- Avisos al 80 % (plantilla de correo nueva) y panel de consumo para la plataforma.
