# Gestión de personas: la ficha del colaborador y el menú lateral del panel
Estado: TERMINADO

## Objetivo

Que el equipo de RR.HH. de cada empresa tenga en el panel **la ficha de cada persona que trabaja
en ella** y el **mapa de la empresa**: sedes, áreas, cargos y quién es jefe de quién, todo con su
historia. Es el apartado 1 de [la ampliación de RR.HH.](../../docs/AMPLIACION-RRHH.md) y la base
del resto. Documentos, portal del colaborador, vacaciones, clima, desempeño y planilla se aplican
a esta persona, que hoy el sistema no conoce.

Con la ampliación, las pestañas de arriba del panel dejan de caber. Por eso esta spec también
**cambia la barra de pestañas por un menú lateral agrupado por familias**, igual que el menú de
productos de Mandü.

Para quién:
- **Talento (RR.HH.):** da de alta, mantiene y da de baja a los colaboradores, y edita sedes y
  cargos.
- **Dirección:** lo mismo que Talento y, además, es el único rol que ve y edita sueldos.
- **Dirección y Responsable del área:** contratan desde la ficha del postulante. El permiso es el
  mismo que hoy exige la API.

## Contexto

- Comportamiento actual:
  - **Contratar solo se puede por la API**, por dos caminos:
    - La decisión en verde, `POST /panel/postulaciones/{id}/decision` con semáforo `VERDE`.
      Pide `decidir_contratacion` la primera vez y `cambiar_decision` si ya hubo decisión.
      Hoy tienen `decidir_contratacion` Responsable del área (alcance `SUS_VACANTES`) y Dirección
      (`TODO`), y Talento no.
    - La transición manual a `CONTRATADO` con `mover_postulacion`.
    - Los dos llevan a `CONTRATADO` **desde cualquier estado no final**.
    - `CONTRATADO` es un estado final: no se revierte, no manda correo ni aviso, y no crea nada
      más. La decisión se guarda en `decision`, una por postulación.
  - **La persona contratada no existe.**
    - `persona` solo guarda nombre, apellidos y ciudad. `telefono`, `documento` y
      `fecha_nacimiento` existen, pero ningún código los escribe.
    - `documento` no es único y no tiene tipo.
    - La cuenta del candidato vive en la organización plataforma. Su postulación pertenece a la
      empresa de la vacante.
  - **La estructura de la empresa, hoy:**
    - Las **áreas** son una lista plana por empresa, sin jefe, y se gestionan en Configuración
      (`Areas.tsx`).
    - Los **puestos** (`puesto`) son un catálogo por empresa con nivel y familia obligatorios. Solo
      se crean desde el formulario de la solicitud o de la vacante, y no se pueden editar ni
      desactivar.
    - **No hay sedes.**
  - **El panel** tiene una cabecera horizontal con «Vacantes», «Simulación», «Pruebas»,
    «Configuración» y el botón «Salir» (`src/panel/Armazon.tsx`).
    - No conoce los permisos del usuario: no existe «mis permisos», y cada DTO trae sus
      booleanos `puedeX`.
    - Configuración es una sola página con secciones apiladas (`Configuracion.tsx`).
  - **Ya existe una importación desde Excel**, la del banco de preguntas:
    - Usa Apache POI y lee el archivo en memoria.
    - Es todo o nada: devuelve 400 con la lista de errores (hoja, fila, mensaje).
    - Su pantalla no enseña esa lista.
    - El almacén de archivos solo acepta PDF, DOC y DOCX.
  - **La auditoría** (`ServicioAuditoria.registrar`) guarda el valor anterior y el nuevo en
    jsonb, y no se puede modificar.
  - **El borrado de datos de la Ley 29733** anonimiza a la persona aunque esté contratada. La
    retención de 2 años solo toca `perfil_candidato`.
  - **El catálogo de ubigeo** solo tiene departamentos y provincias; no hay distritos.
- Pantallas o procesos afectados:
  - El armazón del panel: el menú.
  - La ficha del postulante que se despliega en el ranking (`DetalleDelPostulante` en
    `Vacante.tsx`): el botón «Contratar» y el enlace a la ficha del colaborador.
  - Configuración: las secciones nuevas «Sedes» y «Cargos».
  - Pantallas nuevas: `/admin/colaboradores` y `/admin/colaboradores/:id`.
- Referencias existentes:
  - [docs/AMPLIACION-RRHH.md, sección 1](../../docs/AMPLIACION-RRHH.md): qué es el módulo, cómo lo
    resuelve Mandü (Visma People), lo que exige la ley peruana y las fuentes.
  - [Reseñas a contratados](resenas-de-empresas-a-contratados.md): usa `CONTRATADO` y la fecha de
    su transición. Esta spec no toca ninguna de las dos.
  - [Eliminar vacantes](03-eliminar-vacantes.md): el patrón para conceder un permiso nuevo en
    todas las empresas.
  - `RenaserOsPostulantes/DESIGN.md`, `RenaserOsPostulantes/docs/PANEL.md` y la cabecera de
    `Armazon.module.css`.
  - La planificación de la semana está en `docs/PLANIFICACION-SEMANA-2026-09-28.xlsx`. La hoja
    «Qué pedir a la empresa», puntos 2 a 6, lista los datos de la clienta para este módulo.

## Alcance

- Incluye:
  1. El menú lateral del panel y una consulta de la sesión con los permisos del usuario, solo
     para pintarlo.
  2. La estructura de la empresa:
     - sedes, que son nuevas;
     - gestión del catálogo de cargos, que es el de puestos que ya existe;
     - el jefe directo de cada colaborador.
     - Las áreas no cambian.
  3. La lista de colaboradores, con búsqueda y filtros, y el aviso de contratados pendientes de
     alta.
  4. La ficha del colaborador, con tres pestañas: Perfil, Puesto y contrato, e Historial.
  5. El alta manual.
  6. Contratar desde la ficha del postulante y dar de alta al contratado con sus datos
     precargados.
  7. La carga por Excel: altas nuevas y actualización de datos personales.
  8. Los cambios de la situación laboral, con su historial y la opción de programarlos a futuro.
  9. El cese y el reingreso.
  10. Cuatro permisos nuevos.
