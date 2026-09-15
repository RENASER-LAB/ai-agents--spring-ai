# El sueldo, de los dos lados

Qué dice la vacante sobre el dinero, qué se le pide a quien postula a cambio, y por qué las dos
cosas son la misma decisión. Reúne lo que se construyó el 14 y el 15/09/2026 (migraciones `V55` y
`V56`).

---

## En una frase

**La vacante que enseña lo que paga le exige a quien postula que diga cuánto quiere ganar; la que
lo esconde no le pide nada — y tampoco puede ver lo que esa persona le dijo a otra empresa.**

---

## De dónde viene

Hasta el 14/09/2026 el dinero se decía en dos sitios que no se hablaban entre sí:

- **La vacante** lo decía en un texto libre, donde cabía «S/ 3500», «a convenir», «según
  experiencia» o nada en absoluto.
- **El candidato** lo decía en su perfil: opcional, casi nadie lo llenaba, y quien lo llenaba lo
  hacía sin mirar ninguna vacante concreta.

Las dos mitades del mismo dato vivían separadas y **ninguna comprometía a nadie**. Comparar lo que
alguien pedía con lo que un puesto pagaba exigía leer una frase, interpretarla, y confiar en que
una expectativa escrita hace meses siguiera vigente.

---

## El trato

### 1 · La vacante declara su sueldo de una de tres formas

**No publicarlo**, **un monto fijo**, o **un rango** de mínimo a máximo. Siempre con moneda, y
solo dos: **soles o dólares**. La lista es corta y deliberadamente cerrada: el portal es peruano y
la empresa que contrata en dólares lo hace por excepción. Una lista abierta deja entrar «soles»,
«S/.», «PEN » y cuatro grafías más del mismo dinero, y entonces comparar dos vacantes deja de ser
posible sin un diccionario de sinónimos.

Una vacante nace **sin publicar el sueldo** si nadie dice lo contrario.

### 2 · Enseñarlo obliga a quien postula; esconderlo lo libera

Este es el trato entero, y es **simétrico a propósito**: pedirle su cifra a alguien mientras la
empresa esconde la suya es el desequilibrio que esto viene a romper.

- Si la vacante publica lo que paga, **el formulario de postular exige la pretensión**. Sin ella
  no se envía.
- Si no lo publica, **no se le pide nada**, y si la cifra llega igual por la API, **se ignora**.

Lo que declara es **un monto único, no una banda**. Es lo único que se puede poner de frente
contra el presupuesto de la vacante y contestar «entra o no entra». Rango contra rango contesta
«se solapan», que no sirve para decidir a nadie.

### 3 · La decisión se congela al publicar la vacante

En **las dos direcciones**, y la segunda no es simetría estética:

| De | A | Por qué está cerrado |
|---|---|---|
| Publicarlo | Esconderlo | **Es la revocación, y es la que duele.** Nada impedía publicar un rango, recoger cuarenta pretensiones obligatorias y volver a esconderlo al día siguiente: quedarse lo cobrado y retirar lo pagado, en dos clics |
| Esconderlo | Publicarlo | A quienes ya postularon **no se les pidió nada y no hay forma de volver atrás a pedírselo**. Encenderlo deja media tanda con cifra y media sin ella, que ninguna pantalla puede comparar de frente |

**En borrador no hay nada que proteger**: nadie ha postulado todavía, y la guarda existe justo para
que esa decisión se tome con calma antes de abrir la puerta.

**La salida, si de verdad hace falta cambiarlo: cerrar la vacante y abrir otra.** El panel la
nombra en voz alta. Bloquea **solo la tarjeta que cruzaría la línea**, no las tres, y bloqueada
cambia su línea de ayuda por el motivo: un control gris sin explicación se lee como que la
pantalla está rota, no como una regla. Y no se esconde — quitarla dejaría la pregunta «¿y si
quisiera no publicarlo?» sin respuesta en ninguna parte.

### 4 · Cambiar el monto sí se puede, y se avisa

Subirlo, bajarlo, o cerrar un rango en una cifra fija. **Moverse entre fijo y rango entra aquí**:
las dos formas publican, no se revoca nada, y cerrar el rango cuando ya se sabe el número es de
los cambios más frecuentes.

Ese cambio **le escribe a cada candidato que sigue en carrera**: correo y aviso en su portal. A
quien ya no continúa no se le escribe, porque la noticia no le afecta.

Por eso **tiene verbo propio y no viaja en la edición general de la vacante**: corregir una falta
de ortografía en la descripción no puede mandarle una noticia a cuarenta personas. Y **exige
motivo escrito**: la auditoría tiene que poder contestar «¿por qué le dijimos a cuarenta
candidatos que el sueldo bajó?» con algo más que una marca de tiempo.

