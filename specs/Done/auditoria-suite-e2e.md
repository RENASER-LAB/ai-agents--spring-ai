# Optimizar la suite e2e: clasificar, migrar y recortar hasta dejarla rápida

Estado: TERMINADO

Estado previo (referencia histórica): lista para implementar

## Objetivo

Que la suite e2e deje de ser el cuello de botella del repositorio `RenaserOsPostulantes`:
pasar de 329 pruebas / ~14 000 líneas / ~12 minutos a un conjunto más chico, que corre
notablemente más rápido, sin perder ningún recorrido crítico real. La lógica de negocio que
hoy se prueba con navegador se mueve a pruebas unitarias; lo que sobra por duplicado o por
no poder cazar ningún fallo se borra; lo que se queda como e2e sigue corriendo con
**Playwright CLI/test en modo headless**, que es lo que se mantiene sin cambios.

Dos condiciones no negociables: **ninguna prueba debe quedar rota, deshabilitada sin motivo
o inutilizable** al cerrar — lo que no se pueda arreglar se borra (si sobra) o se documenta
como pendiente explícito, nunca queda en un estado ambiguo; y **la corrida automática no
dispara IA real** — toda prueba que necesite una calificación real con DeepSeek para pasar
queda excluida de `npx playwright test` por defecto y solo se ejecuta a mano.

## Contexto

- Comportamiento actual (repositorio `RenaserOsPostulantes`):
  - `herramientas/e2e/` tiene 43 archivos `*.spec.ts`, 329 pruebas (`test(...)`) y 11 547
    líneas solo en los `.spec.ts` (14 986 si se cuentan también los `ayuda-*.ts` y
    `base-de-datos.ts` de soporte). `src/` tiene 42 377 líneas y 1 007 pruebas unitarias.
  - El CI (`.github/workflows/ci.yml`) corre `npm ci`, `typecheck`, `npm test` (unitarios)
    y `vite build`. Ningún paso invoca Playwright: la suite e2e no se ejecuta en ningún PR.
    Esta spec no cambia eso: sigue sin correr en CI.
  - Se ejecuta con Playwright CLI (`npx playwright test`), en modo headless, con montaje
    manual: `herramientas/e2e/base-de-datos.ts` pide variables de entorno sin valor por
    defecto y un contenedor Postgres con la etiqueta `renaser.e2e.clone`.
  - 15 archivos usan `test.describe.configure({ mode: 'serial' })` o
    `test.describe.serial(...)`: `10-panel-entrar`, `11-perfil`, `12-postular`,
    `14-vacante`, `15-componer-prueba`, `16-cuestionario-tecnico`, `17-prueba-tecnica`,
    `18-ranking-contra-api`, `22-perfil-con-foto-y-cv`, `23-remuneracion`,
    `26-editar-vacante-avisos`, `27-archivar-vacante`, `29-eliminar-vacante`,
    `33-recuperar-contrasena`, `35-recuperar-contrasena-bordes`. Un fallo temprano en
    cualquiera deja sin correr el resto de ese archivo.
  - Las ~22 pruebas que fallan hoy son del panel y dependen de `sembrar-escenario-e2e.py`
    (repositorio `RENASER-RECLUTAMIENTO`), que hace un `UPDATE` sobre una fila que no
    existe: afecta a cero filas y no avisa.
  - `03-orden.spec.ts` y `04-filtros.spec.ts` demuestran que `page.route` funciona aquí:
    interceptando la respuesta del ranking pasaron 23 pruebas en 26 segundos, sin base de
    datos. Esa migración se revirtió, pero la vía quedó probada y es el patrón a reusar.
  - `06-sin-ciudad.spec.ts` es precedente de prueba que se siembra su propio escenario y lo
    restaura al terminar, sin depender del sembrador general. `herramientas/e2e/` ya tiene
    helpers TypeScript de este tipo (`limpieza-cuentas.ts`, `preparar-entorno.ts`), que son
    la vía a reusar si se reemplaza el sembrador Python.
  - Los diez archivos más grandes (de mayor a menor): `34-filtros-y-seleccion-qa` (679 líneas),
    `29-plazo-de-la-prueba` (591), `15-componer-prueba` (589), `20-prueba-y-empresas` (578),
    `25-prueba-del-puesto-estados` (547), `24-ciudad-obligatoria` (441),
    `16-cuestionario-tecnico` (421), `26-editar-vacante-avisos` (407),
    `22-perfil-con-foto-y-cv` (377), `19-banco` (359).
  - 12 archivos mencionan IA/DeepSeek/calificación en el texto; de una revisión rápida, al
    menos `20-prueba-y-empresas.spec.ts` y `16-cuestionario-tecnico.spec.ts` esperan el
    cartel «Está calificando»/«quedó en cola», es decir disparan un pedido real de
    calificación. Cuáles de los doce llaman de verdad al proveedor (frente a los que solo
    verifican un texto de UI con el estado ya sembrado) se confirma caso por caso durante la
    clasificación.
