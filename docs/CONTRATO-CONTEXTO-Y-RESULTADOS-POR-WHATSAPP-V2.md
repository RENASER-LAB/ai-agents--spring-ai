# Contrato v2: contexto y resultados por WhatsApp (EX ↔ bot)

> ⚠️ **Todavía no está activo.** El contrato vigente es el v1,
> [CONTRATO-NOVEDADES-POR-WHATSAPP](CONTRATO-NOVEDADES-POR-WHATSAPP.md), y sigue describiendo lo
> que hoy pasa. El v2 se activa en el orden de C-10: primero el bot con
> `CONVERSATION_EX_VERSION=2` y después EX con su productor v2 (`WHATSAPP_PRODUCTOR_V2=true`, apagado
> por defecto). Mientras tanto, EX ya responde la consulta firmada
> `POST /api/v2/integraciones/whatsapp/contexto`, que solo lee.

Copia tal cual de la sección «Contrato EX v2» de la spec 014 del CRM
(`CRM/specs/Ready/014-postulantes-hacia-eventos/spec.md`, puntos C-1 a C-10), que es el único
contrato de esta ampliación. Si cambia allí, cambia aquí, y también la spec de EX
`contexto-y-resultados-por-whatsapp.md` (en `specs/`). Lo que EX decidió dentro de lo que el
contrato deja abierto va aparte, al final: «Cómo lo cumple EX».

## Contrato EX v2

Contrato único entre EX y el bot para la ampliación. Hereda de v1, sin cambios, las reglas
comunes: `telefono` E.164, firma `X-Renaser-Marca`/`X-Renaser-Firma` sobre `marca.ruta.cuerpo`,
un secreto por dirección, ±5 min, cuerpos ≤ 64 KiB, formatos de error (`{code,…}` del bot,
`{codigo, texto}` de EX), reintentos, timeout `CONVERSATION_EX_TIMEOUT_S`, circuito, fechas ISO 8601
con zona y lectura de respuestas que tolera campos nuevos pero rechaza las que incumplen límites.

- **C-1. Identidad.** `postulacionRef` es la misma referencia opaca de v1 (`pw_` + 32 hex, una
  por vínculo). EX valida en cada llamada número, vínculo, organización y vigencia. Una referencia
  o cursor que no pertenece a ese número responde igual que uno inexistente.
- **C-2. Postulaciones consultables.** Las de vínculo `ACTIVO` y las finalizadas con un vínculo
  activo al terminar, durante 30 días desde el hecho (`consultableHasta`). BAJA, dejar de
  recibir, cambio de celular o borrado retiran el acceso del vínculo anterior.
