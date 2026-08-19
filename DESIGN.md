---
name: "RENASER OS"
description: "Sistema operativo monocromático para trabajo guiado, legible y auditable."
colors:
  canvas-light: "#f5f5f3"
  surface-light: "#ffffff"
  surface-subtle-light: "#efefec"
  surface-muted-light: "#e9e9e6"
  ink-light: "#101010"
  text-muted-light: "#595955"
  divider-light: "#e3e3df"
  divider-strong-light: "#d5d5d0"
  action-primary: "#111111"
  action-on-primary: "#ffffff"
  success-light: "#257a47"
  warning-light: "#a55d00"
  danger-light: "#a72727"
  info-light: "#555d68"
  canvas-dark: "#090909"
  surface-dark: "#121212"
  surface-subtle-dark: "#1b1b1b"
  surface-muted-dark: "#222222"
  ink-dark: "#f5f5f3"
  text-muted-dark: "#b8b8b2"
  divider-dark: "#2b2b29"
  divider-strong-dark: "#3b3b38"
  success-dark: "#70b487"
  warning-dark: "#d2a25b"
  danger-dark: "#df7772"
  info-dark: "#aab5c3"
  sidebar-canvas: "#0f0f10"
  sidebar-surface: "#171718"
  sidebar-divider: "#252527"
  sidebar-text: "#b8b8bc"
typography:
  display:
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Inter, Arial, sans-serif'
    fontSize: "28px"
    fontWeight: 760
    lineHeight: 1.03
    letterSpacing: "-0.035em"
  headline:
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Inter, Arial, sans-serif'
    fontSize: "18px"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "-0.025em"
  title:
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Inter, Arial, sans-serif'
    fontSize: "15px"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.025em"
  body:
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Inter, Arial, sans-serif'
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "normal"
    letterSpacing: "normal"
  body-compact:
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Inter, Arial, sans-serif'
    fontSize: "11px"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  label:
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Inter, Arial, sans-serif'
    fontSize: "10.5px"
    fontWeight: 750
    lineHeight: 1.25
    letterSpacing: "normal"
rounded:
  none: "0px"
  segment: "8px"
  control: "10px"
  field: "12px"
  section: "14px"
  panel: "16px"
  card: "18px"
  modal: "22px"
  pill: "999px"
spacing:
  s1: "4px"
  s2: "8px"
  s3: "12px"
  s4: "16px"
  s5: "20px"
  s6: "24px"
  s8: "32px"
components:
  button-primary:
    backgroundColor: "{colors.action-primary}"
    textColor: "{colors.action-on-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "9px 13px"
  button-secondary:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.ink-light}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "9px 13px"
  button-link:
    backgroundColor: "transparent"
    textColor: "{colors.ink-light}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "3px 0"
  task-navigation:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.text-muted-light}"
    typography: "{typography.label}"
    rounded: "{rounded.section}"
    padding: "0px"
  tag-warning:
    backgroundColor: "{colors.surface-subtle-light}"
    textColor: "{colors.warning-light}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "4px 8px"
  card:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.ink-light}"
    rounded: "{rounded.card}"
    padding: "18px"
  field:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.ink-light}"
    typography: "{typography.body-compact}"
    rounded: "{rounded.field}"
    padding: "10px 11px"
  next-task:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.ink-light}"
    rounded: "{rounded.section}"
    padding: "20px"
  guide-strip:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.ink-light}"
    rounded: "{rounded.section}"
    padding: "15px 16px"
  sidebar-navigation:
    backgroundColor: "{colors.sidebar-divider}"
    textColor: "{colors.action-on-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "10px 11px"
---

# Design System: RENASER OS

## Overview

**Creative North Star: "Mesa de operaciones guiada"**

RENASER OS se siente como una mesa de trabajo sobria: la interfaz reduce el ruido, conserva el contexto y hace que la acción posible se reconozca antes que la instrumentación. Su mundo es monocromático, plano y editorial. Las superficies claras u oscuras ordenan la información; el negro concentra la acción principal; el color aparece solo cuando un estado necesita atención o confirmación.

