-- La fecha real del último cambio de lo que cuenta la consulta v2 de WhatsApp (19/10/2026,
-- hallazgo QA-C0-F1). Ver specs/Ready/contexto-y-resultados-por-whatsapp.md (regla 3, fila
-- «Vigencia») y el contrato v2, C-3: `actualizadoEn` es la fecha real del último cambio y
-- `revisionContexto` un entero que solo crece.
--
-- Hasta la V76 EX solo sabía fechar el estado, la convocatoria, el sueldo, el inicio y la
-- entrega de la prueba y el resultado. Corregir el horario, los requisitos o la fecha límite
-- de la prueba, o ampliar un plazo, cambiaba lo que el bot lee sin mover ni la fecha ni la
-- revisión: el bot no podía saber que el contexto había cambiado.
--
-- Todo es aditivo: tres columnas nuevas y unos triggers que solo las fechan.
--
--   vacante.contexto_actualizado_en          lo publicado de la vacante y cómo es su proceso
--   intento_prueba.contexto_actualizado_en   los plazos y el punto en que está la prueba
--   evaluacion.contexto_actualizado_en       los plazos y el estado de la evaluación
--
-- Las filas que ya existen quedan vacías: no se inventa una fecha. La consulta toma la más
-- reciente de todas sus fuentes, así que una vacía no cuenta.
--
-- ⚠️ Las fechan triggers y no la aplicación, a propósito: la vacante se configura a trozos
-- desde muchos sitios (la edición, el sueldo, la fecha límite, el instrumento, los minutos,
-- la prueba propia…) y los plazos de un intento se mueven desde otros tantos. Un sitio
-- olvidado repetiría el fallo. Solo se fechan cuando cambia DE VERDAD una columna que el
-- contexto cuenta: guardar lo mismo no mueve nada. La entidad JPA las lee y nunca las escribe.
-- Consultar no escribe nada.


-- ============================================================================
-- 1 · La función común: «esto cambió ahora»
-- ============================================================================

CREATE FUNCTION marcar_contexto_actualizado() RETURNS trigger AS $$
BEGIN
    NEW.contexto_actualizado_en := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ============================================================================
-- 2 · La vacante
-- ============================================================================
--
-- Las columnas que viajan en el contexto: la ficha pública (título, textos, lugar, horario,
-- sueldo), la convocatoria y lo que decide el procedimiento y los plazos de la etapa técnica.
-- Si mañana la consulta cuenta otra columna de la vacante, se añade aquí también.

ALTER TABLE vacante ADD COLUMN contexto_actualizado_en timestamptz;

COMMENT ON COLUMN vacante.contexto_actualizado_en IS
    'Cuándo cambió por última vez lo que la vacante cuenta a quien postula (ficha pública, '
    'requisitos indispensables, empresa, convocatoria, procedimiento y plazos de la prueba). '
    'La fechan triggers (V77); vacía si no cambió desde entonces. Es una de las fuentes de '
    'actualizadoEn y revisionContexto de la consulta v2 de WhatsApp.';

CREATE TRIGGER vacante_contexto_actualizado
    BEFORE UPDATE ON vacante
    FOR EACH ROW
    WHEN ((OLD.titulo, OLD.descripcion, OLD.proposito, OLD.responsabilidades, OLD.requisitos,
           OLD.modalidad, OLD.horario, OLD.ubicacion, OLD.ciudad_ubigeo,
           OLD.remuneracion_tipo, OLD.remuneracion_min, OLD.remuneracion_max, OLD.remuneracion_moneda,
           OLD.estado, OLD.publicada_en, OLD.cerrada_en,
           OLD.aplica_evaluacion, OLD.instrumento_etapa_tecnica, OLD.minutos_etapa_tecnica,
           OLD.version_plantilla_prueba_id, OLD.prueba_cierra_en)
          IS DISTINCT FROM
          (NEW.titulo, NEW.descripcion, NEW.proposito, NEW.responsabilidades, NEW.requisitos,
           NEW.modalidad, NEW.horario, NEW.ubicacion, NEW.ciudad_ubigeo,
           NEW.remuneracion_tipo, NEW.remuneracion_min, NEW.remuneracion_max, NEW.remuneracion_moneda,
           NEW.estado, NEW.publicada_en, NEW.cerrada_en,
           NEW.aplica_evaluacion, NEW.instrumento_etapa_tecnica, NEW.minutos_etapa_tecnica,
           NEW.version_plantilla_prueba_id, NEW.prueba_cierra_en))
    EXECUTE FUNCTION marcar_contexto_actualizado();


-- ============================================================================
-- 3 · Lo que vive fuera de la fila de la vacante y también se cuenta
-- ============================================================================
--
-- Tocan la fecha de su vacante: los requisitos indispensables (salen dentro de «requisitos»),
-- la prueba propia publicada (su modalidad y su duración) y el nombre de la empresa.

