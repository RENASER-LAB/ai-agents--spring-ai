# Editar, archivar y eliminar vacantes · índice de entregas

Estado: TERMINADO

Estado previo (referencia histórica): dividida en tres specs el 19/09/2026 por autorización del usuario. **Este archivo es un índice; no ejecutarlo con `/harness`.** Los requisitos ejecutables están en las tres entregas enlazadas.

Las tres entregas están integradas. Este índice se conserva para consulta.

## Orden de trabajo
| Orden | Spec | Depende de | Criterios de la spec original |
|---|---|---|---|
| 1 | [Editar vacantes y unificar avisos](01-editar-vacantes-y-avisos.md) | Base actual integrada | AC-01 a AC-10 y AC-19 para editar |
| 2 | [Edición en modal y archivo](02-archivar-desarchivar-vacantes.md) | PR de 01 integrados en ambos repos | AC-11 a AC-14, AC-19 y AC-22 a AC-25 (modal y navegación) |
| 3 | [Eliminar mediante borrado lógico](03-eliminar-vacantes.md) | PR de 02 integrados en ambos repos | AC-15 a AC-18, AC-20, AC-21 y AC-19 para eliminar |

Se mantienen los IDs originales para trazabilidad; AC-19 se concreta por permiso en cada entrega. El rechazo de edición de archivadas se implementa en 02 y el de eliminadas en 03; no se anticipan columnas ni acciones futuras en 01. No hay criterios originales descartados.

## Secuencia original (histórica; no volver a ejecutar)
La entrega 01 ya llegó a PR. Su documento se conserva como referencia de lo ejecutado: no volver a lanzarlo para cambiar la presentación del formulario. El ajuste a modal se implementa en 02 junto con Archivadas; no se crea una cuarta spec.

Antes de lanzar 02, integra los PR de 01, termina el cierre/limpieza del trabajo anterior y actualiza las bases de ambos repositorios, conservando cambios locales. En una nueva sesión de Claude Code en el backend:

```text
/harness specs/02-archivar-desarchivar-vacantes.md
```

Tras aprobar, publicar e integrar 02 y actualizar ambas bases, en otra sesión:

```text
/harness specs/03-eliminar-vacantes.md
```

Son entregas dependientes: no ejecutarlas en paralelo ni juntas. Cada una usa sus propios worktrees, puertos y clon; la siguiente no depende del clon anterior. El harness aplica las migraciones pendientes sobre el snapshot autorizado. No repitas setup salvo que el controlador lo requiera.

## Ajustes del 21/09/2026
- Edición con todos los campos vigentes dentro de un modal, con guardar/cancelar/cerrar, sin desplazar la tabla.
- Icono de archivo en la zona de acciones y modal de confirmación, conservando la regla de solo cerradas sin nadie en carrera.
- «Archivadas (N)» junto a «Escribir una solicitud» y «Crear vacante» abre la vista de archivadas, con consulta en lectura del proceso; la lista habitual de `/admin` las excluye.
- Confirmado por el usuario el 21/09/2026: eliminar no exige archivar antes. La papelera va en la misma zona de acciones que el lápiz y Archivar, según estados y permisos, y siempre pide confirmación y motivo. Archivar conserva consulta/trazabilidad; eliminar retira de las pantallas mediante borrado lógico.

## Decisiones y aclaraciones
- Se mantienen los acuerdos de la spec original: borrado lógico, cierre humano con aviso al eliminar, archivo reversible de cerradas, avisos visibles solo por campana, campos internos sin aviso y permiso de eliminación para Talento y Dirección.
- Confirmado por el usuario el 19/09/2026: archivo solo sin nadie en carrera, motivo y liberación de solicitud al eliminar, y permiso `cerrar_vacante` para archivar/desarchivar. No quedan decisiones de negocio pendientes de la spec original.
- QA tiene que acceder al panel y al portal con cuentas de prueba del clon; la revisión humana es adicional.
- Si fallan avisos al eliminar, se informan cierres y avisos publicados realmente por separado. Se conserva el comportamiento de no revertir la eliminación por ese fallo.
- La comprobación de coherencia ante reintentos/operaciones simultáneas pertenece a los tests pertinentes; no requiere repetir todo el conjunto de E2E ni crear infraestructura genérica.

La primera entrega sigue incluyendo cambios coordinados y notificaciones; prueba el flujo completo, pero no equivale a un ajuste trivial de formulario. Cada entrega actualiza únicamente la documentación afectada. El harness y sus configuraciones quedan fuera de los cambios de producto.
