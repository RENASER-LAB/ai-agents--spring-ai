# Ampliación: de la selección a la gestión de personas

Estado: orden verificado · 26/09/2026 (contra las 27 entradas del menú de productos de mandu.pe)

El MVP de selección está terminado. Ahora el sistema se amplía **dentro del mismo panel admin**
con los productos de RR.HH. que pidió la clienta, tomando como referencia el menú de productos
de Mandü (mandu.pe). Se avanza uno por uno: cada apartado se investiga y se entiende aquí antes
de escribir su spec.

Todo se construye dentro de EX. RENASER OS no interviene: ningún apartado se apoya en él ni en
lo que queda de su integración.

---

## Orden

El orden lo marca lo que cada apartado necesita que exista antes. Lo que depende de terceros o
tiene riesgo legal va al final.

| # | Apartado | Por qué va ahí |
|---|---|---|
| **Fase 1** | **Administración** | |
| 1 | Gestión de personas, con «contratar desde el panel» y la carga de la plantilla actual | Todo lo demás se aplica a la persona contratada, que antes no existía, y contratar solo se podía por la API. **Implementado y aprobado el 29/09/2026** |
| 2 | Documentos laborales | Solo necesita a la persona. RR.HH. le saca provecho sin que nadie más entre |
| 3 | Portal del Colaborador (Hub) | Abre con los documentos de cada uno ya cargados |
| 4 | Firma electrónica | Se firma dentro del portal |
| 5 | Onboarding | Junta formularios, documentos y firmas |
| 6 | Vacaciones y licencias | Necesita el portal (pedir) y el jefe directo (aprobar). El récord vacacional exacto, que depende de los días efectivos de trabajo, llega con Asistencia (19) |
| 7 | App | La app Android del portal ya existe: se le añade el portal del colaborador y se publica |
| **Fase 2** | **Cultura y Bienestar** | |
| 8 | Encuestas personalizadas | Es el motor de encuestas que usa el siguiente |
| 9 | Clima laboral, con los planes de acción con IA | Una encuesta más la medición de clima y el anonimato |
| 10 | Red de comunicación interna y Reconocimientos | Viven en el portal y en la app |
| 11 | Chat corporativo | Al final de la fase: es costoso (mensajes en tiempo real) y lo que aporta es poco |
| **Fase 3** | **Talento y Desarrollo** | |
| 12 | Evaluación de desempeño, con Feedback y su IA | Es la base de los tres siguientes |
| 13 | Mapeo de talento (Nine Box) | Sale de las evaluaciones |
| 14 | Aprendizaje y capacitación | Tiene que estar antes que los planes de desarrollo, para que un plan pueda asignar cursos |
| 15 | Planes de desarrollo con IA | Usa las evaluaciones y los cursos |
| **Fase 4** | **Completar Selección IA** | |
| 16 | Base de candidatos (el Radar de Talento) y publicación en Google Empleos | La selección ya funciona hoy, por eso no corre prisa |
| 17 | Entrevistas con agenda | |
| 18 | Agente entrevistador con IA, grabado y transcrito | La pieza más grande de Selección |
| **Fase 5** | **Gestión de Nómina** | |
| 19 | Asistencia | Planillas la necesita para horas extra y tardanzas |
| 20 | Planillas (PLAME, T-Registro, AFPnet, CTS, gratificación) | Al final por el riesgo legal: un error acaba en multa |
| **Fase 6** | **Canales** | |
| 21 | WhatsApp | Un solo canal para los avisos de selección y para el agente |
| 22 | Agente HR | Responde sobre todo lo anterior |

Las fases 2, 3 y 4 no dependen entre sí: su orden se basa en lo que aporta cada una, no en una
necesidad. La fase 5 podría adelantarse, porque solo depende de la fase 1. Va al final por el riesgo, no
porque le falte algo. Si la clienta necesita la planilla antes, entra justo después de la fase 1.

## Lo que no es un apartado propio

| Entrada del menú de Mandü | Qué es |
|---|---|
| ¿Buscabas Tu Recibo? | Firma electrónica con su nombre antiguo (4) |
| Planillas + Asistencia | Un paquete comercial de 19 y 20 |
| Outsourcing de planillas | Un servicio hecho por personas, no software |
| Cürsa | Cursos grabados por expertos: contenido, no software |
| Planes de acción + IA | Va dentro de Clima laboral (9) |
| Talento IA | Es la IA de Desempeño y de los planes de desarrollo (12 y 15) |

## Pendientes sin apartado asignado