La personalidad es precisa, discreta y operativa. La jerarquía depende de escala, peso, espacio y separadores finos, no de ornamento. Las listas y bloques de contenido favorecen lectura secuencial, nombres explicativos y contexto por rol. La composición de Inicio de Selección —una tarea dominante seguida por vacantes— es una expresión local de este mundo, no una plantilla obligatoria para todos los módulos.

**Key Characteristics:**

- Monocromo cálido con una paleta equivalente para modo oscuro.
- Superficies planas, bordes finos y sombra excepcional.
- Jerarquía tipográfica compacta construida con una familia de sistema.
- Listas editoriales y controles con nombres completos antes que iconografía ambigua.
- Color semántico reservado para confirmación, atención, riesgo e información.
- Foco de teclado visible y movimiento reducido cuando el sistema lo solicita.

## Colors

La paleta combina papeles grises cálidos con tinta casi negra; el tema oscuro conserva las mismas relaciones semánticas y aumenta la luminosidad de los estados.

### Primary

- **Action Black** (`action-primary`): acción principal, selección activa y progreso de mayor jerarquía.
- **White on Black** (`action-on-primary`): texto y marcas sobre la acción negra.

### Secondary

- **Confirmed Green** (`success-light`, `success-dark`): proceso confirmado, publicado o satisfactorio.
- **Attention Amber** (`warning-light`, `warning-dark`): espera, borrador o revisión que requiere atención.
- **Critical Red** (`danger-light`, `danger-dark`): bloqueo, error, cierre sensible o urgencia crítica.
- **Slate Information** (`info-light`, `info-dark`): estado informativo sin juicio positivo o negativo.

### Tertiary

- **Command Rail Black** (`sidebar-canvas`): fondo persistente de la navegación global.
- **Command Rail Raised** (`sidebar-surface`): selector de rol y elementos contenidos dentro del rail.
- **Command Rail Divider** (`sidebar-divider`): divisiones y selección contenida en la navegación.
- **Command Rail Text** (`sidebar-text`): rótulos secundarios legibles sobre el rail.

### Neutral

- **Warm Canvas / Night Canvas** (`canvas-light`, `canvas-dark`): fondo general de la aplicación.
- **Paper / Night Surface** (`surface-light`, `surface-dark`): superficie principal de controles, paneles y contenido.
- **Soft Paper / Night Subtle Surface** (`surface-subtle-light`, `surface-subtle-dark`): agrupación secundaria, hover y bloques de menor jerarquía.
- **Muted Paper / Night Muted Surface** (`surface-muted-light`, `surface-muted-dark`): pistas, barras y capas tonales internas.
- **Near Black / Warm White Ink** (`ink-light`, `ink-dark`): contenido principal y foco.
- **Quiet Graphite / Quiet Silver** (`text-muted-light`, `text-muted-dark`): explicaciones, metadatos y contexto.
- **Hairline / Night Hairline** (`divider-light`, `divider-dark`): estructura ordinaria.
- **Strong Hairline / Strong Night Hairline** (`divider-strong-light`, `divider-strong-dark`): límite o separación que necesita un grado más de presencia.

### Named Rules

**The Reserved Color Rule.** El negro dirige la acción; los tonos cromáticos solo comunican estado y nunca decoran superficies completas.

**The Semantic Pair Rule.** Todo rol de color debe conservar su significado al cambiar de tema; no se reutiliza un verde, ámbar o rojo con un significado diferente.

## Typography

**Display Font:** pila nativa de sistema con SF Pro y Segoe UI como referencias.

**Body Font:** la misma pila nativa de sistema para continuidad y velocidad.

**Label/Mono Font:** no existe una familia monoespaciada propia; etiquetas y cifras usan la pila del sistema, con cifras tabulares donde se comparan valores.