- Pantallas o procesos afectados: ninguno en producción. Solo se reorganizan pruebas de
  `herramientas/e2e/` y se añaden unitarios en `src/`, ambos del repositorio
  `RenaserOsPostulantes`.
- Referencias existentes: [plantilla de spec](../plantilla-spec.md); `.github/workflows/ci.yml`
  y `herramientas/e2e/base-de-datos.ts` del repositorio `RenaserOsPostulantes`.

## Alcance

- Incluye:
  - Clasificar las 329 pruebas de los 43 archivos en **Recorrido**, **Baja a unitario** o
    **Sobra** (paso previo necesario para saber qué tocar).
  - **Aplicar** esa clasificación:
    - Baja a unitario: escribir (o confirmar que ya existe) el test unitario en el módulo
      de `src/` correspondiente, y borrar la prueba e2e equivalente.
    - Sobra: borrar la prueba; si un archivo queda vacío, borrar el archivo.
    - Recorrido: se queda como e2e.
  - Corregir, dentro de las pruebas que se quedan como e2e, lo que esté mal escrito:
    `describe.serial` innecesario, aserciones sobre datos sembrados que puedan
    interceptarse con `page.route` (patrón de `03-orden` / `04-filtros`), selectores
    frágiles.
  - Mantener **Playwright CLI/test en modo headless** como corredor de la suite resultante:
    no se cambia de framework ni se pasa a modo headed por defecto en
    `playwright.config.ts`.
  - Dejar la suite resultante corriendo (montaje manual descrito arriba) y reportar cuántas
    pruebas quedan y cuánto tarda, frente a las 329 / ~12 minutos actuales.
  - **Medir y decidir sobre `sembrar-escenario-e2e.py`** (repositorio
    `RENASER-RECLUTAMIENTO`): para cada prueba que hoy depende de él, construir la
    alternativa (siembra o limpieza propia en TypeScript dentro de `herramientas/e2e/`,
    mismo patrón que `limpieza-cuentas.ts` / `preparar-entorno.ts` / el propio
    `06-sin-ciudad.spec.ts`, o interceptar con `page.route`) y **medir su tiempo contra el
    sembrador actual**. Se migra esa prueba solo si la alternativa resulta claramente más
    rápida; si el sembrador es igual de rápido o más rápido, la prueba se queda con él. El
    script se borra únicamente si, tras revisar todas sus dependientes, ninguna lo sigue
    necesitando. Las ~22 pruebas rotas por el `UPDATE` que falla se arreglan de todas formas
    (migrándolas si ganan, o corrigiendo el `UPDATE` del script si se quedan con él): que
    fallen no es aceptable en ningún caso.
  - **Ninguna prueba de IA real en la corrida automática**: las que necesiten que DeepSeek
    complete una calificación de verdad para pasar quedan excluidas de `npx playwright test`
    por defecto (p. ej. `test.skip` condicionado a una variable de entorno, o un proyecto de
    Playwright aparte) y solo se ejecutan a mano.
- Queda fuera:
  - Cambiar el corredor de pruebas (sigue Playwright) o montar las e2e en el CI: ninguna de
    las dos cosas es parte de esta spec.
  - Hacer que la corrida automática dispare IA real: se excluye de ella, no se activa.
  - Añadir funcionalidad nueva a la aplicación: el cambio es solo de pruebas.

## Comportamiento esperado

1. Se recorren los 43 archivos de `herramientas/e2e/*.spec.ts`, empezando por los diez más
   grandes listados en «Contexto» (más de un tercio de las 11 547 líneas). Si el contexto de
   la sesión no alcanza para los 43, se completan esos diez antes que una pasada superficial
   de todos.
2. Para cada `test(...)` se decide el cubo:
   - **Recorrido**: prueba que el frontend, el backend y la base de datos se conectan de
     punta a punta (login real, una llamada de red real, una redirección entre pantallas).
   - **Baja a unitario**: la aserción central compara datos, aplica una regla de negocio o
     transforma una estructura que vive en un módulo de `src/` sin depender del DOM ni de la
     red (filtrado, orden, cálculo de una nota, formato de fecha, validación de un
     formulario).
   - **Sobra**: repite lo que ya prueba otra e2e con datos o pasos distintos pero la misma
     conexión, o comprueba algo que no puede romperse por sí solo.