| Qué | Hoy |
|---|---|
| **Renovar un contrato**, con avisos de vencimiento, un botón propio, las prórrogas y el tope de 5 años de los contratos encadenados | Anotado por el usuario al aprobar la gestión de personas (29/09/2026). No tiene flujo propio: se registra con «Registrar un cambio» y el motivo «renovación de contrato». La lista marca «vence en N días» y «vencido», y el filtro «Contratos por vencer» los junta, pero nada avisa a nadie |

---

## 1 · Gestión de personas

Estado: implementado y aprobado · 29/09/2026 (`V64` y `V65`). Spec en
[specs/rrhh-01-gestion-de-personas.md](../specs/rrhh-01-gestion-de-personas.md). Incluye el menú
lateral del panel. Qué se construyó y qué decidió el usuario al aprobarlo está más abajo, en
«Lo que quedó construido».

### Qué es

Es la **ficha maestra de cada colaborador y de la estructura de la empresa, con toda su
historia**. No calcula nada: guarda, y los demás módulos leen de aquí. Mandü la presenta como
«la base de tu nómina». Su web argentina lo dice así: «Administración de personas es el cerebro
que centraliza datos. Gestión de Nómina es el motor que los procesa».

En Mandü este módulo no viene de la startup peruana que dio nombre a la marca. Es **Visma
People**, antes RH Pro, un sistema argentino de nómina de 1996, y lleva encima Tu Recibo para el
legajo digital y la autogestión. El centro de ayuda oficial lo confirma para clientes peruanos:
sincroniza con Visma People «en horario GMT-5 (… Perú …)», deriva a support.peru@visma.com y
dice que «la creación de colaboradores se podrá realizar únicamente desde Visma People»
(zetechsa.freshdesk.com, artículos 13000109157 y 13000109161, 10/01/2025). Por eso la API y el
manual públicos de Visma sirven para saber cómo funciona por dentro.

Sin confirmar: que todos los clientes peruanos usen Visma People, porque algunos podrían usar
solo Tu Recibo o la base propia de Mandü Experience, y que las capturas de mandu.pe sean la
ficha actual de Visma People.

### Las tres ideas que lo explican

1. **Una ficha por persona.** Guarda quién es, cómo se le contacta, dónde está en la empresa, con
   qué contrato, cuánto gana, cómo cotiza y quién es su familia.
2. **La empresa es una estructura.** Se organiza en empresa, área y cargo, más sede, centro de
   costo y régimen. Cada persona tiene un **jefe directo** («reporta a»), y de esos jefes sale el
   organigrama.
3. **Nada se sobrescribe.** Cada cambio lleva una fecha «desde», que puede ser pasada o futura,
   y un motivo. Lo anterior se cierra el día previo y queda en el historial. El cese tampoco
   borra a nadie: la persona pasa a inactiva.

### La ficha en pantalla

mandu.pe publica maquetas de diseño de la «Carpeta del empleado», no pantallas reales: el
«Teléfono 1» dice «Sanchez». Tiene cuatro pestañas: **Perfil**, **Contrato activo**,
**Ausentismo** y **Plantilla propia**.

| Pestaña | Campos (* = obligatorio) |
|---|---|
| Perfil · datos del perfil | Tipo de documento\*, nombre\*, apellido paterno\*, apellido materno\*, fecha de nacimiento\*, sexo\*, estado civil\*, nivel educativo\*, contrato actual\* |
| Perfil · datos de contacto | Correo corporativo\*, teléfono 1\*, correo personal |
| Contrato activo · datos del contrato | Estado, nombre del contrato\*, contrato\*, tipo de contrato\*, fecha de inicio\*, término del periodo de prueba\*, agrupación de seguridad\*, detalle del contrato (opcional) |
| Contrato activo · datos organizacionales y de empresa | Empresa\*, área\*, cargo\*, régimen laboral\*, sede\*, centro de costo\*, sueldo base\*, sindicato\* |

Otras maquetas de la misma web:
- Un tablero «Dashboard People» con colaboradores, personas de vacaciones y ausencias del día, y
  las ausencias del mes por tipo.
- Un resultado de carga con «Procesados 102 / No procesados 0».
- Una ficha de planilla por persona con número de legajo, «conceptos» y «acumuladores», que son
  términos de Visma People.

### Qué guarda de cada colaborador

