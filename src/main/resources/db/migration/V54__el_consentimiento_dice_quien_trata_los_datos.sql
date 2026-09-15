-- ============================================================================
-- El consentimiento dice quién trata los datos, y deja de pedirse dos veces
-- ============================================================================
--
-- Dos cosas, y la segunda es consecuencia de la primera.
--
-- 1 · **El candidato firmaba el MISMO texto dos veces.** Al crear la cuenta aceptaba el
--     texto PROCESO de la plataforma —que dice «evaluar mi postulación a esta vacante»
--     cuando todavía no hay ninguna vacante— y al postular a una vacante de Renaser
--     volvía a firmar ese mismo texto, ahora sí con su postulación. Dos filas en
--     `consentimiento` del mismo texto para la misma persona, y una de ellas mintiendo.
--
-- 2 · **Ningún texto nombraba a quien trata los datos de verdad.** La evaluación la hace
--     DeepSeek y la búsqueda por significado, Google; los archivos viven en Supabase y
--     los servidores en Amazon Web Services. Las cuatro están fuera del Perú, así que
--     esto es flujo transfronterizo del artículo 15 de la Ley 29733 y hay que decirlo
--     ANTES, no después. Mientras la IA no calificara a nadie no tenía efecto; desde la
--     V53 la vacante se califica sola, y el currículum de una persona real sale del país.
--
-- La separación: un tipo nuevo, PLATAFORMA, para lo que se acepta con Renaser al crear
-- la cuenta (la cuenta, el perfil, la IA, los proveedores, el plazo, los derechos), y
-- PROCESO se queda para lo único que es de cada empresa: que ELLA decide sobre esta
-- postulación. El contenido pesado vive en un solo sitio y no se repite por empresa.
--
-- Y PROCESO es **un solo texto para todas**, con un hueco donde va el nombre de la
-- empresa. Antes había una fila por empresa y lo único que cambiaba entre ellas eran esas
-- tres palabras: corregir una coma obligaba a republicar empresa por empresa, y una
-- empresa nueva no podía recibir candidatos hasta publicar la suya. Ahora no publica
-- nadie más que la plataforma, y a una empresa le está prohibido.
--
-- Nada de la v1.0 se toca: quien la aceptó queda ligado a ella, que es para lo que existe
-- el versionado. Ver docs/BORRADOR-CONSENTIMIENTO-v1.1.md.


-- ============================================================================
-- 1 · El catálogo de tipos admite PLATAFORMA
-- ============================================================================
--
-- Soltar el CHECK y volverlo a atar, igual que la V38 con trabajo_ia_estado_check.

ALTER TABLE texto_consentimiento DROP CONSTRAINT IF EXISTS texto_consentimiento_tipo_check;
ALTER TABLE texto_consentimiento ADD CONSTRAINT texto_consentimiento_tipo_check
    CHECK (tipo IN ('PLATAFORMA', 'PROCESO', 'FUTUROS_CONTACTOS'));

COMMENT ON COLUMN texto_consentimiento.tipo IS
    'PLATAFORMA = lo que se acepta con Renaser al crear la cuenta (solo lo tiene la '
    'organización plataforma); PROCESO = lo que se acepta con LA EMPRESA de la vacante al '
    'postular, uno por empresa; FUTUROS_CONTACTOS = el permiso opcional para avisar de '
    'otras vacantes, también de la plataforma.';


-- ============================================================================
-- 1b · La plataforma se llama como dice su ficha RUC
-- ============================================================================
--
-- La V9 la sembró como «Clínica Renaser S.A.C.» y la razón social es RENASER CONSULTING
-- S.A.C. (RUC 20615428419). No es cosmético: `organizacion.nombre` es lo que el portal
-- pinta como «quien trata tus datos» al postular y lo que la V54 mete en el texto PROCESO
-- de cada empresa. Con el nombre viejo, el candidato firmaría con «Clínica Renaser» un
-- texto en el que Renaser Consulting dice ser la responsable: la misma empresa aparecida
-- como dos, una con RUC y otra sin él.
--
-- Guardado por el nombre viejo: en producción ya se renombró a mano, y ahí esto no hace
-- nada. Lo que arregla son las bases nuevas —desarrollo, pruebas, la próxima instalación—,
-- que nacen de la semilla.

UPDATE organizacion SET nombre = 'RENASER CONSULTING S.A.C.'
 WHERE es_plataforma AND nombre = 'Clínica Renaser S.A.C.';


