# Comprobación · ¿sobra algo en el modelo?

Sistema de selección de personal — Renaser Consulting
15 de agosto de 2026

## Por qué se hizo esto

Salió la pregunta de si el sistema no se habrá complicado de más, y si empezar la parte
técnica de cero —quedándonos solo con la idea del proyecto— daría algo más simple.

En vez de opinar, se hizo una prueba: **escribir el sistema entero en dos páginas sin una sola
palabra técnica**, y después comprobar que cada una de las tablas se pueda amarrar a una
frase de esas dos páginas. Lo que no se amarre, sobra.

Este documento tiene las dos páginas, el rastreo tabla por tabla, y lo que salió.

---

# Primera parte · El sistema en dos páginas

Sin nombres de tablas, sin estados, sin porcentajes, sin nada de lo ya definido. Solo qué pasa.

**1.** Alguien de Renaser nota que hace falta contratar. Escribe por qué hace falta y qué
resultados concretos tiene que dar la persona nueva. A veces no lo nota una persona: el sistema
avisa, porque ve que un equipo está sobrecargado o que alguien se va. Eso se revisa y se
convierte en una convocatoria.

**2.** La convocatoria dice qué puesto es, cuánto dura cada parte del proceso, y qué cosas son
de verdad indispensables: una licencia obligatoria por ley, estar en cierto sitio. Dice también
qué fallos no los compensa ningún promedio alto. Y dice quién vigila que la prisa por cubrir el
puesto no baje el listón.

**3.** La gente postula por el portal. Sube su currículum y acepta cómo se van a usar sus datos.
Aceptar que le evalúen y aceptar que le llamen para otras oportunidades son dos permisos
distintos, y se piden por separado.

**4.** El sistema comprueba **solo** lo indispensable, y guarda qué regla exacta aplicó. Nada más
detiene a nadie por su cuenta. El currículum no descarta.

**5.** El candidato ve la ficha real del puesto —lo bueno y lo difícil— y responde una sola vez.
Ahí va su currículum, unas preguntas iguales para todos y otras propias del puesto. Si hace poco
ya respondió parte de esto para un puesto parecido, no lo repite.

**6.** Una inteligencia artificial lee el currículum y las respuestas y arma un retrato de la
persona: en qué es fuerte, qué riesgos tiene, y dónde lo que dijo en un sitio no cuadra con lo
que dijo en otro. Si una respuesta es superficial, vuelve a preguntar. **No decide**: ordena a
todos en cuatro grupos según cuánta atención merecen, y explica por qué puso a cada uno donde lo
puso.

**7.** Una persona del equipo revisa, empezando por los grupos prometedores, y confirma quién
sigue. A los que no destacaron los puede confirmar de golpe, y aun así cada uno conserva su razón
escrita. Quién puede hacer qué depende de su cargo, y algunos solo ven lo suyo.

**8.** Quien sigue hace una prueba del puesto con reloj. A mitad cambia algo —una restricción
nueva, un dato que aparece— para ver cómo reacciona. Entrega una o varias cosas, cada una con su
regla, y explica cómo lo hizo.

**9.** Quien sigue va a una simulación de trabajo en grupo, de un par de horas. Falta información
a propósito: si la pide o no, cuenta. Solo se anota lo que se puede ver, nunca lo que se supone
que pensó. Después una persona lo califica y conversa con él, con preguntas escritas para ese
candidato en concreto.

**10.** Quien sigue trabaja unos días de verdad, o hace una versión larga de la simulación si no
se puede lo primero. Lo que el sistema de gestión de Renaser ya sabe —tareas, tiempos, bloqueos,
retrabajo— se apunta solo. El responsable del área completa lo que no se ve en los datos.

**11.** Decide el responsable del área, o Dirección. Puede decir cinco cosas: se contrata; hay
una duda concreta y se pide una prueba más; no; falta información y hay que buscarla en vez de
suponer que falla; o no es para esta vacante pero interesa para otra.

**12.** Todo lo que se decidió queda guardado: quién, cuándo, por qué, y con qué versión de las
reglas se calculó. También queda todo lo que se le dijo al candidato, con el texto exacto. Si
alguien reclama meses después, se puede reconstruir su caso entero.

**13.** Pasados unos meses se mira si la contratación funcionó. Eso dice si el proceso está
acertando o no.

**14.** Cualquiera puede pedir que borren sus datos. Se borran, pero el registro de las
decisiones se queda, sin nada que identifique a la persona.