**Guardar lo mismo que ya había no cuenta como cambio** y no avisa a nadie.

⚠️ **El aviso del portal se compromete antes que el cambio que lo provocó.** Va en transacción
propia porque, sin ella, un fallo al escribir el aviso tumbaba también el cambio de sueldo y su
fila de auditoría, incluso atrapando la excepción. El precio es que puede quedar un aviso de algo
que luego no se guardó; es el lado correcto del que equivocarse — **un aviso de más se explica; un
sueldo revertido en silencio, no**.

⚠️ **«A cuánta gente le llegó» cuenta avisos del portal, no correos.** El correo sale por la
puerta de atrás tanto si falta la plantilla como si el servidor está caído, así que contar intentos
dejaba al panel diciendo «avisamos a 40 candidatos» cuando podían ser cero.

### 5 · Nada va hacia atrás

Encender o apagar la remuneración **no vuelve a pedirle la pretensión a quien ya postuló**. El
trato se juzga con las reglas que había **el día que cada uno envió su candidatura**.

Por eso, donde falta la cifra, la pantalla **dice cuál de los tres motivos es**:

1. La postulación es anterior a que esto existiera.
2. **La vacante no publicaba su sueldo**, así que a esa persona no se le exigió nada.
3. Quien mira no tiene permiso para verla.

Un guion a secas se lee siempre como un cuarto motivo que no existe: un candidato esquivo. Solo
uno de los tres habla del candidato, y **el único que se arregla desde el panel es el segundo**.

### 6 · El perfil propone, la postulación decide

El perfil de la persona sigue guardando su **banda** de expectativa general. Al postular:

1. El formulario se **prellena con el centro de esa banda** —quien puso «3000 a 4000» no está
   diciendo que quiera 3000—.
2. La persona **confirma o corrige**.
3. Lo que declare **vuelve a su perfil solo si lo tenía vacío**. Propone, nunca pisa.

El prellenado **no vuelve a pisar el campo** una vez escrito: una recarga de datos en segundo
plano reescribiría encima de lo que se acaba de teclear.

### 7 · La reciprocidad rige también en el panel: dos llaves

Una vacante que **no publica su sueldo no ve ninguna pretensión** — ni la declarada al postular ni
la del perfil—, tenga o no tenga el permiso quien esté mirando.

El agujero que cierra es anterior a todo esto y esta función lo ensanchó: **el perfil es de la
plataforma, no de ninguna empresa**. Desde que declarar la pretensión al postular la guarda en el
perfil de quien no tenía ninguna, lo que el perfil enseña puede ser exactamente lo que se le dijo a
**otra** empresa — la que sí puso su presupuesto sobre la mesa. Cobrar por un lado lo que no se
paga por el otro, dando un rodeo.

Las dos llaves rigen en **los tres sitios** que leen la pretensión: el ranking de la tanda, la
ficha del candidato y el perfil visto desde el panel. Y la segunda llave, igual que el permiso,
**no es «no pintarlo»**: sin ella la consulta ni se lanza, y el dato no llega a existir en la
memoria de la petición.

⚠️ **Al panel le viaja el permiso a secas, no la conjunción**, y es a propósito: son dos motivos
distintos para una casilla vacía y la pantalla tiene que poder decir cuál es. Con un solo sí/no
para los dos, a Dirección le diría «tu rol no puede verla» — que es falso, y además la manda a
pedir un permiso que ya tiene.

De regalo, esto vuelve **verdad** el aviso que el panel ya pintaba en esas vacantes: «esta vacante
no publica su remuneración, así que a nadie se le pidió la suya». Hasta entonces esa frase convivía
con una columna que sí traía cifras.

### 8 · Los sueldos van en cifras enteras

Entre **100** y **1 000 000**. Los separadores de miles valen —coma, punto o espacio— si agrupan de
tres en tres: «3,500», «3.500» y «3 500» son el mismo sueldo. «3,50» no es ninguna de las dos cosas
y se rechaza diciendo cómo escribirlo.

**Sin decimales, y esa es la clave**: con ellos no hay forma de distinguir tres soles y medio de un
3500 mal tecleado. Antes de esta regla, escribir «3,500» quedaba registrado como **S/ 3.50** —en el
portal, como pretensión copiada al perfil; en el panel, como monto fijo de una vacante publicada,
disparando los correos que decían «Ahora: S/ 3.50»—.

El **suelo de 100 no es el sueldo mínimo legal**, y es a propósito: la cifra también viaja en
dólares, y un umbral pegado a la ley peruana rechazaría una práctica pagada en USD que es
perfectamente real. **Lo que se para es el error de magnitud, no la oferta modesta.** El techo
para el dedo de más: alguien que escribe 35000 donde quería 3500 publica una vacante que promete
diez veces lo que paga.

