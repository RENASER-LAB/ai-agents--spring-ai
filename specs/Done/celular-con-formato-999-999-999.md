# Celular con formato 999 999 999
Estado: TERMINADO

## Objetivo
Que nadie pueda dejar mal escrito un celular, ni en el portal ni en el panel. El campo solo
admite cifras, las agrupa sola como «999 999 999» y no deja escribir una décima. Es un pedido
de la clienta: «que los números de teléfono sean directo: 999 999 999». Hoy el campo acepta
cualquier texto y el error aparece recién al enviar el formulario.

## Contexto
- Comportamiento actual:
  - **Portal del candidato** (registro, «Mi cuenta» y la tarjeta de WhatsApp de «Mis
    procesos»). El campo «Celular (WhatsApp)» muestra «+51» delante y el ejemplo
    «987 654 321», pero deja escribir letras, cualquier cantidad de cifras y separadores.
    Solo al enviar avisa «Escribe un celular de 9 cifras que empiece por 9, o el número
    completo con su prefijo». Acepta números de otro país que empiecen por «+» o «00», lo
    que encaja con la ciudad «Fuera del Perú» del registro. El backend vuelve a comprobar la
    regla y guarda el número en formato internacional («+51987654321»).
  - **Panel, ficha del colaborador** (alta, alta desde un contratado y edición). «Celular ·
    opcional» es texto libre de hasta 30 caracteres; no se comprueba nada y se guarda tal
    cual. En el alta desde un contratado se rellena con el teléfono que la IA leyó del CV, sin
    comprobarlo y sin mirar el celular de su cuenta.
  - **Panel, carga de colaboradores por Excel**. La columna Celular se guarda tal cual. La
    carga ya es todo o nada: si una celda tiene un error, se rechaza el archivo entero y se
    listan la fila, la columna y el motivo, como ya pasa con el correo.
- Pantallas o procesos afectados: los cinco anteriores.
- Referencias existentes:
  - La regla, que es una sola: en el backend
    [Celular.java](../../src/main/java/com/renaser/ai/ai_engine/whatsapp/service/Celular.java),
    y en el frontend (repositorio `Frontend-EX`) `src/dominio/celular.ts`. Las dos deben
    seguir siendo idénticas.
  - Portal (`Frontend-EX`): `src/paginas/cuenta/Registro.tsx`,
    `src/paginas/perfil/MiCelular.tsx`, `src/paginas/procesos/TarjetaWhatsapp.tsx` y el campo
    con prefijo `src/ui/campos/Campo.tsx`.
  - Panel (`Frontend-EX`): `src/panel/colaboradores/Campos.tsx` y `formulario.ts`.
  - Backend del colaborador:
    [ServicioColaboradoresImpl](../../src/main/java/com/renaser/ai/ai_engine/colaborador/service/impl/ServicioColaboradoresImpl.java)
    (comprobación de los datos personales y precarga desde un contratado) y
    [ServicioCargaColaboradoresImpl](../../src/main/java/com/renaser/ai/ai_engine/colaborador/service/impl/ServicioCargaColaboradoresImpl.java)
    (lectura de cada fila del Excel).
  - Spec que introdujo la regla del celular (V73):
    [novedades-por-whatsapp](novedades-por-whatsapp.md).

## Alcance
- Incluye:
  - Una máscara de escritura común a los tres campos del portal y al de la ficha del
    colaborador, con la regla de siempre.
  - Una salida para los números de otro país: si lo escrito empieza por «+» o «00», el campo
    acepta el número internacional completo.
  - Comprobar en el backend el celular del colaborador, tanto en la ficha como en el Excel,
    con la misma regla, y guardarlo como el de la cuenta (formato internacional).
  - Precargar el alta desde un contratado con el celular de su cuenta.
- Queda fuera:
  - Un selector de país o formatos distintos para cada país.
  - Corregir o convertir los celulares de colaboradores ya guardados.
  - Cambiar la regla del backend para el portal, que ya acepta todo lo que la máscara
    produce, o la forma en que se guarda el celular de la cuenta.
  - El teléfono que la IA saca del CV (`dato_cv`), el CRM y el bot de WhatsApp.
  - Volver obligatorio el celular del colaborador: sigue siendo opcional.

## Comportamiento esperado
1. **Número peruano, el caso normal.** Con «+51» delante, el campo solo acepta cifras.
   Mientras la persona escribe, aparece un espacio después de la tercera cifra y otro
   después de la sexta («987 654 321»). No se puede escribir una décima cifra. Si teclea
   letras, guiones, puntos, paréntesis o espacios, no se escriben.
