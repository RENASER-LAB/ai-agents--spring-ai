-- Adopta la tabla del módulo de agentes IA, que antes creaba Hibernate con ddl-auto: update.
-- Desde aquí el esquema entero es de Flyway y Hibernate solo valida.
--
-- La tabla vector_store NO va aquí: la crea Spring AI al arrancar (initialize-schema: true),
-- con la extensión pgvector. Su DDL depende de la versión interna de Spring AI y copiarlo
-- acoplaría esta migración a esa versión.

CREATE TABLE agent_run (
    id                       uuid PRIMARY KEY,
    agent_type               varchar(255),
    flow_id                  uuid,
    parent_run_id            uuid,
    depth                    integer NOT NULL,
    entity_id                varchar(255),
    -- Texto libre del usuario: con el varchar(255) por defecto reventaba la inserción
    objective                varchar(4000),
    output_json              jsonb,
    severity                 varchar(255),
    requires_human_approval  boolean NOT NULL,
    approved                 boolean NOT NULL,
    created_at               timestamptz(6),
    finished_at              timestamptz(6),
    error_message            varchar(2000),
    version                  varchar(255)
);
