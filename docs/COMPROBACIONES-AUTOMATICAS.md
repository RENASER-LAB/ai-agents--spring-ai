# Lo que el proyecto se comprueba solo

Qué mira la compilación por su cuenta, qué deja pasar, y qué falta por poner.

Sirve para saber de qué te avisa el proyecto y de qué no. Lo que no está aquí, no lo mira
nadie salvo una persona leyendo el código.

---

## Cómo se lanza todo

```bash
./mvnw verify
```

`test` lanza solo las unitarias y las de arquitectura; `verify` añade las de integración,
que corren en su propia fase. Es lo mismo que hace la tubería de integración continua.

En Linux basta con el demonio de Docker encendido: comprobado el 19/08/2026, `verify` corre
limpio sin tocar nada más.

**En Windows puede hacer falta forzar el canal de Docker.** Si el contexto activo es
`desktop-linux`, cuyo canal no existe allí, todas las pruebas de integración fallan con
«Could not find a valid Docker environment» —y **eso no es un fallo del código**—. Se
arregla apuntando al `default`:

```bash
DOCKER_HOST='npipe:////./pipe/docker_engine' ./mvnw verify
```

### La que no corre sola

`CalificacionIaRealIT` llama al proveedor de verdad y gasta saldo. Está apagada salvo que se
pida:

```bash
RENASER_IA_REAL=si ./mvnw test -Dtest=CalificacionIaRealIT
```

Se apaga por defecto y no al revés a propósito: **olvidarse de encenderla no cuesta nada;
olvidarse de apagarla, sí.** Sin la bandera se salta en milisegundos, sin levantar
contenedores ni llamar a nadie.

Lo que comprueba —que la clave llegue, que el modelo conteste y que lo que conteste encaje en
el contrato de cada agente— se mira **antes de publicar**, no en cada compilación. Todo lo
demás de la calificación se prueba con un doble del modelo y no gasta nada.

---

## 871 pruebas

Contadas de correrlas el 27/08/2026 (tras fusionar el multiempresa con los inscritos de la
simulación y los permisos editables), no de recordarlas: el desglose sale de los informes de
surefire y failsafe, y suma exacto.

| Qué | Cuántas | Necesita |
|---|---:|---|
| Unitarias, con dobles | 659 | nada |
| Arquitectura | 12 | nada |
| Las fórmulas del banco v3 | 22 | nada |
| El validador de las respuestas v3 | 21 | nada |
| El perfil del candidato (paquete `perfil`: merge, lectura, CRUD, retención, borrado) | 51 | nada |
| Integración, de punta a punta | 100 | Docker |
| Contra el proveedor de verdad, y el envío de correo | 6 | Docker o SMTP, y su bandera |

El multiempresa (25/08) sumó 39 unitarias: 31 de la implementación —el login del panel, las
invitaciones, el alta de empresas, el resolutor de dueño de instrumento, el copiador y la
personalización—, una regla de arquitectura nueva y `FlujoDosEmpresasIT` (dos empresas de
verdad, de punta a punta); y 8 del QA —la carrera del canje, el equipo que no entra al
portal, el mismo correo en dos empresas, y una prueba negativa por cada fuga cerrada que no
la tenía (inscripción, barrera, prueba ajena, alerta ajena). La revisión final añadió a
`FlujoDosEmpresasIT` el viaje que faltaba: la candidata rinde el examen entero del banco de
Renaser en la vacante de ACME, la nota queda atada a los pesos de la plataforma, y ACME ve
la ficha, la nota, su bandeja y su embudo — mientras la plataforma no ve nada de eso.

