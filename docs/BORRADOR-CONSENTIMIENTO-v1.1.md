# Borrador · Textos de consentimiento v1.1

⚠️ **Este documento nació como borrador para el abogado y hoy está en parte implementado.** La
migración `V54` (14/09/2026) cargó los textos nuevos y el portal ya los enseña. Lo que sigue
faltando es **la firma de un abogado**: lo que cambió no es que los textos estén aprobados, sino
que ya no les falta información.

Lo que sí queda intacto es la v1.0: **quien la aceptó sigue ligado a ella**, que es para lo que
existe el versionado. Eso tiene una consecuencia que no se ha resuelto y está al final, en
«Lo que sigue pendiente».

---

## Por qué hacía falta cambiarlo

Eran dos problemas, y el segundo se descubrió mirando el primero.

### 1 · Nadie decía a quién salen los datos

El texto que firmaba un candidato decía que «una inteligencia artificial participa en la
evaluación», y ahí se quedaba. **No decía que sus datos salen de Renaser, ni hacia quién.**

Cuando se escribió eso era verdad: la inteligencia artificial corría en el propio servidor. Ya
no. Hoy el sistema se apoya en cinco servicios de otras empresas, todos fuera del Perú:

| Empresa | Para qué | Qué se le manda |
|---|---|---|
| **DeepSeek** | Califica el currículum, las respuestas abiertas y la prueba del puesto | El currículum ya recortado y las respuestas que escribió el candidato |
| **Google** | Busca por significado dentro del sistema | Fragmentos de texto convertidos en números |
| **Supabase** | Guarda la base de datos y los archivos | La cuenta, el perfil y los archivos que sube |
| **Amazon Web Services** | Aloja los servidores | Todo lo anterior, mientras el sistema funciona |
| **Vercel** | Sirve las páginas del portal | Lo que el candidato escribe en ellas, camino del servidor |

A esos cinco se suma el proveedor de correo con el que se le escribe. Eso es lo que la Ley 29733
llama **flujo transfronterizo de datos personales**, y hay que decírselo a la persona antes, no
después. Mientras la inteligencia artificial no calificara a nadie no tenía efecto; **desde la
V53 la vacante puede calificar sola**, y el currículum de una persona real sale del país.

### 2 · El candidato firmaba el mismo texto dos veces

Al crear la cuenta se le pedía el texto de tipo `PROCESO` **de la plataforma** —el que dice
«evaluar mi postulación a esta vacante» cuando todavía no hay ninguna vacante—, y al postular a
una vacante de Renaser volvía a firmar ese mismo texto, ahora sí con su postulación. Dos filas en
`consentimiento` del mismo texto para la misma persona, y una de ellas mintiendo.

---

## Qué se llevó la V54

### Tres tipos de consentimiento, con tres alcances

| Tipo | Con quién se firma | Cuándo | Versión que publicó la V54 |
|---|---|---|---|
| `PLATAFORMA` | Con **Renaser** | Al crear la cuenta, obligatorio | `1.0` (es un tipo nuevo) |
| `PROCESO` | Con **la empresa** de la vacante | Al postular, obligatorio, uno por postulación | `1.1` |
| `FUTUROS_CONTACTOS` | Con **Renaser** | Al crear la cuenta, opcional | `1.1` |

**El contenido pesado vive en uno solo.** `PLATAFORMA` es el que cuenta la cuenta, el perfil, la
inteligencia artificial, los encargados, la salida de datos del país, el plazo de conservación y
los derechos. `PROCESO` se quedó para lo único que es de cada empresa —que **ella** decide sobre
esa postulación— y por eso pasó a tener tres párrafos: repetir lo técnico empresa por empresa era
exactamente lo que hacía que el candidato firmara dos veces lo mismo.

### El texto del proceso es UNO SOLO para todas las empresas

Es el cambio que más consecuencias tiene, y conviene entenderlo antes de seguir leyendo.

