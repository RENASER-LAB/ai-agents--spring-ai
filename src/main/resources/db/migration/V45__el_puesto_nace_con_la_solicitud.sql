-- El puesto deja de elegirse tarde, al abrir la vacante: nace con la solicitud.
-- Nullable conserva las solicitudes históricas, que se resolverán al crear su vacante.
ALTER TABLE solicitud_talento
    ADD COLUMN puesto_id bigint REFERENCES puesto(id);

CREATE INDEX solicitud_talento_puesto_idx ON solicitud_talento (puesto_id);
