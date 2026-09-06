# Reglas del código

Cómo está organizado el backend y qué reglas no se negocian al tocarlo. Es el documento que hay
que leer antes de escribir una clase, una migración o un endpoint. El estado del proyecto (qué
está hecho y qué falta) vive en [Estado del proyecto](ESTADO-DEL-PROYECTO.md); cómo levantarlo
y probarlo, en [Trabajar en local](TRABAJAR-EN-LOCAL.md).

---

## Un proyecto, dos módulos

Un solo proyecto Spring Boot (`ai-engine`) para dos cosas: **el módulo de selección de personal**
y **el motor de agentes de IA**. Nacieron con dos responsables distintos y no comparten paquetes.

**El motor de agentes** vive entero bajo `ai_engine/ai/`, organizado por capa: `config`,
`controller`, `service`, `repository`, `dto`, `model`, `mapper`, `exception`, `context`, `flow`,
`messaging`, `prompt`, `rag`, `supabase`. Hasta el 24/08/2026 lo llevaba otro desarrollador, que ya
no está en el proyecto; desde entonces es responsabilidad de este equipo y se toca como cualquier otra parte. Lo que sigue en pie es la
**separación entre los dos módulos**: una regla de ArchUnit solo deja cruzar la frontera por las
clases de la lista `ACORDADAS` de `ArquitecturaTest`, y falla si aparece algo que no esté ahí. La
más usada es `ai/exception/ResourceNotFoundException`. Mirar la lista: es la que manda.

**La selección de personal** vive en un paquete por dominio, cada uno con sus capas dentro:

```
com.renaser.ai.ai_engine.<dominio>
├── controller/    los endpoints
├── service/       la interfaz, y la clase en impl/
├── repository/    los repositorios de Spring Data
├── entity/        las entidades JPA
└── dto/           los contratos de entrada y salida
```

| Dominio | Qué guarda |
|---|---|
| `usuario` | Persona, usuario del equipo, roles y permisos |
| `organizacion` | La organización, sus áreas, la plataforma (alta de empresas) y la personalización de instrumentos |
| `solicitud` | La Solicitud de Talento y sus resultados esperados |
| `vacante` | Vacante, puesto, catálogos y requisitos objetivos. Aquí vive también `AlcanceSobreLaVacante`, que decide para todo el panel qué filas alcanza cada alcance |
| `postulacion` | La postulación, su historial, el CV y **la máquina de estados** |
| `pesos` | Las versiones de pesos del embudo |
| `consentimiento` | Textos legales, consentimientos y solicitudes de borrado |
| `notificacion` | Plantillas y registro de correos |
| `archivo` | El almacén de archivos (memoria, disco o bucket según el perfil) |
| `auditoria` | El registro inmutable de quién hizo qué |
| `parametro` | Los valores que Renaser cambia sin programar |
| `portal` | La cara del candidato: un controlador y tres servicios por tema (la cuenta y su acceso, el tablón público, la postulación) |
| `administracion` | La cara de configuración del panel; el borrado 29733 vive aparte en `ServicioBorradoDatos` |
| `seguridad` | Las dos puertas, tokens, contexto y permisos con alcance |
| `comun` | El manejador de errores propio y el candado de Swagger |
| `catalogo` | Los catálogos que el panel consulta |
| `perfilintegral` | Evaluación, criterios, notas y el Perfil de Talento (hito 2) |
| `prueba` | La prueba del puesto: plantillas, intentos y entregables (hito 3) |
| `decision` | La decisión final, sus barreras y evidencias (hito 3) |
| `simulacion` | Sesiones, inscripciones, eventos observables y conversación final |
| `validacion` | El periodo de validación práctica y sus métricas |
| `perfil` | El perfil del candidato, único por persona y transversal a organizaciones |

Dos clases marcan la frontera con el motor de agentes y **hay que actualizarlas al añadir un
controlador nuevo**, porque enumeran los nuestros: `comun/exception/ManejadorErrores` (para que
sus errores no salgan como 500) y `comun/config/ConfiguracionSwagger` (para que sus endpoints
salgan con candado). Está escrito en el javadoc de ambas.

---

## Reglas que no se negocian

- **Toda transición pasa por `MaquinaEstados.transicionar`.** Nunca tocar
  `postulacion.estado_codigo` a mano. Ningún estado fuera de la lista de
  [Estados de la postulación](03-ESTADOS-POSTULACION.md) puede existir.