**Character:** compacta, neutral y editorial. Los titulares reciben tensión mediante espaciado negativo y peso firme; el texto operativo permanece pequeño pero con altura de línea generosa y contraste suficiente.

### Hierarchy

- **Display** (760, 28px, 1.03): título principal del área o módulo.
- **Headline** (700, 18px, 1.25): siguiente acción, bienvenida o encabezado de herramienta.
- **Title** (700, 15px, 1.2): paneles, secciones y nombres de entidades destacadas.
- **Body** (400, 14px, normal): base tipográfica de la aplicación y contenido que necesita lectura continua.
- **Body Compact** (400, 11px, 1.5): explicaciones operativas, metadatos extendidos y ayuda contextual.
- **Label** (750, 10.5px, 1.25): controles, estados, navegación y rótulos breves; las microetiquetas pueden usar mayúsculas y espaciado amplio.

### Named Rules

**The Weight Before Ornament Rule.** La jerarquía se construye con peso, tamaño y espacio; no se introducen familias decorativas para distinguir niveles.

**The Plain Label Rule.** Los controles usan palabras cotidianas y completas; las mayúsculas quedan reservadas para rótulos breves, nunca para instrucciones o acciones largas.

## Layout

El lienzo de escritorio combina un rail global fijo (250px) con un contenido fluido limitado (1460px), centrado y con relleno de 30px que se compacta a 14px en móvil. El ritmo base es de 4px y se expresa mediante la escala `s1`–`s8`; los grupos habituales usan separaciones de 8px, 12px, 16px, 20px, 24px y 32px.

Los paneles operativos usan grid cuando la comparación simultánea aporta valor y listas apiladas cuando importa el orden. A 1180px, la navegación de tareas pasa de seis a tres columnas; a 1120px, las rejillas generales reducen columnas; a 1040px, las composiciones de editor y panel auxiliar se apilan; a 780px, desaparece el rail fijo, el contenido ocupa todo el ancho y los objetivos táctiles principales alcanzan al menos 44px.

**The One Navigation Surface Rule.** Cada nivel ofrece una sola superficie de navegación visible; el responsive reorganiza esa superficie en vez de duplicarla.

**The Local Composition Rule.** La prioridad única de Inicio, el orden de vacantes y las cinco etapas pertenecen a Selección; otros módulos reutilizan el ritmo y los componentes, no esa composición.

## Elevation & Depth

El sistema es plano por defecto. La profundidad se construye con cambios tonales y bordes de un píxel; las sombras aparecen solo para comunicar superposición, selección contenida o respuesta de hover. Drawer y modal admiten sombras amplias porque abandonan el plano del documento; las tarjetas en reposo no tienen sombra.

### Shadow Vocabulary

- **Hover Lift** (`box-shadow: 0 9px 30px rgba(0,0,0,.035)`): elevación muy tenue para una tarjeta accionable en hover.
- **Selected Segment** (`box-shadow: 0 3px 10px rgba(0,0,0,.05)`): sombra corta bajo el segmento activo sobre una pista tonal.
- **Drawer Separation** (`box-shadow: -25px 0 80px rgba(0,0,0,.1)`): sombra lateral amplia que separa el expediente del contenido.
- **Modal Separation** (`box-shadow: 0 35px 100px rgba(0,0,0,.2)`): sombra envolvente para una ventana modal centrada.

### Named Rules

**The Flat-by-Default Rule.** Una superficie en reposo se separa con tono y borde; una sombra siempre debe explicar interacción o superposición.

## Shapes

La forma es suavemente redondeada y jerárquica. Los paneles principales usan radios de 14–18px, los campos 12px y los controles compactos 8–10px. Botones, filtros, etiquetas y barras de progreso adoptan forma de cápsula. Los modales usan 22px porque son la capa más independiente; avatares y señales son circulares.