| Grupo | Datos |
|---|---|
| Identidad | Tipo y número de documento, nombres, dos apellidos, fecha de nacimiento, sexo, estado civil, nacionalidad |
| Contacto | Correo personal y corporativo, celular, domicilio (formato SUNAT, con ubigeo), contacto de emergencia |
| Organización | Número de legajo, empresa, área, cargo, sede, centro de costo, jefe directo |
| Relación laboral | Fecha de ingreso, fin del periodo de prueba, régimen laboral, tipo de trabajador, tipo de contrato y su fecha de fin, jornada, situación especial (dirección, confianza, teletrabajo) |
| Remuneración y pago | Sueldo base, periodicidad, forma de pago, cuenta sueldo y cuenta CTS (en Perú son dos) |
| Seguridad social | Salud (EsSalud o EPS), pensión (ONP o AFP, con el código de afiliado o CUSPP), seguro de riesgo (SCTR) |
| Familia | Derechohabientes de EsSalud e hijos para la asignación familiar. **No son la misma lista**: la asignación cubre hasta los 24 años si el hijo estudia, EsSalud solo a menores |
| Formación | Situación educativa, institución, carrera, año de egreso |
| Sensibles | Discapacidad, sindicalizado, aptitud médica (solo «apto» o «no apto») |
| Campos propios | Los que añade cada empresa |

### Los recorridos

**Alta.** Hay tres puertas:
- **A mano:** un asistente pide primero identidad y documento, luego la ubicación en la
  estructura, el domicilio y los documentos. Se puede copiar la configuración de otra persona.
- **Carga masiva:** un Excel con una fila por persona. El mismo archivo sirve para actualizar
  datos en bloque.
- **Desde selección:** el candidato contratado pasa a onboarding sin volver a cargar datos.
  Aparece como «en onboarding» y después como «activo».

**Cambio de área, cargo, sede o sueldo.** Se elige lo nuevo, la fecha desde la que vale y el
motivo. Puede hacerse para un grupo entero, por ejemplo al renovar contratos en bloque. Visma
People tiene circuitos de aprobación, con una lista de personas que firman, para tres casos:
cambios de estructura, bajas y cambios de jefe. Todo cambio queda en una auditoría con el valor
anterior, el nuevo, el usuario y la fecha.

**Cese.** Se registra el último día trabajado, el motivo (renuncia, despido, fin de contrato,
mutuo disenso…) y el preaviso. La persona pasa a inactiva: se la necesita para la liquidación y
los informes, y **sigue pudiendo recibir y firmar documentos**. Borrarla es una herramienta
especial que se desaconseja.

**Reingreso.** Se reactiva al inactivo con una fecha nueva, lo que abre otro periodo laboral, y
recupera su última ubicación en la estructura. Hay una opción para no respetar la antigüedad
anterior, pensada para temporadas.

### Quién ve qué

| Quién | Qué |
|---|---|
| RR.HH. | Todo, o solo su sede o sector |
| Jefe | Su equipo directo, y más niveles si se configura |
| Colaborador | Su propia ficha. Puede pedir cambios de contacto, domicilio y cuentas bancarias, y RR.HH. los aprueba. Solo cabe una solicitud pendiente a la vez |

Las notas confidenciales y los datos sensibles van aparte, con acceso restringido. En Visma
People, «quién ve a quién» se configura con una «seguridad por vistas»: cada usuario ve solo a
los empleados de ciertas estructuras (por ejemplo, su sede) y puede modificar solo algunas.

### Qué reportes da

- **Tablero del día:** cuántos colaboradores hay, cuántos de vacaciones y cuántos ausentes.
- **Plantilla:** dotación por área y sede, altas y bajas, rotación con sus causas, antigüedad y
  edades.
- **Vencimientos:** contratos y documentos que están por vencer.
- **Organigramas:** uno por jefes y otro por cargos, que muestra las plazas vacantes.
- **Exportación a Excel.** Los informes legales (T-Registro, PLAME) llegan con la planilla.

### Por qué es la base de lo demás

| Módulo | Qué toma de aquí |
|---|---|
| Planillas | Quién estuvo activo en el mes, régimen, contrato, sueldo, cuentas, pensión, salud, derechohabientes |
| Asistencia | Altas y bajas, jornada, horario, centro de costo |
| Vacaciones | Antigüedad (fecha de ingreso) y quién aprueba (el jefe) |
| Documentos y firma | A quién se le envía cada documento |
| Clima y desempeño | Área, cargo y sede para segmentar; el jefe directo para armar los equipos |
| Agente HR | Colaboradores, áreas y equipos |

### Lo que exige la ley peruana

- **Los campos los define el T-Registro de SUNAT.** Su Anexo 1 lista cada dato, y las tablas
  paramétricas (actualizadas el 11/09/2026) dan los códigos permitidos: tipo de trabajador,
  régimen, tipo de contrato, motivo de baja, AFP…