2. **Pegar o autocompletar.** Lo que se pega, o lo que rellena el navegador, se limpia igual.
   «987-654-321», «987654321», «(987) 654 321», «51987654321» y «+51 987 654 321» quedan
   todos como «987 654 321». Si lo pegado no se puede leer como un celular peruano ni como
   uno internacional (paso 3), el campo se queda como estaba y aparece el mensaje de la
   regla. Nunca se corta en silencio un número largo a sus primeras 9 cifras.
3. **Número de otro país.** Si el primer carácter es «+», o si empieza por «00», el «+51» de
   delante desaparece. El campo acepta entonces «+» seguido de hasta 15 cifras, con los
   espacios que ponga la persona, y «00» se convierte en «+». Si borra el «+», vuelve el
   modo peruano con su «+51».
4. **Al enviar** se aplica la regla de hoy con el mismo mensaje: 9 cifras que empiecen por 9,
   o un número internacional de 8 a 15 cifras; un «+51» que no va seguido de un celular
   peruano no vale. La máscara evita casi todos los errores. El número incompleto («987 65»)
   y el que no empieza por 9 se siguen avisando al enviar, junto al campo.
5. **«Mi cuenta»** abre el número guardado ya con su forma: «987 654 321» para los
   peruanos, y para los de otro país el número completo con su «+».
6. **Ficha del colaborador (panel).** Tiene el mismo campo y la misma máscara, pero es
   opcional: puede quedar vacío. Se guarda como el de la cuenta («+51987654321») y la ficha
   lo muestra como «+51 987 654 321».
7. **Celular antiguo de un colaborador** guardado antes de este cambio que no cumple la
   regla. La ficha lo muestra tal cual. Al editar, el campo lo presenta sin máscara; si
   RR.HH. no lo toca, se guarda el resto de la ficha sin obligarle a corregirlo. En cuanto
   lo modifica, se aplica la máscara; también puede vaciarlo.
8. **Alta desde un contratado.** El celular se rellena con el de la cuenta del candidato. Si
   la cuenta no tiene celular, se usa el del CV, pero solo si cumple la regla; si no, queda
   vacío.
9. **Carga por Excel.** Una celda Celular que no cumple la regla es un error de esa fila y
   columna, con el valor y el mensaje de la regla, y rechaza el archivo como ya pasa con el
   correo. Una celda vacía no toca el celular de un colaborador existente, igual que hoy. Si
   la celda trae el mismo valor que ya está guardado, no cuenta como error ni como cambio.
   Los válidos se guardan en formato internacional, y un número que Excel guardó como cifra
   (987654321) vale igual que escrito como texto.
10. **Petición directa al backend.** Si el celular de un colaborador no cumple la regla, la
    respuesta es un 400 con el mensaje y no se guarda nada. La única excepción es el mismo
    valor que ya estaba guardado (paso 7).

## Referencia visual
No aplica: se sigue usando el campo con prefijo que ya existe (`Campo` con `prefijo`). Lo
único nuevo a la vista es que el «+51» desaparece mientras el número empieza por «+», y que
el campo del colaborador también muestra «+51» delante.

## Criterios de aceptación
- AC-01: En el registro, cuando el candidato escribe «987654321» tecla a tecla, el campo
  muestra «987 654 321» y una décima cifra no se escribe.
- AC-02: En cualquiera de los tres campos del portal, al teclear letras, «-», «.», «(» o «)»,
  no se escribe nada y el valor no cambia.
- AC-03: Al pegar «+51 987 654 321», «51987654321» o «987-654-321» en el campo vacío, el
  campo muestra «987 654 321» y el formulario se envía sin error.
- AC-04: Al pegar un texto que no es un celular (por ejemplo «hola» o «12345678901234567»),
  el campo no cambia y aparece «Escribe un celular de 9 cifras que empiece por 9, o el
  número completo con su prefijo».
- AC-05: Al escribir «+34 612 345 678» en el registro, el «+51» desaparece, se crea la
  cuenta y se guarda «+34612345678». Si se borra el «+», vuelve a aparecer el «+51».
- AC-06: Al enviar el registro con «987 65» o con «812 345 678», no se crea la cuenta y
  aparece el mensaje de la regla junto al campo, sin perder los demás datos.
- AC-07: En «Mi cuenta», con un celular guardado «+51987654321», al pulsar «Cambiar» el
  campo muestra «987 654 321» con la máscara activa. Al guardar «912 345 678», la pantalla
  muestra «+51 912 345 678».
- AC-08: En la tarjeta de WhatsApp de una cuenta sin celular, el campo se comporta como en
  AC-01 a AC-06.
- AC-09: En el alta de un colaborador desde el panel, el «Celular · opcional» aplica la misma
  máscara. Vacío se guarda sin celular; «987 654 321» se guarda como «+51987654321» y la
  ficha lo muestra «+51 987 654 321».