**15.** Renaser quiere usar esto también con sus clientes de consultoría. Los datos de cada
empresa van separados, y una empresa no ve nada de otra.

**16.** *(Se decidió dejarlo para después.)* Hay gente que interesa aunque hoy no haya ninguna
vacante abierta, y cuando se abre una habría que acordarse de ella.

---

# Segunda parte · El rastreo

Las 93 tablas contra las dieciséis frases. La columna «frase» dice a cuál se amarra.

### Organización · 1 tabla

| Tabla | Frase | Para qué |
|---|:--:|---|
| `organizacion` | 15 | Cada empresa por separado |

### Personas, acceso y permisos · 7 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `persona` | 1, 3 | Quién es alguien, sea del equipo o candidato |
| `usuario` | 3 | Cómo entra |
| `area` | 1 | El departamento que contrata |
| `rol` | 7 | Su cargo |
| `permiso` | 7 | Cada acción que se concede o no |
| `usuario_rol` | 7 | Alguien puede tener varios cargos |
| `rol_permiso` | 7 | Y el «solo lo suyo» |

### Consentimiento y borrado · 4 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `texto_consentimiento` | 3 | El texto exacto que aceptó |
| `consentimiento` | 3 | Los dos permisos, por separado |
| `politica_conservacion` | 14 | Cuánto se guardan los datos |
| `solicitud_borrado` | 14 | Pedirlo y ejecutarlo son dos cosas |

### Solicitud de Talento · 3 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `solicitud_talento` | 1 | Por qué hace falta contratar |
| `resultado_esperado` | 1 | Qué resultados tiene que dar |
| `evidencia_necesidad` | 1 | Qué vio el sistema para avisar |

### Vacantes · 8 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `nivel_puesto` | 2 | Los tres niveles |
| `familia` | 2 | Las siete familias de trabajo |
| `familia_afin` | 5 | Cuáles se parecen lo bastante para no repetir |
| `puesto` | 2 | El catálogo de puestos |
| `vacante` | 2 | La convocatoria |
| `requisito_objetivo` | 4 | Lo único indispensable |
| `barrera_critica` | 2 | Lo que ningún promedio compensa |
| `evaluador_estandar` | 2 | Quién vigila el listón |

### Radar de Talento · 3 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `prospecto` | **16** | Interesa sin vacante |
| `prospecto_familia` | **16** | Para qué encaja |
| `contacto_prospecto` | **16** | Cada vez que se habló |

### Postulación y su historia · 3 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `estado_postulacion` | 4–11 | Dónde va cada uno |
| `postulacion` | 3 | Una persona en una vacante |
| `transicion_estado` | 12 | Cada movimiento, para siempre |

### Criterios y notas · 2 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `criterio` | 8, 9, 10 | Cualquier cosa que se puntúa |
| `nota_criterio` | 8, 9, 10 | Su puntaje, y por qué |

### El currículum · 3 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `cv` | 3 | Lo que subió |
| `enlace_cv` | 3 | Portafolio, repositorio |
| `afirmacion_cv` | 6 | Lo que dice, para cruzarlo con lo que hizo |

### Banco de preguntas · 7 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `dimension` | 6 | Las cosas que se miden |
| `version_banco` | 12 | Con qué versión respondió |
| `pregunta` | 5 | Las preguntas |
| `opcion` | 5 | Sus opciones |
| `opcion_dimension` | 6 | Cuánto suma cada opción |
| `pregunta_dimension` | 6 | Lo mismo, para las abiertas |
| `par_consistencia` | 6 | Dos que deberían cuadrar |

### Plantilla de evaluación · 2 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `plantilla_evaluacion` | 5 | Qué preguntas le tocan a este puesto |
| `cuota_plantilla_evaluacion` | 5 | Cuántas de cada tipo |

### Evaluación · 8 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `evaluacion` | 5 | Lo que respondió |
| `orden_pregunta` | 12 | En qué orden lo vio, para poder reconstruirlo |
| `respuesta` | 5 | Cada respuesta |
| `nota_respuesta` | 6 | Su puntaje, y de qué parte salió |
| `repregunta` | 6 | Cuando la respuesta fue superficial |
| `respuesta_repregunta` | 6 | Lo que contestó entonces |
| `resultado_alineacion` | 6 | Si lo que dice cuadra con lo que hace |
| `alerta` | 6 | Contradicciones y respuestas demasiado perfectas |