- **C-3. Consulta (bot → EX):** `POST /api/v2/integraciones/whatsapp/contexto`, firmada con la
  ruta completa. Cuerpo `{telefono, postulacionRef?, cursor?}`; `postulacionRef` y `cursor` no
  se combinan (`400 VALIDACION`).
  - **Sin referencia (listado):** `200 {postulaciones: [Resumen], cursor, consultadoEn}`, hasta
    9 por página, más recientes primero; `cursor` opaco (≤ 200) o `null`. Lista vacía = sin
    procesos consultables. `Resumen = {postulacionRef, vacante: {titulo, distintivo}, situacion:
    ACTIVA|FINAL, resultado: null|CONTRATADO|NO_CONTINUA|CERRADA, convocatoria: PUBLICADA|CERRADA,
    actualizadaEn}`.
  - **Con referencia (detalle):** `200 Contexto`:

    | Campo | Contenido y límites |
    |---|---|
    | `postulacionRef`, `consultadoEn`, `actualizadoEn` | La fecha real del último cambio y la de esta consulta, separadas |
    | `revisionContexto` | Entero creciente por postulación |
    | `vacante` | `{ref (opaca ≤ 64), titulo ≤ 60, empresa ≤ 60 \| null, distintivo ≤ 72 \| null, convocatoria: {estado: PUBLICADA\|CERRADA, cerradaEn \| null, revision}}` |
    | `vacante.ficha` | Objeto con `descripcion`, `proposito`, `responsabilidades`, `requisitos`, `ubicacion`, `modalidad`, `horario`, `remuneracion`, `tipoContrato`, `beneficios`; cada uno es un `Campo` |
    | `Campo` | `{estado: PUBLICADO\|NO_PUBLICADO\|NO_APLICA\|POR_DEFINIR, texto ≤ 600 \| null}`; `texto` solo con `PUBLICADO` |
    | `procedimiento` | Hasta 8 `{codigo: PERFIL_INTEGRAL\|PRUEBA_PUESTO\|SIMULACION\|VALIDACION\|DECISION, nombre ≤ 60, explicacion ≤ 300, orden, estado: HECHA\|ACTUAL\|PREVISTA}`, solo las etapas que aplican a esa vacante |
    | `situacion` | `{estado: ACTIVA\|FINAL, etapaCodigo \| null, momento: POR_HABILITAR\|TURNO_CANDIDATO\|CALIFICANDO\|POR_CONFIRMAR \| null, titulo ≤ 80, texto ≤ 300, actualizadaEn}`; `titulo`/`texto` son los de `TextosDelMomento` |
    | `accion` | `null` (no hay tarea) o `{tipo: EVALUACION_INICIAL\|PRUEBA_PUESTO\|CUESTIONARIO_TECNICO\|SIMULACION\|VALIDACION\|OTRA, estado: HABILITADA\|EN_CURSO\|COMPLETADA\|VENCIDA, instrucciones ≤ 600, ingreso ≤ 600, enlace (https ≤ 300; obligatorio con `HABILITADA`/`EN_CURSO`, si no `null`), modalidad: CRONOMETRADA\|PLAZO_ABIERTO \| null, duracionMinutos \| null, plazoDias \| null, empezarAntesDe \| null, iniciadaEn \| null, entregarAntesDe \| null, zonaHoraria: "America/Lima"}`. `empezarAntesDe`: último momento para iniciarla; `entregarAntesDe`: límite de entrega del intento ya iniciado (o de la acción sin intento, como una evaluación inicial); en `VENCIDA`, el límite que venció |
    | `resultado` | `null` o `{tipo: CONTRATADO\|NO_CONTINUA\|CERRADA, fecha, revision, explicacion ≤ 300 \| null, esCorreccion, consultableHasta}` |

  - Reglas de contenido: `remuneracion` usa el texto público de EX (`FIJA`/`RANGO`); con `OCULTA`
    es `NO_PUBLICADO` y no viaja ningún importe. `tipoContrato` y `beneficios` van `NO_PUBLICADO`
    mientras EX no tenga una fuente pública. `resultado.explicacion` va `null` mientras EX no
    registre una causa pública (el `motivo_cierre` actual es interno y no viaja). `enlace` es la
    URL pública fija de «Mis procesos» del portal de EX, igual para todas las personas: `https`,
    sin usuario/contraseña, sin parámetros (`?`), sin fragmento (`#`), sin tokens ni
    identificadores de persona o postulación. No concede acceso por sí sola: el portal pide
    iniciar sesión. El bot rechaza como fuera de contrato un `enlace` que no cumpla esto
    (AC-40). Nada de notas, puntuaciones,
    preguntas, respuestas ni datos de otras personas.
  - Errores: `404 {codigo: "SIN_ACCESO", texto}` para referencia o cursor inexistente, ajeno o sin
    acceso; `400 VALIDACION`; `401 FIRMA_INVALIDA`; `5xx` reintentable. El bot trata `5xx`,
    timeout y circuito abierto como `SIN_EX`, y una respuesta fuera de contrato como
    `ex_protocolo_invalido` (AC-40). La consulta es de solo lectura: no cambia estados, intentos,
    relojes ni consentimiento.
- **C-4. Eventos (EX → bot):** `POST /v1/ex/resultados`, firmada con `CONVERSATION_EX_SECRETO_ENTRADA`.
  Cuerpo:
  `{eventoId (UUIDv7), tipo: RESULTADO|CONVOCATORIA_CERRADA, telefono, postulacionRef, nombre ≤ 60,
  vacante ≤ 60, ocurridoEn, resultado?, convocatoria?, aviso: {autorizado, caducaEn}, cierraVinculo}`.
  - `RESULTADO` lleva `resultado` (como en C-3) y `cierraVinculo: true`.
  - `CONVOCATORIA_CERRADA` lleva `convocatoria: {cerradaEn, revision}` y `cierraVinculo: false`.
  - Respuesta `202 {eventoId}`; repetido igual, `202`; mismo `eventoId` con otro contenido,
    `409 evento_distinto`; versión 1, `404 ruta_no_disponible`; sin exactamente una cuenta NUEVO,
    `503 ex_cuenta_no_disponible`. Mismos códigos y orden de comprobación que `/v1/ex/avisos`.
