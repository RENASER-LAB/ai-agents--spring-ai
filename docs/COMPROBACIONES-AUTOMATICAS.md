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

## 680 pruebas

Contadas de correrlas el 26/08/2026, no de recordarlas: el desglose sale de los
informes de surefire y failsafe, y suma exacto.

| Qué | Cuántas | Necesita |
|---|---:|---|
| Unitarias, con dobles | 479 | nada |
| Arquitectura | 8 | nada |
| Las fórmulas del banco v3 | 22 | nada |
| El validador de las respuestas v3 | 21 | nada |
| El perfil del candidato (merge, lectura, CRUD, estados, retención, borrado) | 64 | nada |
| Integración, de punta a punta | 80 | Docker |
| Contra el proveedor de verdad, y el envío de correo | 6 | Docker o SMTP, y su bandera |

⚠️ Al recontar, **no sirve el atributo `tests=`** de los XML de surefire: con clases anidadas
(`@Nested`) subcuenta, y por eso dos filas de esta tabla llevaban tiempo mal —las fórmulas
ponían 20 cuando son 22, y el validador 14 cuando son 21— aunque el total cuadrase por
compensación. Lo que hay que contar son los elementos `<testcase>`:

```bash
grep -ho "<testcase" target/surefire-reports/*.xml | wc -l
```

Las tres filas del medio se listan aparte porque se prueban solas, sin contexto de Spring:
son las que deciden la nota de una persona y las que impiden que una respuesta con mala
forma llegue a puntuarse.

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

## Las ocho reglas de arquitectura

Están en `ArquitecturaTest` y no inventan nada: son las reglas que el `CLAUDE.md` ya tenía
escritas en prosa. **Una regla en prosa se rompe sin que nadie se entere** —alguien añade un
import, el código compila, las pruebas pasan y la frontera ya no existe— y eso es lo que
estas ocho impiden.

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

### Las dos desviaciones que había, y ya no

Al poner las reglas aparecieron dos sitios que se las saltaban desde antes:
`CatalogoController` y `PanelAuthController` inyectaban repositorios y tocaban entidades.

Quedaron **nombrados uno por uno en la prueba, con su motivo escrito**, en vez de escondidos
tras un patrón genérico. Eso es lo que hizo que se arreglaran: una desviación a la vista se
decide, una escondida se olvida. Hoy los catálogos salen de `ServicioCatalogo` y el arranque
del primer usuario del equipo de `ServicioAccesoEquipo`.

**Las ocho reglas no tienen excepciones.**

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
