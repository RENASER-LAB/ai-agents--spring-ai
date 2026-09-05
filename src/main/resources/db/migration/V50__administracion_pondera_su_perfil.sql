-- Administrador y Asistente Administrativo pasan a ponderar su perfil integral.
--
-- Las dos vacantes comparten una versión de pesos que daba **100 a la prueba del puesto y nada
-- al perfil integral**. Con ese reparto la nota de un candidato ES su nota de la prueba: la
-- prueba RENASER que sí rindieron —y que va de 31 a 90 puntos entre ellos— no contaba nada.
--
-- Las dos rinden el cazatalentos, así que les toca el reparto que el cliente escribió para ese
-- instrumento: 45 el perfil integral (su prueba RENASER) y 55 la prueba del puesto (la técnica).
-- Está en `CAZATALENTOS-sistema-de-filtro.md` y en la hoja «Cálculo» de los libros de DIR, SUP y
-- OPE: «Índice combinado = RENASER × 0.45 + técnica × 0.55», con su motivo dicho — «la técnica
-- pesa más porque mide si sabe hacer el trabajo».
--
-- ============================================================================
-- Cómo se identifica la versión, y por qué no por su id
-- ============================================================================
-- En la base de producción esa versión es la 5. **Aquí no se escribe ese 5**, y no es manía: los
-- ids de `version_pesos` son de identidad y dependen del orden en que cada base los creó. En una
-- base recién migrada el 5 es «CAZATALENTOS · MICRO», así que una migración clavada al número le
-- cambiaría los pesos a una versión que no tiene nada que ver, en silencio y sin fallar.
--
-- Se identifica por lo que la define: la versión que usan las vacantes de administración y cuyo
-- único peso de etapa es la prueba del puesto al 100. Donde no exista tal cosa —cualquier otra
-- base, cualquier otro entorno— esto no toca nada.
--
-- ============================================================================
-- Por qué se edita esa versión en vez de crear otra
-- ============================================================================
-- La regla del proyecto es que una versión publicada no se toca: editar crea otra
-- (docs/DECISION-UNA-VACANTE-UNA-VERSION.md). Aquí se hace al revés, por dos razones:
--
--   1. **Crear una versión nueva rompe algo más grande.** El sistema elige «la última publicada»
--      como la que hereda toda vacante nueva y la que el copiador replica a cada empresa que se
--      da de alta. Publicar cualquier versión hoy convertiría este 45/55 en el reparto por
--      defecto de todo el mundo — el mismo motivo por el que la V49 dejó las dos versiones del
--      cazatalentos en BORRADOR.
--
--   2. **La inmutabilidad protege el pasado, y aquí no hay pasado que proteger.** Existe para
--      poder reconstruir una decisión vieja. En estas dos vacantes **nadie ha sido contratado ni
--      descartado**: no hay ninguna decisión tomada con el reparto anterior. Comprobado con el
--      equipo antes de escribir esto.
--
-- Las notas ya calculadas no se tocan ni se recalculan: cada `nota_etapa` conserva la versión con
-- la que se calculó (RF-139). Lo que cambia es cómo se combinan de aquí en adelante.
--
-- ============================================================================
-- Lo que esto mueve, dicho antes de moverlo
-- ============================================================================
-- El orden del ranking cambia, y no de forma menor. En Asistente Administrativo, quien hoy va
-- primero con 93 en la prueba pasa a 72.3 —su perfil es 47— y le adelanta quien tiene 70.75 de
-- perfil y 81 de prueba. En la otra dirección, alguien con 81 de perfil y 19 de prueba sube de
-- 19 a 46.9.
--
-- Eso es lo que significa ponderar el perfil: deja de decidir solo la técnica. Es el criterio que
-- el cliente pidió, y se aplica ahora precisamente porque todavía no se ha actuado sobre el orden
-- viejo. Con alguien ya contratado o descartado, esto no se haría: se estrenaría en la siguiente
-- convocatoria.

-- Las versiones a corregir, en una vista temporal para no repetir la condición tres veces.
CREATE TEMPORARY TABLE la_de_administracion ON COMMIT DROP AS
SELECT DISTINCT v.version_pesos_id AS id
  FROM vacante v
 WHERE (lower(v.titulo) LIKE 'administrador%'
        OR lower(v.titulo) LIKE 'asistente administrativo%')
   -- Su único peso de etapa es la prueba al 100: es lo que la hace estar mal.
   AND (SELECT count(*) FROM peso_etapa pe WHERE pe.version_pesos_id = v.version_pesos_id) = 1
   AND EXISTS (SELECT 1 FROM peso_etapa pe
                WHERE pe.version_pesos_id = v.version_pesos_id
                  AND pe.etapa_codigo = 'PRUEBA_PUESTO'
                  AND pe.peso = 100);

UPDATE peso_etapa
   SET peso = 55
 WHERE version_pesos_id IN (SELECT id FROM la_de_administracion)
   AND etapa_codigo = 'PRUEBA_PUESTO';

-- El `ON CONFLICT` no debería hacer falta —la condición de arriba exige que el único peso de
-- etapa sea la prueba, así que el perfil no está—, pero una migración que revienta a mitad deja
-- la versión con el 55 puesto y sin su 45: peor que no haber empezado.
INSERT INTO peso_etapa (version_pesos_id, etapa_codigo, peso)
SELECT id, 'PERFIL_INTEGRAL', 45 FROM la_de_administracion
ON CONFLICT (version_pesos_id, etapa_codigo) DO NOTHING;

-- El reparto dentro del perfil integral, que esta versión no tenía.
--
-- Hace falta porque al publicar se comprueba que los componentes sumen exactamente lo que vale la
-- etapa; sin estas filas la versión no pasaría su propia validación.
--
-- Todo va a EVALUACION porque en el cazatalentos el perfil integral **es** la prueba RENASER: lo
-- calcula `CalificacionCriterios` como índice de los pilares del banco. No hay currículum que
-- ponderar —el CV no se puntúa en este instrumento— ni módulo psicométrico. Un CV en 12, copiado
-- del embudo genérico, prometería una nota que aquí nadie calcula.
INSERT INTO peso_componente_perfil (version_pesos_id, componente, peso)
SELECT a.id, c.componente, c.peso::numeric
  FROM la_de_administracion a,
       (VALUES ('CV', 0), ('PSICOMETRICO', 0), ('EVALUACION', 45)) AS c(componente, peso)
 WHERE NOT EXISTS (
     SELECT 1 FROM peso_componente_perfil ya
      WHERE ya.version_pesos_id = a.id AND ya.componente = c.componente);