Antes había **una fila por empresa**, y lo único que cambiaba entre una y otra eran las tres
palabras de su razón social. Eso salía caro de dos maneras: corregir una coma obligaba a
republicar empresa por empresa, y una empresa recién dada de alta no podía recibir candidatos
hasta que alguien le redactara y publicara un texto legal. Ninguna lo hacía bien: publicaban la
copia de Renaser tal cual, que ni siquiera las nombraba a ellas.

Ahora hay **un solo texto, propiedad de la plataforma, con un hueco donde va el nombre de quien
publica la vacante**. El nombre se pone al leerlo, así que cada candidato lee el de la empresa a
la que está postulando y el texto se mantiene en un único sitio. De ahí salen tres cosas:

- **Ninguna empresa tiene texto propio ni puede publicar uno.** El panel responde **400** a una
  empresa que intente publicar cualquiera de los tres tipos, con un mensaje que lo explica. Un
  texto por empresa es un texto que nadie revisa y que se queda viejo sin que nadie se entere;
  el de la plataforma lo mantiene quien tiene el abogado.
- **Dar de alta una empresa ya no le copia nada.** Puede recibir candidatos desde el primer
  minuto y no tiene nada que redactar.
- **Se retiró el freno de publicar vacantes.** Hasta la V54, publicar una vacante exigía que la
  empresa tuviera su texto publicado. El freno solo llegaba a servir cuando el alta repartía un
  borrador que nadie publicaba, y entonces el error le salía en la cara a quien abría la vacante
  en vez de a quien podía arreglarlo.

### Lo que la V54 escribió en la base

1. El catálogo de tipos de `texto_consentimiento` admite ahora los tres valores.
2. Publicó el texto `PLATAFORMA` de la plataforma, si no lo tenía ya.
3. Publicó **un solo** `PROCESO` v1.1, también de la plataforma, con el hueco para el nombre de
   la empresa.