Las piezas D, E y F (26/08) sumaron 37 unitarias y 7 de integración. Las unitarias: el
consentimiento firmado con la empresa de la vacante (postular sin aceptar, sin texto
publicado, la firma con IP y postulación), publicar-vacante-sin-texto-legal, la calculadora
del costo (tarifa vigente por fecha, sin tarifa, tokens ausentes), el tope mensual (el freno
exacto en el 100%, la campana única del 80%, el mes nuevo, el tope ilegible que no congela a
nadie), los estados EN_ESPERA en la cola y la barrera, la suspensión (login con mensaje
claro, la plataforma que no se suspende a sí misma, el tablón que esconde y el candidato de
dentro que no se toca) y el tope validado desde la plataforma. Las de integración:
`FlujoDosEmpresasIT` ganó la suspensión y la reactivación completas, y `FlujoPlataformaIT`
—nuevo, con la calificación encendida y un doble del modelo con tokens fijos— recorre la
vida entera del tope: nace del alta, cada lectura escribe su costo exacto, al 80% suena una
campana única, al 100% lo nuevo espera sin que la candidata se entere, y subir el tope por
el endpoint despierta lo que esperaba.

El QA de la fase 2 (26/08) sumó 8 unitarias y 1 de integración, cada una encima de un
hallazgo real. El gordo: el modelo rápido (`deepseek-chat`, la lectura de CV — una llamada
por candidato) no tenía tarifa en la V38 y todo ese gasto salía NULL, invisible para el
tope; la V39 la siembra, `ClienteModeloDeepSeek` anota el modelo PEDIDO cuando el proveedor
calla (antes anotaba siempre el caro), y un IT nuevo exige tarifa vigente para todo modelo
de `application.yaml`. Los otros: la suspendida que congela también sus trabajos de IA
nuevos y a la que el barrido no despierta ni con cupo (unitarias, más el viaje
suspensión→reactivación dentro del IT del tope), la campana del 80% que ya no puede tumbar
una postulación (corre en su propia transacción y su fallo se traga), la doble llave de la
personalización desde la plataforma (una empresa con el permiso copiado no le enciende nada
a la competencia, el motivo queda auditado, el objetivo inexistente es 404 — llegó sin una
sola prueba), y el borrado 29733 que también vacía el nombre del papel firmado al postular
(aserción nueva en `FlujoDosEmpresasIT`).

Los inscritos de la simulación y los permisos editables (27/08) sumaron **34 unitarias y 1 de
integración**. Casi todas son de alcance, y casi todas existen porque el conteo y la lista se
separaron tres veces seguidas: `ServicioSimulacionImplTest` creció en 19 —que la lista, el
detalle y `/inscritos` digan el mismo número con `TODO`, con `SUS_VACANTES` y con `PROPIO`,
también cuando los dos permisos traen alcances distintos, que es un reparto que hoy nadie
tiene pero que un solo PUT deja puesto—. `PermisosDeUnRolTest` aporta 8, y las dos que más
importan no son del camino feliz: que el último `administrar_permisos` no se pueda revocar y
que ese candado cuente **dentro de la organización** y no en toda la base, o dos empresas se
taparían la una a la otra. `NombresDeUsuariosTest` aporta 5, todas sobre lo que pasa cuando
no hay nombre que dar: sin persona, con la persona borrada o a medio rellenar sale
`(anonimizado)` y nunca una cadena vacía —ese nombre lo resolvía la bandeja por su cuenta y
ahora lo resuelve un colaborador, así que `ServicioPostulacionesPanelImplTest` cambió de
cuidar cómo se resuelve a cuidar que la ficha lo traiga y que una postulación ajena siga
respondiendo 404 y no 403—. La de integración es el viaje entero por la API dentro de
`FlujoSimulacionValidacionIT`: quién eligió la fecha se ve, y **quién puede verlo se cambia
sin desplegar** — al responsable del área se le revoca el permiso desde el panel y el mismo
token deja de servir en la llamada siguiente, sin desplegar y sin volver a entrar. De paso es
lo único que ejecuta la consulta nueva del conteo contra PostgreSQL de verdad: que su JPQL
con dos saltos sea válida no se ve con dobles.

⚠️ Al recontar, **no sirve el atributo `tests=`** de los XML de surefire: con clases anidadas
(`@Nested`) subcuenta, y por eso dos filas de esta tabla llevaban tiempo mal —las fórmulas
ponían 20 cuando son 22, y el validador 14 cuando son 21— aunque el total cuadrase por
compensación. Lo que hay que contar son los elementos `<testcase>`:

```bash
grep -ho "<testcase" target/surefire-reports/*.xml | wc -l
```

