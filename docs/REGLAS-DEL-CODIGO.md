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
| `organizacion` | La organización, sus áreas, la plataforma (alta de empresas) y la personalización de instrumentos. Desde la `V85`, el «Perfil de la empresa» (`PresentacionEmpresaController`, `ServicioPresentacionImpl`, las reglas en `ReglasDeLaPresentacion` y el logo y la portada, re-codificados sin metadatos, en `ImagenesDeLaEmpresa`). Desde la `V86`, el brochure en PDF: `ServicioBrochureImpl` (lo mínimo, el contenido y el último PDF de cada empresa recordado en memoria), `ContenidoDelBrochure` (lo que lleva, que es también la huella del recuerdo), `PdfDelBrochure` (las tres plantillas) y los catálogos `PlantillaDelBrochure` y `ColorDelBrochure` |
| `solicitud` | La Solicitud de Talento y sus resultados esperados |
| `vacante` | Vacante, puesto, catálogos y requisitos objetivos. Aquí vive también `AlcanceSobreLaVacante`, que decide para todo el panel qué filas alcanza cada alcance |
| `postulacion` | La postulación, su historial, el CV y **la máquina de estados** |
| `pesos` | Las versiones de pesos del embudo |
| `consentimiento` | Textos legales, consentimientos y solicitudes de borrado |
| `notificacion` | Plantillas y registro de correos, la campana del portal y, desde la `V92`, las preferencias de aviso de cada persona (`PreferenciasDeAviso`), que el correo y la campana leen antes de salir |
| `archivo` | El almacén de archivos (memoria, disco o bucket según el perfil) |
| `auditoria` | El registro inmutable de quién hizo qué |
| `parametro` | Los valores que Renaser cambia sin programar |
| `portal` | La cara del candidato: un controlador y cuatro servicios por tema (la cuenta y su acceso, el tablón público, la postulación y, desde la `V92`, los ajustes de avisos, `ServicioNotificacionesPortalImpl`). Desde la `V85`, un segundo controlador sin token, `EmpresasPortalController`, con el perfil público de cada empresa y su logo y portada (`ServicioEmpresasPortalImpl`), y desde la `V86` su brochure en PDF, que arma `organizacion` |
| `administracion` | La cara de configuración del panel; el borrado 29733 vive aparte en `ServicioBorradoDatos` |
| `seguridad` | Las puertas —portal, panel y, desde la V73, integraciones, con la firma del bot en `FiltroFirmaWhatsapp`—, tokens, contexto y permisos con alcance |
| `comun` | El manejador de errores propio y el candado de Swagger. Desde la `V84`, también el motor de PDF (`comun/pdf/`), pensado para cualquier documento que genere el backend: ver «Dónde vive cada cuenta que se hace en un solo sitio» |
| `catalogo` | Los catálogos que el panel consulta |
| `perfilintegral` | Evaluación, criterios, notas y el Perfil de Talento (hito 2) |
| `prueba` | La prueba del puesto: plantillas, intentos y entregables (hito 3) |
| `decision` | La decisión final, sus barreras y evidencias (hito 3) |
| `simulacion` | Sesiones, inscripciones, eventos observables y conversación final |
| `validacion` | El periodo de validación práctica y sus métricas |
| `perfil` | El perfil del candidato, único por persona y transversal a organizaciones |
| `resena` | Las reseñas de empresas a quien contrataron, sus respuestas y la moderación de reportes (V63). No puntúan |
| `colaborador` | La gestión de personas (V64): la ficha del colaborador, sus periodos y su situación laboral, el alta, la carga por Excel, los cambios, el cese y el reingreso. Las sedes y los cargos viven en `organizacion` (`EstructuraController`) y la sesión del panel en `seguridad` |
| `whatsapp` | Las novedades de la postulación por WhatsApp (V73): el vínculo de un número con una postulación, la cola de avisos hacia el bot (`AvisosPorWhatsapp`, `EnvioAlBot`), la integración firmada que llama el bot, el cliente hacia él en tres modos (apagado, falso y real) y el bot falso. El botón del portal entra por `PortalController`, y el sondeo vive en `comun/programado/SondeoWhatsapp`. Ver [el contrato](CONTRATO-NOVEDADES-POR-WHATSAPP.md). Desde la V76, el contrato v2: la consulta del contexto (`ContextoWhatsappController`, `ServicioContextoWhatsappImpl`, con `FichaPublica`, `ProcedimientoPublico`, `LoQueLeToca` y `ReferenciasOpacas`), el resultado consultable y los eventos finales (`ResultadosPorWhatsapp`, `EnvioDeEventos`). Ver [el contrato v2](CONTRATO-CONTEXTO-Y-RESULTADOS-POR-WHATSAPP-V2.md) |
| `informe` | Los informes por etapa (V84): el armado de cada uno (`ArmadoDelCv`, `ArmadoDeLaEvaluacion`, `ArmadoDeLaPrueba`, con lo común en `LoComunDelInforme`), su PDF (`PdfDelInforme`), el servicio con los permisos y los `.zip`, y el redactor del informe (`AgenteRedactorInforme`, con sus comprobaciones en `RedaccionDelInforme` y sus textos en `texto_informe`). Desde la V94, el seguimiento de pendientes entre etapas: el agente (`AgenteSeguimientoPendientes`), lo que se le manda, cómo se comprueba lo que devuelve y cómo se pone en el informe (`PendientesEntreEtapas`, sin base de datos ni IA), qué sigue abierto en cada etapa con los datos de hoy (`LoQueSigueAbierto`), el guardado en `seguimiento_pendiente` (`SeguimientosDelInforme`, entidad `SeguimientoPendiente`) y lo que la cola le pregunta al informe al terminar una etapa (`SeguimientoTrasCalificar`, que implementa `ArmadoDelInforme`). Los textos y el seguimiento se piden igual, una vez por huella (`PedidoSinRepetir`, sobre las filas `PedidoConHuella`). **Sin controlador propio**: sus rutas viven en `postulacion/controller/InformesController`, como el Excel del ranking, porque ese paquete es el que ya reconocen `ManejadorErrores` y `ConfiguracionSwagger`. Ver [Las APIs](09-APIS.md), «Los informes por etapa» |

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
  cuenta: solo salta cuando la segunda se fusiona. **Y un número por debajo de la última
  migración aplicada ya no entra**: Flyway no aplica migraciones fuera de orden (`out-of-order`
  no está activado), y en una base que ya tenga una más alta no arranca. Pasa ya: la `V84`, de
  los informes por etapa, llegó antes que la `V81`, la `V82` y la `V83` que reservaban otras
  specs, y el perfil de la empresa, que reservaba la `V81`, entró como `V85` porque la `V84` ya
  estaba aplicada en producción. Las demás reservas de la reunión del 13/11 se corrieron
  (el brochure ya entró como `V86`; convocatoria `V87`, video por webcam `V88`), y la del Excel
  resumido (`V83`) tendrá que subir al construirse. Las pruebas sin completar y «Darle más tiempo»
  entraron como `V89`, antes que la `V87` y la `V88`: si esas dos no llegan a producción antes que
  la `V89`, también tendrán que subir. Lo mismo con la `V91` (video de la empresa): las
  notificaciones del candidato entraron antes como `V92` y `V93`. Y el seguimiento de pendientes,
  que reservó la `V82` y después la `V90`, entró como `V94`. **Una reserva no
  es un número**: el de verdad se elige al construir y siempre es mayor que la última aplicada.
  Un número que se salta a propósito se anota en `HUECOS_DELIBERADOS` de
  `MigracionesSinChoqueTest`, que hoy admite la `V81` a la `V83`, la `V87`, la `V88`, la `V90` y la `V91`.