- **Plazos:**
  - Alta **el mismo día** del ingreso.
  - Modificación en 5 días hábiles.
  - Baja el primer día hábil siguiente al cese.
  - No inscribir a alguien es falta grave por cada trabajador.
- **Historial obligatorio.** Tipo de trabajador, salud, pensión y SCTR tienen fecha de inicio y de
  fin, porque la planilla de cada mes usa el valor vigente en ese mes.
- **Contratos a plazo fijo:**
  - Llevan fecha de fin, causa y prórrogas.
  - Encadenados no pueden pasar de 5 años.
  - Si la persona sigue trabajando después del vencimiento, el contrato pasa a indefinido. Hace
    falta una alerta de vencimiento.
- **La sede es obligatoria.** Tiene que ser un establecimiento declarado en el RUC, con su código
  de SUNAT. También decide el SCTR y la provincia del banco de la CTS.
- **Datos sensibles (Ley 29733).** El sueldo, la salud, el sindicato, la discapacidad y la huella
  del reloj de asistencia necesitan acceso restringido. Hay que informar al trabajador cuánto
  tiempo se conservan.
- **Conservación:** boletas y planillas 5 años, asistencia 5 años, registros de seguridad y
  salud en el trabajo hasta 20 años.
- **Los regímenes cambian la ficha:**
  - general;
  - MYPE, que exige que la empresa esté en el REMYPE;
  - agrario;
  - construcción civil;
  - minero;
  - practicantes, que no son trabajadores pero van al T-Registro.
- **Cambios recientes:**
  - Desde 2025 se puede pagar el sueldo en una billetera digital (Ley 32413), aunque las tablas de
    SUNAT aún no la incluyen.
  - La remuneración mínima y la UIT cambian, así que ningún monto se fija en el código.

### Lo que ya teníamos antes de la spec

- **Persona:** documento, nombres, apellidos, teléfono, fecha de nacimiento y ciudad.
- **Empresas separadas entre sí.**
- **Áreas por empresa:** son una lista plana, sin jefe ni jerarquía.
- **Catálogo de puestos por empresa:** tiene nivel y familia, y podría servir de cargos.
- **Servicios comunes:** roles y permisos editables, auditoría de cambios, almacén de archivos,
  correos y avisos.
- **Contratar:** hoy solo cambia la postulación a «Contratado», y únicamente por la API. No se
  crea nada más de esa persona.

### Preguntas para la spec

1. **¿Qué campos entran ahora?** Hay dos opciones:
   - Solo lo que usan las fases 1 a 4: identidad, contacto, organización, contrato y jefe.
   - Además, lo del T-Registro, pensando en la planilla. Cargar después los datos de pensión y
     salud de toda la plantilla obliga a recorrerla dos veces.
2. **¿La estructura es fija o configurable?**
   - Fija: área, cargo, sede y centro de costo.
   - Configurable: cada empresa define sus propios niveles, como hace Visma.
3. **¿Quién ve el sueldo?** Hoy la pretensión salarial solo la ve Dirección.
4. **¿Los cambios de cargo o de sueldo necesitan aprobación**, o RR.HH. los aplica directamente?
5. **¿Cómo se carga la plantilla actual?** Con un Excel, con el formato que se defina.

Resueltas el 28/09/2026:
1. **Campos:** la primera versión lleva identidad, contacto, puesto, contrato, jefe y sueldo. Los
   datos de planilla entran con Planillas, mediante otra carga por Excel.
2. **Estructura:** fija, con sede, área, cargo y jefe.
3. **Sueldo:** solo lo ve Dirección, a falta de que lo confirme la clienta.
4. **Aprobación:** los cambios no se aprueban. Quedan en el historial y en la auditoría.
5. **Carga:** un Excel con nuestra plantilla. Si hay algún error, no se guarda nada.

### Lo que quedó construido (29/09/2026)

- **En el panel:** un menú lateral por familias —Selección y Personas, con Configuración al pie—
  que sustituye a la barra de pestañas; la lista `/admin/colaboradores` con el aviso de
  contratados que esperan su alta; la ficha con Perfil, Puesto y contrato, e Historial; el alta
  manual y la carga por Excel; los cambios, que se pueden programar y anular; el cese y el
  reingreso; «Sedes» y «Cargos» en Configuración; y **«Contratar» en la ficha del postulante**,
  que es la decisión en verde de siempre, seguido de «Dar de alta como colaborador».
- **En la base:** `sede`, `colaborador`, `periodo_laboral`, `situacion_laboral`, `cese_anulado` y
  `contratado_sin_alta`, sin ninguna FK a `persona`; los cargos son los puestos de siempre. Los
  códigos son los de SUNAT desde el primer día (tablas 3, 12, 17 y 33). Ver el
  [modelo de datos](05-MODELO-DE-DATOS.md) y el [diccionario](07-DICCIONARIO-DE-DATOS.md).