- **Si no alcanza el tiempo, se recorta en este orden:**
  1. Reingreso (punto 32).
  2. Anular un cese (punto 31).
  3. Anular un cambio programado (punto 29).

  Todo lo demás es la base que necesitan los módulos siguientes.
- Queda fuera:
  - **Datos de planilla**, que entran con Planillas (apartado 20):
    - régimen de pensión y CUSPP, salud y EPS, SCTR;
    - derechohabientes e hijos;
    - cuentas bancarias y de CTS;
    - jornada y centro de costo;
    - el resto de códigos del T-Registro.
    - Los datos legales de la empresa: RUC, inscripción en el REMYPE y régimen agrario. Tampoco
      entra la regla de que micro o pequeña empresa exijan el REMYPE. Hasta entonces, RR.HH. elige
      el régimen de cada persona (decidido el 29/09/2026).
    - Tampoco se generan archivos para SUNAT.
  - Documentos y legajo, que van en el apartado 2.
  - El portal del colaborador y sus cuentas de acceso, que van en el apartado 3.
  - Organigrama visual, tablero, reportes y exportación a Excel.
  - Aprobaciones de cambios, que un RR.HH. solo alcance su sede o área, y que un jefe vea a su
    equipo.
  - Campos propios de cada empresa.
  - Cambios de puesto o de contrato en bloque por Excel.
  - Aviso o correo al candidato contratado, y correos de contratos por vencer.
  - Cerrar la vacante sola al cubrir sus plazas.
  - Reordenar Configuración en pestañas.

## Comportamiento esperado

### A · El menú lateral

1. El armazón del panel sustituye la barra de pestañas por un **menú lateral a la izquierda**,
   agrupado por familias:
   - **Selección:** Vacantes, Simulación, Pruebas.
   - **Personas:** Colaboradores.
   - Al pie, separado: **Configuración**.

   Solo aparecen las entradas que el usuario puede usar (punto 3), y una familia sin entradas no
   sale. No hay entradas de módulos futuros ni avisos de «próximamente».
2. **Arriba queda una barra fina** con tres elementos:
   - La marca con «Panel del equipo», que sigue siendo el enlace a `/admin` con nombre accesible
     «Panel del equipo, inicio».
   - El nombre de la empresa.
   - El nombre de la persona, con el botón «Salir».
3. **Al entrar, el panel pide la sesión del usuario:** nombre, correo, empresa y sus permisos con
   su alcance. Sirve solo para pintar el menú; la API sigue respondiendo 403 y 404 como hoy.
   - «Colaboradores» se ve con `ver_colaboradores` de alcance `TODO`.
   - Las cuatro entradas de hoy siguen el criterio de los permisos que ya exige su pantalla. **Ningún
     rol puede perder una entrada cuya pantalla le funciona hoy.**
   - Si la sesión no se puede cargar, el menú enseña las cuatro entradas de hoy y el panel sigue
     funcionando.
4. **Plegar el menú.** «Plegar menú» deja una columna de iconos. Cada icono conserva su nombre
   accesible y lo muestra como tooltip. La elección se recuerda en ese navegador, y si no se
   puede recordar, el menú sale desplegado.
5. **En pantallas estrechas** (menos de 1024 px) el menú se oculta. Un botón «Menú» en la barra
   lo abre como un cajón, que se cierra al elegir una entrada, con Escape o al tocar fuera. El
   foco queda dentro del cajón mientras está abierto y vuelve al botón al cerrarlo.
6. **La entrada activa se marca**, también en las rutas hijas: `/admin/vacantes/:id` y
   `/admin/archivadas` marcan «Vacantes», y `/admin/colaboradores/:id` marca «Colaboradores».
7. **Los enlaces conservan sus nombres actuales**: «Vacantes», «Simulación», «Pruebas»,
   «Configuración» y el botón «Salir». Ninguna dirección de página cambia.

### B · La estructura de la empresa, en Configuración

8. **Configuración añade las secciones «Sedes» y «Cargos»** junto a «Áreas», con el mismo patrón
   de lista y acciones. Quien no tiene `editar_estructura` las ve sin acciones.
   - **Sedes:**
     - Datos: nombre, que es obligatorio y único en la empresa; dirección; provincia, del
       catálogo de ubigeo; y código de establecimiento de SUNAT, opcional, de 4 dígitos.
     - Acciones: añadir, editar, desactivar y reactivar.
     - Una sede desactivada no se puede elegir en altas, cambios ni cargas, y quien ya está en
       ella la conserva.
     - No se borran sedes.
   - **Cargos:**
     - Es el catálogo de puestos que ya usan las solicitudes y las vacantes. La lista enseña
       nombre, nivel y familia.
     - Acciones: añadir (nombre, nivel y familia, todos obligatorios), renombrar, desactivar y
       reactivar.
     - **Renombrar cambia el nombre en todas partes**, vacantes incluidas, y el formulario lo
       avisa antes de guardar.
     - Un cargo desactivado no se puede elegir en altas, cambios, cargas ni solicitudes nuevas, y
       lo que ya lo usa lo conserva.
   - **Áreas:** sin cambios.

### C · La lista de colaboradores

9. **`/admin/colaboradores`** enseña una tabla con estas columnas:
   - apellidos y nombres;
   - documento;
   - cargo, área y sede vigentes;
   - jefe directo;
   - fecha de ingreso;
   - fin de contrato;
   - estado.

   Se ordena por apellidos.
   - **Tres estados, que salen de las fechas en hora de Lima:**
     - «Por ingresar»: la fecha de ingreso es futura.
     - «Activo».
     - «Cesado»: desde el día siguiente a la fecha de cese.
   - **Fin de contrato:** la fecha, con dos etiquetas posibles:
     - «vence en N días» cuando faltan 30 o menos;
     - «vencido» cuando ya pasó y la persona sigue activa. En este caso se explica que, si sigue
       trabajando, su contrato pasa a ser indefinido.
   - **Filtros:**
     - Estado, que por defecto enseña Activos y Por ingresar.
     - Sede, área y cargo.
     - «Contratos por vencer» (30 días).
     - Una búsqueda por nombre o número de documento.
     - Un contador «N colaboradores».
   - **Volumen:** aguanta al menos 5.000 colaboradores, con páginas de 50 servidas desde el
     backend.
   - **Acciones de la cabecera**, con `editar_colaboradores`: «Nuevo colaborador» y «Cargar
     Excel».