- **C-5. Recepción atómica en el bot.** En una transacción: registra el hecho deduplicado por
  `eventoId`; guarda o actualiza el resultado consultable solo si su `revision` es mayor que la
  guardada (la de convocatoria se compara aparte); si `cierraVinculo`, cierra el vínculo con
  motivo `PROCESO_TERMINADO`; y crea **un** aviso pendiente solo si `aviso.autorizado`,
  `caducaEn` no ha pasado, no hay BAJA ni supresión del número, y no hay una revocación de esa
  postulación por `DEJAR_DE_RECIBIR`, `CAMBIO_CELULAR` o `BORRADO_DATOS` (llegue antes o después).
  Un evento con revisión menor queda registrado sin aviso (`revision_anterior`). Un
  `CONVOCATORIA_CERRADA` posterior a un resultado final no crea aviso (`resultado_final_vigente`).
- **C-6. Autorización final.** EX pone `aviso.autorizado: true` solo si el vínculo estaba
  `ACTIVO` y autorizado al confirmarse el hecho; `caducaEn` ≤ `ocurridoEn` + 24 h. Vale para ese
  evento y sus reintentos; no reactiva avisos de avance ni un consentimiento revocado. Una
  corrección (`esCorreccion: true`) necesita su propia autorización vigente.
- **C-7. Orden con v1.** Con v2 activa, EX no envía `/v1/ex/revocaciones` `PROCESO_TERMINADO` para
  postulaciones cerradas por un `RESULTADO`. Si llega igual (reintento antiguo), el bot cierra lo
  que falte y **no** cancela el aviso final pendiente. Las revocaciones con otros motivos sí lo
  cancelan, también si ya estaba encolado y aún no salió.
- **C-8. Despacho y textos.** Lo despacha la cola de envíos existente, con sus topes, pausa y
  atención humana. Al despachar revalida BAJA, supresión, revocación y `caducaEn` (vencida →
  `OMITIDO autorizacion_caducada`). Dentro de ventana sale como texto, sin lista ni promociones;
  fuera, `OMITIDO sin_plantilla`. Textos (con «*Actualización:* » delante si `esCorreccion`; y
  `explicacion`, si existe, en un párrafo aparte):
  - `CONTRATADO`: «Tu postulación a *{vacante}* terminó con tu contratación 🎉. Revisa el portal,
    en «Mis procesos», para ver los siguientes pasos.»
  - `NO_CONTINUA`: «Gracias por tu interés en *{vacante}*. En esta ocasión tu postulación no
    continuará en el proceso.»
  - `CERRADA`: «Tu postulación a *{vacante}* se cerró el {fecha}. Si tienes dudas, escríbenos por
    aquí.»
  - `CONVOCATORIA_CERRADA`: «La convocatoria de *{vacante}* cerró el {fecha}. Tu postulación sigue
    en curso: te avisaremos por aquí cuando te toque hacer algo.»
  Fechas en formato «17 de octubre», hora de Lima.
- **C-9. EX simulado (`CONVERSATION_EX_MODE=fake`, versión 2).** `/contexto` responde sin red desde
  un fichero JSON indicado por `CONVERSATION_EX_FAKE_CONTEXTO` (por `postulacionRef`), o con un
  contexto por defecto por vínculo activo: «Vacante de prueba», horario y requisitos publicados,
  remuneración y beneficios `NO_PUBLICADO`, tres etapas y una `PRUEBA_PUESTO` `HABILITADA`
  `CRONOMETRADA` de 90 minutos con `empezarAntesDe` dentro de 2 días. El repositorio incluye fixtures para: dos postulaciones con el mismo título,
  prueba vencida, prueba `EN_CURSO` con `entregarAntesDe`, prueba sin `empezarAntesDe`, `NO_CONTINUA`, `CERRADA`, `CONTRATADO`, convocatoria cerrada con postulación
  activa y más de 9 postulaciones. `/v1/ex/resultados` se prueba firmando con el secreto de
  desarrollo, como `/v1/ex/avisos`.
