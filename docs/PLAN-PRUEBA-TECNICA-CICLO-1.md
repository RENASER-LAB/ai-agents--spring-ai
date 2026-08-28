# Plan de desarrollo · Prueba técnica, ciclo 1

Ejecuta el [diseño aprobado](DISENO-PRUEBA-TECNICA-FICHA-Y-REDACTOR.md). Todo el trabajo
ocurre en el worktree `prueba-tecnica` (rama `feat/prueba-tecnica`); la base local de
main no se toca: el ensayo real corre contra una base temporal propia del worktree.

## Fase 0 · Cimientos

1. **Verificar el número de migración libre** contra los `db/migration` de TODOS los
   worktrees vivos (tres ramas ya reclamaron el mismo número una vez). Candidata: V42.
2. **Migración `V42__prueba_tecnica_ficha_y_redactor.sql`:**
   - Tabla `ficha_vacante`: `vacante_id` único, `organizacion_id`, `q1_resultado` …
     `q10_espejo` (Q10 opcional), `gente_en_empresa`, `gente_a_cargo` (enteros),
     `riesgo_1` … `riesgo_4`, `familias`, `eliminatoria_1`, `eliminatoria_2`,
     `requerimientos`, `tamano` CHECK (MICRO/MEDIA/GRANDE), `estado` CHECK
     (BORRADOR/COMPLETA), marcas de auditoría.
   - `version_banco`: `vacante_id` opcional con FK + índices únicos parciales: un solo
     BORRADOR y una sola PUBLICADA por vacante.
   - `pregunta`: `presencial boolean NOT NULL DEFAULT false`.
   - Catálogo `agente`: recrear el CHECK de V11 con `REDACTOR` y sembrar su fila.
3. **Entidades JPA** (`FichaVacante` nueva; `VersionBanco.vacanteId`;
   `Pregunta.presencial`) — `ddl-auto: validate` obliga a que cuadren.

## Fase 1 · La ficha

4. Repositorio + servicio + `GET/PUT /api/v1/panel/vacantes/{id}/ficha` (upsert), con el
   permiso de quien edita la vacante en su organización.
5. Reglas en el servicio: TAMAÑO derivado de `gente_en_empresa` (≤30 MICRO · 31–200
   MEDIA · 200+ GRANDE); máximo 2 eliminatorias y 3 requerimientos; los 4 riesgos
   presentes y en orden; `COMPLETA` se calcula al guardar cuando está todo lo
   obligatorio. La respuesta incluye la `version_pesos` CAZATALENTOS sugerida según el
   tamaño (la asignación real va por el endpoint existente con su vara quieta).
6. La congelación de la ficha por primera rendición llega con el ciclo 2 (aún no existe
   la rendición); en este ciclo la ficha es editable y el efecto de editarla es marcar el
   cuestionario como desactualizado (computado: ficha más nueva que el borrador — sin
   columna nueva).
7. Tests de servicio (derivaciones, topes, estado) y de API.

## Fase 2 · El REDACTOR

8. **La receta como clase pura** (estilo `FormulasCazatalentos`): la estructura exacta
   por nivel — DIR 12 · SUP 10 · OPE 8, bloques y cantidades — y sus tests a mano.
9. **La aduana del borrador como validador puro**: cantidades por bloque, presencial
   marcada solo en DIR y nunca más de una, guía completa (C3/C4/señal) en toda pregunta
   puntuable, lista prohibida (estado civil, hijos, salud, embarazo, religión, política,
   sindicato, origen étnico). Tests con borradores buenos y rotos.
10. **El agente**: prompt del REDACTOR + FORMATO JSON (código, bloque, enunciado, C3,
    C4, señal de 0, presencial — sin puntaje: aquí no se puntúa nada). Se registra en el
    catálogo de agentes del código como los demás.
11. **La cola**: nuevo trabajo de generación por vacante en la fila de IA existente —
    tope mensual delante, reintento solo de FALLIDO, y el resultado que no pasa la
    aduana vuelve al agente con el error explicado; agotado eso, FALLIDO visible.
12. **Persistencia del borrador**: `version_banco` BORRADOR con
    `metodo_calificacion='CRITERIOS'` y `vacante_id`, preguntas `ABIERTA` peso 1 con su
    guía; regenerar archiva el borrador anterior (nada se borra) y crea el nuevo.
13. **Endpoint** `POST /api/v1/panel/vacantes/{id}/cuestionario-tecnico/generacion`
    (exige ficha COMPLETA) + `GET .../cuestionario-tecnico` (el borrador o la publicada,
    con su estado y si quedó desactualizado).
14. **Alcance de permisos en el banco**: el dueño ve, edita, corrige y publica solo los
    bancos de SUS vacantes; los bancos por nivel de la plataforma quedan fuera de su
    alcance. La publicación de un banco de vacante NO archiva los bancos por nivel.

## Fase 3 · Verificación (el cierre obligatorio)

15. `./mvnw test` — unitarios completos en verde.
16. **Pruebas de integración** (Testcontainers levanta su propio Postgres desechable,
    como siempre): `FlujoFichaYRedactorIT` — ficha → COMPLETA → generar con agente
    simulado → aduana → borrador → editar una pregunta → publicar; regenerar reemplaza
    y archiva; un borrador roto no deja nada a medias; un dueño no toca bancos ajenos
    ni de plataforma.
17. **Ensayo real en base temporal del worktree** (nunca la base local de main): crear
    `prueba_tecnica_wt` en el Postgres de docker, backend `spring-boot:test-run` en el
    puerto 8083 apuntando a esa base, Flyway desde cero, una ficha de verdad por la API,
    una generación de verdad con DeepSeek, publicación. Al terminar: backend abajo y la
    base se tira.
18. **Agente de QA adversario al final**: revisa el diff completo buscando bugs, huecos
    de permisos y violaciones de las reglas del diseño; corre por su cuenta los
    unitarios y las pruebas de integración (sus Testcontainers usan bases temporales
    propias). Todo bloqueante se arregla y se re-verifica antes del PR.
19. **Documentación**: `07-DICCIONARIO-DE-DATOS`, `09-APIS`, `CALIFICACION-CON-IA`
    (el REDACTOR), y el estado del documento de diseño.
20. **PR a main** con el resumen del ciclo y los pasos operativos.