10. **Aviso de contratados pendientes.** Si hay postulaciones `CONTRATADO` de la empresa sin ficha
    de colaborador, arriba de la tabla sale «N personas contratadas por selección esperan su
    alta».
    - Al abrirlo, lista a cada una con nombre, vacante, fecha de contratación y dos acciones:
      «Dar de alta» y «No dar de alta».
    - Cuenta todas las contrataciones anteriores a esta spec y las hechas por la API, salvo las de
      vacantes eliminadas.
    - «No dar de alta» pide un motivo, por ejemplo «ya no trabaja aquí». Saca a esa persona del
      aviso y queda en la auditoría.
11. **Estados vacíos:**
    - Sin ningún colaborador: «Todavía no hay colaboradores», con las acciones de alta y carga si
      el usuario tiene el permiso.
    - Con filtros sin resultados: «Ningún colaborador cumple estos filtros» y «Quitar filtros».

### D · La ficha del colaborador

12. **`/admin/colaboradores/:id`.** La cabecera enseña:
    - nombre completo, documento, cargo y área vigentes, y estado;
    - si vino de selección: «Contratado por la vacante {título}», con enlace si la vacante sigue
      existiendo.

    Tiene tres pestañas: **Perfil**, **Puesto y contrato** e **Historial**.
13. **Perfil:**
    - **Identidad:**
      - tipo y número de documento;
      - nombres;
      - apellido paterno;
      - apellido materno;
      - fecha de nacimiento;
      - sexo;
      - estado civil;
      - nacionalidad.
    - **Contacto:** celular, correo personal y correo corporativo.
    - **Domicilio:** dirección y provincia.
    - **Formación:** nivel educativo, del catálogo que ya usa el perfil del candidato.
    - «Editar», con `editar_colaboradores`, cambia estos datos **sin historial**: son correcciones.
      Cada edición queda en la auditoría con el valor anterior y el nuevo.
14. **Puesto y contrato:**
    - **La situación vigente hoy:**
      - sede, área, cargo y jefe directo;
      - tipo de contrato y fecha de fin del contrato;
      - fin del periodo de prueba;
      - régimen laboral;
      - sueldo base y moneda.
    - **El periodo actual:** fecha de ingreso y, si lo hay, cese con fecha y motivo.
    - **Los cambios programados**, es decir, los que rigen desde una fecha futura, con «Anular».
    - **Acciones**, con `editar_colaboradores`: «Registrar un cambio» y «Registrar el cese». En
      una ficha cesada, en su lugar: «Reingresar» y «Anular el cese».
15. **Historial:** una línea de tiempo, de lo más reciente a lo más antiguo:
    - Cada cambio de situación, con:
      - la fecha desde la que rige;
      - lo que cambió, como «antes → después»;
      - el tipo de motivo y su detalle;
      - quién lo registró y cuándo.
    - Cada periodo: ingreso, cese con su motivo y reingreso.
    - Los anulados salen tachados, con quién los anuló y por qué.
    - Las ediciones del Perfil no salen aquí; están en la auditoría.
16. **Sueldo.** Sin `ver_sueldos`, el sueldo **no aparece en ninguna parte**: ni en la ficha, ni
    en el historial, ni en la lista, ni en la plantilla de Excel.
    - Los cambios que solo tocan el sueldo no salen en su historial.
    - Los que tocan el sueldo y otra cosa salen solo con lo demás.
    - No basta con ocultarlo en pantalla: **la API no lo envía**.

### E · El alta manual

17. **«Nuevo colaborador»** abre un formulario con tres bloques: Identidad y contacto, Domicilio y
    formación, e Ingreso y puesto.
    - **Obligatorios:**
      - tipo y número de documento;
      - nombres;
      - apellido paterno;
      - fecha de nacimiento;
      - sexo;
      - fecha de ingreso;
      - sede, área y cargo;
      - tipo de contrato;
      - régimen laboral.
    - **Fecha de fin del contrato:** es obligatoria cuando el tipo es a plazo o de temporada y no
      se admite en el indeterminado.
    - Lo demás es opcional. El sueldo solo se ve y se guarda con `ver_sueldos`.
18. **Validaciones:**
    - **DNI:** 8 dígitos.
    - **Otros documentos:** carné de extranjería, pasaporte, PTP y CPP admiten de 4 a 15 letras o
      dígitos.
    - **Fecha de nacimiento:** no puede ser futura y la persona debe tener al menos 14 años.
    - **Fechas del contrato:** el fin del contrato y el fin del periodo de prueba deben ser
      posteriores a la fecha de ingreso.
    - **Jefe directo:** debe ser un colaborador Activo o Por ingresar de la misma empresa. No puede
      ser la propia persona y no puede crear un círculo, por ejemplo A reporta a B y B a A.
19. **Nunca hay dos fichas del mismo documento (tipo y número) en la misma empresa.**
    - Si ya existe una Activa o Por ingresar, el alta se rechaza con «Ya es colaborador» y un
      enlace a su ficha.
    - Si existe una Cesada: «Ya trabajó aquí. Reingrésalo desde su ficha», con enlace.
20. **Guardar** crea en una sola operación la ficha, su primer periodo y su primera situación
    laboral, con fecha desde igual a la de ingreso y motivo «Ingreso». Queda en la auditoría. El
    botón se desactiva mientras se envía.

### F · Contratar desde el panel

21. **El botón «Contratar».** En la ficha del postulante que se despliega en el ranking aparece
    «Contratar» si la postulación no está en un estado final y el usuario tiene
    `decidir_contratacion` sobre ella. Da igual la etapa.
    - El backend lo indica con un booleano en la ficha, como los demás `puedeX`.
    - Si ya hubo una decisión, en su lugar hace falta `cambiar_decision`, igual que en la API.
