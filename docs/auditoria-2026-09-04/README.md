**Evidencia reproducible de la auditoría del 4 de septiembre de 2026**

`AuditoriaRnf.java` ejecuta siete comprobaciones contra clases reales compiladas del proyecto. Mockito sustituye el broker, repositorios y proveedor de IA. No usa datos reales ni conexiones externas. Las aserciones confirman comportamientos observados; cuando se corrija un defecto, su aserción deberá dejar de cumplirse.

Desde la raíz del proyecto, con Java 25, las clases compiladas y los reportes Surefire disponibles:

```sh
python3 docs/auditoria-2026-09-04/reproducir.py
```

El ejecutor toma el classpath de los reportes existentes y compila la comprobación en un directorio temporal. No modifica el código productivo.

La suite unitaria de la auditoría se ejecutó con:

```sh
./mvnw -B -o -DargLine='-javaagent:/home/n4nd0/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar' test
```

La ruta de Mockito corresponde al entorno auditado; en otra máquina debe apuntar al mismo artefacto de su repositorio Maven. El agente explícito evitó el fallo de autoadjunción en el entorno restringido. Esta ejecución no se usó para calcular cobertura.

Las dos suites de integración seleccionadas usaron Docker y contenedores efímeros:

```sh
./mvnw -B -o -DargLine='-javaagent:/home/n4nd0/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar' -Dspring.config.import= -Dgoogle.gemini.api-key=clave-falsa-auditoria -Drenaser.correo.transporte=log -Dit.test=SeguridadAgentesIaIT,FlujoDosEmpresasIT failsafe:integration-test failsafe:verify
```

`resultados.txt` conserva los totales y las siete observaciones. Los logs completos de esta sesión quedaron en `/tmp/renaser-auditoria-unitarias-agente-20260904.log`, `/tmp/renaser-auditoria-integracion-20260904.log` y `/tmp/renaser-audit-reproducciones.log`. Son temporales; el resumen de resultados es el artefacto permanente entregado.