### Perfil de Talento · 3 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `perfil_talento` | 6 | El retrato |
| `hallazgo_perfil` | 6 | Cada fortaleza y cada riesgo |
| `sugerencia_puesto` | 11 | «Encajaría mejor en otro sitio» |

### Prueba del puesto · 9 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `plantilla_prueba` | 8 | La prueba de un puesto |
| `version_plantilla_prueba` | 8, 12 | Congelada, para poder repetir el cálculo |
| `variante_cambio` | 8 | El cambio de mitad de prueba |
| `pregunta_prueba` | 8 | Lo que se le pregunta al entregar |
| `pregunta_version_plantilla` | 8 | Cuáles eligió esta prueba |
| `entregable_requerido` | 8 | Qué cosas hay que entregar, cada una con su regla |
| `intento_prueba` | 8 | Cuándo empezó y cuándo vence el reloj |
| `entregable` | 8 | Lo que entregó |
| `respuesta_prueba` | 8 | Cómo explicó lo que hizo |

### Simulación de trabajo · 7 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `sesion_simulacion` | 9 | Una fecha con cupo |
| `sesion_vacante` | 9 | Para qué vacantes sirve |
| `inscripcion_sesion` | 9 | Eligió esa fecha, y si asistió |
| `tramo_simulacion` | 9 | Cómo se reparten las dos horas |
| `informacion_critica` | 9 | Lo que falta a propósito |
| `marca_tiempo_simulacion` | 9 | Solo lo que se ve |
| `pregunta_generada` | 9 | Las preguntas de la conversación final |

### Validación práctica y decisión · 7 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `validacion` | 10 | El periodo de trabajo |
| `etapa` | 11 | Las cinco partes del proceso |
| `nota_etapa` | 11 | La nota de cada parte |
| `decision` | 11 | Las cinco respuestas posibles |
| `barrera_detectada` | 11 | Cuando aparece de verdad |
| `opinion_evaluador_estandar` | 2, 11 | Su revisión escrita |
| `evidencia_adicional` | 11 | La prueba extra cuando hay una duda |

### Configuración · 8 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `version_pesos` | 12 | Con qué reglas se calculó |
| `peso_etapa` | 11 | Cuánto vale cada parte |
| `peso_componente_perfil` | 11 | Cómo se reparte la primera |
| `peso_dimension` | 11 | Cuánto pesa cada cosa medida |
| `peso_criterio` | 11 | Cuánto vale cada criterio |
| `parametro` | 2, 11 | Los valores que Renaser cambia sin programar |
| `plantilla_correo` | 12 | El texto exacto que se manda |
| `instruccion_ia` | 12 | Lo que se le pidió a la máquina, versionado |

### Agentes de inteligencia artificial · 3 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `agente` | 6 | Cuál de los nueve fue |
| `trabajo_ia` | 6 | El encargo, en segundo plano |
| `ejecucion_ia` | 12 | Cada intento, para poder mirarlo cuando reclaman |

### Auditoría, archivos y desempeño · 5 tablas

| Tabla | Frase | Para qué |
|---|:--:|---|
| `auditoria` | 12 | Toda acción que cambia una decisión |
| `archivo` | 3, 8 | Dónde está cada archivo |
| `correo_enviado` | 12 | Qué se le dijo, y cuándo |
| `seguimiento_desempeno` | 13 | El corte a los meses |
| `metrica_desempeno` | 13 | Cada medida de ese corte |

---

# Tercera parte · Lo que salió

## 87 del cliente · 3 nuestras · 3 aplazadas

| | Tablas |
|---|:--:|
| Se amarran a algo que Renaser pidió **por escrito** | **87** |
| Se amarran a una frase, pero la idea es **nuestra** | 3 |
| Se amarran a la frase 16, que ya se decidió **dejar para después** | 3 |

Las tres aplazadas son las del **Radar de Talento**, y no es que sobren: nacen vacías y están
previstas para que añadir el Radar no obligue a mover nada.

Las tres nuestras están nombradas al final, en «Hasta dónde vale esta comprobación». Ninguna
inventa una función: las tres guardan un dato de más para poder responder después.

**Conclusión sobre la pregunta que originó esto: rehacer la parte técnica de cero no simplifica.**
No hay grasa que quitar. Dos tercios del modelo son la lista de funciones de Renaser traducida a
tablas, y un rediseño limpio las volvería a inventar con otros nombres, perdiendo de paso la
trazabilidad hacia lo que el cliente pidió.

