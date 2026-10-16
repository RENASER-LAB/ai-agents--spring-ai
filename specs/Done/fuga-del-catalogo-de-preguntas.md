# Cerrar la fuga del catálogo de preguntas y retirar la pantalla «Pruebas»
Estado: TERMINADO

## Objetivo
Que ninguna empresa pueda leer ni bloquear las preguntas de examen de otra.

Las preguntas de la prueba del puesto antigua viven en un catálogo único de toda la plataforma y
sin dueño. Si la empresa A escribe «Describe cómo cerraste la caja el mes que faltó dinero», la
empresa B recibe ese texto al pedir el catálogo. Ese texto es el examen de A. Además, si A usa el
código `T01`, ninguna otra empresa podrá usarlo nunca, y no hay forma de borrarlo.

La sección «Pruebas» del panel ya no se usa, porque las vacantes nuevas arman su prueba en el
editor de la propia vacante. La salida es retirar esa sección y dejar el catálogo solo para la
plataforma.

## Contexto
- Comportamiento actual (comprobado en el código el 02/10/2026):
  - `GET /api/v1/panel/plantillas-prueba/preguntas` devuelve **todas** las preguntas del catálogo
    (`findAll()` en `ServicioPlantillaPruebaImpl.listarPreguntasCatalogo`), sean de la empresa que
    sean. Puede pedirlo cualquier cuenta con `elegir_plantilla_prueba` (TALENTO, DIRECCION,
    RESPONSABLE_AREA) de cualquier empresa.
  - El panel lo pide sin que nadie lo note: `ListasDeLaPrueba.tsx` carga el catálogo entero al
    abrir una versión, aunque esa versión sea de solo lectura. Basta con abrir una prueba prestada
    de RENASER para que el catálogo completo llegue al navegador.
  - `POST .../plantillas-prueba/preguntas` crea la pregunta sin dueño. `pregunta_prueba.codigo`
    es `UNIQUE` en toda la tabla (V15) y no hay ningún endpoint que borre una pregunta. El primero
    que usa un código lo ocupa para todas las empresas y para siempre.
  - `POST .../versiones/{id}/preguntas` (`elegirPregunta`) acepta cualquier id del catálogo. Como
    los ids son correlativos, una empresa con un borrador propio puede ir eligiendo ids y leer el
    texto de preguntas ajenas dentro de su propia versión.
  - Está documentado como defecto abierto n.º 1 en `docs/DEFECTOS-CONOCIDOS.md` y avisado en
    `docs/PRUEBA-DEL-PUESTO.md`.
