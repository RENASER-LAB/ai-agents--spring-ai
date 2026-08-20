# La criba de currículums

Cómo se carga una convocatoria con una carpeta de currículums, se pide que la IA los lea, y
se ve en pantalla quién es el más apto.

---

## En una frase

**Llegan cien currículums y nadie ha respondido nada todavía: la IA los lee uno a uno, los
puntúa contra el anuncio de esa vacante, y devuelve la lista ordenada de a quién invitar
primero.**

---

## Por qué hacía falta

La calificación con IA ya existía, pero **solo arrancaba cuando el candidato entregaba su
evaluación**. Con una carpeta de currículums recién llegados no había forma de pedirla: el
botón del panel respondía «esta postulación todavía no tiene evaluación».

Y esa es justo la primera decisión real de una convocatoria. Antes de mandarle una
evaluación a nadie hay que saber a quién merece la pena mandársela.

---

## Qué pasa, paso a paso

1. Se **monta la convocatoria**: el puesto, la Solicitud de Talento aprobada y la vacante
   con el texto del anuncio. El texto importa: es lo que la IA lee como «qué busca este
   puesto».
2. Por cada archivo de la carpeta se **crea una cuenta de candidato y se postula**, con las
   mismas llamadas que hará el portal. El currículum queda guardado como cualquier otro.
3. Se pide la **criba**. La postulación pasa a «calificando».
4. Corren **dos agentes**, uno detrás de otro:

| Agente | Qué hace |
|---|---|
| **Evidencia del currículum** | Puntúa el currículum sobre 100 con los ocho criterios, con el peso del nivel del puesto. Y clasifica cada afirmación: demostrada, declarada, contradicha o falta información |
| **Potencial y riesgo** | Arma el Perfil de Talento: adecuación, potencial, alto rendimiento, confianza de la evidencia y los hallazgos |

5. Al terminar, cada candidato tiene **nota y grupo de prioridad**, y la postulación queda
   en «por confirmar», esperando a que una persona decida.

**El evaluador se salta solo.** Es el agente que califica las respuestas abiertas, y aquí no
hay ninguna: llamarlo gastaría una petición al modelo para no puntuar nada.

**La nota sale del currículum a solas.** El reparto entre currículum y evaluación reparte
solo lo que existe, así que con una mitad la cuenta sigue saliendo en la misma escala.

---

## La vacante se monta sin requisitos que descarten solos

Un requisito indispensable —«disponibilidad presencial en Arequipa»— es lo único que puede
cerrar una postulación sola. Funciona cuando hay una persona delante confirmándolo al
postular. **En una carga de currículums no la hay**, así que todo lo que entrara se cerraría
al momento y la IA no llegaría a leer nada.

Marcarlo por ellos sería peor: quedaría escrito que el candidato confirmó algo que nadie le
preguntó.

La disponibilidad presencial sigue en el texto de la vacante, que es lo que la IA lee al
puntuar. Simplemente no es una puerta que se cierre sola.

> Si la vacante ya existía de antes y arrastra un requisito activo, el script lo avisa y dice
> cómo quitarlo. No lo quita él: desactivar una puerta sin que nadie lo pida es peor que
> avisar.

---

## Los currículums salen de una carpeta, no de Drive

El sistema **no se conecta a Google Drive**. Hay que descargar la carpeta a `cv-convocatoria/`
antes de lanzar la carga.

De cada archivo salen el nombre y el correo del candidato, y salen **del nombre del archivo**,
no de leer el PDF. Se espera algo como `Ana Torres.pdf` o `cv-ana-torres.pdf`.

> **Por qué del nombre del archivo.** En un currículum el nombre no está siempre en el mismo
> sitio, y equivocarse ahí significa mezclar el currículum de una persona con el de otra. El
> nombre del archivo se puede corregir antes de cargar; una confusión en la base, no.

El **.doc antiguo** de Word no se puede leer. Solo `.pdf` y `.docx`.

---

## Lo que se ve en pantalla

Una tabla con la tanda entera, ordenada de más apto a menos, y la ficha de quien se elija.

**Manda el grupo de prioridad, no la nota.** Alguien con 92 y un riesgo crítico no va por
delante de alguien con 88 y ninguno. Ordenar solo por número escondería justo eso.

**Nadie desaparece de la lista.** Quien todavía no tiene nota, o cuya calificación falló,
sale igual con su estado escrito. Si desapareciera, desaparecería también el problema.

Al abrir un candidato se ven los ocho criterios con la explicación de cada nota, los
hallazgos y los avisos. Un criterio que la IA no pudo puntuar se ve como un hueco, no como
un cero: son cosas distintas.

---

## Flujo de implementación