Las tres filas del medio se listan aparte porque se prueban solas, sin contexto de Spring:
son las que deciden la nota de una persona y las que impiden que una respuesta con mala
forma llegue a puntuarse. La fila del perfil cuenta las del paquete `perfil`; antes esta
tabla decía 64 porque sumaba pruebas de otros paquetes que tocan el perfil de pasada.

**La fila de arquitectura cuenta el paquete entero, y ya no son nueve.** Nueve son las reglas
de `ArquitecturaTest`, que es lo que enumera el apartado de abajo; las otras tres son
`MigracionesSinChoqueTest`, que no vigila el código sino los nombres de los archivos de
migración: **que dos ramas no reclamen el mismo número**. Es el único conflicto del
repositorio que git no sabe ver —dos `V40__…sql` distintos son, para git, dos archivos
distintos, y los fusiona sin marcar nada—, y quien se entera es Flyway al arrancar. Pasó el
26/08/2026 con tres ramas peleándose el 37; esta misma rama tuvo que renumerar su migración
al `V40` por eso. Vive en `arquitectura/` porque lee archivos y no necesita Docker, como las
otras nueve.

Entre las de integración hay dos que no se parecen al resto y conviene conocer: la del **banco
por el panel** (`FlujoBancoPreguntasIT`), donde un administrador construye, publica y archiva un
banco entero por la API; y la de **migraciones por fases** (`MigracionPorFasesIT`), que migra
hasta la V19, siembra los datos que había en Pruebas y solo entonces aplica la V20 — es la única
forma de que una migración se tope con datos viejos, que es donde murió el despliegue del 19/08.

Las dos tandas del banco v3 se prueban sueltas porque deciden la nota de una persona: las
fórmulas contra los ejemplos del documento del cliente, y el validador sobre todo contra lo
que tiene que **rechazar** — desde que se guarda el detalle en `jsonb`, es lo único que impide
que una respuesta con mala forma acabe convertida en un puntaje.

Las de integración levantan un PostgreSQL y un RabbitMQ de verdad con Testcontainers, y
recorren el flujo entero. **El modelo siempre se simula**: ninguna prueba de la tanda normal
llama al proveedor.

Cada prueba de integración fija además su propio broker. Suena a detalle y no lo es: sin eso
heredaban lo que cada uno tuviera en su `application-secrets.yaml`, y seis empezaron a fallar
el día que ese archivo apuntó a un broker con TLS. Una prueba que da distinto según la máquina
no sirve para nada.

---

## Las nueve reglas de arquitectura

Están en `ArquitecturaTest` y no inventan nada: son las reglas que el `CLAUDE.md` ya tenía
escritas en prosa. **Una regla en prosa se rompe sin que nadie se entere** —alguien añade un
import, el código compila, las pruebas pasan y la frontera ya no existe— y eso es lo que
estas nueve impiden.

| Regla | Por qué importa |
|---|---|
| La selección solo cruza la frontera del motor de agentes por las clases acordadas | Son dos mitades que mantienen dos personas. La lista está enumerada: añadir una décima clase falla hasta que alguien la escriba ahí, y escribirla obliga a mirar si la frontera sigue teniendo sentido |
| Ningún controlador habla directamente con un repositorio | Entre la petición y la base hay permisos con alcance, transiciones y auditoría, y viven en el servicio |
| Ningún repositorio sabe de un servicio | Un círculo entre capas obliga a abrir media aplicación para leer una consulta |
| **Solo la máquina de estados cambia el estado de una postulación** | La más cara de romper. Saltársela no da error: la postulación se mueve igual. Lo que desaparece es el registro de quién la movió y por qué, el correo al candidato y la auditoría |
| Cada clase está en el paquete que su nombre promete | Quien busca un endpoint mira en `controller` |
| Las entidades no salen por un endpoint | Una entidad publicada convierte cualquier columna nueva en un cambio de contrato |
| Todo controlador nuevo está en la lista del candado de Swagger | Un endpoint que nadie apuntó ahí queda fuera del candado, y se publica sin que nadie lo haya decidido |
| Nadie escribe en la consola a pelo | Lo que se imprime así no aparece en el registro, y el registro es lo único que queda cuando algo falla en producción |
| **Ningún servicio del panel busca por id suelto en un agregado con dueño** | La del multiempresa (25/08). Con una sola empresa un `findById` sin filtrar funciona idéntico y nadie lo nota; con dos, lee datos de la competencia. Las llamadas legítimas —casi todas «derivar al padre»— están enumeradas en `LLAMADAS_SIN_DUENO_ACORDADAS` con su porqué: añadir una nueva falla hasta que alguien la escriba ahí. Corrida contra el código anterior a los arreglos, denunció las fugas una por una |

