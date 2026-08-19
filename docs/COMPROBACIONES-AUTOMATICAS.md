# Lo que el proyecto se comprueba solo

Qué mira la compilación por su cuenta, qué deja pasar, y qué falta por poner.

Sirve para saber de qué te avisa el proyecto y de qué no. Lo que no está aquí, no lo mira
nadie salvo una persona leyendo el código.

---

## Cómo se lanza todo

```bash
DOCKER_HOST='npipe:////./pipe/docker_engine' ./mvnw verify
```

`test` lanza solo las unitarias y las de arquitectura; `verify` añade las de integración,
que ahora corren en su propia fase. Es lo mismo que hace la tubería de integración continua.

**`DOCKER_HOST` hace falta en esta máquina.** El contexto activo de Docker es
`desktop-linux`, cuyo canal no existe; el que funciona es el `default`. Sin esto, todas las
pruebas de integración fallan con «Could not find a valid Docker environment», y **eso no es
un fallo del código**.

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

## 148 pruebas

| Qué | Cuántas | Necesita |
|---|---:|---|
| Unitarias, con dobles | 99 | nada |
| Arquitectura | 7 | nada |
| Integración, de punta a punta | 37 | Docker |
| Contra el proveedor de verdad | 5 | Docker, saldo y la bandera |

Las de integración levantan un PostgreSQL y un RabbitMQ de verdad con Testcontainers, y
recorren el flujo entero. **El modelo siempre se simula**: ninguna prueba de la tanda normal
llama al proveedor.

Cada prueba de integración fija además su propio broker. Suena a detalle y no lo es: sin eso
heredaban lo que cada uno tuviera en su `application-secrets.yaml`, y seis empezaron a fallar
el día que ese archivo apuntó a un broker con TLS. Una prueba que da distinto según la máquina
no sirve para nada.

---

## Las siete reglas de arquitectura

Están en `ArquitecturaTest` y no inventan nada: son las reglas que el `CLAUDE.md` ya tenía
escritas en prosa. **Una regla en prosa se rompe sin que nadie se entere** —alguien añade un
import, el código compila, las pruebas pasan y la frontera ya no existe— y eso es lo que
estas siete impiden.

| Regla | Por qué importa |
|---|---|
| La selección solo cruza la frontera del motor de agentes por las clases acordadas | Son dos mitades que mantienen dos personas. La lista está enumerada: añadir una décima clase falla hasta que alguien la escriba ahí, y escribirla obliga a mirar si la frontera sigue teniendo sentido |
| Ningún controlador habla directamente con un repositorio | Entre la petición y la base hay permisos con alcance, transiciones y auditoría, y viven en el servicio |
| Ningún repositorio sabe de un servicio | Un círculo entre capas obliga a abrir media aplicación para leer una consulta |
| **Solo la máquina de estados cambia el estado de una postulación** | La más cara de romper. Saltársela no da error: la postulación se mueve igual. Lo que desaparece es el registro de quién la movió y por qué, el correo al candidato y la auditoría |
| Cada clase está en el paquete que su nombre promete | Quien busca un endpoint mira en `controller` |
| Las entidades no salen por un endpoint | Una entidad publicada convierte cualquier columna nueva en un cambio de contrato |
| Nadie escribe en la consola a pelo | Lo que se imprime así no aparece en el registro, y el registro es lo único que queda cuando algo falla en producción |

### Las dos desviaciones que había, y ya no

Al poner las reglas aparecieron dos sitios que se las saltaban desde antes:
`CatalogoController` y `PanelAuthController` inyectaban repositorios y tocaban entidades.

Quedaron **nombrados uno por uno en la prueba, con su motivo escrito**, en vez de escondidos
tras un patrón genérico. Eso es lo que hizo que se arreglaran: una desviación a la vista se
decide, una escondida se olvida. Hoy los catálogos salen de `ServicioCatalogo` y el arranque
del primer usuario del equipo de `ServicioAccesoEquipo`.

**Las siete reglas no tienen excepciones.**

---

## Lo que falta: la seguridad

**Hoy nadie mira el código buscando fallos de seguridad.** Ni la compilación, ni las pruebas,
ni las reglas de arquitectura. Las pruebas comprueban que el sistema hace lo que debe; no que
no haga lo que no debe.

Se va a cubrir con **Semgrep**, que lee el código buscando patrones peligrosos: consultas
armadas pegando cadenas, secretos escritos a mano, endpoints sin permiso, datos personales
que acaban en el registro.

**Cuándo toca usarlo:**

- Antes de una **auditoría de código** o de seguridad, propia o de un tercero.
- Cuando se toque algo que maneja **datos de candidatos**: currículums, correos, teléfonos.
  Este sistema mueve datos personales de gente real hacia dos proveedores externos.
- Al añadir un **endpoint nuevo**, para comprobar que lleva su permiso y su alcance.
- De forma periódica sobre la rama principal, para que una regresión no espere a la
  siguiente auditoría.

Queda pendiente decidir dos cosas: **qué reglas** se aplican —las que trae de serie para Java
y Spring, más las propias del proyecto— y **dónde corre**, si en la máquina de quien programa,
en la integración continua, o en las dos.

---

## Enlaces

- [Fallos corregidos de la criba](FALLOS-CORREGIDOS-CRIBA.md) — los cinco que aparecieron al
  pasar 190 currículums reales, y por qué cuatro no daban error
- [La criba de currículums](CRIBA-DE-CURRICULUMS.md) — el recorrido entero
- [Requisitos no funcionales](02-REQUISITOS-NO-FUNCIONALES.md) — lo que el sistema tiene que
  cumplir además de funcionar