3. Se implementa cada cubo: se escriben los unitarios que falten (siguiendo el patrón de
   pruebas ya existente en el módulo, p. ej. `ranking.test.ts`), se borran las pruebas e2e
   migradas y las que sobran, y se corrigen `describe.serial` innecesario, selectores
   frágiles y dependencias de datos sembrados que puedan interceptarse con `page.route`.
4. Para cada prueba que dependa de `sembrar-escenario-e2e.py`, se construye la alternativa
   en TypeScript o con `page.route` y se mide su tiempo contra el sembrador actual. Si la
   alternativa es claramente más rápida, la prueba migra; si el sembrador es igual o más
   rápido, la prueba se queda con él y se anota el tiempo medido de cada lado. Las ~22
   pruebas rotas por el `UPDATE` que falla se arreglan de todas formas, migrando o
   corrigiendo el script según lo que haya ganado. El script se borra solo si, al final,
   ninguna prueba lo sigue necesitando.
5. Para cada prueba que dispare una calificación real con DeepSeek para pasar, se la excluye
   de la corrida automática (`test.skip` condicionado a variable de entorno, o proyecto de
   Playwright aparte) y se documenta cómo correrla a mano.
6. Se revisa que ninguna prueba haya quedado rota, deshabilitada sin motivo documentado o en
   un estado ambiguo: lo que no se pudo arreglar se borró (si sobraba) o quedó anotado como
   pendiente explícito con su razón.
7. Antes de cerrar, se corre la suite e2e resultante en modo headless con el montaje manual
   existente (sin las pruebas de IA real, que se corren aparte) y se confirma que pasa
   completa.
8. Se corre `npm test` (unitarios) para confirmar que los nuevos no rompen nada y que el
   conteo de pruebas unitarias subió en la proporción esperada.
9. Se identifican los **recorridos críticos sin cobertura** (si los hay) y se deja constancia
   de ellos: no se añaden pruebas nuevas para cubrirlos salvo que se decida explícitamente,
   porque el objetivo de esta spec es recortar, no ampliar.

## Referencia visual

No aplica: no cambia ninguna pantalla ni componente de la aplicación.

## Criterios de aceptación

- AC-01: Dada la suite resultante, cuando se cuenta cuántas pruebas e2e quedan, entonces el
  número es menor a 329 y cada una que se mantuvo está justificada como «Recorrido» según el
  criterio de «Comportamiento esperado».
- AC-02: Dada cada prueba que bajó a unitario, cuando se revisa el módulo de `src/` que se
  nombró como destino, entonces existe un test unitario (nuevo o ya existente) que cubre esa
  misma lógica, y la prueba e2e equivalente ya no está en `herramientas/e2e/`.
- AC-03: Dada cada prueba que se clasificó como «Sobra», cuando se busca en
  `herramientas/e2e/`, entonces ya no existe; si el archivo quedó vacío de pruebas, el
  archivo tampoco existe.
- AC-04: Dado `playwright.config.ts` al terminar, cuando se revisa su configuración de
  ejecución, entonces sigue en modo headless por defecto y se sigue invocando con
  Playwright CLI/test (`npx playwright test`), sin cambiar de framework.
- AC-05: Dados los archivos que antes usaban `test.describe.configure({ mode: 'serial' })` o
  `test.describe.serial(...)` sin necesitarlo, cuando se revisan, entonces ya no lo usan, o
  se documenta por qué seguía siendo necesario.
- AC-06: Dadas las pruebas que dependían de datos sembrados y podían interceptarse con
  `page.route` (patrón de `03-orden` / `04-filtros`), cuando se revisan, entonces usan esa
  intercepción en vez de depender del sembrador, salvo que se documente por qué no aplicaba.
- AC-07: Dada la suite e2e resultante, cuando se ejecuta con el montaje manual existente en
  modo headless, entonces corre completa y se reporta su tiempo total, comparado con los
  ~12 minutos de las 329 pruebas actuales.
- AC-08: Dado `npm test` (unitarios), cuando se ejecuta tras los cambios, entonces pasa
  completo y el número de pruebas unitarias aumentó al menos en la cantidad de pruebas que
  bajaron de cubo.