### Las dos desviaciones que había, y ya no

Al poner las reglas aparecieron dos sitios que se las saltaban desde antes:
`CatalogoController` y `PanelAuthController` inyectaban repositorios y tocaban entidades.

Quedaron **nombrados uno por uno en la prueba, con su motivo escrito**, en vez de escondidos
tras un patrón genérico. Eso es lo que hizo que se arreglaran: una desviación a la vista se
decide, una escondida se olvida. Hoy los catálogos salen de `ServicioCatalogo` y el arranque
del primer usuario del equipo de `ServicioAccesoEquipo`.

**Las ocho primeras reglas no tienen excepciones.** La novena enumera las suyas una por
una, con su motivo escrito — que es distinto de no tenerlas: una excepción a la vista se
decide, una escondida se olvida.

---

## La seguridad: Semgrep y Gitleaks

Las pruebas comprueban que el sistema hace lo que debe; no que no haga lo que no debe. De eso
se encarga **Semgrep**, que lee el código buscando patrones peligrosos: consultas armadas
pegando cadenas, secretos escritos a mano, endpoints sin permiso, datos personales que acaban
en el registro.

**Desde el 19/08/2026 ya no hay que acordarse de lanzarlo**: corre solo en cada Pull Request y
en cada push a `main`, dentro del trabajo «análisis estático» de la tubería. A mano es:

```bash
semgrep scan --error --config p/java --config .semgrep/
```

Son las reglas de serie para Java más **tres propias**, escritas contra fallos que este
proyecto ya tuvo, en `.semgrep/reglas-renaser.yaml`:

| Regla | Qué impide |
|---|---|
| `ia-no-lee-el-texto-sin-anonimizar` | Que el currículum salga hacia el proveedor sin quitarle edad, sexo y estado civil |
| `permitall-solo-en-configuracion-seguridad` | Que aparezca una ruta pública nueva fuera de `ConfiguracionSeguridad` |
| `excepciones-no-se-tragan-por-consola` | Que un error se imprima por consola y se dé por atendido |

Al lado corre **Gitleaks**, que revisa la historia entera de git buscando claves filtradas. Va
con `--redact`: un secreto encontrado no se imprime en el registro público de la corrida, que
sería peor que no haberlo buscado.

**Cuándo mirarlo con atención**, además de cuando la tubería avise:

- Antes de una **auditoría de código** o de seguridad, propia o de un tercero.
- Cuando se toque algo que maneja **datos de candidatos**: currículums, correos, teléfonos.
  Este sistema mueve datos personales de gente real hacia dos proveedores externos.
- Al añadir un **endpoint nuevo**, para comprobar que lleva su permiso y su alcance.

Las **dependencias** las vigila **Dependabot**, aparte: una vez por semana revisa las de Maven
y las de GitHub Actions, y abre un Pull Request cuando alguna tiene versión nueva o una
vulnerabilidad conocida. No fusiona nada solo — su PR pasa por la misma tubería que cualquier
otro cambio.

---

## Enlaces

- [Fallos corregidos de la criba](FALLOS-CORREGIDOS-CRIBA.md) — los cinco que aparecieron al
  pasar 190 currículums reales, y por qué cuatro no daban error
- [La criba de currículums](CRIBA-DE-CURRICULUMS.md) — el recorrido entero
- [Requisitos no funcionales](02-REQUISITOS-NO-FUNCIONALES.md) — lo que el sistema tiene que
  cumplir además de funcionar
