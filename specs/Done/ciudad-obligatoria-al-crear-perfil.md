# Ciudad obligatoria al crear el perfil

Estado: TERMINADO

Estado previo (referencia histórica): borrador

## Objetivo
Garantizar que todo candidato indique su ciudad al crear su perfil, para que el equipo de reclutamiento disponga de ese dato desde el registro inicial.

## Contexto
- Comportamiento actual: el backend ya exige `ciudadUbigeo` al crear la cuenta y valida que pertenezca al catálogo de ciudades habilitadas. Falta comprobar que el formulario comunique y aplique esta obligatoriedad. En esta spec, «crear el perfil» se entiende como el registro inicial del candidato.
- Pantallas o procesos afectados: formulario de registro del candidato y validación del envío de ese formulario.
- Referencias existentes: [plantilla de spec](../plantilla-spec.md), [datos de registro](../../src/main/java/com/renaser/ai/ai_engine/portal/dto/DtosPortal.java), [servicio de creación de cuenta](../../src/main/java/com/renaser/ai/ai_engine/portal/service/impl/ServicioCuentaPortalImpl.java) y [catálogo de ciudades](../../src/main/java/com/renaser/ai/ai_engine/perfil/service/CatalogosDelPerfil.java).

## Alcance
- Incluye: identificar Ciudad como campo obligatorio, impedir completar el registro sin una selección válida y mostrar un error junto al campo. Mantener la validación del backend y el guardado de la ciudad seleccionada.
- Queda fuera: exigir ciudad retroactivamente a candidatos existentes, modificar la edición del perfil, cambiar el catálogo o añadir otros campos obligatorios.

## Comportamiento esperado
1. El usuario abre el formulario de registro y ve que Ciudad es obligatoria, sin una ciudad seleccionada automáticamente.
2. El sistema permite completar el registro únicamente cuando se selecciona una ciudad válida y se cumplen las demás validaciones existentes.
3. Si el usuario intenta enviar el formulario sin ciudad, el sistema impide el registro y muestra junto al campo «Selecciona tu ciudad». Conserva los demás datos ingresados para que pueda corregirlo.
4. Al seleccionar una opción válida y volver a enviar, el sistema guarda la ciudad y continúa el flujo habitual de registro.
5. Si se intenta omitir o manipular la ciudad enviando una solicitud directa, el backend rechaza la solicitud sin crear la cuenta.

## Criterios de aceptación
- AC-01: Dado el formulario de registro, cuando el candidato lo abre, entonces Ciudad aparece identificada como obligatoria y sin una opción válida preseleccionada.
- AC-02: Dados los demás campos válidos y Ciudad sin seleccionar, cuando el candidato intenta completar el registro, entonces no se crea la cuenta, aparece «Selecciona tu ciudad» junto al campo y se conservan los demás datos ingresados.
- AC-03: Dado el error por falta de ciudad, cuando el candidato selecciona una opción válida y vuelve a enviar el formulario con los demás campos válidos, entonces el registro continúa y se guarda la ciudad elegida.
- AC-04: Dada una solicitud directa de registro, cuando `ciudadUbigeo` está ausente, es nulo, está vacío, contiene solo espacios o no es una opción habilitada, entonces el backend responde con un error de validación HTTP 400 y no crea la cuenta.
- AC-05: Dado un candidato existente sin ciudad, cuando inicia sesión o consulta su perfil, entonces este cambio no le exige completar el dato ni bloquea su acceso.

## Casos límite y errores
- Datos vacíos o inválidos: el texto de ayuda del selector no cuenta como selección. Se rechazan códigos inexistentes, inactivos y departamentos que no sean opciones elegibles del catálogo.
- Usuario sin permisos: el registro conserva su acceso público y las reglas de acceso actuales; seleccionar ciudad no requiere iniciar sesión.
- Recarga, reintento o doble envío: cada nuevo intento valida la ciudad. Se conserva el manejo actual de recargas y envíos duplicados.
- Otros casos relevantes: «Fuera del Perú» (`EXT`) cuenta como selección válida según el catálogo actual. Si el catálogo no carga, se informa el fallo y se permite reintentar, sin aceptar un registro sin ciudad.

## Datos y compatibilidad
- Información que debe guardarse: el código seleccionado en el campo existente `persona.ciudad_ubigeo`.
- Comportamiento para registros existentes: conservar sus datos, incluidos los valores nulos; no completar ciudades automáticamente ni solicitar su actualización por este cambio.
- Restricciones de compatibilidad: reutilizar `ciudadUbigeo` y el catálogo existentes. No se requieren nuevos campos, migraciones ni una restricción global de base de datos que afecte a registros antiguos.
- Si no aplica, indicarlo: no aplica crear estructuras de datos adicionales.

## Verificación
- Actor/rol y datos de prueba necesarios: una persona sin cuenta, un correo disponible, los demás campos de registro válidos, una ciudad del catálogo, la opción «Fuera del Perú» y un candidato existente sin ciudad.
- Recorridos que deben quedar como E2E repetibles: intentar registrarse sin ciudad; corregir seleccionando una ciudad y completar el registro; registrarse con «Fuera del Perú»; acceder con una cuenta existente sin ciudad. Verificar además por API el rechazo de valores omitidos o inválidos.
- Riesgos que QA debe explorar con navegador: envío mediante botón y tecla Enter, error visible y asociado al selector, uso con teclado y móvil, conservación de datos tras el error y fallo de carga del catálogo.
- Resultado que quiero comprobar personalmente: no puedo completar el registro sin ciudad; al seleccionarla, puedo continuar y el dato queda guardado correctamente.

## Dependencias y dudas
- Requiere: localizar el frontend del formulario de registro y disponer del catálogo actual para verificar el flujo completo. La validación del backend ya está presente en este repositorio.
- Decisiones pendientes: ninguna para el alcance definido de registro inicial del candidato.