22. **El modal «Contratar a {nombre}»** enseña:
    - la vacante y la etapa en que está la persona;
    - si no está en la etapa Decisión: «Todavía está en la etapa {etapa}. Contratar cierra su
      proceso aquí, sin pasar por las etapas que faltan».
    - un motivo obligatorio;
    - «No se le envía ningún correo».

    Confirmar registra la **decisión en verde** con ese motivo, por el mismo camino que la API de
    hoy, y la postulación pasa a Contratado. Cancelar no hace nada. Mientras se envía no admite un
    segundo clic, y si hay un error, el modal conserva el motivo.
23. Al terminar, el mensaje de éxito ofrece **«Dar de alta como colaborador»** si el usuario tiene
    `editar_colaboradores`.
24. **La ficha de una postulación Contratado** de la empresa enseña:
    - «Dar de alta como colaborador» si todavía no tiene ficha, con `editar_colaboradores`;
    - «Ver su ficha de colaborador» si ya la tiene, con `ver_colaboradores`.
25. **Dar de alta a un contratado** abre el alta manual con estos datos precargados:
    - Nombres.
    - Apellidos, en «apellido paterno», para que RR.HH. los separe.
    - Correo, el de su cuenta, salvo si es uno generado por la carga de CV
      (`@cv-convocatoria.local`).
    - Celular, el del CV.
    - Cargo, el puesto de la vacante.
    - Área, la de la solicitud de la vacante.
    - Sueldo, el fijo que publicaba la vacante, si lo tenía y solo con `ver_sueldos`.

    La fecha de ingreso queda vacía y es obligatoria. La ficha queda **enlazada a esa postulación**,
    y cada postulación contratada da como mucho un alta. Si el documento coincide con una ficha
    Cesada, se ofrece reingresarla (punto 32), y el periodo nuevo queda enlazado a la postulación.

### G · La carga por Excel

26. **«Cargar Excel»** abre un panel con tres pasos: descargar la plantilla, subir el archivo y ver
    el resultado.
    - **La plantilla** (.xlsx) se genera al momento y tiene tres hojas:
      - **«Colaboradores»:** los encabezados, con los obligatorios marcados, y listas desplegables
        en las columnas de lista. La columna de sueldo solo está si el usuario tiene
        `ver_sueldos`.
      - **«Instrucciones».**
      - **«Valores»:** las opciones válidas en ese momento: tipos de documento, sexo, estado
        civil, niveles educativos, sedes, áreas y cargos activos, tipos de contrato y regímenes.
27. **Cada fila se identifica por el tipo y el número de documento:**
    - **Si no existe en la empresa**, es un alta, con la misma validación que el alta manual. Su
      situación rige desde la fecha de ingreso, con el motivo «Carga inicial».
    - **Si existe y está Activa o Por ingresar**, se actualizan identidad, contacto, domicilio y
      formación. Una celda vacía no borra nada.
      - Si las columnas de puesto y contrato traen valores distintos de los vigentes, la fila da
        error: «Los cambios de puesto y contrato se registran en su ficha».
    - **Si existe y está Cesada**, la fila da error: «Está cesado. Reingrésalo desde su ficha».
    - **Sede, área y cargo** se buscan por nombre, sin distinguir mayúsculas, y deben existir y
      estar activos. Si no, el error los nombra.
    - **Jefe directo** se indica por el tipo y el número de documento del jefe, que puede ser otra
      fila del mismo archivo o un colaborador que ya existe. Las mismas reglas del punto 18 valen
      para el archivo entero: nada de círculos.
    - **Un documento repetido en el archivo** da error en todas las filas donde aparece.
    - **Formato:** las columnas se reconocen por su encabezado, no por su orden. Un encabezado
      desconocido es un error, y una fila vacía se ignora.
28. **Todo o nada.**
    - Primero se valida el archivo entero. Si hay algún error, **no se guarda nada** y la pantalla
      enseña **todos** los errores en una tabla: fila, columna, valor y qué pasa.
    - Si no hay errores, se aplican todas las filas en una sola transacción y la pantalla dice
      «Se dieron de alta N colaboradores y se actualizaron M».
    - Se admite un .xlsx de hasta 10 MB y 5.000 filas.
    - El archivo se lee en memoria y no se guarda.
    - En la auditoría queda una fila con el resumen: el nombre del archivo y los dos recuentos.
    - Con `ver_sueldos` ausente, un archivo con columna de sueldo se rechaza entero: «No tienes
      permiso para cargar sueldos».

### H · Los cambios de la situación laboral

29. **«Registrar un cambio»** abre la situación vigente precargada y pide tres cosas:
    - **La fecha desde la que rige.**
      - No puede ser anterior a la fecha de ingreso ni a la del último cambio del periodo.
      - Puede ser futura. Ese cambio queda «programado» y rige solo ese día.
    - **El tipo de motivo:** promoción, traslado, cambio de jefe, renovación de contrato, ajuste
      de remuneración, corrección u otro.
    - **Un detalle opcional**, que es obligatorio si el tipo es «otro».

    Debe cambiar al menos un campo.
    - **Al guardar**, la situación anterior termina el día antes y la nueva rige desde su fecha. La
      lista y la ficha siempre enseñan la vigente hoy.
    - **Sin `ver_sueldos`**, el sueldo no se ve y pasa igual a la situación nueva.
    - **Un cambio ya vigente o pasado no se borra.** Se corrige con otro cambio de tipo
      «corrección».
    - **Un cambio programado se puede «Anular»** con motivo. Queda tachado en el historial y en
      la auditoría.

### I · El cese y el reingreso

30. **«Registrar el cese»** pide:
    - **La fecha de cese**, que es el último día trabajado. No puede ser anterior a la fecha de
      ingreso y puede ser futura; hasta ese día la persona sigue Activa, con «Cesa el
      {fecha}».
    - **El motivo**, con la lista de la tabla 17 de SUNAT: renuncia, renuncia con incentivos,
      despido, cese colectivo, jubilación, invalidez, fin del contrato o de la obra, mutuo
      disenso, fallecimiento, no se inició la relación laboral u otro.
    - **Una observación**, opcional.

    **El modal explica las consecuencias antes de confirmar:**
    - La persona pasa a Cesada desde el día siguiente, sigue en el sistema y no se borra nunca.
    - Si N personas la tienen como jefe, las nombra y avisa de que conviene cambiarles el jefe.
      No bloquea.
    - Los cambios programados con fecha posterior al cese se anulan, y el modal los enumera.
