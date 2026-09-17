# Los currículums dejan de vivir en el backend

Hasta ahora los PDF se guardaban en el disco de la máquina que atendía la subida. Este
documento explica por qué eso no puede seguir así, qué se hizo, y qué hay que configurar.

---

## Por qué había que cambiarlo

Un archivo en el disco del backend tiene tres problemas, y los tres aparecen justo cuando el
sistema empieza a usarse de verdad:

**Con dos copias del backend, la mitad de las descargas falla.** El currículum quedó en la
máquina que atendió la subida. Si la descarga la atiende la otra, contesta que el archivo no
existe. No es un fallo intermitente que se pueda depurar: es que el archivo está en otro sitio.

**Un despliegue se lleva los currículums por delante.** El contenedor se reemplaza y su disco
se va con él. Sin aviso y sin error: simplemente, un día ya no están.

**Cada descarga cuesta el doble.** El backend se baja el archivo entero para volver a mandarlo.
Paga el tráfico dos veces, lo tiene en memoria mientras dura, y varios currículums grandes a la
vez se notan.

---

## Qué se hizo

Los archivos van a un **bucket privado de Supabase**. El backend no guarda ningún PDF.

Y aparecen los **enlaces firmados**, que es lo mismo que en Amazon S3 se llama
*presigned URL*: una dirección que lleva dentro un permiso temporal para **un solo archivo**,
para que el navegador hable directamente con el almacén.

| | Para qué sirve | Cuánto dura |
|---|---|---|
| **Enlace de descarga** | El navegador se baja el currículum del bucket. El backend no lo toca | 5 minutos |
| **Enlace de subida** | El navegador manda el archivo al bucket. El backend no lo toca | 2 horas (lo fija Supabase) |
| **Enlace del enunciado** | El correo de la prueba le manda al candidato el enunciado | 180 días (`dias-enlace-consigna`) |
| **Enlace del Excel del ranking** | Es el que se escribe **dentro** de la hoja, en la columna «CV» | 8 horas (`horas-enlace-volcado`) |

Duran poco a propósito. **El enlace no vuelve a preguntar quién eres**: el permiso se comprueba
una vez, al pedirlo, y a partir de ahí el enlace vale por sí solo. Es tan secreto como el
currículum que abre, así que no puede andar circulando por un chat una semana después.

### El del Excel dura horas, y es una decisión, no un descuido

Los cinco minutos del primero valen porque **lo abre un navegador que ya tiene la pantalla
delante**: se pide y se usa en el acto. El de la columna «CV» del Excel del ranking se escribe en
una hoja que **se descarga, se guarda y se abre más tarde** —muchas veces al día siguiente—, así
que con cinco minutos el archivo nacería con esa columna ya muerta, que es peor que no ponerla.
Y son horas y no días como el del enunciado porque un enunciado no es el dato de nadie y un
currículum sí.

⚠️ **El precio hay que decirlo entero: durante esas horas el enlace abre el currículum sin pedir
sesión ni permiso.** Quien reciba la hoja —reenviada, subida a una carpeta compartida, adjunta en
un correo— puede abrir esos currículums aunque no tenga cuenta en el sistema. Por eso **la propia
hoja lo avisa en su pie**, con el plazo que de verdad le queda a sus enlaces y no con un número
escrito a mano, y por eso el plazo es una propiedad (`app.archivos.supabase.horas-enlace-volcado`)
y no un valor en el código: el día que ocho horas parezcan muchas se baja sin tocar nada más.

Escribir esos enlaces pide **el mismo permiso que la descarga de siempre**
(`descargar_entregables`); quien descargue la hoja sin él se lleva la columna «CV» con el nombre
del archivo y nada más, y el pie se lo dice. Un currículum que ya no está en el almacén, o que no
se pudo firmar, deja su fila sin enlace y se cuenta al pie: **una firma que falla no puede dejar
sin hoja a la tanda entera**.

### El almacén de disco ya no existe

Hubo uno y se quitó: hoy el único sitio donde viven los archivos es el bucket. Las pruebas usan
un almacén de mentira que no escribe en ningún sitio y que **no firma nada** —su enlace no lo
abre ningún navegador—, y ningún entorno de verdad tiene esa opción. Por eso quien llama pidiendo
un enlace tiene que estar preparado para que no haya ninguno: **un almacén que no sepa firmar
contesta que no, y quien pide sirve los bytes él mismo** en vez de quedarse sin archivo.

---

## Los dos endpoints

| Endpoint | Qué devuelve | Cuándo usarlo |
|---|---|---|
| `GET /panel/archivos/{id}/enlace` | Una URL firmada y su caducidad | **Con el bucket.** Es el que hay que usar |
| `GET /panel/archivos/{id}/descarga` | El archivo, pasando por el backend | Cuando no hay enlace que dar: es el camino que funciona en cualquier entorno |

Los dos exigen el mismo permiso (`descargar_entregables`) y comprueban lo mismo. Comparten el
código que decide si quien pregunta puede ver ese archivo, y eso no es casualidad: si una
comprobara el permiso y la otra no, la que no lo comprueba sería la puerta de atrás.