- AC-10: Si un colaborador tiene guardado «01-4567890», RR.HH. edita otro dato de su ficha
  sin tocar el celular y guarda, el cambio se guarda y el celular sigue siendo «01-4567890».
  Si en cambio lo modifica, se aplica la máscara y no puede guardar un valor que no cumpla la
  regla.
- AC-11: En el alta desde un contratado cuya cuenta tiene «+51987654321», el celular aparece
  ya rellenado como «987 654 321», aunque el CV diga otra cosa.
- AC-12: Un Excel con una fila cuyo celular es «12345» se rechaza entero. El error indica esa
  fila, la columna Celular, el valor «12345» y el mensaje de la regla. El mismo archivo con
  «987 654 321» se carga y guarda «+51987654321».
- AC-13: Una petición directa que crea o edita un colaborador con celular «abc» recibe un 400
  con el mensaje de la regla y no guarda nada.

## Casos límite y errores
- Datos vacíos o inválidos:
  - En el portal, un campo vacío sigue dando «Escribe tu celular.».
  - En el colaborador, vacío es válido.
  - Un «+» solo, o un «+51» seguido de un fijo, no pasa al enviar.
- Usuario sin permisos: no cambia nada. Cada formulario conserva el acceso que ya tiene.
- Recarga, reintento o doble envío: la máscara no guarda estado propio. Si se recarga la
  página, el campo vuelve al valor guardado o al vacío. El doble envío se maneja igual que
  hoy.
- Otros casos relevantes:
  - Si la persona corrige en medio del número (borra con retroceso justo sobre un espacio o
    inserta una cifra entre otras), el cursor no salta al final y no se borran cifras de
    más.
  - En el móvil se abre el teclado de teléfono, que incluye el «+».
  - El lector de pantalla sigue anunciando el «+51» como parte del campo.

## Datos y compatibilidad
- Información que debe guardarse:
  - El celular de la cuenta, igual que hoy (`persona.telefono`, formato internacional).
  - El del colaborador (`colaborador.celular`), a partir de ahora también en formato
    internacional.
- Comportamiento para registros existentes: no se cambia ningún celular guardado. Los del
  colaborador que no cumplan la regla se siguen mostrando tal cual y solo se comprueban cuando
  alguien los modifica (pasos 7 y 9).
- Restricciones de compatibilidad:
  - No hace falta migración: la columna del colaborador es `text`, la API la limita a 30
    caracteres y el formato internacional ocupa como mucho 16.
  - La API del portal sigue aceptando los separadores que acepta hoy, para no romper a
    ningún cliente.
  - `Celular.java` y `celular.ts` deben seguir dando el mismo resultado para la misma
    entrada.

## Verificación
- Actor/rol y datos de prueba necesarios:
  - Una persona sin cuenta, para el registro.
  - Un candidato con celular peruano guardado, para «Mi cuenta».
  - Una cuenta antigua sin celular, para la tarjeta de WhatsApp.
  - Un usuario del panel con permiso sobre colaboradores.
  - Un contratado cuya cuenta tenga celular, para el alta desde un contratado.
  - Un colaborador con un celular antiguo que no cumpla la regla.
  - Dos Excel de carga: uno con un celular inválido y otro con celulares válidos.
- Recorridos que deben quedar como E2E repetibles en headless:
  - Registro: escribir, pegar con +51, número extranjero y número incompleto.
  - «Mi cuenta»: cambiar el celular.
  - Panel: alta de un colaborador con celular.
  - Panel: editar un colaborador con un celular antiguo sin tocarlo.
  - Panel: carga por Excel con un celular inválido.
  - Además, pruebas unitarias de la máscara y de que `celular.ts` y `Celular.java` dan lo
    mismo para la misma lista de entradas.
- Riesgos que QA debe explorar con Chrome visible:
  - El cursor al corregir en medio del número.
  - Pegar con el atajo de teclado y con el menú contextual.
  - El autocompletado del navegador.
  - El paso de peruano a extranjero y vuelta.
  - El teclado del móvil a 390×844.
- Comparación visual de QA: no aplica.
- Resultado que quiero comprobar personalmente: al crear una cuenta, el celular se escribe
  solo con la forma «999 999 999» y no deja poner letras ni una cifra de más. En la ficha
  del colaborador pasa lo mismo.

## Dependencias y dudas
- Requiere: nada nuevo. La regla y el campo con prefijo ya existen en los dos repositorios.
- Decisiones pendientes: ninguna.
  - Decidido el 20/10/2026: máscara peruana con salida por «+» para los de otro país, sin
    selector de país.
  - Decidido el 20/10/2026: se aplica al portal del candidato, a la ficha del colaborador y
    a la carga por Excel.
  - Decidido al redactar (se puede tachar): el alta desde un contratado toma el celular de su
    cuenta antes que el del CV.
