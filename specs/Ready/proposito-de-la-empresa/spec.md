# El propósito de la empresa, a la vista del candidato

Estado: lista para implementar

## Objetivo
Que quien busca trabajo en EX sepa **por qué existe la empresa** antes de postular, sin salir
a buscarla. Es la pieza 4a de la reunión con la clienta del 13/11/2026: EX deja de ser «una
bolsa de trabajo más» y pasa a conectar a las personas con el propósito de la empresa. Hoy el
candidato solo ve el nombre de la empresa sobre cada vacante; lo demás lo tiene que averiguar
por su cuenta.

Esta spec es la primera pieza: cada empresa guarda su propósito, el enlace a su video de
presentación, sus redes sociales y su página web. Lo llena desde el panel y el candidato lo ve
en el portal, junto a la vacante. Las piezas que vienen después (el brochure, la convocatoria
redactada por la IA y la entrevista por webcam) se apoyarán en estos datos.

## Contexto
- Comportamiento actual:
  - **Panel, «Configuración».** Tiene los plazos y reglas, el banco de preguntas, las áreas, las
    sedes, los cargos, el equipo y los permisos. No hay ningún sitio donde la empresa hable de
    sí misma. De la empresa solo se guardan el nombre y el código, que los pone Renaser al
    darla de alta.
  - **Portal, buscar vacantes.** En el teléfono hay una lista de tarjetas y cada una abre la
    ficha de la vacante en su propia página. Desde 1024 px la pantalla se parte: la lista a la
    izquierda y la vacante elegida entera a la derecha. En las dos, de la empresa solo se ve el
    nombre, junto a un círculo con sus iniciales. Luego vienen el sueldo, la modalidad, la
    ciudad y el horario, y los bloques «El resultado que esperamos» (o «Sobre el puesto»), «Lo
    que harás», «Lo que buscamos» y los requisitos indispensables.
  - **Cuidado con el nombre.** La vacante ya tiene un campo «propósito». En el panel se llama
    «El resultado que se espera (propósito)» y en el portal sale como «El resultado que
    esperamos». Es el propósito **del puesto**, no el de la empresa, y no cambia. Lo nuevo
    tiene que llamarse distinto en los datos y en la pantalla para que nadie confunda uno con
    otro.
  - El tablón se sirve sin cuenta y mezcla vacantes de todas las empresas activas. Es la
    excepción deliberada al aislamiento entre empresas: cada vacante dice de quién es.
- Pantallas o procesos afectados:
  - Panel: un módulo nuevo en el menú, «Perfil de la empresa», y el formulario de crear y editar
    vacante, con el campo «Video del puesto».
  - Portal: la vacante elegida en la pantalla partida, y la página de la vacante en el teléfono
    o al entrar por su enlace.
- Referencias existentes:
  - [Qué hace el sistema](../../../docs/00-QUE-HACE-EL-SISTEMA.md): los datos de cada empresa van
    separados.
  - [Roles y permisos](../../../docs/04-ROLES-Y-PERMISOS.md): la tabla de «Configuración», el
    aislamiento entre empresas y cómo el alta de una empresa copia los roles de la plataforma.
  - La empresa:
    [Organizacion.java](../../../src/main/java/com/renaser/ai/ai_engine/organizacion/entity/Organizacion.java)
    (hoy: código, nombre, activa, fecha de alta, si es la plataforma y las dos banderas de
    personalización) y el controlador del panel de la propia empresa,
    [PersonalizacionController](../../../src/main/java/com/renaser/ai/ai_engine/organizacion/controller/PersonalizacionController.java)
    (`/api/v1/panel/organizacion`).
  - El tablón: `GET /api/v1/portal/vacantes` y `GET /api/v1/portal/vacantes/{id}`, sin token, en
    [PortalController](../../../src/main/java/com/renaser/ai/ai_engine/portal/controller/PortalController.java).
    Las dos respuestas se arman en
    [ServicioTablonPortalImpl](../../../src/main/java/com/renaser/ai/ai_engine/portal/service/impl/ServicioTablonPortalImpl.java)
    (`comoPublica`), que ya carga las empresas activas de todo el lote en una sola consulta. El
    contrato es `VacantePublica`, en
    [DtosPortal](../../../src/main/java/com/renaser/ai/ai_engine/portal/dto/DtosPortal.java).
  - Una regla de enlaces que ya existe (RF-166, enlaces del perfil del candidato: solo `http` o
    `https`, con dominio, y LinkedIn de linkedin.com):
    [ValidacionEnlaces](../../../src/main/java/com/renaser/ai/ai_engine/perfil/service/ValidacionEnlaces.java).
  - Cómo se agrega un permiso a todas las empresas que ya existen:
    [V63](../../../src/main/resources/db/migration/V63__las_empresas_resenan_a_quien_contrataron.sql).
  - Panel (repositorio `Frontend-EX`): `src/panel/configuracion/Configuracion.tsx` (la página y
    sus secciones) y `src/panel/menu.ts` (cómo el panel lee los permisos de la sesión).
  - Portal (`Frontend-EX`): `src/paginas/vacantes/PanelDeVacante.tsx` (la vacante a la derecha
    de la pantalla partida), `src/paginas/vacantes/Vacante.tsx` (la página de la vacante),
    `src/paginas/vacantes/FilaDeVacante.tsx` (la tarjeta de la lista) y el tipo
    `VacantePublica` en `src/api/tipos.ts`.
  - Un precedente de enlace externo pintado con cuidado (solo `http`/`https`, `noopener
    noreferrer`): `ElEnlaceQuePego` en `src/panel/vacantes/EntregablesDePrueba.tsx`.

