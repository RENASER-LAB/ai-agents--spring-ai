# El brochure de la empresa, armado solo

Estado: lista para implementar

## Objetivo
Que cada empresa cliente tenga un **brochure de presentación en PDF** sin tener que diseñarlo.
Es la pieza 4b de la reunión con la clienta del 13/11/2026: «que cada empresa nos deje la data
de misión, visión… y automáticamente tener diferentes modelos tipo brochure que arman nuestros
sistemas», «porque no todas las empresas tienen página web».

La pieza 4a ([El propósito de la empresa](../../Done/proposito-de-la-empresa/spec.md), PR #139)
ya está en producción con la parte web: el módulo del panel «Perfil de la empresa» y el perfil
público `/empresas/<código>`. Esta spec es la **versión corta** del brochure (elegida por el
usuario el 17/11/2026). Solo añade:
- misión y visión, dos textos opcionales;
- el **brochure en PDF**, armado con lo que la empresa ya llenó en la 4a, en una de tres
  plantillas y uno de seis colores;
- el botón «Descargar brochure (PDF)» en el módulo del panel y en el perfil público.

**La web es el perfil público de la 4a; el brochure es su versión en PDF**, siempre al día con
lo guardado. No hay que publicarlo ni hay una página web aparte.

## Contexto
- Comportamiento actual (la 4a, fusionada en el PR #139 con la migración V85):
  - **Lo que la empresa cuenta de sí misma.** Quien tiene el permiso
    `editar_presentacion_empresa` (Talento, Dirección y Administrador) entra al módulo «Perfil de
    la empresa» del menú del panel (familia «/ Selección», ruta `/admin/empresa`). La página va
    en dos columnas: el editor a la izquierda y la vista previa en vivo del perfil público a la
    derecha. Por debajo de 1280 px, la vista previa se abre con el botón «Vista previa». El
    editor tiene tres secciones seguidas, sin índice ni anclas, y un solo «Guardar»:
    - **Identidad:** logo, foto de portada, nombre (solo lectura), sector, tamaño, año de
      fundación y las sedes (solo lectura, con su provincia y el enlace «Gestionar sedes»);
    - **Presentación:** «Quiénes somos» y «Nuestro propósito» (hasta 1500 caracteres cada uno,
      con el contador «N / 1500» siempre a la vista) y los beneficios (chips, hasta 12, de 40
      caracteres);
    - **Video y enlaces:** el video de presentación, la página web y cinco redes.
  - Tras guardar sale «Guardado. Tu perfil y tus vacantes publicadas ya lo muestran.». Si algo
    no vale: «No se guardó nada. Corrige los campos marcados.».
  - **El perfil público** `/empresas/<código>` se abre sin cuenta. Lleva la portada con el logo,
    «Sector · Tamaño · Fundada en AAAA», la provincia de la primera sede, «Ver vacantes (N)» y
    «Visitar web»; en la columna principal, «Quiénes somos», «Nuestro propósito», el video,
    «Beneficios» y «Vacantes abiertas (N)»; y la ficha lateral «Datos de la empresa», con las
    sedes, la web y las redes.
  - **Imágenes públicas.** El logo (512×512) y la portada (1600×400) se suben en JPG o PNG de
    hasta 2 MB, ya recortados por el panel y vueltos a codificar por el servidor sin metadatos.
    Viven en el almacén privado y el backend los sirve él mismo por
    `GET /api/v1/portal/empresas/{codigo}/imagenes/{logo|portada}`.
  - **El motor de PDF.** El informe por etapa
    ([informe-pdf-por-etapa](../../Done/informe-pdf-por-etapa/spec.md)) ya genera PDF en el
    propio backend, con PDFBox, sin servicios externos y con las fuentes incrustadas (Onest,
    Syne y la parte latin-ext de Onest). Las piezas comunes están en `comun/pdf`: el lienzo A4
    con coordenadas desde arriba, el texto que pasa de una hoja a otra sin cortarse y los
    enlaces que se pueden pulsar. **Todavía no sabe dibujar imágenes.**
  - **Lo que todavía no hay.** No hay misión ni visión, ni ningún PDF de la empresa.
- Pantallas o procesos afectados:
  - Panel: el módulo «Perfil de la empresa»: misión y visión en «Presentación» y una cuarta
    sección, «Brochure».
  - Portal: el perfil público de la empresa (el bloque «Misión y visión» y el botón «Descargar
    brochure (PDF)»).
- Referencias existentes:
  - Specs de las que depende, ya terminadas:
    [El propósito de la empresa](../../Done/proposito-de-la-empresa/spec.md) (4a), con su
    [maqueta](../../Done/proposito-de-la-empresa/maqueta), e
    [Un informe en PDF por cada etapa](../../Done/informe-pdf-por-etapa/spec.md) (el motor de
    PDF).
  - [Archivos en el bucket](../../../docs/ARCHIVOS-EN-BUCKET.md) y
    [Roles y permisos](../../../docs/04-ROLES-Y-PERMISOS.md).
  - Backend de la 4a, que esta spec amplía:
    - [PresentacionEmpresaController](../../../src/main/java/com/renaser/ai/ai_engine/organizacion/controller/PresentacionEmpresaController.java)
      (`/api/v1/panel/organizacion/presentacion`);
    - [ServicioPresentacionImpl](../../../src/main/java/com/renaser/ai/ai_engine/organizacion/service/impl/ServicioPresentacionImpl.java),
      [ReglasDeLaPresentacion](../../../src/main/java/com/renaser/ai/ai_engine/organizacion/service/ReglasDeLaPresentacion.java)
      y [DtosPresentacion](../../../src/main/java/com/renaser/ai/ai_engine/organizacion/dto/DtosPresentacion.java);
    - el perfil público:
      [EmpresasPortalController](../../../src/main/java/com/renaser/ai/ai_engine/portal/controller/EmpresasPortalController.java),
      [ServicioEmpresasPortalImpl](../../../src/main/java/com/renaser/ai/ai_engine/portal/service/impl/ServicioEmpresasPortalImpl.java)
      y [PresentacionEnElPortal](../../../src/main/java/com/renaser/ai/ai_engine/portal/service/PresentacionEnElPortal.java);
    - la migración
      [V85](../../../src/main/resources/db/migration/V85__el_proposito_de_la_empresa.sql).
  - El almacén:
    [AlmacenArchivos](../../../src/main/java/com/renaser/ai/ai_engine/archivo/service/AlmacenArchivos.java)
    (`leer`, para traer el logo y la portada al PDF).
  - El motor de PDF:
    [LienzoPdf](../../../src/main/java/com/renaser/ai/ai_engine/comun/pdf/LienzoPdf.java),
    [FlujoPdf](../../../src/main/java/com/renaser/ai/ai_engine/comun/pdf/FlujoPdf.java),
    [RenglonesPdf](../../../src/main/java/com/renaser/ai/ai_engine/comun/pdf/RenglonesPdf.java),
    [TipografiaPdf](../../../src/main/java/com/renaser/ai/ai_engine/comun/pdf/TipografiaPdf.java),
    [EstiloPdf](../../../src/main/java/com/renaser/ai/ai_engine/comun/pdf/EstiloPdf.java) y, como
    ejemplo de uso con los colores Northstar,
    [PdfDelInforme](../../../src/main/java/com/renaser/ai/ai_engine/informe/service/PdfDelInforme.java).
  - El patrón de catálogo fijo en código, sin tabla:
    [PortadasDeLaCasa](../../../src/main/java/com/renaser/ai/ai_engine/perfil/service/PortadasDeLaCasa.java).
  - Frontend (repositorio `Frontend-EX`):
    - el módulo: `src/panel/empresa/PerfilDeLaEmpresa.tsx` y `PerfilDeLaEmpresa.module.css`
      (`Contador`, `VistaPrevia`), `formulario.ts` (mensajes y validación) y
      `src/panel/api/presentacion.ts`;
    - el perfil público: `src/paginas/empresas/PerfilDeEmpresa.tsx` y su `.module.css`, y el
      tipo `PerfilDeEmpresa` de `src/api/tipos.ts`;
    - piezas: `AreaTexto` de `src/ui/campos/Campo.tsx`; `.marca` y `.retirada` de
      `src/panel/configuracion/Areas.module.css`; `acentoMenor` y `secundario` de
      `src/estilos/piezas.module.css`; `IconoDescargar` de `src/ui/Iconos.tsx`.
  - Diseño: [DESIGN.md](../../../../Frontend-EX/DESIGN.md) y los tokens Northstar de
    `src/estilos/mundo.css`.

## Alcance
- Incluye:
  - Misión y visión: dos textos opcionales en la sección «Presentación» del módulo, que salen en
    la vista previa, en el perfil público y en el PDF.
  - Una cuarta sección del módulo, **«Brochure»**, para elegir la plantilla y el color, con el
    botón «Descargar brochure (PDF)».
  - **Tres plantillas** («Clásica», «Portada» y «Columnas») y seis colores fijos.
  - El brochure en PDF A4, armado en el backend con el motor del informe por etapa, con los datos
    de la 4a más misión y visión. Siempre al día con lo guardado.
  - El botón «Descargar brochure (PDF)» en el perfil público.
  - Enseñar al motor de PDF a dibujar imágenes, para el logo y la portada.
- Reutiliza de la 4a, sin redefinirlo ni volver a pedirlo: logo, portada, quiénes somos,
  propósito, beneficios, sector, tamaño, año de fundación, sedes, video, página web, redes y el
  permiso.
- Queda fuera (posibles ampliaciones, en otra spec):
  - **Clientes con quienes trabaja la empresa.**
  - **Responsabilidad social.**
  - **Una galería de fotos** de la empresa: subirlas con la casilla del permiso de las personas
    que salen, ordenarlas, quitarlas y verlas en grande en el perfil y en el PDF.
  - **Publicar y retirar** el brochure a mano. En esta versión no hay ese paso: el brochure
    existe en cuanto hay lo mínimo (paso 5) y siempre refleja lo guardado.
  - El enlace al brochure en el bloque «Sobre {empresa}» de la vacante.
  - Más plantillas. Añadir una después no pide tocar a las empresas que ya eligieron.
  - Una plantilla de una sola hoja: obligaría a recortar textos, y el motor sigue la regla del
    informe, nada se recorta nunca.
  - Un código QR en el PDF. El perfil público va como enlace que se puede pulsar, con la
    dirección escrita.
  - «Qué buscamos en las personas».
  - El video dentro del PDF: un PDF no lo reproduce, así que va como enlace.
  - Un color libre o un selector de colores: solo los seis del catálogo.
  - Que Renaser llene el perfil o elija la plantilla por una empresa cliente.
  - Que la empresa edite el diseño: mover bloques, cambiar tipografías o escribir texto libre
    dentro de la plantilla.
  - Traducciones u otros idiomas, y contar cuántas veces se descargó.

## Comportamiento esperado
1. **Misión y visión.** En la sección «Presentación» del módulo, después de «Nuestro propósito»
   y antes de los beneficios, van dos campos nuevos, con el mismo `AreaTexto` y el mismo
   contador siempre a la vista que «Quiénes somos» y «Nuestro propósito»:
   - «Misión»: hasta 400 caracteres, contador «N / 400»;
   - «Visión»: hasta 400 caracteres, contador «N / 400».
   - Son opcionales, sin texto de ayuda debajo, y no dejan escribir más del máximo.
   - Se guardan con el «Guardar» de siempre, con sus mensajes de siempre.
   - **Vacías no salen en ningún sitio**: ni en la vista previa, ni en el perfil público, ni en
     el PDF, y tampoco su título.
   - La vista previa del módulo las muestra al momento, debajo de «Nuestro propósito», con el
     mismo título pequeño en mayúsculas que los otros bloques.
2. **La sección «Brochure».** Es la cuarta sección del módulo, debajo de «Video y enlaces» y
   antes de la fila de «Guardar», con el mismo bloque blanco y el mismo título que las otras
   tres. Lleva, en este orden y sin ninguna línea de ayuda:
   - **«Plantilla del brochure»**: tres tarjetas, cada una con una miniatura de su primera hoja
     y su nombre. La elegida lleva el borde azul, como el consentimiento marcado de los campos
     compartidos. Por defecto, «Clásica».
   - **«Color»**: seis círculos con su nombre accesible. El elegido lleva un anillo azul. Por
     defecto, «Azul».
   - **«Descargar brochure (PDF)»** (botón secundario con el icono de descarga).
   - La plantilla y el color se guardan con el «Guardar» de siempre; «Dejarlo» los descarta con
     lo demás.
   - Nada del brochure va en Configuración.
3. **Las plantillas.**
   - **«Clásica»**: solo tipografía, filetes y el color, y el logo. Es la más sobria y la que
     mejor se imprime en blanco y negro.
   - **«Portada»**: la primera hoja es la foto de portada de la 4a a toda la página, recortada al
     centro, con un velo oscuro, el logo, el nombre y el propósito encima. Las siguientes llevan
     el resto de los textos. Sin foto de portada, la primera hoja es el color elegido con el logo
     (o las iniciales) en grande.
   - **«Columnas»**: la estructura del informe por etapa. Una columna del color a la izquierda
     con el logo, el nombre, los datos, «Dónde estamos» y el contacto; a la derecha, la foto de
     portada como una franja arriba (si la hay) y los textos. Si hace falta una segunda hoja,
     lleva una franja estrecha del color con el nombre en vertical, como el informe.
   - **Los colores**, un catálogo fijo: «Azul» `#1E3A8A` (el de Northstar), «Verde» `#0B7A63`,
     «Rojo» `#C22A3F`, «Noche» `#111312`, «Petróleo» `#155E75` y «Ciruela» `#6B2D5C`. Todos
     llevan texto blanco encima con un contraste de al menos 4,5:1. El color tiñe los títulos,
     los filetes, la columna o el velo, según la plantilla; el resto es la paleta Northstar
     (tinta `#111312`, gris `#4A5251`, bruma `#F5F7FB`, filete `#E6E7E4`).
4. **Qué lleva el brochure.** Es el perfil público pasado a papel, en este orden y solo con lo
   que esté lleno:
   - el logo (sin logo, las iniciales sobre el color elegido) y el nombre de la empresa, con
     «Sector · Tamaño · Fundada en AAAA»;
   - «Quiénes somos» y «Nuestro propósito» (en «Portada», el propósito va en la portada);
   - «Misión» y «Visión». Si solo hay una, ocupa el ancho entero;
   - «Beneficios»;
   - «Dónde estamos»: las provincias de las sedes activas, como en la ficha del perfil, sin
     direcciones;
   - «Contacto»: el perfil público en EX, la página web, las redes y el video, como enlaces que
     se pueden pulsar y con la dirección escrita para quien lo imprima;
   - al pie de la última hoja, «Brochure generado con EX · {fecha}».
   - **Sin logo ni portada, ninguna plantilla deja huecos**: ni recuadros vacíos ni imágenes de
     relleno. Una sección vacía no se pinta, tampoco su título.
   - **Nada se recorta.** Los textos largos pasan a la hoja siguiente sin cortarse. El brochure
     ocupa de una a tres hojas A4 verticales.
5. **Cuándo se puede descargar.** Basta con tener guardado «Quiénes somos». El nombre siempre
   está, y sin logo salen las iniciales.
   - Sin «Quiénes somos» guardado, en el módulo el botón sale desactivado y a su lado el chip
     «Falta «Quiénes somos»». En el perfil público el botón no sale.
6. **Descargar desde el panel.** «Descargar brochure (PDF)» arma el PDF con lo guardado y lo abre
   en otra pestaña, donde el navegador deja verlo, guardarlo o imprimirlo.
   - Mientras se arma, dice «Generando…» y no se puede volver a pulsar.
   - Si hay cambios sin guardar, a su lado sale el chip «Usa lo último guardado».
   - El archivo se llama «Brochure - {empresa}.pdf».
7. **Descargar desde el perfil público.** En `/empresas/<código>`, la cabecera lleva «Descargar
   brochure (PDF)» después de «Ver vacantes (N)» y «Visitar web», con el estilo secundario y el
   icono de descarga. En el teléfono, los botones van bajo el nombre y pasan a la línea siguiente
   cuando no caben. Abre el mismo PDF en otra pestaña, sin cuenta.
8. **Siempre al día.** El PDF refleja lo guardado en ese momento: cualquier «Guardar» del módulo
   o cambio en Configuración → Sedes se ve en la siguiente descarga. No hay que publicar nada.
9. **Misión y visión en el perfil público.** En la columna principal, después de «Nuestro
   propósito» y antes del video, un bloque **«Misión y visión»** con el mismo estilo que «Quiénes
   somos»: las dos en dos columnas, cada una con su subtítulo («Misión», «Visión»). Si solo hay
   una, ocupa el bloque y el título del bloque es solo el suyo. En el teléfono van una debajo de
   otra. Con las dos vacías no hay bloque.
10. **Cada empresa, lo suyo.** El módulo lee y guarda siempre la empresa de quien ha entrado; la
    dirección del panel no lleva el identificador de la empresa. El brochure de una empresa solo
    enseña lo suyo.

## Referencia visual
- Maqueta: copia local en [maqueta/](maqueta), en HTML que se abre directamente en el navegador,
  sin JavaScript. Los datos son inventados (Cafetalera Valle Alto y Textiles Ayllu). El logo y la
  portada son dibujos SVG que hacen de imagen.
  - Panel, el módulo «Perfil de la empresa» completo (las cuatro secciones, el pie y la vista
    previa):
    - [panel-con-datos.html](maqueta/panel-con-datos.html): misión y visión llenas en
      «Presentación»; la sección «Brochure» con «Portada» y «Verde» elegidos, «Descargar brochure
      (PDF)» y el chip «Usa lo último guardado». La vista previa trae misión y visión;
    - [panel-vacio.html](maqueta/panel-vacio.html): una empresa recién dada de alta, todo vacío,
      «Clásica» y «Azul» elegidos y «Descargar brochure (PDF)» desactivado con el chip «Falta
      «Quiénes somos»».
  - Brochure, una hoja A4 (794×1123) por página, apiladas en cada archivo:
    - [brochure-clasica.html](maqueta/brochure-clasica.html): «Clásica» en azul, dos hojas;
    - [brochure-portada.html](maqueta/brochure-portada.html): «Portada» en verde, dos hojas, con
      la portada a toda página;
    - [brochure-columnas.html](maqueta/brochure-columnas.html): «Columnas» en petróleo, una hoja,
      con la portada en franja y **con la visión vacía**: la misión ocupa el ancho entero y no
      queda el título. La franja estrecha de las hojas siguientes no se dibuja: copia la del
      informe por etapa;
    - [brochure-sin-fotos.html](maqueta/brochure-sin-fotos.html): la primera hoja de «Portada» y
      de «Columnas» de una empresa sin logo ni portada (iniciales y color, sin huecos).
  - Portal:
    - [portal-perfil.html](maqueta/portal-perfil.html): el perfil
      `/empresas/cafetalera-valle-alto` con «Descargar brochure (PDF)» y «Misión y visión».
      Adaptable: a 390 px se ve como en el teléfono.
- Versión aprobada: pendiente de que el usuario la revise.
- **La maqueta copia el frontend real** (`Frontend-EX`, main del 17/11/2026), y quien implemente
  usa estos mismos componentes y estilos sin improvisar:
  - el armazón y el menú de `src/panel/Armazon.tsx` y `menu.ts`;
  - la página, las secciones, los contadores, los chips de beneficios, las zonas de imagen, el
    pie y la vista previa de `PerfilDeLaEmpresa.module.css`;
  - `Campo`/`AreaTexto` de `Campo.module.css`;
  - los chips «Usa lo último guardado» y «Falta «Quiénes somos»» con `.marca` + `.retirada` de
    `Areas.module.css`;
  - los botones `acentoMenor` y `secundario` de `piezas.module.css`;
  - en el portal, `.seccion`, `.texto` y la cabecera de `PerfilDeEmpresa.module.css` y
    `Cabecera.module.css`, y la ficha de `Lateral.module.css`.
- **Lo único nuevo**, que copia la maqueta: las tres tarjetas de plantilla con su miniatura, los
  seis círculos de color, la fila del botón de descarga, el bloque «Misión y visión» del perfil,
  el botón «Descargar brochure (PDF)» y las tres plantillas del PDF.
- Pantallas y estados relevantes:
  - Brochure, por plantilla: completo; sin logo ni portada; solo con quiénes somos; con todos los
    textos al máximo; con muchas sedes.
  - Panel: la sección vacía; con datos; con cambios sin guardar; y el botón desactivado con su
    chip.
  - Portal: el perfil con misión y visión, con solo una y sin ninguna; con y sin el botón.
- Escritorio y móvil: el PDF es A4 vertical; panel a 1280×800; portal a 1280×800 y a 390×844, sin
  desplazamiento horizontal.
- Fidelidad requerida: el orden del paso 4; que ninguna plantilla deje huecos sin logo ni
  portada; la primera hoja de cada plantilla; el lugar de misión y visión en el módulo y en el
  perfil; ningún texto de ayuda bajo los campos. Puede adaptarse: márgenes, tamaños de letra y el
  reparto exacto de los textos entre hojas.

Los textos y reglas de esta spec prevalecen sobre la maqueta. Respetar el diseño existente salvo
los cambios visuales aquí definidos.

## Criterios de aceptación
- AC-01: Dado un usuario con el permiso y una empresa sin datos, cuando abre «Perfil de la
  empresa», entonces ve «Misión» y «Visión» vacías en «Presentación» con «0 / 400», y la sección
  «Brochure» debajo de «Video y enlaces» con «Clásica» y «Azul» elegidos y «Descargar brochure
  (PDF)» desactivado con el chip «Falta «Quiénes somos»». En Configuración no hay nada del
  brochure.
- AC-02: Cuando escribe la misión, entonces la vista previa la muestra al momento, antes de
  guardar, y no deja escribir el carácter 401.
- AC-03: Dada una empresa con quiénes somos, propósito y web guardados, cuando llena la misión,
  guarda y pulsa «Descargar brochure (PDF)», entonces se abre «Brochure - {empresa}.pdf» con
  quiénes somos, el propósito, la misión y la web, sin «Visión» ni títulos de otras secciones
  vacías.
- AC-04: Dada una empresa con misión y sin visión, cuando se abre su perfil público, entonces el
  bloque se titula «Misión» y no aparece «Visión» en ningún sitio. Con las dos vacías no hay
  bloque.
- AC-05: Cuando elige «Columnas» y «Petróleo» y guarda, entonces la siguiente descarga sale con
  esa plantilla y ese color, y la tarjeta elegida lleva el borde azul.
- AC-06: Dados cambios sin guardar, cuando mira la sección «Brochure», entonces sale el chip «Usa
  lo último guardado» y la descarga trae lo guardado, no lo escrito.
- AC-07: Dada una empresa con quiénes somos guardado, cuando un candidato sin cuenta abre su
  perfil, entonces ve «Descargar brochure (PDF)» y al pulsarlo se abre el PDF. Sin quiénes somos,
  el botón no sale y la dirección del brochure responde 404.
- AC-08: Dado el brochure descargado, cuando la empresa cambia «Quiénes somos» y guarda, entonces
  la siguiente descarga, desde el panel o desde el perfil, trae el texto nuevo.
- AC-09: Dada una empresa sin logo ni portada, cuando descarga el PDF en cada una de las tres
  plantillas, entonces ninguna tiene huecos vacíos ni imágenes rotas, y la cabecera lleva las
  iniciales y el nombre.
- AC-10: Dada una empresa con logo y portada, cuando descarga «Portada» y «Columnas», entonces la
  portada sale a toda página en la primera y como franja en la segunda, y el logo en las tres
  plantillas.
- AC-11: Dados todos los textos al máximo (los de la 4a y misión y visión), cuando descarga el PDF
  en cada plantilla, entonces ningún texto se corta y ninguno pasa de tres hojas.
- AC-12: Dada una empresa con una sede activa y una desactivada, cuando descarga el brochure,
  entonces «Dónde estamos» trae solo la provincia de la activa, sin dirección.
- AC-13: Dados textos con tildes, «ñ», comillas, saltos de línea y algo parecido a etiquetas
  HTML, cuando se descarga el brochure y se abre el perfil, entonces todo se lee tal cual se
  escribió.
- AC-14: Dadas dos empresas A y B, cuando un usuario de A guarda o descarga, entonces nada de B
  cambia y el brochure de A no enseña nada de B.
- AC-15: Dado un usuario sin el permiso (por ejemplo, Responsable del área), cuando llama
  directamente a guardar o a la descarga del panel, entonces recibe un 403 y no cambia nada. La
  descarga pública sigue funcionando para cualquiera, como el perfil.

## Casos límite y errores
- Datos vacíos o inválidos:
  - Una misión o visión con solo espacios se guarda vacía.
  - Una misión o visión de más de 400 caracteres por la API recibe un 400 con «La misión admite
    hasta 400 caracteres» (o «La visión…»), como el propósito de la 4a.
  - Una plantilla o un color que no son del catálogo reciben un 400 y no se guarda nada.
- Usuario sin permisos: AC-15.
- Recarga, reintento o doble envío:
  - «Descargar brochure (PDF)» se desactiva mientras se arma.
  - Si dos personas editan a la vez, gana el último guardado, como en la 4a.
- Otros casos relevantes:
  - **Falla el armado.** En el panel, al lado del botón sale «No se pudo armar el brochure.» y se
    puede volver a pulsar. En el perfil público, la dirección responde un error y el fallo queda
    en el registro.
  - **El brochure lo puede pedir cualquiera.** Para que pedirlo mil veces no cargue el servidor,
    el backend guarda en memoria el último PDF de cada empresa y solo lo vuelve a armar cuando
    cambia algo de lo que lleva (lo guardado en el módulo, sus sedes o la fecha del pie). Se
    sirve sin caché pública, para que cada descarga refleje el perfil del momento.
  - **Empresa suspendida.** Su perfil da «no encontrada», como en la 4a, y su brochure responde
    404.
  - **Enlaces de la 4a.** Dentro del PDF solo se pintan los que son `http` o `https`, aunque el
    backend ya los haya comprobado al guardarlos.
  - **Logo PNG con transparencia.** Se pinta sobre blanco, nunca sobre negro.
  - **Nombre de empresa muy largo.** Se parte en líneas en la portada; en el nombre del archivo se
    recorta a 80 caracteres.
  - **Tiempo de armado:** menos de 3 segundos.

## Datos y compatibilidad
- Información que debe guardarse (migración **V86**; la V85 es la del propósito, la V87 está
  reservada para la convocatoria y la V88 para el video). Todo en `organizacion`:
  - `mision` y `vision`: texto opcional de 1 a 400 caracteres, con su `CHECK` (vacío es `NULL`,
    como en la V85);
  - `plantilla_brochure`: `CLASICA`, `PORTADA` o `COLUMNAS`, no nulo, por defecto `CLASICA`, con
    su `CHECK`;
  - `color_brochure`: `AZUL`, `VERDE`, `ROJO`, `NOCHE`, `PETROLEO` o `CIRUELA`, no nulo, por
    defecto `AZUL`, con su `CHECK`.
  - No hay tablas nuevas, no se guarda ningún PDF y no hace falta un permiso nuevo: se usa
    `editar_presentacion_empresa` (V85).
- Contratos:
  - Panel, siempre de la empresa de quien ha entrado y con `editar_presentacion_empresa`:
    - `GET` y `PUT /api/v1/panel/organizacion/presentacion` suman `mision`, `vision`,
      `plantillaBrochure` y `colorBrochure`;
    - `GET /api/v1/panel/organizacion/presentacion/brochure` devuelve el PDF con lo guardado, o
      un 409 si falta «Quiénes somos».
  - Público, sin token:
    - `GET /api/v1/portal/empresas/{codigo}` suma `mision`, `vision` y `brochure` (la dirección
      del PDF, o nulo si falta lo mínimo). Los campos de la 4a no cambian;
    - `GET /api/v1/portal/empresas/{codigo}/brochure` devuelve el PDF
      (`Content-Disposition: inline; filename="Brochure - {empresa}.pdf"`, sin caché pública), o
      un 404 si la empresa no existe, está suspendida o le falta lo mínimo.
- Comportamiento para registros existentes: todas las empresas quedan sin misión ni visión, con
  «Clásica» y «Azul». Las que ya tienen «Quiénes somos» pueden descargar su brochure desde el
  primer día; ningún otro dato del perfil cambia.
- Restricciones de compatibilidad:
  - **El PDF se arma en el backend con el motor del informe por etapa** (`comun/pdf`), con su
    librería y sus fuentes. No se manda nada a un servicio externo.
  - **Dibujar imágenes.** Lo que el motor tiene que aprender es a dibujar una imagen JPG o PNG en
    un rectángulo, recortada al centro y sobre blanco: una primitiva nueva en `LienzoPdf`, que el
    informe no usa y no cambia. Sirve para el logo y la portada, que el backend lee del almacén
    con `leer`. El brochure va en su propia clase, junto a la presentación, y comparte con
    `PdfDelInforme` los colores Northstar.
  - Las plantillas y los colores son un catálogo fijo en el código, como las portadas del perfil.
  - **El bucket no necesita nada.** El brochure no se guarda en el almacén: se arma en el momento
    a partir del logo y la portada que ya están ahí. Por eso no hay tipos nuevos ni archivos
    nuevos que se vean sin cuenta.
  - En el portal, `mision`, `vision` y `brochure` son opcionales en el tipo: un backend anterior
    no los manda y el perfil se pinta como en la 4a.
  - [Roles y permisos](../../../docs/04-ROLES-Y-PERMISOS.md) aclara que el permiso de la 4a
    también cubre misión, visión y la elección de plantilla.

## Verificación
- Actor/rol y datos de prueba necesarios:
  - Dos empresas activas, A y B. A tiene los datos de la 4a llenos (con logo y portada), dos sedes
    activas y una desactivada; B no tiene logo, ni portada, ni «Quiénes somos».
  - En A, un usuario de Talento (o Dirección o Administrador) y uno de Responsable del área.
  - Un logo PNG con transparencia y un juego de textos al máximo con tildes, «ñ», saltos de línea
    y algo parecido a HTML.
- Recorridos que deben quedar como E2E repetibles en headless:
  - Panel: llenar misión y visión, ver la vista previa, guardar; elegir plantilla y color;
    descargar; el botón desactivado con su chip; el chip «Usa lo último guardado».
  - Portal: el perfil con misión y visión, con una sola y sin ninguna; con el botón del brochure y
    sin él.
  - Backend: pruebas que arman cada plantilla con y sin logo y portada y leen su texto (orden,
    secciones vacías ausentes, misión sin visión, sedes activas sin dirección, caracteres
    especiales); el 400 de los máximos y del catálogo; el 409 y el 404 sin lo mínimo; el 403; y
    que A no ve nada de B.
  - ⚠️ El `check` del harness no corre los `@Nested`; el CI sí. Si las pruebas del brochure van
    anidadas, correrlas también con `verify`.
- Riesgos que QA debe explorar con Chrome visible:
  - Abrir los PDF de las tres plantillas, con y sin logo y portada, e imprimir uno.
  - Pulsar los enlaces dentro del PDF.
  - Abrir el brochure en el teléfono a 390×844 y dentro de la app de Android.
- Comparación visual de QA: la primera hoja de cada plantilla, completa y sin logo ni portada,
  frente a la maqueta aprobada; la sección del panel y el bloque «Misión y visión» del perfil.
- Resultado que quiero comprobar personalmente: **lo visual lo compruebo yo, porque el panel
  tiene login.** Quiero llenar misión y visión, probar las tres plantillas y algún color,
  descargar el brochure desde el panel y desde el perfil sin cuenta, mandármelo por WhatsApp y
  abrirlo en el teléfono.

## Dependencias y dudas
- Requiere:
  - La 4a, ya fusionada (PR #139, V85): su módulo, su perfil público, el logo y la portada en el
    almacén y su permiso.
  - El motor de PDF del informe por etapa, ya fusionado.
  - **La V86 reservada para esta spec** (V87 convocatoria, V88 video). Confirmar antes de
    empezar que ninguna otra rama la reclamó: la base local es compartida y la primera rama que
    arranca se queda el número.
- Decisiones pendientes:
  - **¿Apruebas la maqueta** de las tres plantillas, de la sección del panel y del bloque del
    perfil? Hace falta antes de autorizar. Recomiendo revisar sobre todo la primera hoja de cada
    plantilla y los nombres «Clásica», «Portada» y «Columnas».
- Decidido el 17/11/2026 (usuario):
  - La versión corta: solo misión y visión como datos nuevos, el PDF con los datos de la 4a, y sin
    clientes, responsabilidad social, galería ni publicar y retirar.
  - Misión y visión son opcionales y salen en el perfil público y en el PDF. Vacías no salen, ni
    su título.
  - Tres plantillas para elegir; «Portada» usa la foto de portada de la 4a, sin mosaico de fotos.
  - El botón de descarga va en el módulo del panel y en el perfil público.
  - Sin código QR y sin «Qué buscamos en las personas».
- Decidido el 17/11/2026 (recomendación):
  - Las tres plantillas son «Clásica» (por defecto, porque no necesita fotos), «Portada» y
    «Columnas» (la estructura del informe por etapa).
  - El color es un catálogo de seis, con el azul de Northstar por defecto. La 4a no guarda un
    color de marca, y un selector libre permitiría colores ilegibles.
  - Lo mínimo para descargar es «Quiénes somos»: el nombre siempre está y sin logo salen las
    iniciales.
  - El PDF no se guarda en el almacén: se arma al pedirlo y se recuerda en memoria hasta que
    cambia algo. Así siempre está al día y el bucket no necesita nada.
  - Sin enlace al brochure en la vacante, para mantener la versión corta.
- Decidido al redactar:
  - La web es el perfil público de la 4a y el brochure su versión PDF; no hay página web propia
    del brochure.
  - Misión y visión van en «Presentación», con el mismo contador que los otros textos. La
    plantilla y el color, en la sección «Brochure». Todo con el mismo «Guardar». Nada va en
    Configuración.
  - Se reutiliza todo lo de la 4a sin copiarlo; solo se guarda lo nuevo, con el mismo permiso.
  - «Dónde estamos» lleva solo las provincias de las sedes activas, como la ficha del perfil.