- **Permisos:** `ver_colaboradores`, `editar_colaboradores` y `editar_estructura` para Talento y
  Dirección, y `ver_sueldos` solo para Dirección, todos con alcance `TODO`, que es el único que
  cuenta en esta versión. Ver [roles y permisos](04-ROLES-Y-PERMISOS.md) y las
  [APIs](09-APIS.md).

Decisiones que tomó el usuario al aprobarlo:

- **Administrador no ve «Vacantes»** en el menú: esas pantallas ya le respondían 403.
- **Motivo de cese:** «otro» lleva el código propio `99`, fuera de la tabla 17; «no se inició la
  relación laboral» es el `17` de la tabla.
- **«Contratos por vencer»** incluye los ya vencidos de personas activas.
- **Auditoría:** dice qué campos cambiaron, nunca el importe del sueldo. Cuando un cambio arrastra
  ajustes de solo sueldo que quien lo registra no ve, la fila los nombra
  (`situacionesQueLoHeredan` al registrar, `situacionesQueLoPierden` al anular).
- **Sin `ver_sueldos`, un ajuste de solo sueldo no existe** para esa persona, programado o
  vigente, y anularlo —aunque ya esté anulado— responde 404.
- **Un cambio anulado se compara con la copia de lo que tenía detrás**, tomada al anularlo
  (`V65`), no con lo que se registró después.
- **Las sedes y los cargos los lee todo el equipo**; escribirlos pide `editar_estructura`.
- **Talento puede anular un cambio programado que toca el sueldo y otra cosa**, y con él se
  deshace también el sueldo. **Anular un programado en medio de una cadena** hace que el
  siguiente traiga, en su fecha, los datos que el anulado cambiaba, sin aviso.
- **Renovar un contrato queda pendiente**, sin apartado asignado: ver «Pendientes sin apartado
  asignado», arriba.

### Fuentes y cuánto fiarse

Revisadas el 28/09/2026, de la más fiable a la menos fiable:

| # | Fuente | Qué aporta | Cuánto fiarse |
|---|---|---|---|
| 1 | API pública de Visma People & Payroll (developer.vismalatam.com, colección de Postman) | El modelo de datos real: alta, baja, reingreso, estructuras, familiares, domicilios, cuentas, cargas masivas y registro de cambios. Marca los campos «Perú requerido» (por ejemplo, el CCI de 20 dígitos) | Oficial y vigente. Es la base más sólida para replicar el modelo |
| 2 | Centro de ayuda de Visma: Mandü Experience y Tu Recibo (zetechsa.freshdesk.com) | Que Perú usa Visma People; los pasos del legajo digital, del alta y la baja por CSV, de los perfiles y de la autogestión con aprobación de RR.HH. | Oficial, de 2022 a 2026, con referencias a Perú |
| 3 | Manual de Visma People (wiki.vismalatam.com) | Ficha, fases, estructuras, cambios masivos, circuitos de aprobación, auditoría, seguridad por vistas, informes, T-Registro | Oficial, con capturas, pero es la versión clásica (2018-2024) |
| 4 | Maquetas de mandu.pe | Las pestañas y los campos de la ficha | Oficial, pero son diseños, no pantallas reales |
| 5 | manduhr.com (Argentina) y prensa (Mercado, 10/02/2026) | Que es «la misma plataforma» de Visma Latam HR | Oficial o prensa. Contexto, no detalle |
| 6 | Vídeos oficiales en YouTube, por ejemplo «Visma People: cómo gestionar la ficha del colaborador» (18/09/2025, 85 min) | Serían la mejor fuente de la ficha actual | Sin transcribir: solo se tienen el título y la descripción |
| 7 | La ley: SUNAT (Anexo 1 de la Planilla Electrónica, 10/07/2023, y tablas paramétricas, 11/09/2026); DS 015-2010-TR; TUO del DL 728; DL 713; DS 001-97-TR; Ley 29733 y DS 016-2024-JUS | «Lo que exige la ley peruana» | Oficial |

Sin resultado:
- El centro de ayuda propio de Mandü Perú está apagado.
- help.vismalatam.com y community.visma.com piden iniciar sesión.
- Los comparadores de software no traen capturas ni reseñas.
- Contratos: TUO del DL 728. Vacaciones: DL 713. CTS: DS 001-97-TR. Datos personales: Ley 29733 y
  DS 016-2024-JUS
