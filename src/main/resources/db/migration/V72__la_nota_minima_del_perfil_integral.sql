-- ============================================================================
-- La nota mínima del Perfil Integral para pasar sola a la prueba (12/10/2026)
-- ============================================================================
--
-- Ver specs/umbral-del-perfil-integral.md. Dos cosas:
--
-- 1 · **La nota mínima por vacante.** `vacante.nota_minima` existe desde la V5 y nadie la
--     leía ni la escribía. Desde aquí es la nota del Perfil Integral que hace falta para que,
--     con «Calificar y avanzar sola» encendido, la postulación pase sola a la prueba del
--     puesto. Quien no llega no se descarta: se queda en «por confirmar» y decide una persona.
--
--     ⚠️ **Las vacantes que ya avanzan solas empiezan a filtrar con 60 en cuanto se despliega
--     esto.** Es lo acordado (60 por defecto para todas). A quien ya esperaba en «por
--     confirmar» no se le mueve: el pase solo se mira cuando termina o se recalcula una nota.
--
--     Un entero de 0 a 100. La columna sigue siendo numeric(5,2) —la nota con la que se compara
--     lleva dos decimales y la entidad la lee como BigDecimal—, y el CHECK es el que exige que
--     no traiga decimales.
--
-- 2 · **El texto PLATAFORMA 1.1.** La 1.0 (V54) decía que ninguna de las tres cosas que el
--     sistema hace solo depende de una nota; la tercera, el pase a la etapa siguiente, ahora sí
--     depende de una, y nunca para descartar. La 1.1 es la 1.0 con ese párrafo reescrito, y
--     nada más. La 1.0 no se toca: quien la aceptó queda ligado a ella, y a nadie se le pide
--     aceptar otra vez. Las cuentas nuevas firman la 1.1, que es la vigente por ser la
--     publicada más reciente.


-- ============================================================================
-- 1 · La nota mínima: 60 por defecto, entera y de 0 a 100
-- ============================================================================

-- Nadie la escribía, así que lo normal es que todas estén vacías. Por si alguien la tocó a
-- mano, se redondea y se acota antes de atar el CHECK: una migración que revienta en
-- producción por un dato suelto es peor que un valor ajustado.
UPDATE vacante SET nota_minima = 60 WHERE nota_minima IS NULL;
UPDATE vacante SET nota_minima = LEAST(100, GREATEST(0, round(nota_minima)))
 WHERE nota_minima <> trunc(nota_minima) OR nota_minima < 0 OR nota_minima > 100;

ALTER TABLE vacante ALTER COLUMN nota_minima SET DEFAULT 60;
ALTER TABLE vacante ALTER COLUMN nota_minima SET NOT NULL;
ALTER TABLE vacante ADD CONSTRAINT vacante_nota_minima_check
    CHECK (nota_minima BETWEEN 0 AND 100 AND nota_minima = trunc(nota_minima));

COMMENT ON COLUMN vacante.nota_minima IS
    'La nota del Perfil Integral (sobre 100) que hace falta para pasar sola a la prueba del '
    'puesto cuando calificacion_automatica está encendida. Entera, de 0 a 100, 60 por defecto. '
    'Llega quien tiene esa nota o más, comparando con sus dos decimales (59,99 no llega). Quien '
    'no llega se queda en PERFIL_POR_CONFIRMAR: nunca se descarta por nota. Apagado el '
    'interruptor, no bloquea nada.';


-- ============================================================================
-- 2 · El texto PLATAFORMA 1.1
-- ============================================================================
--
-- Publicado con clock_timestamp() y no con now(): el vigente es el publicado más reciente, y
-- así queda por delante de la 1.0 aunque alguien corra todas las migraciones en una sola
-- transacción. Guardado por la versión, como la V54, para que repetirla no duplique nada.

INSERT INTO texto_consentimiento (organizacion_id, tipo, version, texto, hash, publicado_en)
SELECT o.id, 'PLATAFORMA', '1.1', t.texto, encode(digest(t.texto, 'sha256'), 'hex'), clock_timestamp()
FROM organizacion o,
     (VALUES ('QUIÉN TRATA MIS DATOS. RENASER CONSULTING S.A.C., RUC 20615428419, con domicilio en Pj. Manuel Castillo Nro. 205, Urb. Alto Selva Alegre (frente a la Clínica Oftalmosalud), Alto Selva Alegre, Arequipa, Perú. Correo de contacto: renaserlab@gmail.com. Renaser opera este portal de empleo y es responsable de mi cuenta y de mi perfil.

QUÉ DATOS ENTREGO. Al crear la cuenta: mi nombre, mis apellidos, mi correo y la provincia donde vivo. Después, si decido darlos: mi teléfono, mi documento de identidad, mi fecha de nacimiento, mi currículum, mi perfil (titular, resumen, habilidades, experiencia, formación, disponibilidad, enlaces, foto y portada), mis respuestas a las evaluaciones y a las pruebas —que pueden incluir archivos o vídeos—, mi asistencia a las sesiones de simulación y, si la indico, mi pretensión salarial. Algunos de esos datos los lee la inteligencia artificial de mi propio currículum y aparecen marcados como tales en mi perfil, para que yo pueda corregirlos. También quedan registrados la fecha, la dirección IP y el navegador desde los que acepto, porque la ley exige poder probar que este permiso lo di yo.

PARA QUÉ SE USAN. Para crear y mantener mi cuenta; para armar mi perfil a partir de mi currículum; para presentar mi candidatura a las vacantes a las que yo postule; y para escribirme sobre el estado de mis procesos.

UNA INTELIGENCIA ARTIFICIAL PARTICIPA Y UNA PERSONA DECIDE. Mi currículum y mis respuestas se procesan con inteligencia artificial para ordenarlos y puntuarlos. NINGUNA NOTA ME CONTRATA NI ME DESCARTA POR SÍ SOLA: quien decide si sigo en el proceso o no es siempre una persona de la empresa, y puedo pedirle que revise ese resultado si no estoy de acuerdo.

El sistema sí hace tres cosas solo, y solo la tercera depende de una nota, y nunca para descartarme: (1) cierra mi postulación al postular si yo mismo declaro que no cumplo un requisito indispensable de la vacante; (2) cierra mi proceso si se me pasa el plazo que tenía para responder una evaluación o una prueba; y (3) me pasa a la etapa siguiente sin esperar a nadie cuando la empresa configuró su vacante para avanzar sola y mi nota del Perfil Integral llega a la mínima que fijó; si no llega, no me descarta: una persona revisa mi caso. En cualquiera de los tres puedo escribir a Renaser y pedir que una persona lo revise.

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
     WHERE ya.organizacion_id = o.id AND ya.tipo = 'PLATAFORMA' AND ya.version = '1.1');