31. **«Anular el cese»**, con motivo, deja el periodo abierto otra vez. Solo se puede mientras no
    haya habido un reingreso posterior, y queda en la auditoría.
32. **«Reingresar»**, en una ficha Cesada, pide una fecha de ingreso nueva, que tiene que ser
    posterior a la última fecha de cese. Trae precargada la última situación, que se puede editar.
    - Abre un **periodo nuevo** con el motivo «Reingreso».
    - Los periodos anteriores quedan en el historial.
    - Es el camino al que llevan el alta manual, la carga y el alta de un contratado cuando el
      documento ya tiene una ficha Cesada.

### J · Los permisos

33. **Cuatro permisos nuevos**, en el grupo nuevo «Personas» de la matriz de Configuración:

    | Permiso | Etiqueta | Roles |
    |---|---|---|
    | `ver_colaboradores` | Ver los colaboradores y sus fichas | Talento, Dirección |
    | `editar_colaboradores` | Dar de alta, editar, cesar y reingresar colaboradores | Talento, Dirección |
    | `ver_sueldos` | Ver y editar el sueldo de los colaboradores | Dirección |
    | `editar_estructura` | Editar las sedes y los cargos | Talento, Dirección |

    - Se conceden con alcance `TODO` en todas las empresas existentes y en las que se creen
      después, porque el alta de empresa copia la matriz.
    - **En esta versión solo cuenta el alcance `TODO`.** Con cualquier otro, el usuario no alcanza
      a ningún colaborador.
    - Contratar sigue pidiendo `decidir_contratacion`, o `cambiar_decision` si ya hubo decisión.
    - Sin permiso, la API responde 403. Una ficha de otra empresa, o fuera de alcance, responde
      404.

## Referencia visual

Reglas visuales:
- **Referencias:** `RenaserOsPostulantes/DESIGN.md` y el mundo del panel descrito en
  `Armazon.module.css`: el filete del espectro, Mulish, más densidad que el portal y
  `--ancho-panel`.
- **Qué se reutiliza:**
  - El modal compartido.
  - El patrón de lista y acciones de `Areas.tsx`.
  - La búsqueda, los filtros y la tabla del ranking.
  - Los iconos que ya use el panel.
- **Queda a criterio de quien implementa:** los iconos concretos, los anchos exactos y la
  animación del cajón.

**Armazón, en escritorio (desplegado y plegado):**

```
┌──────────────────┬──────────────────────────────────────────────┐
│ ◈ Panel del      │  Clínica Renaser S.A.C.        Ana Pérez ▾   │
│   equipo         │                                  Salir       │
├──────────────────┼──────────────────────────────────────────────┤
│ SELECCIÓN        │                                              │
│ ▣ Vacantes       │                                              │
│ ▣ Simulación     │            (pantalla de la entrada)          │
│ ▣ Pruebas        │                                              │
│ PERSONAS         │                                              │
│ ▣ Colaboradores  │                                              │
│                  │                                              │
│ ──────────       │                                              │
│ ▣ Configuración  │                                              │
│ « Plegar menú    │                                              │
└──────────────────┴──────────────────────────────────────────────┘

Plegado: ┌───┬───────────────────────────
         │ ▣ │  (tooltip «Vacantes»)
         │ ▣ │
         │ » │
```

**En pantallas estrechas:** una barra con «☰ Menú», la marca y la persona. El menú se abre como
un cajón encima de la página.

**Lista de colaboradores:**

```
Colaboradores                           [Cargar Excel] [Nuevo colaborador]
┌──────────────────────────────────────────────────────────────────────┐
│ ⓘ 2 personas contratadas por selección esperan su alta      [Ver]    │
└──────────────────────────────────────────────────────────────────────┘
[Buscar por nombre o documento]  Estado: Activos, Por ingresar ▾  Sede ▾
Área ▾  Cargo ▾  [ ] Contratos por vencer                148 colaboradores
┌────────────────────┬──────────┬───────────┬────────┬─────────┬────────┬──────────┬───────────────┬─────────┐
│ Colaborador        │ Documento│ Cargo     │ Área   │ Sede    │ Jefe   │ Ingreso  │ Fin contrato  │ Estado  │
├────────────────────┼──────────┼───────────┼────────┼─────────┼────────┼──────────┼───────────────┼─────────┤
│ Rivas Sánchez, Ana │ DNI 4512…│ Analista  │ Finanz.│ Lima    │ Torres │ 02/03/26 │ 31/10 · vence │ Activo  │
│                    │          │           │        │         │        │          │ en 12 días    │         │
└────────────────────┴──────────┴───────────┴────────┴─────────┴────────┴──────────┴───────────────┴─────────┘
                                                       ‹ 1 2 3 ›
```

**Ficha:**

```
Rivas Sánchez, Ana María        DNI 45123456        Activo
Analista de finanzas · Finanzas · Contratada por la vacante «Analista…»
[ Perfil ] [ Puesto y contrato ] [ Historial ]
─────────────────────────────────────────────
Puesto y contrato                [Registrar un cambio] [Registrar el cese]
Sede: Lima · Área: Finanzas · Cargo: Analista · Jefe: Torres, Luis
Contrato: inicio o incremento de actividad · fin 31/10/2026
Periodo de prueba hasta 02/06/2026 · Régimen: general
Sueldo: S/ 4.500 (solo con permiso)
Periodo actual: ingreso 02/03/2026
Cambios programados: desde 01/11/2026 · renovación de contrato [Anular]
```

**Carga por Excel:**

```
1 · Descarga la plantilla        [Descargar plantilla]
2 · Sube el archivo              [Elegir archivo .xlsx]  [Validar y cargar]
3 · Resultado
    ✗ El archivo tiene 3 errores. No se guardó nada.
    ┌──────┬──────────────┬────────────┬────────────────────────────────┐
    │ Fila │ Columna      │ Valor      │ Qué pasa                        │
    │ 14   │ Sede         │ Arequipa   │ No existe la sede «Arequipa»    │
    │ 20   │ Documento    │ 4512345    │ El DNI tiene que tener 8 dígitos│
    │ 31   │ Jefe directo │ 40111222   │ Crea un círculo con la fila 12  │
    └──────┴──────────────┴────────────┴────────────────────────────────┘
```