- **Nada se recalcula hacia atrás por su cuenta.** Cada nota queda atada a la versión de pesos de
  su vacante. La única vía para cambiarla es cambiar la versión de la vacante (`V74`), que
  recalcula a todos a la vez por `RecalculoConOtrosPesos`, sin IA, sin máquina de estados y sin
  tocar `decision`. Ver [Una vacante, una versión](DECISION-UNA-VACANTE-UNA-VERSION.md). Cambiar
  «Qué buscar en el CV» (`V80`) no toca los pesos, pero rehace la nota del CV de quien la tenía:
  bloquea la vacante como el cambio de pesos, recalcula sin lo buscado sin mover a nadie y vuelve a
  buscar por el carril `REBUSCA` (`vacante/service/QueBuscarEnElCvDeLaVacante`).
- **Quien escribe una nota que depende de los pesos, o que dice con qué versión se calculó, toma
  primero el cerrojo de la vacante** (`VacanteRepository`, fragmento `CerrojoDeLosPesos`:
  `bloquearParaCalificar`, `…PorId` o `…YReleer`, un `FOR SHARE` hasta el final de la
  transacción). Cambiar los pesos bloquea la vacante, pone la versión nueva y reescribe notas y
  postulaciones en una transacción; una nota que leyera la vacante sin esperar saldría con la
  versión de antes y pisaría lo recalculado. Tres reglas, cada una de un ciclo de QA:
  - **La vacante antes que nada**: antes de leer o escribir ninguna nota, intento, evaluación o
    postulación. Pedirla después de escribir una de esas filas es el cruce con el cambio, que
    espera a esa fila mientras tú esperas a la vacante.
  - **Quien mueve la postulación antes de escribir la nota** —entregar la prueba o la evaluación,
    el barrido de vencidas, el ajuste a mano y la IA que cierran la prueba del editor— toma el
    cerrojo **al empezar**, con `bloquearParaCalificarYReleer`; el segundo, ya al escribir la
    nota, no espera a nadie.
  - **Lo que se escribe después de esperar parte de la fila releída**: `…YReleer` vuelca lo
    pendiente y relee la postulación con el cerrojo puesto, porque la de memoria trae el grupo
    de antes del cambio y no ve lo que el equipo hizo en ese rato.

  Los `FOR SHARE` no se esperan entre sí: dos notas a la vez no se frenan. Un camino nuevo que
  escriba `nota_etapa`, `nota_criterio`, `perfil_talento` o el grupo entra por el mismo sitio.