## Lo que sí simplifica, y una corrección

En el mensaje anterior dije que la primera migración podría arrancar con unas 35 tablas. **Eso
estaba mal**: era un cálculo a ojo. El rastreo da el número real, y es más interesante.

| Si el primer hito es… | Tablas | Qué se deja fuera |
|---|:--:|---|
| Recibir postulaciones y moverlas a mano (con la Solicitud de Talento y los pesos) | **34** | Todo lo que puntúa |
| Eso, más el Perfil Integral calificado por IA | **62** | Prueba, simulación, validación, desempeño, Radar |
| Todo | 93 | — |

**El Perfil Integral solo, duplica el esquema.** Pasa de 33 a 67 porque arrastra el banco de
preguntas entero (7 tablas), la evaluación (8), los pesos (8) y los agentes con su registro (3).

Así que la decisión de por dónde empezar no es «cuántas tablas», es una sola pregunta: **¿el
primer hito lleva el Perfil Integral calificado, o solo la entrada?** Todo lo demás se ordena
solo a partir de ahí.

## Dos cosas que se miraron y se descartaron

**Convertir los catálogos cerrados en columnas con restricción.** Serían cinco: los tres niveles,
las cinco etapas, los dieciocho estados, las siete familias y las veintidós dimensiones. Solo
merecería la pena en dos de ellos, porque los estados guardan etapa y momento como columnas —que
es lo que permite calcular el siguiente— y familias y dimensiones las referencian muchas tablas y
el cliente puede añadir más. Ahorro real: **2 tablas**, a cambio de perder la integridad que da
la base. No compensa.

**Juntar `nota_respuesta` con `nota_criterio`.** Repiten las mismas seis columnas de procedencia
—explicación, confianza, qué ejecución la produjo, quién la ajustó y por qué—, pero puntúan cosas
de distinto tamaño: una, cada respuesta; la otra, cada criterio. Juntarlas obliga a una referencia
que apunta a dos sitios distintos, que es peor. Lo que sí conviene es **escribir esas seis
columnas una sola vez en el código**, no en la base.

---

# Hasta dónde vale esta comprobación

Las dos páginas de la primera parte las escribimos nosotros, y después del modelo. Un rastreo
contra un texto propio no puede fallar del todo: si nos acordamos de una tabla, le escribimos su
frase.

Lo que sostiene el resultado no es el texto, es que **cada frase sale del documento que mandó
Renaser**, no de nuestra cabeza. Hay tres donde el amarre es nuestro y conviene decirlo:

| Tabla | Por qué es nuestra |
|---|---|
| `orden_pregunta` | Renaser pide que el orden se mezcle; guardar el orden que le tocó a cada uno es idea nuestra, para poder reconstruir una reclamación |
| `tramo_simulacion` | Renaser dice que la simulación dura hasta dos horas; que el sistema conozca cómo se reparten esos minutos lo propusimos nosotros. **Conviene preguntar si al facilitador le sirve o la lleva él por su cuenta** |
| `evidencia_necesidad` | Renaser pide que el sistema anticipe la necesidad; guardar qué dato concreto la disparó es idea nuestra |

---

# Una cosa que quedó de paso

La primera parte de este documento **se sostiene sola**. Es el sistema entero explicado sin nada
técnico, en dos páginas, y sirve para enseñárselo a alguien que entra nuevo o al propio cliente
sin abrir ningún otro documento.

Si interesa, puede salir de aquí y convertirse en un documento propio al principio de la carpeta.
Hoy no existe: quien quiere entender el sistema tiene que empezar por los requisitos funcionales,
que son 154 y no están escritos para leerse del tirón.

---

# Documentos relacionados

| Documento | Qué contiene |
|---|---|
| [Requisitos funcionales](../01-REQUISITOS-FUNCIONALES.md) | Qué hace el sistema, etapa por etapa |
| [Modelo de datos](../05-MODELO-DE-DATOS.md) | Las 93 tablas por área y por qué existe cada una |
| [Diccionario de datos](../07-DICCIONARIO-DE-DATOS.md) | Las 93 tablas columna por columna |
| [Cambios del documento nuevo](CAMBIOS-DEL-DOCUMENTO-NUEVO.md) | Qué cambió el cliente y qué se decidió |