**Estados que hay que dibujar en cada pantalla:**
- Normal.
- Vacío (sin colaboradores, o filtros sin resultados).
- Cargando.
- Con error de red. La ficha sin permiso responde 404, «Este colaborador no existe».
- Con datos largos: nombres y cargos largos, 5.000 filas y un historial de 50 cambios.

**Móvil y escritorio:**
- En móvil la tabla de colaboradores pasa a tarjetas con nombre, cargo, área y estado.
- La ficha apila sus bloques.
- Los modales ocupan la pantalla entera.

## Criterios de aceptación

- AC-01: Dado un usuario de Talento, cuando entra al panel, entonces ve el menú lateral con
  Selección (Vacantes, Simulación, Pruebas), Personas (Colaboradores) y Configuración al pie. Los
  enlaces se llaman igual que hoy y llevan a las mismas direcciones.
- AC-02: Dado un usuario de Responsable del área, que no tiene `ver_colaboradores`, cuando entra
  al panel, entonces no ve la familia Personas. Sigue viendo todas las entradas que hoy le
  funcionan.
- AC-03: Dado el menú desplegado, cuando se pliega y se recarga la página, entonces sigue plegado,
  y cada icono tiene nombre accesible y tooltip. Dada una pantalla de menos de 1024 px, el menú se
  abre como cajón con «Menú» y se cierra con Escape, al elegir o al tocar fuera.
- AC-04: Dada una ruta hija, como `/admin/vacantes/12` o `/admin/colaboradores/3`, cuando se abre,
  entonces su entrada del menú aparece marcada como activa.
- AC-05: Dado un usuario con `editar_estructura`, cuando añade la sede «Planta Ventanilla» con
  provincia y código SUNAT 0003, entonces aparece en la lista y se puede elegir en el alta. Al
  desactivarla, deja de poder elegirse y quien estaba en ella la conserva.
- AC-06: Dado un cargo usado por una vacante, cuando se renombra, entonces el formulario avisa
  antes de guardar y el nombre nuevo sale en la vacante y en las fichas.
- AC-07: Dado un alta manual válida con fecha de ingreso de hoy, cuando se guarda, entonces la
  persona sale Activa en la lista, con su cargo, área, sede y jefe. Su historial tiene el ingreso
  y la situación inicial con el motivo «Ingreso», y la auditoría registra el alta.
- AC-08: Dado un alta con fecha de ingreso futura, cuando se guarda, entonces sale «Por ingresar»
  hasta ese día, y «Activo» a partir de ese día, sin que nadie toque nada.
- AC-09: Dado un DNI ya registrado en la empresa, cuando se intenta otra alta con él, entonces se
  rechaza con «Ya es colaborador» y un enlace a su ficha. Si la ficha está Cesada, el mensaje
  ofrece reingresarla. El mismo DNI sí se puede dar de alta en otra empresa.
- AC-10: Dado un alta cuyo jefe crearía un círculo, o que se pone a sí misma como jefe, cuando se
  guarda, entonces se rechaza con un mensaje que lo explica.
- AC-11: Dada una postulación en la etapa Prueba y un usuario de Dirección, cuando abre su ficha en
  el ranking, entonces ve «Contratar». El modal avisa de que todavía está en la etapa Prueba y
  exige un motivo. Al confirmar, la postulación queda Contratado, con una decisión en verde y ese
  motivo, y no se envía ningún correo.
- AC-12: Dado un usuario de Talento, que no tiene `decidir_contratacion`, cuando abre la misma
  ficha, entonces no ve «Contratar», y la API de decisión le responde 403.
- AC-13: Dada una postulación Contratado sin ficha, cuando se pulsa «Dar de alta como colaborador»,
  entonces el alta sale precargada con nombre, apellidos, correo (salvo uno de
  `@cv-convocatoria.local`), celular del CV, cargo de la vacante y área de su solicitud. Al
  guardar, la ficha queda enlazada a la postulación y un segundo alta desde esa postulación no es
  posible.
- AC-14: Dadas dos postulaciones Contratado sin ficha, una de ellas contratada por la API antes de
  esta spec, cuando se abre la lista, entonces el aviso dice «2 personas… esperan su alta». Tras
  «No dar de alta» con motivo en una, el aviso dice 1 y la auditoría lo registra.
- AC-15: Dado un Excel de 20 filas válidas, en el que 3 tienen como jefe a otra fila del mismo
  archivo, cuando se carga, entonces se crean 20 fichas con sus jefes y la pantalla dice «Se dieron
  de alta 20 colaboradores y se actualizaron 0».
- AC-16: Dado un Excel con una sede inexistente, un DNI de 7 dígitos, un documento repetido y un
  círculo de jefes, cuando se carga, entonces no se guarda ninguna fila y la pantalla enseña todos
  los errores con fila, columna, valor y explicación.
- AC-17: Dado un Excel con un colaborador ya Activo cuyo celular cambió y cuyo cargo es el mismo,
  cuando se carga, entonces solo se actualiza el celular. Si la fila traía otro cargo, esa fila da
  error y no se guarda nada.
- AC-18: Dado un colaborador Activo, cuando se registra un cambio de cargo con fecha de hoy y tipo
  «promoción», entonces la lista y la ficha enseñan el cargo nuevo, y el historial enseña «Cargo:
  Analista → Jefe de finanzas», con su fecha, motivo, autor y momento.
- AC-19: Dado un cambio con fecha futura, cuando se guarda, entonces sale como programado y la
  situación vigente no cambia hasta ese día. «Anular» con motivo lo tacha en el historial.
- AC-20: Dado un cambio con una fecha anterior a la del último cambio del periodo, cuando se
  intenta guardar, entonces se rechaza y se explica por qué.
- AC-21: Dado un colaborador que es jefe de 2 personas, cuando se registra su cese con fecha de hoy
  y motivo «renuncia», entonces el modal nombra a esas 2 personas antes de confirmar. Desde mañana
  sale Cesado, no desaparece de la lista si se filtra por Cesados, y conserva su historial.