4. Publicó el `FUTUROS_CONTACTOS` v1.1 de la plataforma.
5. **Retiró** —dejó sin publicar, sin borrar nada— **todos los `PROCESO` y `FUTUROS_CONTACTOS`
   que seguían siendo el texto provisional de la v1.0**: tanto las copias que el alta había
   repartido a cada empresa como las dos filas de la propia plataforma, a las que la v1.1 acaba
   de relevar. Se reconocen por la marca «[TEXTO PROVISIONAL» y por cómo empieza el texto de la
   `V9`, porque basta con que alguien borrara el corchete al copiar y pegar. Nadie las lee ya,
   pero el panel de textos legales las listaría como vigentes, y dejarlas así es sembrar la duda
   de cuál manda. La fila se queda entera y los consentimientos firmados contra ella siguen
   apuntando a su texto y su huella: lo único que cambia es que deja de estar publicada, que es
   una propiedad del presente y no del pasado.
6. Renombró la organización plataforma de «Clínica Renaser S.A.C.» a **«RENASER CONSULTING
   S.A.C.»**, que es la razón social de su ficha RUC, y solo si seguía con el nombre de la
   semilla. En producción ya se había renombrado a mano; lo que arregla son las bases nuevas.
7. Añadió una columna a `consentimiento` para guardar **el texto tal como se le pintó a la
   persona**, con el nombre de la empresa ya puesto. Ver justo debajo.

**Nada de la v1.0 se borra ni se modifica.** Publicar una versión nueva no despublica la
anterior: la vigente es la de fecha de publicación más reciente.

### Lo firmado se guarda entero, y no solo la fila a la que apunta

Hasta ahora bastaba con apuntar a la fila del texto: lo que ponía ahí era literalmente lo que la
persona había leído. Con el hueco deja de serlo —dos candidatos de dos empresas firman la misma
fila y han leído cosas distintas—, así que al postular se guarda además **el texto compuesto**,
con el nombre dentro. Se guarda el texto entero y no solo el nombre de la empresa porque el
nombre puede cambiar después (`organizacion.nombre` ya cambió una vez, en esta misma migración) y
entonces lo leído no se podría reconstruir.

⚠️ **Con quién se firmó lo dicen la postulación y ese texto, no el dueño de la fila.** La fila es
siempre de la plataforma, porque hay una sola para todas. Mirar de quién es la fila para saber
con qué empresa se firmó era cierto cuando cada una tenía la suya, y dejó de serlo.

Un consentimiento con esa columna vacía significa «lo firmado es el texto literal de su fila»:
es el caso de los dos de la cuenta, que no llevan hueco, y el de todo lo anterior a la V54.

---

## Qué cambió respecto de lo que este borrador proponía

El borrador proponía un solo texto largo por proceso. Se hizo distinto, y hay cuatro diferencias
que conviene que el abogado vea antes de leer el texto cargado:

| Lo que proponía el borrador | Lo que se cargó |
|---|---|
| Un texto de proceso que lo contaba todo | Dos textos: el de la plataforma lo cuenta todo, y el de la vacante solo lo suyo |
| Un texto de proceso **por empresa** | **Uno solo para todas**, con el nombre de la empresa puesto al leerlo |
| Dos empresas de fuera: DeepSeek y Google | **Cinco**, más el proveedor de correo: se sumaron Supabase, Amazon Web Services y Vercel, que también están fuera |
| «Ninguna persona ajena a Renaser revisa los datos» | **No se escribió.** Es falso: la empresa de la vacante los ve, y ese es el objeto de su permiso |
| «La máquina no decide sola: una persona revisa y confirma **toda** decisión» | **Se corrigió**, porque era falso. Ver abajo |

### La corrección que más importa: hay tres cosas que pasan sin que nadie las toque

El texto anterior prometía que **ninguna** decisión sobre la candidatura se tomaba
automáticamente. No era cierto. Hay tres transiciones en las que no interviene ninguna persona:

| Qué pasa solo | Dónde ocurre | ¿Se puede apagar? |
|---|---|---|
| Se cierra la postulación al postular, si el candidato declara que no cumple un requisito indispensable de la vacante | Al enviar la postulación | No |
| Se cierra el proceso cuando se acaba el plazo para responder una evaluación o una prueba | Un sondeo que corre cada 60 segundos | **No tiene interruptor** |
| Se pasa a la etapa siguiente sin esperar a nadie | Solo en las vacantes que encendieron «calificar y avanzar sola» (V53) | Sí, es el interruptor de la vacante |

Los dos textos —el de la plataforma y la política de privacidad del portal— dicen ahora
**«ninguna nota te contrata ni te descarta por sí sola»** y enumeran esas tres. Ninguna de las
tres depende de una nota, y en las tres el candidato puede escribir y pedir que una persona lo
revise.

### Los huecos que el borrador dejaba abiertos

| Hueco | Con qué se rellenó |
|---|---|
| `[PLAZO]` | **24 meses sin actividad**, que es el valor por defecto del parámetro `meses_conservar_perfil` |
| `[POLÍTICA AL VENCER]` | **Se elimina el perfil**, con todo lo que cuelga de él. No se anonimiza: el perfil no sostiene ninguna nota |
| `[CORREO DE CONTACTO]` | `renaserlab@gmail.com` |

El plazo sigue siendo configuración y no un número en el código, tal como pedía este borrador.
Lo que ata las dos cosas es una prueba: si alguien cambia el parámetro sin republicar el texto,
`BarridoRetencionPerfilTest#elPlazoDelTextoLegalEsElQueAplicaElBarrido` falla, porque son dos
archivos que de otro modo no se citan entre sí.

---

## Lo que sigue pendiente

### 1 · Los textos siguen sin revisión de un abogado

**Nada de lo cargado está aprobado legalmente.** Lo que cambió es que ya no falta información:
están el responsable con su RUC y su domicilio, los encargados por su nombre, la salida de los
datos del país, el plazo y los derechos. Falta que alguien con título los firme.

Una cosa concreta a decidir con él: el correo de contacto sigue siendo **uno de equipo**
(`renaserlab@gmail.com`) y no uno institucional del responsable del tratamiento. Cambiarlo obliga
a republicar los textos, que lo llevan escrito dentro.

### 2 · La casilla de postular se retiró, y es lo que más necesita ese visto bueno

**En la pantalla de postular ya no hay casilla de consentimiento.** Encima del botón se dice
quién va a recibir la candidatura y se enlaza el texto entero; enviar es el acto.

El razonamiento es que enviar tu candidatura a una empresa que tú elegiste, después de leer quién
la recibe, ya es un acto afirmativo inequívoco, y que el candidato viene de marcar dos casillas
en el registro. **Pero es un cambio de figura legal**: se pasa de «consentimiento expreso por
casilla» a «consentimiento por acto inequívoco». Las dos se defienden y no son lo mismo, así que
es la primera decisión que hay que poner delante del abogado.

**Lo que no se retiró es la constancia.** Al enviar se sigue guardando la firma a nombre de esa
empresa, con el texto compuesto, la fecha y la dirección desde la que se envió; y el backend
sigue exigiendo el dato, para cortarle el paso a quien llame a la API por su cuenta. Lo que
cambió es cómo se da el permiso, no que se dé ni que quede registrado.

### 3 · Quien ya tenía cuenta nunca firmó el texto `PLATAFORMA`

La V54 **no toca la tabla `consentimiento`**, y no existe ningún mecanismo de re-aceptación. Los
candidatos que se registraron antes siguen amparados por el texto v1.0 que aceptaron, que **no
nombra a los encargados ni dice que sus datos salen del Perú**.

Es una decisión de producto sin tomar, y bloquea el objetivo del cambio para esa gente. Las
opciones son las mismas que planteaba la pregunta 2 de este borrador: pedirles que acepten la
versión nueva la próxima vez que entren, o terminar sus procesos sin inteligencia artificial.

### 4 · Todo el peso informativo del registro recae ahora en el enlace

La explicación que acompaña a la casilla obligatoria del registro **se acortó a una línea** por
decisión de producto (15/09/2026). Antes nombraba ahí mismo la inteligencia artificial y los
proveedores de fuera del Perú, que es la primera capa del aviso por capas: lo que hace defendible
que el resto viva detrás de un enlace.

Al quitarla, lo que sostiene que el consentimiento esté informado es **la política de privacidad
y el enlace que lleva a ella**. Si ese enlace se rompe, o el documento se recorta, el
consentimiento deja de estar informado aunque la casilla siga ahí. Es lo que hay que mirar antes
de tocar cualquiera de los dos.

### 5 · El plazo efectivo es el más largo de todas las organizaciones, y el barrido solo borra el perfil

Dos matices que la documentación no debe simplificar a «se borra todo a los 24 meses»:

- **El plazo que manda es el máximo entre organizaciones.** El perfil es de la persona y es
  transversal; el parámetro es por organización. Ante dos plazos distintos gana el más largo,
  porque borrar antes de tiempo es irreversible.
- **El barrido solo borra el perfil.** La persona, el usuario, las postulaciones y lo que
  respondió en ellas se conservan mientras haya cuenta. Los textos cargados ya lo dicen así.

La tabla `politica_conservacion` con su `accion_al_vencer` (`ELIMINAR`, `ANONIMIZAR`,
`RENOVAR_CONSENTIMIENTO`) sigue existiendo en el modelo, pero **hoy no la lee nadie**: quien
decide es el parámetro `meses_conservar_perfil`, y lo que ejecuta es siempre borrar el perfil.

### 6 · La pregunta que sigue siendo para el abogado

**¿Basta con informar y consentir el flujo transfronterizo, o hace falta algo más?** Si la Ley
29733 exige además un registro ante la autoridad o un contrato con cada encargado, eso está
fuera de lo que el sistema resuelve solo.

---

## Cómo se carga una versión nueva

Igual que la V54: una migración de Flyway inserta las filas en `texto_consentimiento` con su
versión y su huella, **sin tocar ni borrar las anteriores**. Desde ahí el portal enseña la
versión publicada más reciente, y cada aceptación queda guardada contra la versión que se firmó.

La plataforma puede además publicar una versión nueva desde su propio panel
(`POST /panel/textos-consentimiento`), que la crea **y la publica**. **Ninguna empresa puede**:
los tres textos son de la plataforma, y pedir cualquiera de los tres desde una empresa responde
**400**. Si hay que cambiar algo, se pide a Renaser, y el cambio vale para todas a la vez — que
es justamente la ventaja de tener un solo texto.