---

## Flujo de Implementación

1. **Crea el bucket.** En el dashboard de Supabase → **Storage** → **New bucket**:
   - Nombre: `Documentos-cv` (es el que ya existe; si usas otro, cambialo en `application.yaml`)
   - **Public bucket: apagado.** Es lo más importante de este paso, y ya se olvidó una vez.
     Con el bucket público, la caducidad del enlace firmado **no protege nada**: el enlace
     enseña la ruta en la barra de direcciones, y esa ruta sigue funcionando para siempre y
     para cualquiera. Se descubrió bajando un currículum real sin token, sin clave y sin
     firma.
   - **Tamaño máximo: 10 MB**, el mismo que acepta el portal. Y los tipos permitidos, solo
     PDF y Word. Son dos comprobaciones que ya hace el backend; tenerlas también en el bucket
     significa que siguen valiendo el día que algo suba por otro camino.

   Comprobar que quedó bien no es mirar la pantalla, es esto:

   000

   Tiene que dar **400**. Si da **200**, el bucket sigue abierto.

2. **Añade la configuración** a `application-secrets.yaml`, que no se versiona:

   ```yaml
   app:
     archivos:
       tipo: supabase
       supabase:
         clave: TU_SERVICE_ROLE_KEY
   ```

   Solo la clave. **La url del proyecto y el nombre del bucket ya están en
   `application.yaml`**, porque no son secretos: la url aparece en cualquier frontend que
   hable con Supabase, y el nombre del bucket no abre nada por sí solo. La clave sí lo es.

   ⚠️ Es la `service_role` del proyecto **de la base de datos del backend**, no la del
   Supabase de los agentes de IA. Son dos proyectos distintos y confundirlos manda los
   currículums a la cuenta equivocada, sin ningún error que lo avise.

3. **Arranca.** Si falta algo de esa configuración, la aplicación **no arranca** y dice qué
   falta. Es a propósito: descubrirlo cuando un candidato ya pulsó «enviar» significa perder su
   currículum y su tiempo.

4. **Comprueba.** Sube un currículum desde el portal y míralo en Storage → `Documentos-cv`.
   Tiene que aparecer como `{organización}/{uuid}.pdf`.

5. **No hay a dónde volver.** Borrar el bloque `app.archivos` no devuelve los archivos a la
   máquina —ese almacén se quitó—: deja el backend sin clave para el bucket, y entonces no
   arranca.

---

## Decisiones

- **Los archivos que ya están en disco no se mueven solos.** Si hay currículums en la máquina
  de alguien y quieren conservarse, hay que subirlos al bucket y actualizar la columna `ruta`.
  Con datos de prueba no vale la pena; con currículums reales, sí, y entonces hay que escribir
  ese traspaso.

- **El enlace de subida existe pero todavía no lo usa nadie.** El portal sigue subiendo el
  currículum a través del backend, que lo reenvía al bucket. Funciona y ya cumple lo que
  importaba —el PDF no se queda aquí—, pero el archivo sigue pasando por en medio. Cambiar el
  portal para que suba directo toca el recorrido del candidato: hay que decidirlo aparte.

---

## Lo que conviene saber

**La ruta dentro del bucket es `{organización}/{uuid}.{extensión}`.** El uuid es aleatorio para
que nadie adivine rutas ajenas, y la organización va delante para que el aislamiento entre
empresas también exista dentro del bucket, no solo en la base.

**Acortar el plazo del enlace del Excel no pide tocar código.** Vive en `application.yaml`, en
`app.archivos.supabase.horas-enlace-volcado`, y vale 8. Ponerle 2 hace que las hojas que se
descarguen desde ese momento traigan enlaces de dos horas, **y el pie de cada hoja lo dirá solo**:
el aviso se calcula del propio enlace y no de un número escrito en el texto. Las hojas ya
descargadas conservan el plazo con el que se firmaron: un enlace ya entregado sigue abriendo
hasta que le toque caducar, así que bajar la propiedad protege lo que se descargue después, no lo
que ya está circulando.

**La clave `service_role` se salta todas las reglas de acceso.** Por eso puede escribir en un
bucket privado, y por eso **nunca puede llegar al navegador**: quien la tenga lee el bucket
entero. Lo que llega al navegador es un enlace firmado, que sirve para un archivo y caduca.

**Borrar sigue conservando la fila.** Al anonimizar, el objeto se borra del bucket y la `ruta`
se anula, pero la fila de `archivo` se queda: se sabe que existió sin poder recuperarlo. Si el
bucket no deja borrar, se anota el error y se sigue —la anonimización de la base no puede
quedarse a medias por un objeto que no se dejó borrar—.

---

## Enlaces

- [Conectar la base de datos a Supabase](CONEXION-SUPABASE.md) — retirado: el perfil que hacía
  eso se borró el 21/08. Queda por qué
- [Requisitos no funcionales](02-REQUISITOS-NO-FUNCIONALES.md) — lo que el sistema tiene que
  cumplir además de funcionar