- AC-22: Dado un colaborador Cesado, cuando se reingresa con una fecha posterior a su cese,
  entonces sale Activo con un periodo nuevo, y el historial enseña los dos periodos.
- AC-23: Dado un usuario de Talento, que no tiene `ver_sueldos`, cuando abre la lista, la ficha, el
  historial o la plantilla, o consulta la API de colaboradores, entonces el sueldo no aparece en
  ninguna respuesta. Si sube un Excel con una columna de sueldo, se rechaza entero.
- AC-24: Dado un usuario de otra empresa, cuando pide la ficha de un colaborador de la primera,
  entonces recibe 404, y ese colaborador no sale en su lista.
- AC-25: Dada una empresa creada después de esta spec, cuando se da de alta, entonces sus roles
  Talento y Dirección ya tienen los permisos nuevos, igual que en las empresas existentes.
- AC-26: Los E2E actuales que dependen del armazón siguen en verde sin relajar lo que comprueban:
  el enlace «Pruebas» de `15-componer-prueba`, el banner y el enlace de la marca de
  `41-logotipo`, y «Salir» de `10-panel-entrar`.

## Casos límite y errores

- Datos vacíos o inválidos:
  - Fechas imposibles, documento con espacios o puntos (se limpian antes de validar), nombres de
    solo espacios, sueldo negativo o con letras.
  - Un Excel sin la hoja «Colaboradores», con un encabezado desconocido, de otro formato (.xls,
    .csv) o de más de 5.000 filas: se rechaza con un mensaje claro y sin guardar nada.
- Usuario sin permisos:
  - La API responde 403 aunque el botón no se vea.
  - Una ficha de otra empresa, o fuera de alcance, responde 404.
  - Talento no contrata.
  - Sin `ver_sueldos`, el sueldo no viaja en ninguna respuesta, ni siquiera en los mensajes de
    error de la carga.
- Recarga, reintento o doble envío:
  - Los botones se desactivan mientras envían, pero el backend también valida:
    - Dos altas simultáneas del mismo documento dejan una sola ficha.
    - Dos «Dar de alta» de la misma postulación dejan una sola ficha.
    - Contratar dos veces no crea dos decisiones.
  - Si la sesión caduca a mitad de un formulario, se avisa sin perder lo escrito.
- Otros casos relevantes:
  - **Fechas en hora de Lima.** Un cese con fecha de hoy deja a la persona Activa hasta el final
    del día. Un cambio o un ingreso con fecha de hoy rige desde hoy.
  - **Un cambio el mismo día que el ingreso** sustituye la situación inicial como un cambio más,
    sin romper el historial.
  - **Un jefe cesado:** quien lo tenía como jefe lo conserva hasta que se le cambie, y la ficha
    enseña «(cesado)» junto a su nombre.
  - **Una sede, un área o un cargo desactivados** siguen saliendo en la situación de quien ya los
    tenía, marcados como «(inactivo)».
  - **Una vacante eliminada:** su contratado no sale en el aviso de pendientes, y una ficha que ya
    venía de ella conserva el texto sin enlace.
  - **El candidato pide el borrado de sus datos** (Ley 29733) en el portal: su ficha de
    colaborador no cambia (ver «Datos y compatibilidad»).
  - **Un apellido compuesto que viene de selección** («De la Cruz Pérez») llega entero a «apellido
    paterno», y RR.HH. lo separa a mano.

## Datos y compatibilidad

- Información que debe guardarse:
  - **Sede:** empresa, nombre, dirección, provincia (ubigeo), código de establecimiento de SUNAT y
    si está activa.
  - **Colaborador**, de la empresa:
    - **Identidad:**
      - tipo de documento, con el código de la tabla 3 de SUNAT: 01 DNI, 04 carné de
        extranjería, 07 pasaporte, 23 PTP y 26 CPP;
      - número;
      - nombres y apellidos paterno y materno;
      - fecha de nacimiento;
      - sexo;
      - estado civil;
      - nacionalidad.
    - **Contacto y domicilio:** celular, correos, dirección y provincia.
    - **Formación:** nivel educativo.
    - **Otros:** la postulación de origen, opcional, y quién la creó y cuándo.
    - **Único:** empresa + tipo de documento + número.
  - **Periodo laboral:**
    - colaborador;
    - fecha de ingreso;
    - fecha de cese;
    - motivo de cese, con el código de la tabla 17 de SUNAT;
    - observación;
    - postulación de origen, opcional;
    - datos de anulación del cese.
  - **Situación laboral, con vigencia desde y hasta:**
    - sede, área, cargo y jefe;
    - tipo de contrato, con el código de la tabla 12 de SUNAT: 01 indeterminado, 02 tiempo
      parcial, 03 inicio o incremento de actividad, 04 necesidades de mercado, 05 reconversión
      empresarial, 06 ocasional, 07 suplencia, 08 emergencia, 09 obra o servicio específico, 10
      intermitente, 11 temporada y 99 otros. Además, «convenio de prácticas» como opción propia;
    - fin del contrato;
    - fin del periodo de prueba;
    - régimen laboral, con el código de la tabla 33 de SUNAT: 01 general, 16 microempresa, 17
      pequeña empresa, 18 agrario (Ley 27360), 20 minero, 21 construcción civil, 26 agrario
      (Ley 31110) y 99 otros;
    - sueldo y moneda (PEN o USD);
    - tipo de motivo y detalle;
    - quién y cuándo;
    - datos de anulación.
  - **Contratado que no se da de alta:** la postulación, el motivo, quién y cuándo.
  - **Por qué los códigos de SUNAT desde ahora:** para que Planillas los use sin migrar valores.
    En pantalla se enseña el texto, nunca el código.
- Comportamiento para registros existentes:
  - Hoy no hay colaboradores.
  - Las postulaciones que ya están en `CONTRATADO` se quedan igual y aparecen en el aviso de
    pendientes (punto 10).
  - Las áreas y los puestos existentes no cambian. Los puestos solo ganan editar, desactivar y
    reactivar.