- Lo que el editor nuevo ya resolvió: las vacantes nuevas (PR #108 y #110) guardan sus preguntas
  en su propia versión (`version_banco`, que tiene `organizacion_id`). «Copiar de otra vacante»
  solo ofrece versiones de la propia empresa (`propiasPublicadasDe` y
  `pruebasPropiasPublicadasDe`), y sus códigos no son únicos en toda la plataforma. Ahí no hay
  fuga. **El bug solo sigue vivo en el catálogo antiguo y en su pantalla.**
- Pantallas o procesos afectados:
  - Panel (`RenaserOsPostulantes`): la entrada «Pruebas» del menú (`panel/menu.ts`) y las rutas
    `/admin/pruebas` y `/admin/pruebas/versiones/:versionId` (`app/App.tsx`, `rutas.ts`,
    `Armazon.tsx`). Esas rutas cargan la carpeta `panel/pruebas/` entera, que nadie más importa.
  - Panel: el aviso con el enlace «Ir a las pruebas del puesto» en la configuración de una vacante
    antigua (`Vacante.tsx`, cerca de la línea 3965).
  - Backend: `PlantillaPruebaController` y `ServicioPlantillaPruebaImpl`, en concreto
    `listarPreguntasCatalogo`, `crearPreguntaCatalogo` y `elegirPregunta`.
- Quién sigue usando el catálogo, y siempre como plataforma: el sembrador
  `scripts/sembrar-datos-de-prueba.py`, los scripts de carga y unos 14 tests de integración.
  `FlujoDosEmpresasIT` arma la prueba con `tokenPlataforma`. Las vacantes antiguas que ya rinden
  una plantilla leen sus preguntas por dentro (rendición, calificación y ranking con
  `findByIdIn`) y no pasan por estos endpoints.
- Referencias: `docs/DEFECTOS-CONOCIDOS.md` §1, `docs/PRUEBA-DEL-PUESTO.md`,
  `specs/Done/prueba-del-puesto-con-el-editor.md` (decisión 13 y «Queda fuera») y
  `DuenoDelInstrumento`.

## Alcance
- Incluye:
  - Retirar la sección «Pruebas» del panel para todas las empresas, RENASER incluida: la entrada
    del menú, las dos rutas, la carpeta `panel/pruebas/` y las funciones de `api/panel.ts` que
    solo usaba esa carpeta.
  - Cerrar el catálogo a las empresas. Listar, crear y elegir preguntas del catálogo queda solo
    para la organización plataforma.
  - Quitar el enlace a la pantalla retirada en la configuración de las vacantes antiguas.
  - Actualizar la documentación: la entrada 1 sale de `docs/DEFECTOS-CONOCIDOS.md`, y
    `docs/PRUEBA-DEL-PUESTO.md` pasa a decir que el catálogo es solo de la plataforma.
  - Ajustar las pruebas del panel que dependen de la pantalla retirada (comprobado el
    05/10/2026):
    - `herramientas/e2e/15-componer-prueba.spec.ts` recorre la pantalla vieja de punta a punta:
      se borra entero.
    - `herramientas/e2e/41-logotipo.spec.ts` (AC-02) entra en `/admin/pruebas` solo para mirar
      la cabecera: pasa a entrar en otra pantalla del panel, por ejemplo `/admin/archivadas`.
    - `src/panel/menu.test.ts` y `src/panel/Armazon.test.tsx` esperan «Pruebas» en el menú y
      la ruta `/admin/pruebas/versiones/4`: se actualizan.
  - No se tocan las pruebas del editor nuevo que leen plantillas viejas para montar una vacante
    antigua: `48-prueba-del-puesto-con-el-editor.spec.ts` (recorrido 7) y
    `ayuda-preguntas-propias.ts` usan `GET /plantillas-prueba` y `GET .../{id}/versiones`, que
    esta spec no cierra. Tampoco las listas de 404 permitidos de `08-…`, `13-…`,
    `ayuda-tecnica.ts` y `ayuda-prueba.ts`.
- Queda fuera:
  - Dar dueño al catálogo (`organizacion_id`, código único por empresa) y permitir borrar
    preguntas. Con el catálogo solo para la plataforma ninguna empresa escribe en él, así que
    ninguna bloquea un código a otra.
  - Borrar la tabla, las plantillas o los endpoints de plantillas, versiones, entregables y
    rúbrica. Las vacantes antiguas, el sembrador y los tests los siguen usando, y no filtran nada
    porque ya comprueban la empresa. Se van con la limpieza del préstamo, cuando no quede ninguna
    vacante antigua.
  - El préstamo de las pruebas de RENASER a otras empresas. Lo elimina
    `specs/pesos-con-candidatos-y-fin-del-prestamo.md` (decisión 10). Esta spec no lo toca y no
    depende de él: los candados del catálogo valen tanto con préstamo como sin él.
  - Las vacantes nuevas y el editor de preguntas: no cambian.

## Comportamiento esperado
1. Quien entra al panel ya no ve «Pruebas» en el menú, sea de la empresa que sea.
2. Si abre una dirección antigua guardada (`/admin/pruebas` o `/admin/pruebas/versiones/…`), el
   panel lo lleva a la lista de vacantes, sin pantalla de error.
3. Una vacante antigua que todavía rinde una prueba del puesto existente conserva el selector de
   versión tal como está. Cuando no hay ninguna prueba que ofrecer, el aviso mantiene su
   explicación pero ya no lleva a una pantalla que no existe.
4. Una empresa que no es la plataforma pide el catálogo, crea una pregunta o elige una por id a
   través de la API. Recibe 404, como cualquier recurso ajeno, y no se escribe nada.
5. La plataforma sigue listando, creando y eligiendo preguntas como hoy, de modo que el sembrador,
   los scripts y los tests siguen funcionando.
6. En las vacantes antiguas que ya rinden una plantilla nadie nota el cambio: el candidato ve sus
   preguntas, se califican y salen en el ranking igual que antes.

## Referencia visual
No cambia ninguna pantalla. Solo se quitan una sección, su entrada del menú y un enlace, sin
diseño nuevo.

## Criterios de aceptación
- AC-01: Dado un usuario de cualquier empresa, RENASER incluida, con `elegir_plantilla_prueba`,
  cuando entra al panel, entonces el menú no muestra «Pruebas».
- AC-02: Dado ese usuario, cuando abre `/admin/pruebas` o `/admin/pruebas/versiones/5`, entonces
  llega a la lista de vacantes sin error.
- AC-03: Dada una empresa que no es la plataforma, cuando pide
  `GET /api/v1/panel/plantillas-prueba/preguntas` (con `tipo` o sin él), entonces recibe 404 y
  ningún texto de pregunta.
- AC-04: Dada esa empresa, cuando hace `POST /api/v1/panel/plantillas-prueba/preguntas`, entonces
  recibe 404, no se crea ninguna fila y el código queda libre.
- AC-05: Dada esa empresa con un borrador propio, cuando hace
  `POST .../plantillas-prueba/versiones/{id}/preguntas` con el id de una pregunta que existe,
  entonces recibe 404 y la versión no gana esa pregunta.
- AC-06: Dada la plataforma, cuando lista, crea y elige preguntas, entonces todo funciona como hoy
  y el sembrador corre de principio a fin.
- AC-07: Dada una vacante antigua con una prueba del puesto publicada y candidatos dentro, cuando
  un candidato la rinde y se califica, entonces ve las mismas preguntas y su nota sale igual que
  antes.
- AC-08: Dada una vacante antigua con «prueba del puesto existente», cuando se abre su
  configuración, entonces el desplegable de versiones se comporta igual que antes de esta spec
  (lo que ofrezca depende del préstamo, no de esta spec), y el aviso sin pruebas no enlaza a la
  pantalla retirada.
- AC-09: Dado el cambio desplegado, cuando se lee `docs/DEFECTOS-CONOCIDOS.md`, entonces la fuga
  ya no aparece, y `docs/PRUEBA-DEL-PUESTO.md` dice que el catálogo es solo de la plataforma.

## Casos límite y errores
- Datos vacíos o inválidos: para la plataforma, las mismas validaciones de hoy. Para las demás
  empresas, el 404 llega antes de validar el cuerpo.
- Usuario sin permisos: sin `elegir_plantilla_prueba` o `editar_plantillas_prueba` sigue el 403
  de hoy. El permiso se comprueba antes que la empresa.
- Recarga, reintento o doble envío: un POST rechazado no escribe nada, así que repetirlo da el
  mismo 404.
- Empresas que personalizaron sus pruebas antes de la fase 2: si alguna tiene un borrador propio,
  ya no puede añadirle preguntas del catálogo. Sus versiones publicadas siguen intactas y se
  pueden leer. Se acepta porque la sección ya no se usa.
- Quitar una pregunta de un borrador propio
  (`DELETE .../versiones/{id}/preguntas/{preguntaId}`) sigue permitido, porque no lee ni escribe
  el catálogo.
- Ver una versión (`GET .../versiones/{id}`) sigue mostrando el texto de sus preguntas elegidas
  a quien hoy puede verla. Es contenido de esa prueba, no del catálogo. Quién puede ver las
  versiones de RENASER lo decide `specs/pesos-con-candidatos-y-fin-del-prestamo.md`.
- Una pestaña con la pantalla antigua ya abierta: al navegar o recargar, llega a la lista de
  vacantes.

## Datos y compatibilidad
- Información que debe guardarse: nada nuevo. No hay migración.
- Comportamiento para registros existentes: las filas del catálogo, las elecciones de cada
  versión y las respuestas se quedan como están. Tienen claves foráneas y respuestas que dependen
  de ellas.
- Restricciones de compatibilidad:
  - Qué organización es la plataforma se lee de `organizacion.es_plataforma`
    (`DuenoDelInstrumento.plataforma()`), nunca de un id fijo.
  - Los scripts y tests que usan el catálogo lo hacen como plataforma y siguen funcionando. Si
    alguno lo usara con otra empresa, se cambia para que lo haga como plataforma.

## Verificación
- Actor/rol y datos de prueba necesarios: un usuario TALENTO de la plataforma, un usuario TALENTO
  de una segunda empresa (ACME, como en `FlujoDosEmpresasIT`) y una vacante antigua con una
  plantilla publicada y un candidato que la rinde.
- Recorridos que deben quedar como E2E repetibles: el menú sin «Pruebas» y la redirección de las
  dos rutas antiguas. El cierre de la API (AC-03 a AC-06) se prueba con un test de integración de
  dos empresas, no con un E2E. Ese test va en una clase nueva (por ejemplo
  `CatalogoDePreguntasSoloPlataformaIT`), no en `FlujoPruebaIT` ni en `FlujoPruebaPropiaIT`,
  para no chocar con `specs/volver-a-entrar-en-validacion.md`, que se puede implementar en
  paralelo.
- Los tests de integración que hoy crean preguntas del catálogo (`FlujoPruebaIT`,
  `FlujoPreguntasPropiasIT`, `FlujoPruebaPropiaIT`, `FlujoDosEmpresasIT` y otros) entran con
  `dev-login`, que siempre usa la organización plataforma (`ServicioAccesoEquipoImpl.devLogin`).
  Deben seguir verdes sin cambios; si alguno falla con 404, está creando preguntas como otra
  empresa y hay que pasarlo a la plataforma.
- Riesgos que QA debe explorar con navegador: la configuración de una vacante antigua (el selector
  y el aviso sin pruebas), y la ficha y el ranking de una vacante antigua con la prueba rendida.
  Hay que confirmar que nada de eso dependía de la pantalla retirada.
- Resultado que quiero comprobar personalmente: entrar al panel y no ver «Pruebas», y abrir una
  vacante antigua y ver su prueba como siempre.

## Dependencias y dudas
- Requiere: antes de desplegar, comprobar en producción que ninguna empresa distinta de la
  plataforma creó preguntas en el catálogo. Se espera cero, porque personalizar nunca tuvo botón
  en el panel.
  ```sql
  select a.organizacion_id, count(*)
    from auditoria a join organizacion o on o.id = a.organizacion_id
   where a.accion = 'crear_pregunta_prueba' and not o.es_plataforma
   group by a.organizacion_id;
  ```
  Si sale vacío, cerrar el catálogo basta. Si sale alguna fila, esas preguntas seguirían visibles
  para la plataforma: hay que parar y decidir antes de seguir.
- Relación con otra spec: la entrada «Pruebas» y la ruta `/admin/pruebas` vuelven en
  `specs/pruebas-indice-de-la-empresa.md`, como el índice de solo lectura de las pruebas de la
  empresa, construido sobre el editor nuevo. Hasta que esa spec esté desplegada, la ruta redirige a Vacantes. Esta spec no debe
  reservar ni bloquear esa dirección para otro uso.
- Se solapa con `specs/pesos-con-candidatos-y-fin-del-prestamo.md`: su AC-14 y su paso de
  verificación abren `/admin/pruebas` como empresa cliente. Si esta spec se despliega antes, esa
  pantalla ya no existe y esa parte se da por cumplida. Si va después, no cambia nada aquí.
- Decisiones pendientes: ninguna.