-- ============================================================================
-- 2 · El texto de la plataforma (nuevo): lo que se acepta al crear la cuenta
-- ============================================================================
--
-- Cada afirmación de aquí se comprobó contra el código, que es lo único que evita el
-- fallo peor de un documento así: prometer un tratamiento que no ocurre.
--
--   · Los 24 meses son el valor por defecto del parámetro `meses_conservar_perfil`
--     (V36). Si alguien lo cambia, este texto deja de ser verdad: lo vigila
--     BarridoRetencionPerfilTest#elPlazoDelTextoLegalEsElQueAplicaElBarrido.
--   · «Se elimina» y no «se anonimiza»: ServicioCicloVidaPerfil#borrarPorPersona borra
--     de verdad, porque el perfil no sostiene ninguna nota.
--   · La foto, la edad, el sexo y el estado civil se quitan antes de mandar el
--     currículum al modelo (PuenteCalificacionIaImpl, RF-41).
--
-- Solo para la organización plataforma: el resto de empresas no tiene cuentas, tiene
-- vacantes.

INSERT INTO texto_consentimiento (organizacion_id, tipo, version, texto, hash, publicado_en)
SELECT o.id, 'PLATAFORMA', '1.0', t.texto, encode(digest(t.texto, 'sha256'), 'hex'), now()
FROM organizacion o,
     (VALUES ('QUIÉN TRATA MIS DATOS. RENASER CONSULTING S.A.C., RUC 20615428419, con domicilio en Pj. Manuel Castillo Nro. 205, Urb. Alto Selva Alegre (frente a la Clínica Oftalmosalud), Alto Selva Alegre, Arequipa, Perú. Correo de contacto: renaserlab@gmail.com. Renaser opera este portal de empleo y es responsable de mi cuenta y de mi perfil.

QUÉ DATOS ENTREGO. Al crear la cuenta: mi nombre, mis apellidos, mi correo y la provincia donde vivo. Después, si decido darlos: mi teléfono, mi documento de identidad, mi fecha de nacimiento, mi currículum, mi perfil (titular, resumen, habilidades, experiencia, formación, disponibilidad, enlaces, foto y portada), mis respuestas a las evaluaciones y a las pruebas —que pueden incluir archivos o vídeos—, mi asistencia a las sesiones de simulación y, si la indico, mi pretensión salarial. Algunos de esos datos los lee la inteligencia artificial de mi propio currículum y aparecen marcados como tales en mi perfil, para que yo pueda corregirlos. También quedan registrados la fecha, la dirección IP y el navegador desde los que acepto, porque la ley exige poder probar que este permiso lo di yo.

PARA QUÉ SE USAN. Para crear y mantener mi cuenta; para armar mi perfil a partir de mi currículum; para presentar mi candidatura a las vacantes a las que yo postule; y para escribirme sobre el estado de mis procesos.

UNA INTELIGENCIA ARTIFICIAL PARTICIPA Y UNA PERSONA DECIDE. Mi currículum y mis respuestas se procesan con inteligencia artificial para ordenarlos y puntuarlos. NINGUNA NOTA ME CONTRATA NI ME DESCARTA POR SÍ SOLA: quien decide si sigo en el proceso o no es siempre una persona de la empresa, y puedo pedirle que revise ese resultado si no estoy de acuerdo.

El sistema sí hace tres cosas solo, y ninguna de las tres depende de una nota: (1) cierra mi postulación al postular si yo mismo declaro que no cumplo un requisito indispensable de la vacante; (2) cierra mi proceso si se me pasa el plazo que tenía para responder una evaluación o una prueba; y (3) me pasa a la etapa siguiente sin esperar a nadie, cuando la empresa configuró su vacante para avanzar sola. En cualquiera de los tres puedo escribir a Renaser y pedir que una persona lo revise.

Antes de enviar mi currículum a la inteligencia artificial, Renaser le quita la foto, la edad, el sexo y el estado civil: lo que sale es esa versión recortada, nunca el archivo completo que subí.

QUIÉN MÁS VE MIS DATOS. Las empresas a cuyas vacantes yo postule, cada una responsable de su propio proceso y con un permiso aparte que firmo al postular. Y los proveedores que prestan el servicio por encargo de Renaser, solo para lo anterior: DeepSeek (la evaluación con inteligencia artificial), Google (la búsqueda por significado dentro del sistema), Supabase (la base de datos y los archivos), Amazon Web Services (los servidores), Vercel (las páginas del portal donde escribo mis datos) y el proveedor de correo con el que se me escribe. Renaser no vende mis datos ni los usa para publicidad.

MIS DATOS SALEN DEL PERÚ. Todos los proveedores nombrados arriba están fuera del país, el de correo incluido, así que al aceptar consiento también el flujo transfronterizo de mis datos personales que regula el artículo 15 de la Ley 29733. Renaser sigue respondiendo por ellos ante mí.

CUÁNTO SE CONSERVAN. Mientras tenga cuenta. Si paso 24 meses sin actividad —sin postular y sin tocar mi perfil—, mi perfil se elimina con todo lo que cuelga de él: ese es el plazo que Renaser tiene puesto hoy, y si alguna empresa del portal necesitara uno más largo para sus procesos, se aplica el más largo de los dos. Lo que sostiene una decisión ya tomada —mi postulación y lo que respondí en ella— se conserva mientras tenga cuenta. Puedo pedir el borrado de todo antes, cuando quiera y sin explicar por qué.

MIS DERECHOS. Puedo acceder a mis datos y corregirlos desde mi perfil, donde veo y edito todo lo que hay sobre mí. Oponerme al tratamiento y pedir que se cancelen se hace desde «Privacidad y control» en mi cuenta —ahí retiro una postulación, salgo de los avisos de futuras vacantes y pido el borrado— o escribiendo a renaserlab@gmail.com. La solicitud de borrado queda registrada y la ejecuta Renaser dentro de los plazos de la ley; no es instantánea. Si no me atienden, puedo reclamar ante la Autoridad Nacional de Protección de Datos Personales del Ministerio de Justicia y Derechos Humanos.

ESTO ES VOLUNTARIO Y PUEDO RETIRARLO. Sin este permiso no se puede crear la cuenta, porque no habría forma de evaluar ninguna postulación. Puedo retirarlo cuando quiera pidiendo el borrado de mis datos; retirarlo no deshace lo que ya se hizo mientras estuvo vigente.')
     ) AS t(texto)
WHERE o.es_plataforma
  AND NOT EXISTS (
    SELECT 1 FROM texto_consentimiento ya
     WHERE ya.organizacion_id = o.id AND ya.tipo = 'PLATAFORMA');


-- ============================================================================
-- 3 · El texto que se firma al postular: UNO solo, con hueco para el nombre
-- ============================================================================
--
-- Una sola fila, de la plataforma, con «{EMPRESA}» donde va el nombre de quien publica
-- la vacante. No hay texto por empresa: lo único que cambiaba entre una y otra eran esas
-- tres palabras, y tener una fila por empresa significaba que corregir una coma obligaba
-- a republicar empresa por empresa.
--
-- Solo dice lo que es de ESA empresa: que ella decide, que el permiso es por vacante y
-- que no alcanza a las demás del portal. Lo técnico —la IA, los proveedores, la salida
-- del país— vive en el texto PLATAFORMA y no se repite aquí: repetirlo por empresa es
-- exactamente lo que hacía que el candidato firmara dos veces lo mismo.
--
-- ⚠️ **El hueco obliga a guardar lo firmado.** Esta tabla existe para poder demostrar qué
-- texto exacto leyó cada persona, y un texto con un hueco ya no es el que se leyó. Por eso
-- la sección 5 añade `consentimiento.texto_firmado`: al firmar se guarda el texto ya con
-- el nombre puesto, tal como se le pintó en la pantalla.
--
-- ⚠️ **A Renaser se la nombra «Renaser» y no por su razón social.** Si una empresa se
-- llamara igual que el hueco sustituido, la frase de quién presta la plataforma diría el
-- nombre equivocado. Quién es Renaser con RUC y domicilio está en el texto PLATAFORMA,
-- que el candidato ya aceptó y que esta misma frase le manda a releer.
--
-- Y no hay excepción: a una empresa le está prohibido publicar textos legales, de
-- cualquiera de los tres tipos. Un texto por empresa es un texto que nadie revisa y que
-- se queda viejo sin que nadie se entere; este lo mantiene quien tiene el abogado.

INSERT INTO texto_consentimiento (organizacion_id, tipo, version, texto, hash, publicado_en)
SELECT o.id, 'PROCESO', '1.1', t.texto, encode(digest(t.texto, 'sha256'), 'hex'), now()
FROM organizacion o,
     (VALUES ('Acepto que {EMPRESA} trate mi currículum, mis respuestas y los resultados de mi evaluación para decidir sobre mi postulación a esta vacante.

{EMPRESA} es quien publica esta vacante y quien decide sobre mi candidatura. Renaser, que opera el portal, le presta la plataforma y realiza por su encargo el tratamiento técnico —incluida la evaluación con inteligencia artificial y el envío a los proveedores de fuera del país—, en los términos de la política de privacidad que acepté al crear mi cuenta y que puedo volver a leer cuando quiera.

Este permiso vale solo para esta vacante: no alcanza a las demás empresas del portal, que me pedirán el suyo si postulo a las suyas. Puedo retirar mi postulación en cualquier momento, y pedir el borrado de mis datos desde mi cuenta.')
     ) AS t(texto)
WHERE o.es_plataforma
  AND NOT EXISTS (
    SELECT 1 FROM texto_consentimiento ya
     WHERE ya.organizacion_id = o.id AND ya.tipo = 'PROCESO' AND ya.version = '1.1');


-- ============================================================================
-- 4 · El permiso opcional (v1.1): ahora nombra el perfil que se conserva
-- ============================================================================
--
-- El RF-169: el perfil es un dato permanente y el texto de la v1.0 solo hablaba de
-- «mis datos». Se dice qué se guarda y por cuánto, y se repite que retirarlo no toca
-- ningún proceso en curso —que es la duda por la que la gente no lo marca.

INSERT INTO texto_consentimiento (organizacion_id, tipo, version, texto, hash, publicado_en)
SELECT o.id, 'FUTUROS_CONTACTOS', '1.1', t.texto,
       encode(digest(t.texto, 'sha256'), 'hex'), now()
FROM organizacion o,
     (VALUES ('Acepto que RENASER CONSULTING S.A.C. conserve mi perfil y mi currículum para avisarme de otras vacantes que encajen conmigo, aunque no tenga ninguna postulación abierta.

Es un permiso aparte del de cualquier postulación: puedo retirarlo cuando quiera desde «Privacidad y control» en mi cuenta, sin que eso afecte a ningún proceso en el que esté participando ni a las decisiones ya tomadas.

Si lo retiro, mis datos dejan de usarse para avisarme de vacantes nuevas; lo que se conserve por mis postulaciones se rige por el permiso que firmé con cada empresa.')
     ) AS t(texto)
WHERE o.es_plataforma
  AND NOT EXISTS (
    SELECT 1 FROM texto_consentimiento ya
     WHERE ya.organizacion_id = o.id AND ya.tipo = 'FUTUROS_CONTACTOS' AND ya.version = '1.1');


-- ============================================================================
-- 4b · Se retiran los textos provisionales que el alta repartió
-- ============================================================================
--
-- Cada empresa dada de alta se llevó una copia del texto de Renaser, publicada o en
-- borrador, que ni siquiera la nombraba a ella: el alta copiaba el texto LITERAL, sin
-- sustituir nada.
--
-- Desde esta migración nadie las lee —el de PROCESO se busca siempre en la plataforma y
-- se compone con el nombre de la empresa—, así que esto es higiene y no un arreglo: son
-- filas marcadas como publicadas que no rigen nada, y el panel de textos legales las
-- listaría como vigentes. Dejarlas así es sembrar la duda de cuál manda.
--
-- Retirar no es reescribir: la fila se queda entera y los consentimientos ya firmados
-- siguen apuntando a ella con su texto y su huella intactos. Lo único que cambia es que
-- deja de estar publicada, que es una propiedad del presente y no del pasado.
--
-- Se reconoce por dos señales: la marca «[TEXTO PROVISIONAL» y el arranque del texto de
-- la V9, porque basta con que alguien borrara el corchete al copiar y pegar.

UPDATE texto_consentimiento
   SET publicado_en = NULL
 WHERE tipo IN ('PROCESO', 'FUTUROS_CONTACTOS')
   AND publicado_en IS NOT NULL
   AND (texto LIKE '%[TEXTO PROVISIONAL%'
        OR texto LIKE 'Acepto que mis datos personales y mis respuestas se usen para evaluar mi postulación a esta vacante.%'
        OR texto LIKE 'Acepto que Renaser conserve mis datos para contactarme por futuras oportunidades laborales.%');


-- ============================================================================
-- 5 · Lo que se firmó, guardado tal como se leyó
-- ============================================================================
--
-- Hasta ahora `texto_consentimiento.texto` era literalmente lo que la persona vio, así
-- que bastaba con apuntar a la fila. Con el hueco de «{EMPRESA}» deja de serlo: dos
-- candidatos de dos empresas distintas firman la misma fila y leyeron cosas distintas.
--
-- Se guarda el texto entero y no solo el nombre de la empresa porque el nombre puede
-- cambiar después —`organizacion.nombre` ya cambió una vez, en esta misma migración— y
-- entonces lo leído ya no se podría reconstruir. Son unos cientos de bytes por firma.
--
-- Vacío significa «lo firmado es el texto de su fila», que es el caso de todo lo anterior
-- a esta migración: esas filas apuntan a textos literales, sin hueco que rellenar.

ALTER TABLE consentimiento ADD COLUMN IF NOT EXISTS texto_firmado text;

COMMENT ON COLUMN consentimiento.texto_firmado IS
    'El texto tal como se le pintó a la persona, con el nombre de la empresa ya puesto. '
    'Vacío = lo firmado es el texto literal de texto_consentimiento, sin sustituciones.';
