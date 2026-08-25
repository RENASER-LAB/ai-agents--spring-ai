# Pieza B · Aislamiento: ninguna empresa ve nada de otra

Fecha: 2026-08-25 · Estado: aprobado en conversación, pendiente de revisión final

Segunda pieza del multiempresa. La pieza A decidió de quién es cada dato; esta garantiza
que ese reparto se cumple — no solo hoy, sino cada vez que alguien añada código nuevo.

## El problema

Hoy 13 de los 20 servicios del panel comprueban «esto es de tu organización» a mano, cada
uno con su guardián. Es una **costumbre**, no una **garantía**: nada impide que el próximo
endpoint lo olvide, y con una sola empresa el olvido no se nota jamás. Con dos empresas,
cada consulta sin comprobar es una fuga de datos entre competidoras.

Decisión tomada: **costumbre + vigilante automático**. Se mantiene el patrón actual y se
le suma vigilancia que convierte el olvido en una compilación rota. Ni filtro mágico en la
capa de datos ni candado en la base (Row-Level Security) por ahora — se pueden añadir
después sin deshacer nada de esto.

---

## 1 · La regla y el vigilante

**La regla:** toda consulta del panel entra por la empresa de quien pregunta. Lo que no es
de tu empresa responde **404 («no existe»)**, no 403 — decir «prohibido» ya confirma que
existe.

**El vigilante son dos, porque uno solo mentiría:**

1. **Prueba de arquitectura (ArchUnit).** Ningún servicio del panel puede buscar por id
   suelto (`findById`) en repositorios de tablas con dueño; tiene que pasar por el
   guardián que comprueba la organización (`findByIdAndOrganizacionId` o el helper
   `laVisible`). Quien lo olvide no compila en verde. Se suma a las 8 reglas que el
   proyecto ya tiene.
2. **La prueba de las dos empresas (`FlujoDosEmpresasIT`).** Siembra dos empresas, llena
   las dos con datos, y recorre los endpoints del panel con el usuario de la empresa B
   pidiendo cosas de la A — esperando 404 en todos. La prueba de arquitectura vigila la
   forma; esta vigila la verdad.

---

## 2 · La auditoría de lo que hay

- **Los 20 servicios del panel se repasan todos** (13 ya comprueban, 7 no — hasta hoy no
  hacía falta). Ojo especial a las **tablas hijas**: comprobar la vacante y luego pedir
  sus preguntas por id suelto es la fuga típica, invisible con una sola empresa.
- **Los procesos sin nadie conectado** (cola de calificación, barridos nocturnos) no
  tienen «empresa actual»: toman la organización de cada trabajo que procesan. La mayoría
  ya lo hace; se verifica que todos.
- **La única excepción deliberada, con nombre y apellido:** el listado público de
  vacantes del portal muestra las de todas las empresas juntas — eso es ser plataforma
  tipo Indeed. El resto del portal (mis postulaciones, mi evaluación, mi perfil) ya se
  filtra por la persona y no cambia.

---

## Consecuencias para la implementación (resumen)

- Regla ArchUnit nueva + lista explícita de repositorios «con dueño» que la regla cubre.
- Auditoría y arreglo de los 7 servicios sin guardián y de los accesos a tablas hijas.
- `FlujoDosEmpresasIT` con dos organizaciones sembradas.
- Sin migraciones: esta pieza no toca el esquema.

## Qué NO es esta pieza

- No mete empresas nuevas (pieza C). Para probar el aislamiento basta sembrarlas en el
  test.
- No decide qué comparte cada empresa (eso fue la pieza A: el resolutor
  `DuenoDelInstrumento` es un permiso de lectura deliberado, no una fuga).