- AC-09: Dado el resultado final, cuando se compara con los recorridos críticos
  identificados en el paso 9, entonces ninguno de los que ya estaban cubiertos hoy quedó sin
  cobertura e2e tras el recorte.
- AC-10: Dada cada prueba que dependía de `sembrar-escenario-e2e.py`, cuando se revisa el
  resultado final, entonces el documento registra el tiempo medido con el sembrador y con la
  alternativa; si la alternativa fue claramente más rápida, la prueba ya no depende del
  script, y si no, se quedó con él. El script se borra solo si, tras esa comparación,
  ninguna prueba lo sigue necesitando.
- AC-11: Dada la suite tras los cambios, cuando se revisa cualquier prueba, entonces ninguna
  quedó rota, deshabilitada sin motivo documentado, ni en un estado ambiguo: lo que no se
  arregló se borró (si sobraba) o quedó anotado como pendiente explícito con su razón.
- AC-12: Dada la corrida estándar de `npx playwright test` en modo headless, cuando se
  ejecuta, entonces ninguna prueba dispara una llamada real al proveedor de IA; las que
  necesiten esa calificación real quedan excluidas de esa corrida y el documento explica
  cómo ejecutarlas a mano.

## Casos límite y errores

- Datos vacíos o inválidos: si un archivo tiene pruebas generadas dinámicamente (un `for`
  que arma varios `test(...)`) y no se puede migrar una a la vez sin romper las demás, se
  migran o se dejan todas juntas y se documenta la razón.
- Usuario sin permisos: no aplica (cambio de pruebas, no de funcionalidad).
- Recarga, reintento o doble envío: no aplica.
- Otros casos relevantes:
  - Si una prueba mezcla una aserción de conexión (recorrido) con una de lógica de negocio
    en el mismo `test(...)`, se queda como e2e y, además, se añade el unitario que cubre la
    parte de lógica, sin borrar el e2e.
  - Si migrar una prueba a `page.route` cambia lo que realmente se está cazando (por ejemplo,
    deja de probar la integración real con el backend), no se migra: se documenta como
    «Recorrido» aunque sea de las que hoy dependen del sembrador.
  - Si el contexto de la sesión se agota antes de cubrir los 43 archivos, se deja constancia
    de qué archivos quedaron sin tocar, en vez de recortarlos con una clasificación
    adivinada.
  - Si una prueba de calificación real con IA es la única forma de cubrir un recorrido
    crítico, no se borra: se queda como e2e manual (excluida de la corrida automática) y se
    documenta que ese recorrido solo se verifica a mano.

## Datos y compatibilidad

- Información que debe guardarse: ninguna; no hay cambios de esquema ni de datos de
  producción.
- No aplica compatibilidad hacia atrás: los cambios son solo de pruebas, no de código de
  producción ni de contratos de API.

## Verificación

- Actor/rol y datos de prueba necesarios: el montaje manual de e2e ya existente
  (`herramientas/e2e/base-de-datos.ts`, contenedor Postgres `renaser.e2e.clone`).
- Recorridos que deben quedar como E2E repetibles: los que la clasificación marque como
  «Recorrido», corriendo en Playwright CLI/test headless con el montaje manual actual.
- Riesgos que QA debe explorar: que ninguna prueba migrada a unitario, a `page.route` o a
  siembra propia en TypeScript haya perdido lo que realmente cazaba; que la suite headless
  siga verde con el montaje manual y sin disparar IA real; que los `describe.serial` que se
  quitaron no dejen pruebas dependientes de orden sin aislarse correctamente.
- Resultado que quiero comprobar personalmente: correr `npx playwright test` completo en
  modo headless (sin las pruebas de IA real) y ver el nuevo número de pruebas y el tiempo
  total, junto con `npm test` en verde. Las pruebas de IA real, si quedó alguna, las corro
  yo a mano siguiendo lo que documente la spec.

## Dependencias y dudas

- Requiere: acceso de escritura a `herramientas/e2e/` y `src/` del repositorio
  `RenaserOsPostulantes`, el montaje manual de e2e (variables de entorno + contenedor
  Postgres `renaser.e2e.clone`) para poder correr la suite y medir tiempos antes de cerrar,
  y acceso de escritura a `scripts/sembrar-escenario-e2e.py` del repositorio
  `RENASER-RECLUTAMIENTO` (para corregir su `UPDATE` si se mantiene, o borrarlo si queda en
  cero usos), y acceso a la variable/entorno que active las pruebas de IA real para poder
  confirmar que están excluidas de la corrida por defecto.
- Decisiones pendientes: ninguna.