- Restricciones de compatibilidad:
  - **La ficha es independiente de `persona` y del perfil del portal.** Editar una no cambia el
    otro.
    - **El borrado de la Ley 29733 y la retención de 2 años no tocan la ficha**, porque la
      relación laboral tiene su propia base legal (Ley 29733, art. 14.5) y la ley laboral obliga a
      conservar registros.
    - Esto queda **pendiente de validar con el abogado** (punto 14 de «Qué pedir a la empresa»).
      Si dice otra cosa, se cambia el alcance del borrado, no esta estructura.
  - **Contratar reutiliza la decisión en verde** que ya existe. No crea otro camino a
    `CONTRATADO` ni cambia su transición ni su fecha, que usa la spec de reseñas.
  - **La consulta de la sesión es nueva y solo sirve para pintar el menú.** Los booleanos `puedeX`
    de los DTO se quedan, y la ficha del postulante suma los suyos para contratar y dar de alta.
  - **La carga por Excel sigue el patrón de la importación del banco:** POI, lectura en memoria,
    todos los errores juntos y todo o nada. Esta vez **la pantalla sí pinta la lista de errores**.
    No cambia el almacén de archivos.
  - **Las rutas nuevas van bajo `/api/v1/panel/…`.** No cambia ninguna ruta del panel ni del
    portal.
  - **Migración de Flyway:** hoy la última integrada es la V63, y otras specs abiertas también
    añaden migraciones. Hay que consultar las migraciones integradas y las ramas abiertas y usar el
    siguiente número libre, sin fijarlo por anticipado y sin editar migraciones anteriores. Los
    permisos se conceden a Talento y Dirección en todas las empresas existentes, con el patrón de
    la V60.
  - **Documentación tras la aprobación:** `docs/PANEL.md` y `DESIGN.md` de RenaserOsPostulantes
    (armazón y menú), y el estado de la sección 1 de `docs/AMPLIACION-RRHH.md`.

## Verificación

- Actor/rol y datos de prueba necesarios:
  - **Una empresa** con 2 sedes (una desactivada), 3 áreas y 4 cargos.
  - **Tres usuarios:**
    - Talento: tiene los permisos nuevos salvo `ver_sueldos`.
    - Dirección: los tiene todos.
    - Responsable del área: tiene `decidir_contratacion` con `SUS_VACANTES` y no tiene
      `ver_colaboradores`.
  - **Postulaciones:** una vacante suya con una en la etapa Decisión y otra en Prueba, y una
    postulación `CONTRATADO` anterior sembrada en la base, como si viniera de la API.
  - **Una segunda empresa** con un colaborador del mismo DNI.
  - **Excels:** uno válido de 20 filas con jefes cruzados y otro con los errores de AC-16.
- Recorridos que deben quedar como E2E repetibles:
  1. El menú por rol (AC-01, AC-02), plegar y recordar, y el cajón en móvil (AC-03).
  2. El alta manual y el documento duplicado (AC-07, AC-09).
  3. Contratar desde la ficha, dar de alta precargado y la ficha enlazada (AC-11, AC-13).
  4. La carga por Excel con errores, sin guardar nada y con la tabla de errores, y después la
     válida (AC-15, AC-16).
  5. Un cambio con fecha de hoy, uno programado y su anulación, y el historial (AC-18, AC-19).
  6. El cese con aviso de sus reportes, y el reingreso (AC-21, AC-22).
  7. El sueldo oculto para Talento en pantalla y en la API (AC-23).
  8. Los E2E existentes del armazón, en verde (AC-26).
- Riesgos que QA debe explorar con navegador:
  - Fechas en el borde: medianoche de Lima, cese hoy, ingreso futuro, cambio el mismo día del
    ingreso.
  - Que el sueldo se cuele en respuestas, en la plantilla o en los mensajes de error.
  - Círculos de jefes entre la base y el archivo.
  - La carga de 5.000 filas: tiempo y memoria.
  - El paso a 1024 px.
  - Teclado y foco en el menú, el cajón y los modales.
  - Nombres accesibles de los iconos plegados.
  - Que ningún rol pierda una entrada del menú que hoy le funciona.
- Resultado que quiero comprobar personalmente:
  - **Yo reviso el panel visualmente**, porque hay que iniciar sesión: el menú desplegado, plegado
    y en móvil, la lista, la ficha, la carga por Excel y el contratar.
  - Cuando llegue, cargo la plantilla real de la clienta.

## Dependencias y dudas

- Requiere:
  - Nada de otras specs.
  - Es compatible con la de reseñas a contratados, que lee `CONTRATADO` y su fecha sin que esta
    las cambie.
  - Los datos de la clienta (puntos 2 a 6 de «Qué pedir a la empresa») no bloquean, porque se
    prueba con datos sintéticos.
- Decisiones pendientes: ninguna que bloquee.
  - **Quién ve los sueldos:** por defecto solo Dirección, y está por confirmar con la clienta
    (punto 6). Si decide otra cosa, se cambia en la matriz de permisos de Configuración, sin tocar
    código.

### Decisiones tomadas en esta spec

- **Recorte (26 a 28/09/2026, con el usuario):**
  - Primera versión como base de los módulos siguientes.
  - Los datos de planilla entran con Planillas, cargándolos por Excel sobre la misma plantilla.
- **Estructura fija:** sede, área, cargo y jefe. No es configurable como la de Visma.
- **Cargos:** son el catálogo de puestos que ya existe, no otro.
- **Cambios:** se aplican sin aprobación, con historial y auditoría.
- **Contratar:**
  - Es la decisión en verde, con el mismo permiso de hoy, desde cualquier etapa y avisando de las
    que se salta.
  - **Contratar y dar de alta son dos pasos**, porque la decisión y el primer día de trabajo no
    suelen coincidir. Eso deja además el aviso de pendientes para las contrataciones anteriores.
- **Excel:** todo o nada. No hace cambios de puesto en bloque.
- **Borrado:** la ficha no depende de `persona` ni del borrado del portal.
- **Alcance:** solo cuenta `TODO` en esta versión.
- **Menú lateral** agrupado por familias, plegable y con cajón en móvil. Lo aprobó el usuario el
  28/09/2026.
