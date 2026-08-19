# Los fallos que aparecieron al cribar de verdad

Cinco fallos salieron al pasar 190 currículums reales por el sistema. Ninguno se veía con
diez, y cuatro de ellos **no daban error**: devolvían un resultado creíble y equivocado.

Se documentan porque los cinco tienen la misma forma —algo que funciona con pocos datos y
deja de funcionar con muchos— y porque el que los vuelva a tocar conviene que sepa por qué
el código está escrito así.

Cada uno tiene su prueba con nombre propio. Si alguien deshace el arreglo, la prueba lo dice.

---

## 1 · El aviso salía antes de guardar

**Qué pasaba.** Al pedir la criba de una tanda, los trabajos se quedaban en «pendiente» para
siempre. Sin error, sin nada en el registro. Quince minutos después el vigilante de atascados
los rescataba y entonces sí corrían.

**Por qué.** El aviso a la cola se mandaba dentro de la transacción, antes del `commit`. Del
otro lado hay ocho consumidores esperando: uno cogía el mensaje en milisegundos, iba a la
base, no encontraba el trabajo —todavía no estaba guardado— y lo soltaba.

**Lo que lo escondía.** Con un solo consumidor la carrera se ganaba casi siempre por
casualidad. Al subir a ocho se perdía casi siempre.

**Cómo está ahora.** El mensaje se manda **después del commit**, con una sincronización de
transacción. Si no hay transacción abierta, sale al momento.

> `TrabajoIaPublisher` · prueba: `dentroDeUnaTransaccionElMensajeEsperaAlCommit`

---

## 2 · Quien fallaba y luego salía bien quedaba marcado como fallido

**Qué pasaba.** Un candidato cuya calificación falló y se reintentó con éxito seguía saliendo
en la pantalla como «la IA falló», con su retrato completo debajo.

**Por qué.** El estado se calculaba preguntando primero «¿hay algún trabajo fallido?» y
después «¿terminó el último?». La fila fallida no se borra —es el registro de lo que pasó— así
que la primera pregunta contestaba que sí para siempre.

**Cómo está ahora.** Se mira primero si el último agente terminó. El éxito manda sobre un
fallo viejo.

> `ColaCalificacionIaImpl.comoVa` · prueba:
> `quienFalloYLuegoSalioBienAlReintentarNoQuedaMarcadoComoFallido`

---

## 3 · Un candidato sin grupo tumbaba el ranking entero

**Qué pasaba.** Pedir el ranking de una vacante recién cargada devolvía error 500.

**Por qué.** El orden pregunta en qué posición está el grupo de prioridad dentro de una lista
fija. Quien todavía no está calificado no tiene grupo, y `List.of(...).indexOf(null)` no
devuelve −1: **lanza excepción**, porque las listas inmutables de Java no admiten nulos.

**Cómo está ahora.** El nulo se atiende antes de preguntar, y va al final de la tanda. Que es
además lo correcto: sin nota no se puede estar arriba.

> `ServicioPerfilIntegralPanelImpl.posicionDe` · prueba:
> `unCandidatoSinGrupoNoRompeElRankingYSeVaAlFinal`

---

## 4 · La segunda pasada elegía por orden alfabético

**Qué pasaba.** Alguien pulsó «2ª pasada» mientras la tanda todavía se estaba cargando. El
sistema mandó 43 currículums al modelo caro. Ninguno tenía nota todavía.

**Por qué.** La segunda pasada coge «los de arriba» del ranking. Cuando nadie tiene nota, el
orden cae en el último criterio de desempate: el nombre. Se gastaron 43 llamadas eligiendo
gente por la letra de su apellido, y el resultado parecía perfectamente normal.

**Cómo está ahora.** Si nadie tiene nota, la segunda pasada **se niega** y dice por qué. Si
solo una parte la tiene, el corte se calcula sobre esos y no sobre la lista entera.

> `ServicioPerfilIntegralPanelImpl.cribaFina` · prueba:
> `laSegundaPasadaSeNiegaSiTodaviaNadieTieneNota`

---

## 5 · Un byte invisible tiraba la calificación ya hecha

**Qué pasaba.** Dos currículums fallaban siempre. Se reintentaban tres veces y las tres
morían igual, con la nota ya calculada y pagada.

**Por qué.** El texto que sale de un PDF mal generado puede traer bytes nulos. Una columna de
texto de PostgreSQL los rechaza, así que la calificación se estrellaba **al guardar**, después
de haber llamado al modelo. Y el reintento volvía a estrellarse, porque el archivo no cambia.

**Cómo está ahora.** El byte nulo y los demás caracteres de control se quitan al extraer el
texto, antes de que llegue a ninguna parte.

> `ExtractorTextoCv.exigirContenido` · prueba: `elByteNuloNoLlegaAlTextoQueSeGuarda`

---

## Y una lentitud, que no es un fallo pero se le parecía

El ranking pedía **once cosas por candidato**: sus trabajos dos veces, su perfil, sus
hallazgos, sus notas, su usuario, su persona, su currículum, su archivo y sus alertas. Con 103
postulantes eran más de mil consultas para pintar una pantalla —justamente la que existe para
mirar la tanda entera.

Ahora son once consultas en total: cada una trae lo de todos los candidatos y el recorrido
solo busca en memoria.

---

## Lo que tienen en común

Los cinco aparecieron al pasar de diez currículums a cien, y **cuatro no daban error**:
devolvían un número, una lista o un estado que parecían correctos. El único que se quejaba
—el byte nulo— lo hacía después de haber gastado la llamada al modelo.

De ahí dos reglas que el código ya sigue y conviene no aflojar:

- **Nunca se inventa una nota.** Si algo falla, la postulación se queda esperando y se dice.
  Un cero y un hueco no son lo mismo.
- **Nadie desaparece de la lista.** Quien no tiene nota, o cuya calificación falló, sale igual
  con su estado escrito. Si desapareciera, desaparecería también el problema.

---

## Enlaces

- [La criba de currículums](CRIBA-DE-CURRICULUMS.md) — cómo funciona el recorrido entero
- [La calificación con IA](CALIFICACION-CON-IA.md) — los agentes y qué pasa si el modelo falla