- **`Postulacion` lleva `@DynamicUpdate`**, como `Vacante`: cada guardado escribe solo las
  columnas que cambió. La fila la escriben a la vez la máquina de estados (estado, `movido_en`,
  motivo de cierre) y el recálculo del Perfil Integral (grupo); con el `UPDATE` de la fila entera,
  el último en confirmar devolvía la columna del otro a lo que leyó. **No ordena dos escrituras
  del mismo dato**: eso sigue abierto, ver el 18 de [Defectos conocidos](DEFECTOS-CONOCIDOS.md).
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
- **Cada escritura sobre una ficha de colaborador empieza bloqueando su fila**
  (`laFichaParaEscribir` → `ColaboradorRepository.bloquear`, `PESSIMISTIC_WRITE`) y lee después. Dos anulaciones o dos ceses a la
  vez pasan de uno en uno: la segunda ve lo que dejó la primera y responde 409 en vez de
  repetirlo. Un método nuevo que escriba en la ficha, sus periodos o su situación tiene que
  entrar por el mismo sitio.
- **El sueldo del colaborador no sale de un servicio sin mirar `ver_sueldos`**, y nunca va a la
  auditoría: ahí van los nombres de los campos que cambiaron. `ver_auditoria` no es `ver_sueldos`.

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
| Qué pesos y qué plantillas de evaluación lee cada empresa | `organizacion/service/DuenoDelInstrumento`. Es el único punto que interpreta las banderas de personalización. **Desde la `V74` no resuelve el banco ni las pruebas**: ya no son valores de `Instrumento`, se piden por la organización de quien pregunta, y una vacante de antes que leía el banco de RENASER lo tiene en `vacante.version_banco_fija_id` |
| Rehacer las notas de toda una vacante cuando cambia su versión de pesos (`V74`) | `perfilintegral/service/RecalculoConOtrosPesos`, llamado solo desde `ServicioPesosDeLaVacanteImpl`, dentro de su transacción. El Perfil Integral va por `PuenteCalificacionIa.recalcularConOtrosPesos`, que es `recalcularSinMover` **sin publicar `NotaDelPerfilRecalculada`**: con el aviso, el pase automático movería a gente por un cambio de pesos. La simulación y la validación se reescalan desde `puntaje_original` (`V75`) y se suman con `calcularNotaEtapa` |
| La nota de la etapa de la prueba | `ponderarSiLaRubricaEstaEntera`: suma solo si todos los criterios tienen puntaje. Ver [Prueba del puesto](PRUEBA-DEL-PUESTO.md) |
| La nota de las preguntas propias de una vacante (`V66`) | `perfilintegral/service/CalificacionPorPuntos`, calculada al leer. La usan la nota de lo cerrado al entregar, la del Perfil Integral, el desglose de la ficha, lo que recibe el evaluador y el cambio de puntos: si cada uno sumara por su cuenta, la ficha y el ranking dirían cifras distintas |
| Qué puede guardarse y publicarse en las preguntas propias (`V66`) | `perfilintegral/service/ReglasDePuntos`. Lo usan guardar una pregunta, publicar, cambiar los puntos y validar lo que propone la IA; con una copia en cada uno, el editor dejaría guardar algo que la publicación rechaza por otra razón |
| La envoltura de una guía de calificación escrita por una empresa | `ai/service/impl/EnvolturaDeGuia`, la misma para la prueba del puesto y para las preguntas propias. Dos copias de una defensa son dos sitios donde arreglarla |
| La nota de la prueba escrita en el editor (`V67`) | `prueba/service/CalificacionDeLaPruebaPropia`, calculada al leer: parte automática más parte calificada, por id de criterio. La usan el editor (balance y lo que frena publicar), la entrega (¿falta algo por responder?), el cierre de la calificación, la ficha, el ranking, el Excel y el cambio de puntos. **No se unifica con `CalificacionPorPuntos`**, que califica pregunta a pregunta |
| Cuándo la prueba del editor tiene nota de la etapa y pasa a «por confirmar» (`V67`) | `prueba/service/CierreDeLaPruebaPropia`. La llaman la entrega, el agente, el ajuste a mano y los recálculos: el paso lo da quien complete la rúbrica, sea la IA o una persona |
| Qué puede guardarse y publicarse en la prueba del editor (`V67`) | `prueba/service/ReglasDeLaPrueba`. La usan guardar, publicar, cambiar los puntos y validar lo que propone la IA; las reglas de cada tipo de pregunta siguen siendo las de `ReglasDePuntos` |
| Lo común de los dos editores de una vacante: sus preguntas propias (`V66`) y su prueba (`V67`) | `perfilintegral/service/impl/EditorDeVersionPropia`, la base de `ServicioPreguntasVacanteImpl` y `ServicioPruebaPropiaImpl`: ver, el borrador, quitar y mover criterios y preguntas, publicar, corregir las instrucciones, reintentar, copiar de otra vacante, las recomendaciones y la recalificación. Lo propio de cada uno entra por sus ganchos. Las 14 rutas comunes se declaran una vez en `perfilintegral/controller/EditorDeLaVacanteController` (interfaz `perfilintegral/service/EditorDeLaVacante`), y cada hijo —`PreguntasVacanteController`, `PruebaPropiaController`— solo fija su ruta; por eso los resúmenes de Swagger de esas rutas son genéricos. Las de la ficha, bajo `/postulaciones`, viven en `AjusteDeAbiertaController` y `prueba/controller/PruebaPropiaDelCandidatoController`. Con dos copias, un arreglo en un editor dejaba el otro roto. **La calificación no se unifica**: cada editor conserva la suya (`CalificacionPorPuntos` y `CalificacionDeLaPruebaPropia`) |
| Lo que el candidato lee de su proceso en cada momento (`V73`) | `postulacion/service/TextosDelMomento`: el título y el texto de cada uno de los dieciocho estados, más las variantes del cuestionario técnico y de la prueba sin completar. Lo dice el bot de WhatsApp y lo pinta el portal, que tiene su copia. Las dos están amarradas a `docs/contratos/textos-del-momento.json`, igual en los dos repositorios: `TextosDelMomentoTest` aquí y `estados.test.ts` allí fallan si no coinciden. **Cambiar un texto es cambiarlo en los dos** |
| Qué celular vale y cómo se escribe (`V73`) | `whatsapp/service/Celular`: nueve cifras que empiezan por 9 (Perú) o el número completo con su prefijo, siempre a E.164, y cómo se enseña y se enmascara. El alta, «Mi cuenta» y la integración con el bot lo usan, y desde el 12/11/2026 también la ficha del colaborador y su carga por Excel, por `colaborador/service/ReglasDelColaborador` (`errorDelCelular` y `celularParaGuardar`, que dejan pasar sin comprobar el valor que ya estaba guardado). El portal tiene la misma regla en `src/dominio/celular.ts` y el mismo mensaje de error, amarradas a `docs/contratos/celular-casos.json`, igual en los dos repositorios: `CelularTest` aquí y `celular.test.ts` allí fallan si no dan lo mismo. **Cambiar la regla es cambiarla en los dos y añadir el caso al contrato** |
| La nota del CV (`V80`) | `perfilintegral/service/NotaDelCurriculum`: criterios generales × (1 − p) + lo buscado × p, y la de siempre sin líneas, sin búsqueda terminada o con p = 0. La usan la nota del Perfil Integral y lo que recibe `POTENCIAL_RIESGO` (`InsumoPerfil`), los dos en `PuenteCalificacionIaImpl`, y el ranking (`ServicioPerfilIntegralPanelImpl`). Hasta la `V80` cada uno ponderaba por su cuenta, y con lo buscado dentro habrían dicho tres números distintos |
| Qué se puede escribir en «Qué buscar en el CV» (`V80`) | `vacante/service/ReglasQueBuscarEnElCv`: vacías, repetidas, el tope de seis y de 200 caracteres, y lo que no se puede buscar. El panel tiene la misma regla en `src/panel/vacantes/reglasQueBuscar.ts`, con las mismas palabras; **no hay contrato que las amarre**: cada una tiene sus pruebas (`ReglasQueBuscarEnElCvTest` aquí, `reglasQueBuscar.test.ts` allí). **Es una lista cerrada**: cambiarla es cambiarla en los dos, con su caso en las dos pruebas (ver el 20 de [Defectos conocidos](DEFECTOS-CONOCIDOS.md)) |
| Qué dice el informe de una etapa (`V84`) | `informe/service/ArmadoDelInforme`: **un solo armado, dos salidas**. El JSON del visor y el PDF leen el mismo objeto, y el redactor del informe recibe sus datos de ese mismo armado (`RedaccionDelInforme.insumo`). Las notas no se recalculan aquí: la del CV sale de `NotaDelCurriculum` y las demás, de los servicios que ya usa la ficha. Con un armado para la pantalla y otro para el PDF, el visor y el archivo acabarían diciendo cosas distintas |
| Qué dudas siguen abiertas en cada etapa y cómo quedan (`V94`) | `informe/service/LoQueSigueAbierto`, con las cuentas en `PendientesEntreEtapas`. Lo leen el armado del informe, la cola al terminar una etapa (por `SeguimientoTrasCalificar`) y el propio agente: con una copia en cada uno, el agente seguiría unas dudas, el informe enseñaría otras y la huella nunca casaría. La comprobación de la explicación reutiliza `RedaccionDelInforme.vale`, la misma que la del redactor |
| Cómo se dibuja un PDF (`V84`) | `comun/pdf/`: `LienzoPdf` (páginas A4 de PDFBox, con las coordenadas desde arriba como en la maqueta HTML), `FlujoPdf` (coloca piezas una debajo de otra y abre página cuando no caben, sin partir una pieza que quepa entera en una hoja), `RenglonesPdf` (parte el texto en renglones sin recortar nada), `TipografiaPdf` y `EstiloPdf`. **Las fuentes van incrustadas**, solo con los glifos usados: Onest y Syne, pasadas a TTF desde los WOFF2 del panel (`src/main/resources/pdf/fuentes/`, con su licencia en `LEEME.txt`). Son variables y el PDF usa su peso 400; el peso medio se simula con un trazo fino sobre el relleno. Un carácter que ninguna fuente dibuja sale como «?», en vez de tumbar el documento. Ningún dato sale a un servicio externo para armarlo. Lo propio de cada documento —el informe por etapa, el brochure de la empresa (`V86`) y los que vengan— va en su paquete, no aquí. Desde la `V86`, `LienzoPdf` también dibuja imágenes JPG o PNG (`leerImagen` e `imagen`: llenando el rectángulo, recortadas al centro y con la transparencia pintada sobre blanco; una imagen que no se puede leer no se dibuja y no rompe el documento), y `ColoresPdf` guarda la paleta Northstar que comparten el informe y el brochure |
| Qué enlace vale en el perfil de la empresa y en el video del puesto (`V85`) | `organizacion/service/EnlacesDeLaEmpresa` (web, redes y los dos videos: `http` o `https`, con dominio, sin usuario delante y hasta 500; sin esquema se le pone `https://`) y `organizacion/service/VideoDePresentacion` (qué video de YouTube o de Vimeo vale y su servicio, identificador y clave). Los usan el «Perfil de la empresa» y el alta y la edición de la vacante. El panel tiene la misma regla en `src/dominio/presentacionEmpresa.ts`, con las mismas palabras y **sin contrato que las amarre**: cambiarla es cambiarla en los dos, con su caso en las dos pruebas |
| Qué plantillas y qué colores tiene el brochure (`V86`) | `organizacion/service/PlantillaDelBrochure` y `ColorDelBrochure` (con los tres acompañantes de cada color en el PDF; `CatalogoDelBrochureTest` comprueba que todos llevan texto blanco encima con un contraste de al menos 4,5:1). El panel pinta las mismas tarjetas y los mismos círculos, con los mismos códigos y colores, en `src/panel/empresa/brochure.ts`, **sin contrato que los amarre**: añadir una plantilla o un color es añadirlo en los dos y ampliar el CHECK de su columna con una migración nueva |
| La fecha de hoy y la regla de los datos personales que recibe la IA (17/11/2026) | `ai/service/impl/FechaDeHoyParaLaIa`, que `EjecutorAgenteIaImpl` pone entre la instrucción y el formato de cada llamada, con un `Clock` inyectable para que las pruebas fijen el día. ⚠️ **La frase `REGLA` es la que se busca en `ejecucion_ia.envio`** (`RegistroTrabajosIa.conLaFechaDeHoy`) para saber quién se calificó ya con la fecha: cambiarla haría parecer afectado a todo lo calificado antes |
| Cuándo una «N años» es una edad, y qué texto de la IA no se enseña (17/11/2026) | `postulacion/service/impl/ReglaDeLaEdad`, usada por `AnonimizadorCv`; la regla anterior sigue ahí solo para que la recalificación sepa a quién le tapaba de más (`laReglaViejaTapabaDeMas`). `AnonimizadorCv.traeLaMarca` decide qué texto de la IA lleva `[DATO NO UTILIZABLE]`, y lo usan el guardado de las afirmaciones, la ficha, los informes, el redactor y la simulación: con un criterio en cada uno, la marca volvería a salir por alguno |
| Lo que el portal enseña de una empresa (`V85`) | `portal/service/PresentacionEnElPortal`: el tablón, el detalle de la vacante y el perfil público dicen lo mismo. **El tablón carga las redes de todas las empresas del lote en una sola consulta**, nunca una por vacante: es la pantalla más pedida y se sirve sin cuenta |

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
