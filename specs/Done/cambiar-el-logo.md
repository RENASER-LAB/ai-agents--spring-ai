# Cambiar el logotipo de EX
Estado: TERMINADO

## Objetivo
EX estrena logotipo: una «E» maciza y una «X» de trazos huecos, cruzada por una hormiga de
perfil. Hay que ponerlo en todos los sitios donde hoy sale el anterior (la palabra «EX» con la
hormiga vista desde arriba posada sobre la X), para que el candidato, el equipo y quien
instala la aplicación de Android vean una sola marca.

## Contexto
- **Comportamiento actual.** El logotipo no es una imagen. Es el componente `Marca`
  (`src/ui/Marca.tsx`): el texto «EX» en Geist 700 más un SVG en línea de la hormiga, pintados
  con `currentColor`. Los estilos globales `.marca`, `.marca-letras` y `.marca-hormiga` viven
  en `src/estilos/mundo.css:686-718`. El favicon es otro dibujo de la misma hormiga
  (`public/hormiga.svg`), y los iconos de Android se dibujaron con Pillow a partir de él
  (PR #18).
- **Pantallas o procesos afectados.** Todo está en el repositorio del frontend,
  `~/Documentos/RenaserOsPostulantes`. El backend no se toca: ningún correo ni documento lleva
  logotipo.

  | Sitio | Archivo | Tamaño hoy (`tamano`) | Color |
  |---|---|---|---|
  | Cabecera del portal | `src/app/Armazon.tsx:301` | 22 | tinta |
  | Pie en columnas del portal | `src/app/Armazon.tsx:423` | 20 (48 en la portada) | tinta |
  | Cabecera del panel, junto a «Panel del equipo» | `src/panel/Armazon.tsx:54` | 22 | tinta |
  | «Entrando…» y enlace fallido | `src/paginas/cuenta/Acceso.tsx:90,103` | 40 | tinta |
  | Crear acceso al panel (invitación) | `src/panel/entrar/Invitacion.tsx:139` | 24 (por defecto) | tinta |
  | Estados vacíos y «Acceso necesario» | `src/ui/Vacio.tsx:25`, `src/ui/Mensajes.tsx:75` | 30 | gris `--regla2`, marca de agua (`src/ui/Estados.module.css:116`) |
  | Pestaña del navegador | `index.html` → `public/hormiga.svg` | — | `#1c1c1e` |
  | Icono del lanzador de Android | `android/app/src/main/res/mipmap-*` (5 densidades, adaptativo y redondo) | — | `#232b36` sobre `#F6F8FB` |
  | Ficha de Play | `android/icono-play-512.png` | — | ídem |
  | Pantalla de arranque de Android | `android/app/src/main/res/drawable*/splash.png` (11 archivos) | — | **todavía la X azul de Capacitor que trae la plantilla** |

- **Referencias existentes.**
  - Archivo del logotipo nuevo: `~/Descargas/Ex-App.png`. Es un PNG de 808 × 714 con
    transparencia, 15 KB, negro puro y sin margen (el dibujo toca los cuatro bordes).
  - `PRODUCT.md` › Brand Commitments, `DESIGN.md` (pie y cabecera) y
    `docs/03-ESTADO-DEL-REDISENO.md:65` describen el logotipo anterior como algo que «se
    conserva tal cual».
  - `herramientas/capturar-logo.mjs` fotografía la cabecera a 4 aumentos.

## Alcance
- **Incluye:**
  - Copiar el archivo **sin retocarlo** como `src/ui/logotipo.png`. `Marca` lo importa, así
    Vite le pone huella al nombre y nadie se queda con el viejo en caché. Se copia, no se
    mueve: el original sigue en Descargas.
  - Rehacer `Marca`, con el mismo nombre y las mismas props, para que pinte el logotipo nuevo
    en el color del texto que lo rodea.
  - Ajustar los tamaños de los 8 sitios, según la tabla de «Referencia visual».
  - Hacer un favicon nuevo (`public/favicon.png`) y borrar `public/hormiga.svg`.
  - Regenerar los iconos de Android (lanzador en las 5 densidades, adaptativo, redondo y
    `icono-play-512.png`) y las 11 pantallas de arranque a partir del logotipo nuevo.
  - Dejar en `herramientas/` el script que genera los derivados (favicon, iconos y arranque),
    para rehacerlos si el logotipo vuelve a cambiar.
  - Quitar la hormiga vieja y sus estilos (`.marca-letras` y `.marca-hormiga`), y actualizar
    los comentarios de `Marca.tsx` y `mundo.css`.
  - Actualizar `PRODUCT.md`, `DESIGN.md` y `docs/03-ESTADO-DEL-REDISENO.md` para que
    describan el logotipo nuevo.
- **Queda fuera:**
  - Publicar una versión nueva en Play (`versionCode` 10) y subir el icono a la ficha de Play
    Console. Los teléfonos no verán el icono nuevo hasta esa publicación, que hace el usuario.
  - Rehacer las capturas de `capturas/` y `capturas-promocion/`.
  - Vectorizar o redibujar el logotipo.
  - Tocar el nombre, la paleta, la tipografía, el backend o el retrato abstracto de la portada
    (`.ventanaMarca`, que es una barra y no el logotipo).
  - Hacer una versión para fondo oscuro: el portal solo tiene tema claro.

## Comportamiento esperado
1. En cualquier pantalla del portal o del panel, el logotipo nuevo aparece exactamente donde
   estaba el anterior, a un tamaño equivalente y sin que la cabecera, el pie ni ninguna
   pantalla cambien de alto.
2. `Marca` pinta el PNG como **máscara** (`mask-image` más `-webkit-mask-image`, porque lo
   pide la WebView de Android) sobre un fondo `currentColor`. Así el logotipo sale en tinta
   `#101828` y no en negro puro, y la marca de agua de los estados vacíos sigue saliendo en
   gris sin tocar `Estados.module.css`.
3. La prop `tamano` pasa a ser **el alto total del dibujo, en píxeles**. El ancho sale de la
   proporción del archivo (808/714 ≈ 1,13) y la caja se reserva aunque la imagen tarde en
   llegar, para que nada salte.
4. Accesibilidad:
   - `Marca` se anuncia como una imagen con el nombre «EX» (`role="img"`, `aria-label="EX"`).
   - Donde el enlace ya lleva su propia etiqueta («EX, inicio», «Panel del equipo, inicio»),
     manda la del enlace, como hoy.
   - Donde el envoltorio va con `aria-hidden` (estados vacíos, «Entrando…»), sigue oculta.
5. Favicon:
   - La pestaña del navegador muestra el logotipo completo en tinta sobre un cuadrado claro
     `#F6F8FB` de esquinas redondeadas, el mismo tratamiento que el icono de Android. Así se ve
     también en navegadores con la barra de pestañas oscura.
   - Es un PNG de 64 × 64 enlazado con `<link rel="icon" type="image/png">`.
6. Iconos de Android:
   - El lanzador muestra el logotipo en tinta `#101828` sobre el fondo actual `#F6F8FB`
     (`ic_launcher_background`, que no cambia).
   - En el adaptativo, **todo el dibujo cabe en el círculo central de 66 dp del lienzo de
     108 dp**, porque cada fabricante recorta con su forma. Se comprueba por las esquinas: la
     «E» y la antena son lo primero que se corta.
   - Los clásicos (`ic_launcher.png` e `ic_launcher_round.png`) y el de Play siguen las
     mismas reglas que se usaron en el PR #18.
7. La pantalla de arranque de Android deja de mostrar la X de Capacitor. Muestra el
   logotipo en tinta, centrado sobre `#F9FAFB` (el `--cielo`, el mismo color que pinta la
   barra del teléfono), con un ancho de como mucho un tercio del lado corto.
8. Si el PNG no carga (sin conexión en la web con la caché vacía), no se ve el logotipo, pero
   su hueco se mantiene y los enlaces conservan su nombre accesible. No aparece la imagen
   rota del navegador ni ningún error en consola.

## Referencia visual
- **Boceto de la cabecera del portal** (móvil y escritorio, igual que hoy):
  ```
  ┌────────────────────────────────────────────────────────────┐
  │  E╲╳🐜      Inicio   Vacantes   Mis procesos    [Mi cuenta] │
  └────────────────────────────────────────────────────────────┘
     ↑ 28 px de alto, ~32 px de ancho
  ```
  En el panel el logotipo va seguido de «Panel del equipo», como hoy.
- **Tamaños.** `tamano` es el alto total del dibujo. Se sube alrededor de un 25 % respecto al
  anterior porque la «E» nueva ocupa solo unos 3/5 del alto del dibujo; a 16 px ya no se lee.

  | Sitio | Antes | Ahora |
  |---|---|---|
  | Cabeceras del portal y del panel | 22 | 28 |
  | Pie en columnas | 20 | 24 |
  | Pie de la portada | 48 | 56 |
  | «Entrando…» y enlace fallido | 40 | 48 |
  | Invitación al panel (el valor por defecto) | 24 | 28 |
  | Estados vacíos y «Acceso necesario» | 30 | 36 |

  Quien implementa puede mover cada valor ±4 px si al verlo no cuadra con el texto de al
  lado. Lo que no puede es hacer crecer la barra de la cabecera, que hoy tiene 44 px de alto
  mínimo.
- **Estados a dibujar.** Solo aplican el normal y el de imagen que no carga (punto 8). El
  logotipo no tiene estados vacío, de carga ni de datos largos.
- **Móvil y escritorio.** Mismos tamaños en los dos. A 360 px de ancho la cabecera no puede
  desbordarse ni empujar los destinos de su columna central.
- **Reglas visuales.**
  - Se aplica `DESIGN.md`.
  - El logotipo es monocromo: nunca en índigo ni en coral, ni animado.
  - `Marca` sigue siendo el único sitio que conoce el archivo: ninguna pantalla importa el PNG
    directamente.

## Criterios de aceptación
- AC-01: Dado un visitante en `/`, cuando mira la cabecera, entonces ve el logotipo nuevo de
  28 px de alto que enlaza a Inicio, con el nombre accesible «EX, inicio». Ya no existe
  `.marca-letras` ni ningún SVG de la hormiga vieja.
- AC-02: Dado un miembro del equipo en el panel, cuando mira la cabecera, entonces ve el
  logotipo nuevo de 28 px seguido de «Panel del equipo», y el enlace lleva a Vacantes.
- AC-03: Dada la portada, cuando se baja hasta el pie, entonces el logotipo mide 56 px. En
  cualquier otra página con pie en columnas mide 24 px.
- AC-04: Dada una lista vacía o la pantalla «Acceso necesario», cuando se muestra, entonces el
  logotipo sale en gris `--regla2` a 36 px. Eso prueba que se pinta con `currentColor` y no en
  negro fijo.
- AC-05: Dada cualquier pantalla con el logotipo en tinta, cuando se inspecciona su color
  calculado, entonces es `rgb(16, 24, 40)` (`--tinta`) y no negro puro.
- AC-06: Dado un enlace mágico, cuando se abre «Entrando…» o el aviso de enlace fallido,
  entonces el logotipo mide 48 px. En la invitación al panel mide 28 px y se anuncia como
  «EX».
- AC-07: Dada cualquier página del portal, cuando carga, entonces la pestaña muestra el
  favicon nuevo, `/favicon.png` responde 200 con `image/png`, nadie pide `/hormiga.svg` y el
  archivo ya no existe en `public/`.
- AC-08: Dada la barra de la cabecera, cuando se compara su alto antes y después del cambio
  a 360 px y a 1280 px de ancho, entonces es el mismo y la página no tiene scroll horizontal.
- AC-09: Dada una pantalla con densidad 3×, cuando se mira el logotipo del pie de la portada,
  entonces se ve nítido, sin bordes borrosos ni dientes de sierra.
- AC-10: Dado el modo de colores forzados (alto contraste de Windows), cuando se abre la
  cabecera, entonces el logotipo sigue visible en el color de texto del sistema.
- AC-11: Dada la carpeta `android/app/src/main/res/`, cuando se abren los `mipmap-*`,
  entonces las 5 densidades de `ic_launcher`, `ic_launcher_round` e
  `ic_launcher_foreground` muestran el logotipo nuevo. En el adaptativo, el dibujo entero cabe
  en el círculo central de 66/108.
- AC-12: Dados los 11 `splash.png`, cuando se abren, entonces ninguno muestra la X de
  Capacitor: todos llevan el logotipo nuevo centrado sobre `#F9FAFB`.
- AC-13: Dado `android/icono-play-512.png`, cuando se abre, entonces mide 512 × 512 y lleva el
  logotipo nuevo.
- AC-14: Dado el `.apk` de depuración compilado, cuando se abre, entonces contiene los iconos
  y las pantallas de arranque nuevas. No basta con mirar la carpeta de recursos.
- AC-15: Dado `PRODUCT.md`, `DESIGN.md` y `docs/03-ESTADO-DEL-REDISENO.md`, cuando se leen,
  entonces describen el logotipo nuevo y no dicen que el anterior se conserva.

## Casos límite y errores
- **Datos vacíos o inválidos.** No aplica: el logotipo no depende de datos.
- **Usuario sin permisos.** No aplica: el logotipo es igual para todos, con sesión o sin
  ella.
- **Recarga, reintento o doble envío.** Al recargar, el PNG sale de caché. Como su nombre
  lleva huella, un despliegue futuro con otro logotipo no deja a nadie con el viejo.
- **La imagen no carga:** su hueco se mantiene y no hay error en consola (punto 8).
- **Colores forzados:** el sistema anula el color de fondo y el logotipo desaparecería. Se
  resuelve con `forced-color-adjust: none` y `CanvasText` (AC-10).
- **La WebView de Android:** una WebView desactualizada solo entiende la máscara con
  prefijo, por eso se declaran las dos.
- **La barra de pestañas oscura:** el favicon lleva su propio fondo claro (punto 5).
- **16 px:** a ese tamaño el logotipo no se lee. Ningún sitio de la web baja de 24 px. El
  favicon es la única excepción y el navegador lo reduce solo.

## Datos y compatibilidad
- **Información que debe guardarse.** Ninguna: no hay migración ni cambios de API.
- **Registros existentes.** No aplica.
- **Restricciones de compatibilidad:**
  - `appId` (`com.renaser.ex`) y `appName` no cambian.
  - Los nombres de los recursos de Android no cambian, así que el `AndroidManifest.xml` y
    `mipmap-anydpi-v26/*.xml` quedan intactos.
  - La app ya instalada conserva el icono viejo hasta que se publique una versión nueva.
- **El script de derivados:**
  - Parte siempre del PNG original.
  - Pinta a varios aumentos y reduce, igual que en el PR #18.
  - Si es Python, se ejecuta como script y no como módulo importado, para no dejar un
    `__pycache__` en el worktree.

## Verificación
- **Actor, rol y datos de prueba:**
  - Portal: un visitante sin sesión y un candidato con sesión.
  - Panel: cualquier miembro del equipo con el backend simulado de los E2E; no hace falta
    ningún dato especial.
- **Recorridos que deben quedar como E2E repetibles:**
  - Cabecera del portal en `/`: logotipo visible, alto 28 ± 1 px, nombre accesible
    «EX, inicio» y color calculado igual a `--tinta` (AC-01, AC-05).
  - Cabecera del panel: logotipo y «Panel del equipo» (AC-02).
  - Estado vacío: el logotipo sale en gris `--regla2` (AC-04).
  - `/favicon.png` responde 200, ninguna petición va a `/hormiga.svg` y la consola queda sin
    errores (AC-07).
  - A 360 px no hay scroll horizontal y la cabecera no crece (AC-08). Se puede sumar a
    `07-movil.spec.ts`.
- **Riesgos que QA debe explorar con navegador:**
  - Nitidez a DPR 2 y 3 (AC-09).
  - Colores forzados (AC-10).
  - El pie de la portada por debajo de 900 px, donde la marca ocupa su propia fila.
  - Que la hormiga se reconozca en la cabecera a 28 px; usar `herramientas/capturar-logo.mjs`.
- **Resultado que quiero comprobar personalmente:**
  - Ver las cabeceras del portal y del panel.
  - Ver la pestaña del navegador en claro y en oscuro.
  - Instalar el `.apk` en el teléfono y ver el icono en el lanzador y el arranque.

## Dependencias y dudas
- **Requiere:** el archivo `~/Descargas/Ex-App.png` (808 × 714, 15 KB) en esta máquina.
  Quien implementa lo copia al repositorio. Si falta o no coincide con esas medidas, se para y
  se pregunta.
- **Decisiones pendientes:** ninguna.
- **Decisiones tomadas en esta spec** (propuestas sin confirmar; el usuario puede cambiarlas):
  - Se usa el PNG tal cual y no se vectoriza. Redibujarlo cambiaría la forma que entregó el
    usuario. A los tamaños de uso (hasta 56 px, es decir 168 px en pantallas 3×), la fuente de
    714 px sobra.
  - El favicon y el icono de la app llevan el logotipo completo. No existe una versión que
    sea solo la hormiga, y la vieja, que es otro dibujo, se retira para no tener dos hormigas
    distintas.
  - El favicon va sobre un cuadrado claro, para que se vea en pestañas oscuras.
  - El logotipo se pinta en tinta `#101828`, no en negro puro, como todo el texto del portal.
  - La pantalla de arranque de Android entra en el alcance porque hoy muestra la marca de
    Capacitor.