- **El alcance se aplica dentro de las consultas** con `Permisos.alcanceDe`. Un 404 también
  significa «esto no es tuyo».
- **Que una postulación o una vacante sea tuya lo decide `AlcanceSobreLaVacante`, y solo él.**
  Un servicio nuevo que necesite acotar por vacante lo inyecta y llama a `laPostulacionVisible`,
  `laVacanteVisible` o `alcanzaA`; **no** vuelve a escribir el `== SUS_VACANTES`. Esa línea
  estuvo copiada catorce veces y doce se dejaban `PROPIO` sin tratar; la versión del guardián es
  un `switch` sobre el enum, así que un alcance nuevo rompe la compilación en un sitio en vez de
  colarse en doce. Lo que **no** pasa por ahí, y no es un descuido: la **sesión de simulación**,
  que sirve a varias vacantes a la vez y lleva su propia regla (`tocaUnaVacanteSuya`, y el conteo
  con su cuarto caso en `contarInscritos`); y la **solicitud de talento**, cuyo dueño es alguien
  del equipo (`ServicioSolicitudesImpl`).
  - Lo que queda de aquello: `FiltroAlcance.responsableOFiltroNulo()` sigue devolviendo nulo con
    `PROPIO` igual que con `TODO`. Su único uso, la bandeja en `ServicioPostulacionesPanelImpl`,
    lo trata aparte con `noAlcanzaANadieEnElPanel()`, y el javadoc del método avisa. Quien lo use
    en un sitio nuevo tiene que hacer lo mismo.
- **El esquema solo cambia con una migración nueva.** Nunca editar una ya aplicada. Flyway es el
  dueño del esquema (`ddl-auto: validate`).
- **Antes de numerar una migración, mirar cuál es la más alta que existe, y no solo en tu rama.**
  Otras ramas también crean migraciones: la de simulación tuvo que pasar de `V16` a `V18`, la de
  los inscritos de `V37` a `V40`. **Git no ve ese choque**: `V40__una.sql` y `V40__otra.sql` son
  dos archivos distintos y los fusiona sin avisar; quien se entera es Flyway al arrancar. Hay una
  prueba que lo caza (`MigracionesSinChoqueTest`), pero cada rama pasa el CI en verde por su
  cuenta: solo salta cuando la segunda se fusiona.
- **Nada se recalcula hacia atrás.** Cada nota queda atada a la versión de pesos de su vacante.
- **Los bancos y las versiones se archivan, no se borran** (RF-138). Publicada, una versión no
  vuelve a borrador; la salida a un error es una versión nueva.
- **`ServicioContexto` no lleva caché.** Desde la V40 el reparto de permisos se edita por API y
  `FiltroIdentidad` lo relee en cada petición: un cambio vale sin desplegar y sin volver a entrar.
  Una caché ahí es justo lo que lo rompería.
- **Los agregados con dueño no se buscan por id suelto desde el panel.** La regla de ArchUnit
  prohíbe `findById` sobre repositorios de agregados con dueño; al añadir una llamada legítima hay
  que escribirla en la lista `LLAMADAS_SIN_DUENO_ACORDADAS` de `ArquitecturaTest` con su porqué.
- **Columnas con cifra final llevan `@Column` explícito.** `riesgo1` no casa con `riesgo_1` ni
  `genteACargo` con `gente_a_cargo` por la estrategia de nombres, y revienta recién al validar el
  esquema real.
- **Archivar y crear en la misma transacción pide `saveAndFlush`.** Hibernate inserta antes de
  actualizar; sin el flush, el índice parcial de «solo uno vivo» revienta.

---

## Cuidado con Jackson: el proyecto va con la 3, no con la 2

Spring Boot 4 trae **Jackson 3** (`tools.jackson.databind.ObjectMapper`), no el
`com.fasterxml.jackson.databind` de toda la vida. El de siempre **compila** (entra como
transitiva) pero **no existe en tiempo de ejecución**, y el síntoma no dice nada de Jackson: la
clase que lo usa deja de registrarse como bean y salen decenas de errores de contexto («No
qualifying bean of type ServicioEvaluacion»). Costó 37 tests en rojo averiguarlo.

En código de producción: `tools.jackson`. Las pruebas de integración siguen con Jackson 2, que
está en su classpath, así que `integracion/soporte/RespuestaV3` usa la 2 a propósito.

---

## Dónde vive cada cuenta que se hace en un solo sitio

