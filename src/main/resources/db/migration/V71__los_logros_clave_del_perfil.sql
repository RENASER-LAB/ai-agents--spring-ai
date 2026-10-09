-- Los logros clave del perfil del candidato (09/10/2026).
--
-- Hasta tres frases cortas con lo que la persona ha conseguido —«Reduje de 10 a 4 días el
-- cierre contable»—, escritas por ella misma en «Acerca de ti» y pintadas en la cabecera de su
-- perfil. Ver specs/logros-clave-en-la-cabecera-del-perfil.md.
--
-- Una lista ordenada en jsonb y NO un texto con separador, como `habilidades`: un logro puede
-- llevar dentro el « | » que separa las aptitudes, y partirlo después lo convertiría en dos.
--
-- Los perfiles que ya existen arrancan sin logros (NULL). Nadie los rellena: ni la lectura del
-- currículum ni ninguna IA escriben aquí.
--
-- El CHECK cuida la forma —un array de como mucho tres—; el tope de 100 caracteres por logro
-- lo pone el backend al guardar, porque un CHECK no puede recorrer los elementos sin una
-- subconsulta. El CASE no es adorno: `jsonb_array_length` revienta con un escalar y Postgres
-- no garantiza el orden en que evalúa un AND.
ALTER TABLE perfil_candidato
    ADD COLUMN logros jsonb
        CONSTRAINT perfil_candidato_logros_check
        CHECK (logros IS NULL
               OR CASE WHEN jsonb_typeof(logros) = 'array'
                       THEN jsonb_array_length(logros) <= 3
                       ELSE false
                  END);

COMMENT ON COLUMN perfil_candidato.logros IS
    'Los logros clave que escribe el candidato: un array JSON ordenado de 1 a 3 textos de hasta '
    '100 caracteres. NULL si no tiene ninguno. Solo los escribe su dueño; la lectura del CV no '
    'los toca.';