## Alcance
- Incluye:
  - Guardar, para cada empresa: logo, foto de portada, sector, tamaño, año de fundación,
    «Quiénes somos», el propósito, los beneficios, el enlace a su video de presentación, su
    página web y sus redes sociales.
  - **Un módulo nuevo del panel, «Perfil de la empresa»**, fuera de Configuración (decisión del
    16/11/2026), para llenarlos con una vista previa en vivo del perfil público.
  - Un permiso nuevo para editarlos.
  - Un bloque nuevo, «Sobre la empresa», en la vacante del portal: en la pantalla partida y en
    la página de la vacante.
  - Que el tablón y el detalle público de la vacante devuelvan esos datos.
  - **El perfil público de la empresa** en el portal (pedido del usuario del 16/11/2026), una
    página propia por empresa con su presentación y todas sus vacantes abiertas, enlazada desde
    cada vacante.
- Queda fuera, y va en specs posteriores:
  - **4b. El brochure** de la empresa, generado automáticamente con plantillas.
  - **4c. La convocatoria redactada por la IA** a partir del propósito.
  - **4d. La entrevista grabada por webcam** y guiada por la IA.
  - **La responsabilidad social empresarial.**
- Queda fuera también:
  - Subir el video a EX. Solo se guarda el enlace a YouTube o Vimeo.
  - Comprobar que el video dura de 2 a 4 minutos, que existe o que es público. Eso pediría
    llamar a YouTube o a Vimeo en cada guardado.
  - La galería de fotos del perfil, que va con el brochure (4b).
  - Mostrar estos datos en la tarjeta de la lista, en «Mis procesos», en los correos o en
    WhatsApp. La tarjeta `FilaDeVacante` sigue con las iniciales aunque la empresa tenga logo;
    el logo pequeño solo aparece junto al nombre de la empresa en la vacante.
  - Que Renaser los llene o los cambie por una empresa cliente desde la plataforma.
  - Guardar versiones o historial del propósito. Cada cambio queda en la auditoría, nada más.

## Comportamiento esperado
1. **Dónde se llena: el módulo «Perfil de la empresa».** Es una entrada propia del menú lateral
   del panel (`src/panel/menu.ts`), que solo ve quien tiene el permiso
   `editar_presentacion_empresa`. Ya no está en Configuración (decisión del 16/11/2026, que
   sustituye a la fila plegable).
   - La página va en dos columnas: el editor a la izquierda y una **vista previa en vivo del
     perfil público** a la derecha, que cambia al escribir. En pantallas estrechas, la vista
     previa se abre con un botón «Vista previa».
   - Tiene un solo «Guardar» y «Ver perfil público ↗».
   - **En el código real:**
     - en `src/panel/menu.ts`, una entrada nueva, clave `'empresa'`, en la familia «/ Selección»
       después de Simulación, condicionada a `tiene('editar_presentacion_empresa')`, con
       `IconoEdificio` de `src/ui/Iconos.tsx`;
     - la ruta `/admin/empresa`;
     - el icono en `ICONOS` y el título en `TITULOS` de `src/panel/Armazon.tsx`.
   - **La subida del logo y la portada copia la del perfil del candidato**
     (`src/paginas/perfil/Cabecera.tsx`, componentes `Foto` y `Portada`, y `archivos.ts`):
     - `revisarImagen` con sus mensajes;
     - `recortarAlCentro`: 512×512 para el logo y una banda de 1600×400 para la portada;
     - los botones `accionFoto` con `entradaOculta` e `IconoCamara`.
     Hay dos diferencias: aquí solo se admiten JPG y PNG (el perfil admite también WebP), y el
     logo lleva radio 8 en vez de círculo.
   - Si algo no vale al guardar: «No se guardó nada. Corrige los campos marcados.».
   - Tras guardar sale «Guardado. Tu perfil y tus vacantes publicadas ya lo muestran.».
2. **Los campos.** Todos son opcionales, con su etiqueta y sin texto de ayuda debajo. Van en tres
   secciones:
   - **Identidad:**
     - **Logo**: imagen JPG o PNG de hasta 2 MB, al menos de 200×200, que se muestra cuadrada.
       Sin logo, se usan las iniciales.
     - **Foto de portada**: JPG o PNG de hasta 2 MB, al menos de 1200×300, que se muestra
       recortada a lo ancho.
     - Las dos se pueden cambiar y quitar. Al subirlas se vuelven a codificar en el servidor,
       para quitar los metadatos (GPS, cámara).
     - **Nombre**: solo lectura.
     - **Sector**: texto corto de hasta 60.
     - **Tamaño**: uno de 1–10, 11–50, 51–200, 201–500, 501–1000 y «Más de 1000»
       colaboradores.
     - **Año de fundación**: entre 1800 y el año actual.
     - **Sedes**: solo lectura, las de Configuración → Sedes con su provincia, y el enlace
       «Gestionar sedes».
   - **Presentación:**
     - «Quiénes somos»: hasta 1500 caracteres, con contador «N / 1500».
     - «Nuestro propósito»: texto de varias líneas, hasta 1500 caracteres, con el contador
       «N / 1500» a la vista.
     - Los **beneficios**: chips que se añaden y se quitan, hasta 12, de 40 caracteres cada uno.
   - **Video y enlaces:**
     - «Video de presentación»: un enlace de YouTube o de Vimeo.
     - «Página web».
     - «Facebook», «Instagram», «LinkedIn», «TikTok» y «YouTube»: un enlace para cada red.