CREATE FUNCTION requisito_objetivo_toca_su_vacante() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.es_activo THEN
            UPDATE vacante SET contexto_actualizado_en = now() WHERE id = NEW.vacante_id;
        END IF;
    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.es_activo THEN
            UPDATE vacante SET contexto_actualizado_en = now() WHERE id = OLD.vacante_id;
        END IF;
    ELSIF (OLD.es_activo OR NEW.es_activo)
          AND (OLD.vacante_id, OLD.descripcion, OLD.es_activo)
              IS DISTINCT FROM (NEW.vacante_id, NEW.descripcion, NEW.es_activo) THEN
        UPDATE vacante SET contexto_actualizado_en = now()
         WHERE id IN (OLD.vacante_id, NEW.vacante_id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER requisito_objetivo_toca_su_vacante
    AFTER INSERT OR UPDATE OR DELETE ON requisito_objetivo
    FOR EACH ROW EXECUTE FUNCTION requisito_objetivo_toca_su_vacante();

-- Solo la prueba propia de una vacante (V67): un borrador no se ve, la PUBLICADA sí.
CREATE FUNCTION prueba_propia_toca_su_vacante() RETURNS trigger AS $$
BEGIN
    IF NEW.vacante_id IS NULL OR NEW.proposito IS DISTINCT FROM 'PRUEBA_PUESTO' THEN
        RETURN NULL;
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.estado = 'PUBLICADA' THEN
            UPDATE vacante SET contexto_actualizado_en = now() WHERE id = NEW.vacante_id;
        END IF;
    ELSIF (OLD.estado = 'PUBLICADA' OR NEW.estado = 'PUBLICADA')
          AND (OLD.estado, OLD.modalidad, OLD.duracion_minutos, OLD.plazo_dias)
              IS DISTINCT FROM (NEW.estado, NEW.modalidad, NEW.duracion_minutos, NEW.plazo_dias) THEN
        UPDATE vacante SET contexto_actualizado_en = now() WHERE id = NEW.vacante_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prueba_propia_toca_su_vacante
    AFTER INSERT OR UPDATE ON version_banco
    FOR EACH ROW EXECUTE FUNCTION prueba_propia_toca_su_vacante();

-- El nombre de la empresa sale como `empresa` y dentro del `distintivo`. Hoy EX no lo cambia
-- desde ninguna pantalla; si soporte lo corrige en la base, el contexto también lo fecha.
CREATE FUNCTION organizacion_toca_sus_vacantes() RETURNS trigger AS $$
BEGIN
    UPDATE vacante SET contexto_actualizado_en = now() WHERE organizacion_id = NEW.id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER organizacion_toca_sus_vacantes
    AFTER UPDATE OF nombre ON organizacion
    FOR EACH ROW
    WHEN (OLD.nombre IS DISTINCT FROM NEW.nombre)
    EXECUTE FUNCTION organizacion_toca_sus_vacantes();


-- ============================================================================
-- 4 · El intento de la prueba y la evaluación
-- ============================================================================
--
-- Los plazos de una persona: empezar, ampliar o conceder un plazo propio, entregar, vencer
-- sin completar. Una fila nueva nace con su fecha (DEFAULT); las de antes quedan vacías.

ALTER TABLE intento_prueba ADD COLUMN contexto_actualizado_en timestamptz;
ALTER TABLE intento_prueba ALTER COLUMN contexto_actualizado_en SET DEFAULT now();

COMMENT ON COLUMN intento_prueba.contexto_actualizado_en IS
    'Cuándo cambió por última vez lo que el intento cuenta a quien postula (inicio, plazo, '
    'entrega, cierre sin completar, versión). La fechan un DEFAULT y un trigger (V77).';

CREATE TRIGGER intento_prueba_contexto_actualizado
    BEFORE UPDATE ON intento_prueba
    FOR EACH ROW
    WHEN ((OLD.iniciado_en, OLD.vence_en, OLD.plazo_propio, OLD.entregado_en, OLD.no_completada,
           OLD.version_plantilla_prueba_id, OLD.version_banco_id)
          IS DISTINCT FROM
          (NEW.iniciado_en, NEW.vence_en, NEW.plazo_propio, NEW.entregado_en, NEW.no_completada,
           NEW.version_plantilla_prueba_id, NEW.version_banco_id))
    EXECUTE FUNCTION marcar_contexto_actualizado();

ALTER TABLE evaluacion ADD COLUMN contexto_actualizado_en timestamptz;
ALTER TABLE evaluacion ALTER COLUMN contexto_actualizado_en SET DEFAULT now();

COMMENT ON COLUMN evaluacion.contexto_actualizado_en IS
    'Cuándo cambió por última vez lo que la evaluación cuenta a quien postula (estado, '
    'vencimiento, inicio, término). La fechan un DEFAULT y un trigger (V77).';

CREATE TRIGGER evaluacion_contexto_actualizado
    BEFORE UPDATE ON evaluacion
    FOR EACH ROW
    WHEN ((OLD.estado, OLD.vence_en, OLD.iniciada_en, OLD.terminada_en)
          IS DISTINCT FROM
          (NEW.estado, NEW.vence_en, NEW.iniciada_en, NEW.terminada_en))
    EXECUTE FUNCTION marcar_contexto_actualizado();