La regla vive **en los dos lados**. El formulario no es la única puerta: cualquier cliente de la
API puede mandar la cifra sin pasar por una pantalla.

### 9 · El texto libre de compensación queda retirado

Los datos se conservan —son cosas que alguien escribió, y la auditoría de una vacante vieja debe
poder explicarse—, pero **ninguna pantalla lo lee ni lo escribe**, y no viaja en ningún contrato.

**Dos sitios donde decir el sueldo son dos sitios donde contradecirse**, y el trato necesita un
número comparable, no una frase.

---

## La campana del portal

El correo sale y no vuelve: cae en promociones, se marca leído sin abrir, o llega a una dirección
que el cargador de currículums inventó y que nadie mira. Y lo que pasaba mientras el candidato no
estaba **no quedaba en ninguna parte**: su lista de postulaciones se veía exactamente igual el día
que todo seguía igual y el día que le cambiaron el sueldo.

La campana es la otra mitad: **lo que pasó queda esperándolo dentro, hasta que lo vea.**
**Complementa al correo, no lo sustituye** — los dos salen del mismo hecho.

- **Nace con un solo tipo de aviso**, el cambio de remuneración, y está hecha para los que vengan:
  «avanzaste de etapa», «tienes una prueba por rendir», «te queda un día». Hoy esos avisos existen
  solo como correos que salen y no vuelven.
- **El texto se guarda ya armado**, no como una plantilla con variables. Un aviso que se
  reconstruyera al leerlo diría el sueldo de hoy, no el que cambió aquel día: la noticia se
  volvería un espejo.
- **Se marca leído al pulsar cada aviso**, o todos con el botón de la cabecera. **No al abrir la
  campana**: antes se apagaban todos al abrirla, y con ellos el punto de cada fila de «Mis
  procesos», sin que nadie hubiera leído nada.
- **El aviso es un enlace de verdad**, alcanzable con el tabulador y con el «sin leer» dentro de su
  nombre accesible, para que quien navega por enlaces oiga cuáles son nuevos.

⚠️ **El resaltado del monto en el portal sale de comparar la fecha del cambio con la de su
postulación**, no de los avisos sin leer. Con los avisos, pulsar el de la campana apagaba el
resaltado en el mismo gesto que traía a la persona a verlo. Y además es más correcto: una vacante
que se movió antes de que él postulara no tiene ninguna novedad para él, y una que cambió después
la sigue teniendo aunque ya leyera el aviso. Lo que se resalta es «esto no es lo que había cuando
dijiste que sí», y eso es verdad mientras dure el proceso.

**El punto no es del color del turno.** En el portal el violeta significa una sola cosa, «te toca a
ti», y que le cambien el sueldo no le da ningún turno: el contador y el punto van en el color de
«lo que cambia tu decisión».

---

## Privacidad

El borrado de datos de la ley 29733 **se lleva también la pretensión declarada en cada postulación
y los avisos del portal de esa persona**.

Era una regresión: la banda del perfil desaparecía y la cifra exacta que había declarado seguía
viva en el ranking de cada empresa. **Es el mismo dato con el mismo tratamiento declarado**, y uno
se borraba y el otro no.

Los avisos **se borran enteros, no se vacían**. A diferencia del correo enviado, que conserva su
fila porque demuestra que se avisó, **un aviso del portal no es prueba de nada frente a nadie**.

---

## Dónde está cada cosa

| Qué | Dónde |
|---|---|
| Las columnas, sus tipos y sus restricciones | [Diccionario de datos](07-DICCIONARIO-DE-DATOS.md) — `vacante`, `postulacion` y `aviso_portal` |
| Por qué el modelo es así | [Modelo de datos](05-MODELO-DE-DATOS.md) — «Vacantes», «Postulación y su historia» y «Auditoría, archivos y desempeño» |
| Los endpoints y qué exige cada uno | [Las APIs](09-APIS.md) — «El portal del candidato» y «Vacantes» |
| Los requisitos, numerados | [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) — «El sueldo, y el trato que lo acompaña» |
| Las dos llaves de la pretensión | [Roles y permisos](04-ROLES-Y-PERMISOS.md) — «Candidatos» |
| El relato sin nada técnico | [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) |

---

## Lo que no está hecho

- **La campana tiene un solo tipo de aviso.** Todo lo demás que el sistema cuenta —cambios de
  etapa, pruebas por rendir, plazos que vencen— sigue existiendo solo como correo.
- **No hay histórico de sueldos de una vacante.** La columna guarda cuándo se cambió por última
  vez, no la serie de cambios; para reconstruirla hay que leer la auditoría.
- **El correo del cambio no se prueba contra un servidor real** en las comprobaciones automáticas,
  como todos los demás: se comprueba que se manda, no que llegue.