| Qué | Dónde, y por qué no copiarlo |
|---|---|
| La nota combinada de la evaluación | `ServicioCalificacion.notaCombinada`. Mezclar lo cerrado con lo abierto es una interpretación sin confirmar con el cliente: el día que cambie tiene que cambiar a la vez para la nota de la etapa y para lo que ve el panel |
| Cómo se cuentan los inscritos de una sesión | `ServicioSimulacionImpl.contarInscritos`. La lista, el detalle y `/inscritos` se separaron tres veces seguidas; mientras hubiera dos implementaciones, arreglar una dejaba la otra rota |
| El nombre de un candidato respetando la anonimización | `NombresDeUsuarios`: sin usuario, sin persona o con la persona borrada devuelve `(anonimizado)`, nunca una cadena vacía |
| Qué instrumento (banco, pesos, plantillas, pruebas) lee cada empresa | `organizacion/service/DuenoDelInstrumento`. Es el único punto que interpreta las banderas de personalización |
| La nota de la etapa de la prueba | `ponderarSiLaRubricaEstaEntera`: suma solo si todos los criterios tienen puntaje. Ver [Prueba del puesto](PRUEBA-DEL-PUESTO.md) |

---

## Al editar los documentos

- Cada documento debe leerse solo: quien lo abra no necesita otro para entenderlo. Los 01 a 05
  abren con contexto propio y cierran con enlaces a los demás.
- **Nunca citar un requisito por su número entre documentos.** Los números cambian cada vez que
  se inserta uno y las referencias quedan apuntando a otra cosa sin que nada falle. Citar el
  nombre de la sección: `(ver «Panel de gestión»)`, `(ver «Auditoría» en los no funcionales)`.
  Los números RF/RNF solo existen dentro de su propio documento.
- Verificar que las secciones citadas existan: `grep -ohE '«[^»]+»' docs/0[3-4]*.md | sort -u`
- **Las cifras se verifican antes de escribirlas.** El número de tests sale de correrlos, el de
  migraciones de `ls src/main/resources/db/migration`, el de tablas de las migraciones; no de
  recordarlos.
- Lo que se hizo un día concreto va en un documento de avance (`AVANCE-*`, `REPORTE-*`). El
  estado presente va en [Estado del proyecto](ESTADO-DEL-PROYECTO.md), y ahí se **borra lo que
  dejó de ser cierto**, no se acumula.

### Diagramas

`docs/diagramas/` son HTML autocontenidos. Paleta tomada del panel: papel `#f5f5f3`, tinta
`#0d0d0d`, apagado `#70706b`, línea `#e3e3df`, y el semáforo del propio sistema verde `#2f6e51`
/ ámbar `#9b6a22` / rojo `#a43b36`. Revisar siempre el render (`google-chrome --headless
--screenshot` genera un PNG que hay que mirar): un verificador no detecta flechas que cruzan por
detrás de una caja ni texto desbordado.

---

## Qué se actualiza cuando cambia algo

| Si cambió... | Actualizar |
|---|---|
| Se implementó una funcionalidad o se cerró un hito | La tabla de hitos y «lo que falta» en [Estado del proyecto](ESTADO-DEL-PROYECTO.md) |
| Se añadió una migración | El rango `V1-Vxx` en Estado del proyecto y, si crea tablas, el [Modelo de datos](05-MODELO-DE-DATOS.md) y el [Diccionario](07-DICCIONARIO-DE-DATOS.md) |
| Cambió el número de tests | La fila de tests en Estado del proyecto y [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md) |
| Se añadió un paquete de dominio | La tabla de arriba |
| Se añadió un controlador | `ManejadorErrores`, `ConfiguracionSwagger` y [Las APIs](09-APIS.md) |
| Se añadió un permiso | [Roles y permisos](04-ROLES-Y-PERMISOS.md) |
| Un pendiente dejó de serlo, o apareció uno nuevo | «Lo que falta» en Estado del proyecto, o [Defectos conocidos](DEFECTOS-CONOCIDOS.md) |
| Un placeholder se reemplazó por el valor definitivo | Donde se nombre ese placeholder |

Hay un recordatorio automático: al terminar cada turno, si hay código o migraciones sin commit y
`docs/ESTADO-DEL-PROYECTO.md` no se tocó, el hook `.claude/hooks/recordar-estado-del-proyecto.sh`
avisa (una vez cada media hora, sin bloquear nada). Hasta el 06/09/2026 miraba `CLAUDE.MD`, que
ya no guarda estado.