- **C-10. Activación.** Orden: bot con `CONVERSATION_EX_VERSION=2` → EX con su productor v2
  habilitado. Al activar no se envían avisos de hechos anteriores. Volver a `1` deja de llamar a
  v2 y rechaza eventos nuevos; los pendientes caducan sin enviarse.

## Cómo lo cumple EX (19/10/2026)

No es parte de la copia: es lo que EX hace donde el contrato le deja elegir. Las tablas, en el
[Diccionario de datos](07-DICCIONARIO-DE-DATOS.md), «Novedades por WhatsApp»; la ruta, en
[Las APIs](09-APIS.md), «La puerta de las integraciones».

- **Qué es consultable lo decide una sola regla**, la misma para el listado y el detalle
  (`ServicioContextoWhatsappImpl`): el número no escribió BAJA, la persona del vínculo conserva
  ese celular en su cuenta, la vacante no se eliminó y es de la misma organización, y la
  postulación sigue en carrera con el vínculo `ACTIVO` o terminó con un resultado consultable sin
  revocar. Va por la misma puerta y la misma firma que `/api/v1/integraciones/**`.
- **Las referencias** (`ReferenciasOpacas`): `vacante.ref` es `vc_` y 32 cifras hexadecimales, y
  el `cursor`, un HMAC con el secreto del bot atado al número que lo pidió. Cambiar el secreto
  cambia las dos.
- **`actualizadoEn` es la fecha real más reciente de sus fuentes** —la postulación, la vacante
  (publicada, cerrada, el sueldo y `contexto_actualizado_en`, que fechan triggers desde la `V77`),
  el intento o la evaluación mientras sigue en carrera, y el resultado—, nunca posterior a la
  consulta. **`revisionContexto` es esa misma fecha en milisegundos**, así que solo crece. Un
  cambio de los plazos de la vacante sube la de todas sus postulaciones, aunque alguna no los vea
  (aceptado).
- **Ficha, procedimiento y acción** (`FichaPublica`, `ProcedimientoPublico`, `LoQueLeToca`):
  `tipoContrato` y `beneficios` van siempre `NO_PUBLICADO`; una postulación terminada enseña solo
  las etapas que alcanzó; la simulación no da fecha. La acción no lleva nada de la prueba antes de
  «Empezar», y su `enlace` es `WHATSAPP_ENLACE_MIS_PROCESOS`, solo con la acción abierta: si lo
  configurado no es un enlace válido (un `http` de desarrollo, por ejemplo) va `null`, y el bot lo
  rechazaría como fuera de contrato.
- **El resultado** (`ResultadosPorWhatsapp`, tabla `resultado_whatsapp`): se guarda al terminar si
  el vínculo estaba `ACTIVO`, **aunque el productor esté apagado** —la consulta es de EX y no manda
  nada—, y dura 30 días desde el hecho. BAJA, cambio de celular o dejar de recibir lo revocan; el
  borrado de datos se lo lleva. `explicacion` va siempre `null` (el `motivo_cierre` es interno) y
  `esCorreccion` siempre `false`: hoy EX no corrige resultados, así que su `revision` es 1.
- **Los eventos** (`EnvioDeEventos`, tabla `evento_whatsapp`): se escriben en la misma transacción
  que el hecho y los manda `SondeoWhatsapp`, con los reintentos de los avisos v1 y el contenido
  congelado al escribirlos. `RESULTADO` lleva `aviso.autorizado` solo si el vínculo estaba
  `ACTIVO` y la transición no se hizo «sin avisar»; `CONVOCATORIA_CERRADA` sale a cada postulación
  en carrera con vínculo `ACTIVO`, con revisión 1 (una convocatoria no se reabre). Antes de cada
  intento se revalida: caducado, BAJA o acceso retirado lo dejan `OMITIDO`. Un
  `404 ruta_no_disponible` (bot en versión 1) es definitivo: `FALLIDO` a la primera. Un
  `RESULTADO` que no llega a aceptarse devuelve al vínculo la revocación `PROCESO_TERMINADO` de v1,
  para que el bot lo cierre igual.
- **El interruptor**: `WHATSAPP_PRODUCTOR_V2`, apagado por defecto. Apagado no nace ningún evento y
  EX se comporta como v1; al encenderlo no sale ningún hecho anterior; si se vuelve a apagar, los
  pendientes esperan y caducan `OMITIDO`. Ver [CI/CD](CI-CD.md) para encenderlo en el servidor.