**The Nested Radius Rule.** Un elemento interior usa un radio igual o menor que el contenedor que lo aloja, conservando una silueta concéntrica y sobria.

**The Border Carries Structure Rule.** Los bordes finos definen grupos y filas; no se sustituyen por sombras decorativas ni por contenedores excesivamente redondeados.

## Components

### Buttons

- **Shape:** cápsula compacta (`pill`) para acciones estándar; los enlaces editoriales mantienen borde inferior y radio cero.
- **Primary:** fondo `action-primary`, texto `action-on-primary` y relleno compacto; se reserva para la acción de mayor jerarquía del grupo.
- **Hover / Focus:** desplazamiento vertical de 1px en hover y contorno sólido de 2px, separado 3px, en `:focus-visible`.
- **Secondary / Ghost:** superficie principal o fondo transparente, borde hairline y tinta principal; nunca compiten en masa con el primario.

### Chips

- **Style:** cápsula compacta sobre superficie tonal, con borde semántico mezclado y un punto circular que refuerza el estado.
- **State:** verde confirma, ámbar pide atención, rojo marca riesgo e informativo permanece neutro; el texto siempre nombra el estado.

### Cards / Containers

- **Corner Style:** tarjeta general `card`; paneles operativos más densos usan `panel` o `section`.
- **Background:** superficie principal para trabajo y superficie sutil para contexto secundario.
- **Shadow Strategy:** sin sombra en reposo; solo `hover-lift` si todo el contenedor es accionable.
- **Border:** hairline continuo; el borde fuerte se reserva para una guía activa o separación reforzada.
- **Internal Padding:** 14–20px según densidad, alineado con la escala de 4px.

### Inputs / Fields

- **Style:** superficie principal, borde hairline, radio `field` y relleno de 10px por 11px.
- **Focus:** el borde adopta la tinta principal y el foco global añade un contorno de 2px separado 3px.
- **Error / Disabled:** error con lavado rojo, borde rojo atenuado y mensaje recuperable; disabled conserva la forma y reduce opacidad a 58%.

### Navigation

- **Global:** rail oscuro persistente en escritorio, con texto gris y selección tonal; en móvil entra como panel lateral.
- **Local:** segmentos editoriales con título y ayuda; el activo invierte tinta y superficie. En móvil se reorganizan en dos columnas, sin crear otra navegación paralela.
- **Focus:** todos los botones de navegación heredan el contorno visible del sistema.

### Next Action

La siguiente acción combina una señal circular, título, explicación, responsable y botón primario dentro de un panel plano. Es una firma implementada por Selección; otros módulos pueden adoptar su gramática de prioridad sin copiar contenido, orden o flujo.

### Contextual Guide

La guía contextual usa borde de tinta, progreso lineal, pasos breves y acciones anterior/siguiente. El objetivo activo recibe un halo ámbar pulsante; con `prefers-reduced-motion: reduce`, el halo permanece estático.

## Do's and Don'ts

### Do:

- **Do** usar negro para la acción principal y selección activa dentro de un grupo.
- **Do** separar trabajo, contexto y fondo mediante `surface`, `surface-subtle` y `canvas` antes de considerar una sombra.
- **Do** nombrar el estado además de colorearlo y mantener la equivalencia semántica entre temas.
- **Do** preservar nombres explicativos, foco visible y objetivos táctiles de al menos 44px en móvil.
- **Do** revelar configuración avanzada mediante rutas y guías contextuales acordes al rol.

### Don't:

- **Don't** usar verde, ámbar o rojo como decoración o como única evidencia de estado.
- **Don't** añadir gradientes, vidrio, sombras ambientales permanentes o elevación sin propósito.
- **Don't** duplicar la navegación al adaptar el layout; reorganiza el mismo modelo.
- **Don't** convertir la composición de Inicio, la cola de reclutamiento o las cinco etapas de Selección en reglas globales de RENASER OS.
- **Don't** sustituir texto claro por iconos sin nombre o por terminología interna innecesaria.