3. **Guardar.** «Guardar» envía todo junto y «Dejarlo» descarta los cambios. Los espacios de
   los bordes se quitan. Un campo vacío se guarda como «sin dato». Si un enlace no lleva
   `http://` ni `https://`, el panel le pone `https://` delante antes de comprobarlo
   («renaser.pe» se guarda como «https://renaser.pe»). Al guardar, la sección vuelve a mostrar
   lo guardado y el cambio queda en la auditoría con quién, cuándo y el valor anterior.
4. **Enlaces que no valen.** Cada campo con un error lo dice junto al campo y no se guarda
   nada:
   - Un enlace que no es `http` ni `https` (`javascript:`, `data:`, `ftp:`…), que no tiene
     dominio, que lleva usuario y contraseña delante del dominio, o que pasa de 500 caracteres:
     «Escribe una dirección web que empiece por https://».
   - Un video que no es de YouTube ni de Vimeo, o del que no se puede sacar cuál es el video:
     «Pega el enlace de un video de YouTube o de Vimeo».
   - Una red que no es de su dominio (por ejemplo, un enlace de Facebook en «Instagram»):
     «Este enlace no es de Instagram», con el nombre de la red que toca.
   - Un propósito de más de 1500 caracteres: no se puede escribir el 1501 y, si llega igual
     (pegado o por la API), «El propósito admite hasta 1500 caracteres».
5. **Qué videos valen.** De YouTube: `youtube.com/watch?v=…`, `youtu.be/…`,
   `youtube.com/shorts/…` y `youtube.com/embed/…`, con o sin `www.` o `m.`. De Vimeo:
   `vimeo.com/<número>` y `vimeo.com/<número>/<clave>`, que es el de un video oculto. Se guarda
   el enlace tal como se pegó, y al pintarlo se reconstruye a partir del servicio y del
   identificador del video, nunca copiando el enlace guardado dentro de la página.
6. **Lo que ve el candidato.** En la vacante, **después de toda la información del puesto,
   incluidos los requisitos** (decisión del 16/11/2026), aparece el bloque «Sobre {nombre de la
   empresa}». Primero se lee el puesto y luego quién lo ofrece. En orden:
   - El propósito, con sus saltos de línea.
   - El video, **solo en el teléfono y en pantallas de menos de 900 px**. En PC va en la
     tarjeta lateral fija (ver abajo). Es un recuadro 16:9 con el nombre de la empresa y un
     botón de reproducir. **No se
     carga nada de YouTube ni de Vimeo hasta que la persona lo pulsa.** Al pulsarlo, el
     recuadro se cambia por el reproductor del servicio, sin cookies de seguimiento
     (`youtube-nocookie.com` en YouTube y `dnt=1` en Vimeo), y empieza a reproducirse. Debajo
     queda siempre el enlace «Ver en YouTube» o «Ver en Vimeo», que abre el video en otra
     pestaña.
   - La página web y las redes, como enlaces con el nombre: «Página web», «Facebook»,
     «Instagram», «LinkedIn», «TikTok», «YouTube». Abren en otra pestaña.
   - **En PC (900 px o más), el video va en la tarjeta lateral fija de la vacante**, la del
     botón «Postular» (decisión del 16/11/2026), para que se note desde el principio.
     - Va **arriba de todo dentro de la misma `.tarjeta`**, encima de «Remuneración»
       (decisión del 16/11/2026): primero el título «Conoce a {empresa}», en el estilo de
       etiqueta de esa tarjeta, luego el recuadro 16:9 al ancho de la tarjeta y «Ver en
       YouTube». Debajo, el filete `#e6e7e4` y la tarjeta de siempre: Remuneración, Modalidad,
       Ciudad, Horario y «Postular a este puesto».
     - Video y tarjeta caben juntos en una pantalla de 1440×900 cuando la tarjeta queda fija
       (unos 703 px de 815 disponibles).
     - Funciona igual: no carga nada hasta que se pulsa, y debajo va «Ver en YouTube» o «Ver en
       Vimeo».
     - En PC el bloque «Sobre…» no repite el video.
     - Si la empresa no tiene video, la tarjeta queda como hoy.
     - Como la tarjeta es fija, el video queda a la vista al bajar por la vacante.
7. **Sin datos no se pinta nada.** Si la empresa no llenó nada, el bloque no aparece y la
   vacante se ve como hoy. Si llenó solo una parte, se pinta solo lo que tiene; por ejemplo,
   solo la página web y una red, sin título de propósito vacío ni recuadro de video.
7a. **Dos videos: el de la empresa y, si se quiere, uno por vacante** (pedido del usuario del
   16/11/2026). La empresa elige si usa un solo video para todo o uno por puesto.
   - **El de la empresa** es el del módulo «Perfil de la empresa», para conocerla. Sale siempre
     en el perfil (paso 7c).
   - **El del puesto** es un campo opcional nuevo, **«Video del puesto»**, en el formulario de
     crear y editar vacante del panel, con las mismas reglas y errores que el de la empresa.
     Cuando está vacío y la empresa tiene video, el campo indica «Se usará el video de la
     empresa», como chip o dentro del campo y nunca como texto de ayuda debajo.
   - **En la vacante del portal:**
     - **sin video del puesto:** el de la empresa va en la tarjeta lateral («Conoce a
       {empresa}») y no se repite abajo, como en los pasos 6 y 7;
     - **con video del puesto:** el del puesto va en la tarjeta lateral («Conoce el puesto») y el
       de la empresa sale en el bloque «Sobre {empresa}» del final («Conoce a {empresa}»). En el
       teléfono, el del puesto va arriba en la ficha, antes de la información, y el de la
       empresa en el bloque «Sobre…»;
     - sin ningún video, no sale ningún recuadro.
   - El video del puesto **se puede cambiar con la vacante publicada**: es una corrección normal
     y no avisa a los candidatos.
   - Los dos cargan igual: nada hasta que se pulsa, sin cookies de seguimiento y con «Ver en
     YouTube» o «Ver en Vimeo».
7b. **Sin la etiqueta «Vacante / Ficha del puesto».** La ficha de la vacante del portal deja de
   llevar esa etiqueta sobre el titular (decisión del 16/11/2026).
   - Se quita el atributo `data-seccion="Vacante / Ficha del puesto"` del `.encabezado` en
     Frontend-EX `src/paginas/vacantes/Vacante.tsx`.
   - La regla global `[data-seccion]::before` de `src/estilos/mundo.css` se queda, porque la
     usan otras pantallas.
   - En `DESIGN.md`, «La regla de la etiqueta de sección», se quita la ficha de una vacante de la
     lista de pantallas que la llevan.
7c. **El perfil público de la empresa**, del estilo de los perfiles de empresa de los portales
   de empleo grandes (pedido del 16/11/2026). Cada empresa activa tiene una página en el portal,
   `/empresas/<código>`, con el código de la empresa en minúsculas (por ejemplo
   `/empresas/molinos-del-sur`). Se puede abrir sin cuenta, igual que el tablón.
   - **Cabecera:**
     - la foto de portada a todo lo ancho;
     - el logo superpuesto a la izquierda; sin logo, `Iniciales` en tamaño `grande` con el color
       de `tonoDe`;
     - el nombre como titular;
     - «Sector · Tamaño · Fundada en AAAA», solo con lo que haya;
     - el nombre de la provincia de la **primera sede activa**, la más antigua con
       `provincia_ubigeo`, tal como lo guarda `ubigeo` (por ejemplo, «San Román (Juliaca)»);
     - a la derecha, «Ver vacantes (N)», que baja a la lista, y «Visitar web».
     - Arriba, «← Volver a las vacantes».
   - **Columna principal**, en este orden:
     - «Quiénes somos»;
     - «Nuestro propósito»;
     - el **video**, grande, con las reglas del paso 6;
     - **«Beneficios»**, como chips con check;
     - **«Vacantes abiertas (N)»**: todas sus vacantes publicadas y abiertas, con la tarjeta real
       `FilaDeVacante` de `src/paginas/vacantes/FilaDeVacante.tsx`, en una rejilla
       `auto-fill minmax(20rem, 1fr)`. Cada una lleva a su vacante y, dentro del perfil, no
       repite el nombre de la empresa.
   - **Ficha lateral fija, «Datos de la empresa»:** sector, tamaño, año de fundación, sedes (sus
     provincias), la web y las redes como chips con su icono, que abren en otra pestaña.
   - **En el teléfono**, todo en una columna: portada, logo y nombre, la ficha y después lo
     demás.
   - **La cabecera copia la del perfil del candidato:** `.portada`, `.identidad`, `.zonaFoto` y
     `.disco` de `src/paginas/perfil/Cabecera.module.css`, con la portada de 168 px (120 px a
     sangre por debajo de 700) y el logo de 128 px. El cuerpo en dos columnas copia
     `Perfil.module.css` (`.cuerpo` con columna de 20rem) y `Lateral.module.css`.
   Comportamiento:
   - Sin datos de presentación, el perfil sale igual con el nombre y las vacantes, sin bloques
     vacíos.
   - Sin vacantes abiertas dice «Ahora no tiene vacantes abiertas».
   - Una empresa inactiva, o un código que no existe, da la página de «no encontrada» del
     portal.
   **Enlaces al perfil:**
   - en la vacante: el nombre de la empresa bajo el titular; «Ver perfil de {empresa} →» en la
     tarjeta lateral bajo el video, en PC; y el mismo enlace al cerrar el bloque «Sobre…», en PC
     y en el teléfono;
   - en el panel: «Ver perfil público ↗» en el módulo «Perfil de la empresa».
   **Frontend:** la ruta `/empresas/:codigo` se añade a `PUBLICAS_CON_CONTENIDO`, a `TITULOS` y
   a `patrones` en `src/rutas`, para que lleve la cabecera y el pie de columnas del portal
   (`PieEnColumnas` de `src/app/Armazon.tsx`) también sin cuenta.
   **Backend:** un endpoint público nuevo, `GET /api/v1/portal/empresas/{codigo}`, sin token
   como el tablón. Devuelve el nombre, la ciudad, la ficha, la presentación, los beneficios, los
   enlaces del logo y la portada, y la lista de vacantes
   abiertas con los mismos campos de la tarjeta del tablón. Solo sale lo público: nada de
   pretensión, candidatos ni datos internos.
   **Imágenes públicas:** el logo y la portada son los primeros archivos de EX que se ven sin
   cuenta. Se sirven por `GET /api/v1/portal/empresas/{codigo}/imagenes/{logo|portada}`, que
   lee el archivo del almacén y lo entrega con caché pública de 1 día. Nunca se expone un
   enlace al almacén privado ni a otros archivos.
8. **Vacantes ya publicadas.** Los datos son de la empresa, no de cada vacante. En cuanto la
   empresa guarda, todas sus vacantes publicadas los muestran al recargar, sin volver a
   publicarlas. Si cambia el propósito, todas pasan a mostrar el nuevo. No se guarda una copia
   en cada postulación.
9. **Cada empresa, lo suyo.** La sección del panel lee y guarda siempre la empresa de quien ha
   entrado. La dirección no lleva el identificador de la empresa, así que no hay forma de
   pedir la de otra. La plataforma tampoco edita la de una empresa cliente desde aquí.
10. **Sin permiso.** Quien no tiene el permiso no ve la sección. Si llama a la API
    directamente, recibe un 403 y no se guarda nada.

## Referencia visual
- Maqueta o Artifact: https://claude.ai/artifact/QSjigLv9jtQuYJPfFDu3RG. Los datos son
  inventados: Molinos del Sur y Ferretería Los Andes.
- Versión aprobada: pendiente de que el usuario la revise.
- **Copia local**, que manda si el enlace cambia o pide sesión, en [maqueta/](maqueta):
  - Panel, módulo «Perfil de la empresa»:
    - [panel-con-datos.html](maqueta/panel-con-datos.html): interactiva. A la izquierda el editor
      (Identidad, Presentación, Video y enlaces) y a la derecha la vista previa en vivo. Por
      debajo de 1280 px, la vista previa se abre con el botón «Vista previa» en un cajón lateral
      (`cajaLateral` de `Modal`);
    - [panel-vacio.html](maqueta/panel-vacio.html): el módulo vacío la primera vez.
  - Portal, la vacante:
    - [portal-vacante.html](maqueta/portal-vacante.html): interactiva. El video va arriba
      de la tarjeta lateral, encima de «Remuneración», y no carga nada hasta pulsar; sin la
      etiqueta «Vacante / Ficha del puesto»; el bloque «Sobre…» va
      después de los requisitos;
    - [portal-movil.html](maqueta/portal-movil.html): 390×844;
    - [portal-parcial.html](maqueta/portal-parcial.html): una empresa que solo llenó el
      propósito y la web;
    - [portal-perfil.html](maqueta/portal-perfil.html): interactiva, el perfil
      `/empresas/molinos-del-sur` con el video grande, el propósito, los chips y «Vacantes
      abiertas (3)»;
    - [portal-perfil-movil.html](maqueta/portal-perfil-movil.html): el perfil a 390×844.
  - Panel, el video del puesto:
    - [panel-vacante-video.html](maqueta/panel-vacante-video.html): interactiva. Es el modal
      real «Editar vacante» (`EditarVacante.tsx` con el `Modal` de `src/ui/Modal.tsx`).
      - El campo **«Video del puesto (opcional)»** va a todo el ancho, después de «Lo que se
        busca» y antes de Modalidad y Ciudad. Es un `Campo` con `ancho` de
        `CamposDeLaVacante.tsx`, con los estilos de `Vacantes.module.css`.
      - Vacío, lleva a la derecha de la etiqueta el chip «Se usará el video de la empresa», con
        `.marca` + `.viva` de `Areas.module.css`.
      - El error usa el mismo patrón del aviso del formulario («Corrige lo marcado en «Video del
        puesto».») y el borde rojo de `Campo.module.css`.
  - La vacante del portal ([portal-vacante.html](maqueta/portal-vacante.html) y
    [portal-movil.html](maqueta/portal-movil.html)) trae un conmutador **solo de maqueta**,
    punteado, para ver el caso con video del puesto y el caso sin él. No se implementa.
- **La maqueta reutiliza el frontend real (repositorio Frontend-EX)**; quien implemente usa
  estos mismos componentes y estilos, sin improvisar:
  - Tokens Northstar de `src/estilos/mundo.css`. El panel y el portal los usan desde el
    14/11/2026.
  - Panel:
    - barra y menú de `src/panel/Armazon.tsx` y `Armazon.module.css`, con las entradas de
      `src/panel/menu.ts` y los iconos de `src/ui/Iconos.tsx`;
    - página y sección de `src/panel/configuracion/Configuracion.tsx` y
      `Configuracion.module.css` (`.pagina`, `.seccion`, `.tituloSeccion`, `.subtitulo`,
      `.avisoBien`);
    - botones «Guardar», «Editar» y «Dejarlo» como en `Sedes.tsx` y `Areas.module.css`
      (`.guardar` sobre `acentoMenor` de `src/estilos/piezas.module.css`, y `.chico`);
    - las filas de sedes de solo lectura con las clases de `Areas.module.css` (`.fila`,
      `.nombre`, `.chico`), como en `Sedes.tsx`;
    - el chip «Se usará el video de la empresa» con `.marca` + `.viva` de `Areas.module.css`;
    - los campos con `Campo` y `AreaTexto` de `src/ui/campos/Campo.tsx`;
    - el bloque blanco del formulario con `.bloque` de `src/estilos/pagina.module.css`.
  - Portal:
    - cabecera de `src/app/Armazon.tsx`;
    - la vacante de `src/paginas/vacantes/Vacante.tsx` y `Vacante.module.css` (`.bloque`,
      `.tituloBloque`, `.texto`, `.requisitos`, `.accion` fija en el teléfono);
    - el patrón de enlace externo `target="_blank" rel="noopener noreferrer"`.
  - **Lo único nuevo**, que copia la maqueta:
    - la rejilla de los seis enlaces del formulario;
    - el bloque «Sobre {empresa}» (filete superior, 48 px de margen y 32 px de relleno, porque va
      tras el recuadro de requisitos);
    - el recuadro del video en fondo noche con el nombre en Syne y el botón de reproducir;
    - los chips de enlace de 44 px;
    - los iconos de Facebook, Instagram, TikTok y YouTube con el trazo de 1,5 de `Iconos.tsx`.
  - **Dos diferencias deliberadas con lo que ya existe:**
    - el contador del propósito está siempre a la vista y dice «N / 1500», mientras que el
      `Cuenta` compartido solo aparece pasado el 80 % y dice «N de M caracteres»;
    - el mensaje de guardado es «Guardado. Tus vacantes publicadas ya lo muestran.»
  - **En el teléfono, revisar que la última fila de chips no quede tapada** por la barra fija de
    «Postular» (`.accion`): el bloque necesita margen inferior suficiente.
- Pantallas y estados relevantes:
  - Panel, la sección nueva: vacía, con datos, editando, con errores en varios campos a la vez y
    con un propósito de 1500 caracteres.
  - Portal, el bloque «Sobre…»: completo; solo con propósito; solo con enlaces; con el video
    antes y después de pulsar; y con un propósito largo con saltos de línea.
- Escritorio y móvil:
  - Panel a 1280×800.
  - Portal en la pantalla partida a 1280×800 y en la página de la vacante a 390×844. En el
    teléfono el video ocupa el ancho del bloque y los enlaces pasan a la línea siguiente cuando
    no caben, sin desplazamiento horizontal.
- Diseño existente: [DESIGN.md](../../../../Frontend-EX/DESIGN.md) del portal. En el panel, el
  mismo patrón de bloques, campos y «Guardar» que las secciones de Configuración, en un módulo
  propio con vista previa. En el portal, el mismo `Bloque` con título que «Lo que harás».
- Fidelidad requerida: el orden del paso 6, el recuadro del video que no carga nada hasta
  pulsarlo, los enlaces con su nombre y ningún texto de ayuda bajo los campos del panel. Puede
  adaptarse: el icono del botón de reproducir, si los enlaces van como chips o como lista, y
  los espaciados.

Los textos y reglas de esta spec prevalecen sobre la maqueta. Respetar el diseño existente
salvo los cambios visuales aquí definidos.

## Criterios de aceptación
- AC-01: Dado un usuario con el permiso y una empresa sin datos, cuando abre «Perfil de la
  empresa» en el menú, entonces ve el editor con los campos vacíos y la vista previa con solo
  las iniciales, el nombre y sus vacantes.
- AC-02: Dado ese usuario, cuando escribe un propósito, pega
  `https://www.youtube.com/watch?v=abc123DEF45`, escribe «renaser.pe» en «Página web» y
  `https://www.instagram.com/renaser` en «Instagram» y pulsa «Guardar», entonces la sección
  muestra lo guardado, con la página web como «https://renaser.pe», y la auditoría registra el
  cambio.
- AC-03: Cuando escribe `javascript:alert(1)` en «Página web», entonces aparece «Escribe una
  dirección web que empiece por https://» junto al campo y no se guarda ningún campo.
- AC-04: Cuando pega `https://www.facebook.com/renaser/videos/123` en «Video de presentación»,
  entonces aparece «Pega el enlace de un video de YouTube o de Vimeo».
- AC-05: Cuando pega `https://www.facebook.com/renaser` en «Instagram», entonces aparece «Este
  enlace no es de Instagram».
- AC-06: Cuando el propósito tiene 1500 caracteres, entonces el contador marca «1500 / 1500» y
  no se escribe uno más. Una petición directa con 1501 recibe un 400 con «El propósito admite
  hasta 1500 caracteres».
- AC-07: Dado un usuario sin el permiso (por ejemplo, Responsable del área), cuando mira el menú
  del panel, entonces no ve «Perfil de la empresa», y abrir `/admin/empresa` directamente no le
  muestra el editor. Si llama directamente a guardar, recibe un 403 y no cambia nada.
- AC-08: Dadas dos empresas A y B con datos distintos, cuando un usuario de A guarda, entonces
  solo cambian los de A, y las vacantes de B siguen mostrando los de B.
- AC-09: Dada una vacante publicada antes del cambio de una empresa que luego guardó su
  propósito, cuando un candidato sin cuenta abre esa vacante, entonces ve «Sobre {empresa}»
  con el propósito, sin que nadie haya vuelto a publicar la vacante.
- AC-10: Dada una empresa con video, cuando el candidato abre su vacante, entonces el navegador
  no pide nada a YouTube ni a Vimeo. Al pulsar reproducir, se carga el reproductor de
  `youtube-nocookie.com` y empieza el video. El enlace «Ver en YouTube» abre el video en otra
  pestaña.
- AC-11: Dada una empresa con un video de Vimeo oculto (`https://vimeo.com/123456789/abcdef1234`),
  cuando el candidato pulsa reproducir, entonces se reproduce en el reproductor de Vimeo.
- AC-12: Dada una empresa sin ningún dato, cuando el candidato abre su vacante, entonces no hay
  bloque «Sobre…» y el resto de la vacante se ve igual que antes.
- AC-13: Dada una empresa con solo la página web, cuando el candidato abre su vacante, entonces
  el bloque «Sobre {empresa}» muestra solo el enlace «Página web», sin propósito ni recuadro de
  video.
- AC-14: Dada una vacante cuyo propio propósito es «Reducir los tiempos de despacho», de una
  empresa con propósito «Que ninguna familia se quede sin…», cuando el candidato la abre,
  entonces ve los dos textos, cada uno en su bloque: el de la empresa en «Sobre {empresa}» y el
  del puesto en «El resultado que esperamos».
- AC-15: `GET /api/v1/portal/vacantes` y `GET /api/v1/portal/vacantes/{id}`, sin token,
  devuelven en cada vacante los datos de su empresa, y una empresa sin datos sale sin ellos.
  Los campos que ya existían no cambian.
- AC-16: Dado un candidato en el teléfono (390×844), cuando abre la página de una vacante con
  todos los datos, entonces el bloque cabe a lo ancho, el video ocupa el ancho del bloque en
  16:9 y no hay desplazamiento horizontal.
- AC-17: Dada la empresa «Molinos del Sur» con presentación y 3 vacantes publicadas, cuando
  alguien sin cuenta abre `/empresas/molinos-del-sur`, entonces ve el nombre, el video (sin
  pedir nada a YouTube hasta pulsar), «Nuestro propósito», la web y las redes, y «Vacantes
  abiertas (3)» con tarjetas que llevan a cada vacante.
- AC-18: Dada una vacante de esa empresa, cuando el candidato pulsa el nombre de la empresa o
  «Ver perfil de Molinos del Sur →», entonces llega a su perfil.
- AC-19: Dada una empresa sin presentación, cuando se abre su perfil, entonces sale solo el
  nombre con sus vacantes, sin bloques vacíos. Sin vacantes abiertas, dice «Ahora no tiene
  vacantes abiertas».
- AC-20: Dado un código que no existe o una empresa inactiva, cuando se abre
  `/empresas/<código>`, entonces sale la página de «no encontrada», y
  `GET /api/v1/portal/empresas/{codigo}` responde 404.
- AC-21: Dada la respuesta de `GET /api/v1/portal/empresas/{codigo}`, cuando se revisa,
  entonces solo trae datos públicos de la empresa y de sus vacantes abiertas: ni candidatos, ni
  pretensiones, ni vacantes cerradas o archivadas.

- AC-25: Dado un usuario de Talento, cuando abre el menú del panel, entonces ve «Perfil de la
  empresa» y ya no hay nada de esto en Configuración. Al escribir en «Quiénes somos», la vista
  previa cambia en vivo.
- AC-26: Dado un logo PNG de 1 MB y 400×400 con datos GPS, cuando se sube, entonces se guarda sin
  esos metadatos y sale en el perfil y en la vista previa. Un archivo de 3 MB, uno de 100×100 o
  uno que no es imagen se rechaza con su motivo.
- AC-27: Dado un perfil con portada, logo, ficha, beneficios y 3 vacantes, cuando alguien sin
  cuenta lo abre en PC, entonces ve la portada con el logo, «Sector · Tamaño · Fundada en AAAA»,
  la ficha lateral, los beneficios como chips y las vacantes. A 390×844 todo va en una columna y
  sin desplazamiento horizontal.
- AC-28: Dada `GET /api/v1/portal/empresas/{codigo}/imagenes/logo`, cuando se pide sin token,
  entonces devuelve la imagen con caché pública. Con un código de otra empresa o un nombre que no
  es `logo` ni `portada`, responde 404 y no entrega ningún otro archivo.
- AC-22: Dada una vacante con «Video del puesto» y una empresa con video, cuando el candidato
  abre la vacante en PC, entonces la tarjeta lateral muestra el del puesto con «Conoce el
  puesto», y el bloque «Sobre…» muestra el de la empresa con «Conoce a {empresa}». Ninguno pide
  nada a YouTube o Vimeo hasta pulsarlo.
- AC-23: Dada una vacante sin video del puesto, cuando se abre, entonces la tarjeta lateral
  muestra el de la empresa y el bloque «Sobre…» no lo repite. En el panel, su campo «Video del
  puesto» indica «Se usará el video de la empresa».
- AC-24: Dado un enlace de Facebook en «Video del puesto», cuando se guarda la vacante, entonces
  sale el mismo error que en el video de la empresa y no se guarda.

## Casos límite y errores
- Datos vacíos o inválidos:
  - Un campo con solo espacios se guarda vacío.
  - Un propósito vacío con video y enlaces es válido: el bloque sale sin el texto.
  - Un enlace con espacios en medio o que no se puede leer como dirección da el error del paso 4.
  - El propósito se pinta como texto, nunca como HTML. Si alguien escribe etiquetas, se ven
    tal cual.
- Usuario sin permisos: paso 10. El permiso solo sirve dentro de la propia empresa, como todos.
- Recarga, reintento o doble envío:
  - «Guardar» se desactiva mientras se envía.
  - Dos guardados seguidos con lo mismo dejan lo mismo.
  - Si dos personas editan a la vez, gana el último guardado, como en los parámetros. Cada uno
    queda en la auditoría.
  - Si el guardado falla por la red, el formulario conserva lo escrito y dice «No se pudo
    guardar.».
- Otros casos relevantes:
  - **Video privado, borrado o que no se deja incrustar.** EX no lo sabe. Al pulsar, el propio
    reproductor de YouTube o Vimeo dice que no está disponible, y el enlace «Ver en…» sigue
    debajo. No se intenta adivinar.
  - **La app de Android.** El reproductor incrustado puede fallar dentro de la app, porque
    YouTube rechaza algunos orígenes. Si falla, el enlace «Ver en YouTube» abre el video
    fuera. QA lo comprueba (ver «Verificación»).
  - **La política de seguridad de contenidos.** Hoy el portal no declara ninguna (ni en
    `vercel.json` ni en `index.html`), así que el reproductor carga sin tocar nada. Si algún
    día se declara, debe permitir en `frame-src` solo `https://www.youtube-nocookie.com` y
    `https://player.vimeo.com`. El reproductor no puede llevar `referrerpolicy="no-referrer"`:
    YouTube exige saber desde qué página se reproduce y sin eso muestra un error. Se usa
    `strict-origin-when-cross-origin`. El iframe solo lleva los permisos que necesita
    (reproducción automática, pantalla completa, imagen dentro de imagen).
  - **Enlaces externos.** Todos con `target="_blank"` y `rel="noopener noreferrer"`. Al
    pintarlos se vuelve a comprobar que sean `http` o `https`, aunque el backend ya lo haya
    exigido. Si alguno no lo es, no se pinta.
  - **Empresa suspendida o vacante eliminada.** Sus vacantes ya no salen en el tablón y, por
    tanto, sus datos tampoco.
  - **Una empresa nueva** nace sin datos y sin bloque en el portal hasta que los llene.
  - **El nombre de la empresa en el título.** Un nombre largo se parte en varias líneas, sin
    cortarse.

## Datos y compatibilidad
- Información que debe guardarse (migración **V81**):
  - En `organizacion`: `proposito_empresa` (texto, hasta 1500, con `CHECK`), `video_presentacion`
    y `pagina_web` (texto, hasta 500). Todos pueden ir vacíos. El nombre `proposito_empresa` es
    a propósito, para que no se confunda con `vacante.proposito`.
  - También en `organizacion`:
    - `quienes_somos` (texto, hasta 1500);
    - `sector` (texto, hasta 60);
    - `tamano` (`CHECK` con los 6 rangos);
    - `anio_fundacion` (entero, entre 1800 y el año actual);
    - `logo_archivo_id` y `portada_archivo_id` (referencias a `archivo`).
  - Una tabla `organizacion_beneficio` (organización, texto de hasta 40 y orden), con 12 como
    mucho por empresa.
  - En `vacante`: `video_presentacion` (texto, opcional), el video propio del puesto, con las
    mismas reglas de YouTube y Vimeo.
  - Una tabla `organizacion_red_social` (organización, red y enlace), con la red limitada por
    `CHECK` a `FACEBOOK`, `INSTAGRAM`, `LINKEDIN`, `TIKTOK` y `YOUTUBE`, y una sola fila por
    empresa y red. Una red vacía es una fila que no existe.
  - El permiso `editar_presentacion_empresa` (grupo `CONFIGURACION`), concedido con alcance
    `TODO` a Dirección, Administrador y Talento (decisión del 16/11/2026) **en todas las
    organizaciones que ya existen**, con el
    mismo patrón `NOT EXISTS` de la V63. Las empresas que se den de alta después lo reciben con
    la copia de los roles de la plataforma.
- Contratos:
  - Panel: `GET` y `PUT /api/v1/panel/organizacion/presentacion`, de la empresa de quien ha
    entrado, con el permiso nuevo.
  - Portal: `VacantePublica` suma un objeto `empresa` con `proposito`, `video` (servicio,
    identificador y enlace original), `paginaWeb` y `redes` (lista de red y enlace). Va a nulo
    si la empresa no tiene ningún dato. No se toca `nombreEmpresa` ni el `proposito` de la
    vacante.
- Comportamiento para registros existentes: todas las empresas quedan sin datos. Ninguna
  vacante cambia hasta que su empresa guarda los suyos.
- Restricciones de compatibilidad:
  - El tablón es la pantalla más pedida y se sirve sin cuenta. Las redes de todas las empresas
    del lote se cargan en una sola consulta, como ya pasa con los requisitos y las empresas, y
    nunca una consulta por vacante.
  - El objeto `empresa` se repite en cada vacante de la misma empresa. Con 1500 caracteres como
    mucho, se acepta antes que obligar al portal a hacer una segunda llamada.
  - En el portal, el campo nuevo es opcional en el tipo: un backend anterior no lo manda y la
    vacante se pinta como hoy.
  - El documento de roles y permisos suma la fila del permiso nuevo («Editar el perfil de la
    empresa»).

## Verificación
- Actor/rol y datos de prueba necesarios:
  - Dos empresas activas, A y B, cada una con una vacante publicada.
  - En A, un usuario de Talento, uno de Dirección o Administrador y uno de Responsable del área,
    que no tiene el permiso.
  - Una tercera empresa sin datos, con una vacante publicada.
  - Enlaces de prueba: un video de YouTube, uno de Shorts, uno de Vimeo oculto, uno de Facebook
    (inválido como video), `javascript:alert(1)` y un propósito de 1500 caracteres con saltos
    de línea.
- Recorridos que deben quedar como E2E repetibles en headless:
  - Panel: llenar y guardar todos los campos; los errores de enlace, de video y de red; la
    sección oculta para Responsable del área.
  - Portal: la vacante con todos los datos en la pantalla partida y en la página; la vacante
    de una empresa sin datos, sin bloque; el video que no pide nada hasta pulsarlo (interceptar
    las peticiones a YouTube y Vimeo).
  - Backend: pruebas de integración del 403 sin permiso, de que A no toca lo de B, de las
    reglas de enlaces y del tablón con y sin datos, sin consultas por vacante.
- Riesgos que QA debe explorar con Chrome visible:
  - El video al pulsar, en YouTube y en Vimeo, y la vuelta atrás.
  - Un video privado o borrado.
  - El bloque a 390×844 y a 1280×800, con datos largos.
  - Pegar enlaces sin `https://` y con espacios.
  - El cambio de vacante en la pantalla partida con el video sonando: al elegir otra vacante el
    video se detiene.
  - Si se puede, el video dentro de la app de Android.
- Comparación visual de QA: cuando haya maqueta aprobada, el bloque «Sobre…» y la sección del
  panel en los estados de «Referencia visual».
- Resultado que quiero comprobar personalmente: **lo visual lo compruebo yo, porque el panel
  tiene login.** Quiero entrar al panel, llenar el propósito, el video y las redes de mi
  empresa, abrir una de sus vacantes en el portal sin cuenta y ver el propósito y el video
  sin salir de la vacante.

## Dependencias y dudas
- Requiere:
  - **Reservar la V81.** La última migración en `main` es la V80, y puede haber otra rama en
    paralelo que reclame el mismo número. Hay que confirmarlo antes de empezar. La spec hermana
    del informe PDF no lleva migración.
  - Nada más: la regla de enlaces (RF-166), la subida de imágenes del perfil del candidato y los
    patrones del panel ya existen.
- Decisiones pendientes: ninguna.
  - Decidido el 16/11/2026: editan Talento, Dirección y Administrador.
  - Decidido el 16/11/2026: el bloque «Sobre…» va después de toda la información del puesto,
    incluidos los requisitos.
  - Decidido el 16/11/2026: hay maqueta, fiel al diseño actual del panel y del portal (ver
    «Referencia visual»).
  - Decidido al redactar (se puede tachar): solo videos de YouTube y Vimeo, incrustados, con un
    recuadro que no carga nada hasta que se pulsa y sin cookies de seguimiento. Facebook,
    TikTok o Google Drive no se aceptan como video.
  - Decidido al redactar (se puede tachar): las redes son cinco fijas (Facebook, Instagram,
    LinkedIn, TikTok y YouTube), cada una comprobada contra su dominio. X no entra.
  - Decidido al redactar (se puede tachar): el propósito admite hasta 1500 caracteres y no tiene
    mínimo.
  - Decidido al redactar: un permiso nuevo, `editar_presentacion_empresa`. Desde el 16/11/2026
    lo tienen Talento, Dirección y Administrador.
  - Decidido el 16/11/2026: el módulo se llama «Perfil de la empresa» y va en el menú. El bloque
    del portal se titula «Sobre {nombre}».
  - Decidido al redactar (se puede tachar): los datos viajan dentro de cada vacante del tablón
    y no por una llamada aparte, y no se guarda copia en las postulaciones.
  - Decidido al redactar (se puede tachar): la tarjeta de la lista no cambia.