1. **Descarga la carpeta de Drive** a `cv-convocatoria/` en la raíz del proyecto. Está en el
   `.gitignore`: son datos de gente real y no se suben al repositorio.

2. **Renombra los archivos** como `Nombre Apellido.pdf`, si no vienen así.

3. **Levanta la base y la cola**, si no están:
   ```bash
   docker compose up -d
   ```

4. **Arranca el backend** desde IntelliJ, o:
   ```bash
   ./mvnw spring-boot:run
   ```

5. **Elige la convocatoria.** Están en `scripts/convocatorias.json`, una por clave. Hoy hay
   dos: `talento` y `asistente-admin`. Se añaden ahí, sin tocar código.

   Para dejar la vacante montada antes de tener los currículums:
   ```bash
   python scripts/cargar-convocatoria.py --uid TU_UID --convocatoria asistente-admin --solo-convocatoria
   ```

6. **Carga los currículums.** La primera vez conviene mirar que la carga
   salga bien antes de gastar llamadas al modelo:
   ```bash
   python scripts/cargar-convocatoria.py --uid TU_UID --convocatoria asistente-admin --carpeta cv-asistente --sin-criba
   ```
   Y cuando se vea la lista completa, la corrida de verdad:
   ```bash
   python scripts/cargar-convocatoria.py --uid TU_UID --convocatoria asistente-admin --carpeta cv-asistente --esperar
   ```

7. **Abre la pantalla**: el ranking y la ficha se ven en el panel de RENASER OS
   (`~/Documentos/RenaserOs`, rama `feat/talento`, con su proxy de Vite hacia
   `localhost:8080`). El `frontend/` de demostración que vivía en este repositorio se
   retiró a propósito (2e7b176) cuando el panel de verdad quedó en RENASER OS.

Para volver a mirar la tanda sin cargar nada:
```bash
python scripts/cargar-convocatoria.py --uid TU_UID --solo-ranking
```


---

## La otra pantalla: subir currículums a mano

Todo lo de arriba pasa por la terminal: la carpeta, el script de Python, el `--uid`. Eso sirve
para cargar una convocatoria entera, pero no para enseñarle el sistema a alguien de Renaser ni
para que el propio cliente pruebe con dos currículums sueltos.

Para eso hay una segunda pantalla, **fuera de este repositorio**, en
`~/Documentos/RenaserTalentoFrontend`. Es una sola página sin dependencias:

```bash
node servidor.js
```

Queda en `http://localhost:3000` y hace el camino completo desde el navegador: elegir el
puesto, **arrastrar los currículums**, lanzar las dos pasadas y ver la tabla. Los sube por las
mismas llamadas del portal que usa el script, así que no hay un segundo camino de entrada que
mantener.

| | RENASER OS (panel) | `RenaserTalentoFrontend` |
|---|---|---|
| Para qué sirve | Trabajar sobre una tanda ya cargada | Enseñar el flujo y probar con pocos |
| Subir currículums | No, con el script de Python | Sí, desde el navegador |
| Ficha del candidato | Sí, con los ocho criterios | No, solo la tabla |
| Qué hace falta instalar | Node y `npm install` | Solo Node |

Las dos consultan el mismo ranking del backend, así que enseñan el mismo orden.

---

## Decisiones

Tres cosas están puestas con un criterio que Renaser tiene que confirmar:

- **El nivel del puesto es Supervisión.** El nivel decide el peso de los ocho criterios, así
  que no es un detalle: en Dirección pesan más los resultados y la complejidad; en Supervisión,
  los sistemas creados y el desarrollo de personas. Se eligió Supervisión porque el anuncio
  pide construir el sistema y formar a otros, pero no dirigir el área. Si Renaser lo considera
  un puesto de Dirección, se cambia en el script.

- **Los números que separan un grupo de otro (80 y 65) siguen sin confirmarse.** Son un
  parámetro editable, no están en el código.

- **Los textos de consentimiento todavía no nombran a DeepSeek ni a Google.** Mientras la IA
  no leyera a nadie eso no rompía nada. Con una carpeta de currículums reales sí: el
  currículum sale de verdad hacia esos dos. Renaser tiene que aprobar un texto nuevo antes de
  la primera corrida con candidatos reales.

---

## Enlaces

- [La calificación con IA](CALIFICACION-CON-IA.md) — los tres agentes, qué se guarda de cada
  llamada y qué pasa si el modelo falla
- [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) — el sistema entero, sin palabras técnicas
- [Estados de la postulación](03-ESTADOS-POSTULACION.md) — dónde encaja «calificando» y «por
  confirmar»
- [Las APIs](09-APIS.md) — los endpoints del panel
