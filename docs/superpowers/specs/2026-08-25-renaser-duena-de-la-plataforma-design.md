# Pieza F · Renaser como dueña de la plataforma

Fecha: 2026-08-25 · Estado: aprobado en conversación

Sexta y última pieza del multiempresa. Define qué puede hacer Renaser sobre las empresas
cliente — y, sobre todo, qué **no**.

## 1 · La frontera: el continente, no el contenido

Renaser **puede**: dar de alta y suspender empresas, invitar a su primer administrador,
poner y subir topes de IA, ver consumo (pieza E), y encender o apagar la personalización de
instrumentos si una empresa lo pide (pieza A).

Renaser **no puede** ver candidatos, currículums, notas, alertas ni decisiones de ninguna
empresa. **No es una promesa de buena conducta: es el aislamiento de la pieza B aplicado
también a Renaser.** Su panel de plataforma llega a la ficha de la empresa; los endpoints
de candidatos siguen exigiendo pertenecer a esa organización, y Renaser no pertenece. Si
Renaser pudiera mirar dentro, ninguna empresa que compita con ella confiaría sus datos a la
plataforma.

**La única grieta consciente**: el borrado de la ley 29733 lo ejecuta la plataforma (ya
implementado así en la pieza B), porque el candidato es una cuenta de plataforma y alguien
tiene que poder borrarlo de todas las empresas a la vez. Ese endpoint toca datos ajenos por
diseño, queda auditado, y es el único.

Renaser tiene además su propio panel de reclutamiento —contrata para sí misma como
cualquier empresa—, pero ahí entra como **la empresa Renaser**, con sus datos, no como
dueña.

## 2 · Suspender una empresa

Una empresa suspendida queda **congelada, no borrada**:

- Su equipo **no puede entrar**. Hoy ese hueco existe —`organizacion.es_activa` no se
  comprueba al iniciar sesión, señalado por el QA de la fase 1— y lo cierra esta pieza.
- Sus vacantes **salen del tablón público**: nadie debe postular a una empresa que no puede
  responder.
- Los candidatos que ya estaban dentro **conservan acceso y datos**. No pagan ellos el
  problema comercial de la empresa.
- Al reactivar, todo vuelve tal cual.

**Borrar una empresa no existe**, deliberadamente: se llevaría por delante procesos de
personas reales. Si algún día hace falta, será su propio diseño.

## 3 · Todo queda escrito

Cada acción de plataforma —alta, suspensión, cambio de tope, personalización forzada— entra
en la auditoría con quién, cuándo y por qué. Es lo que permite decirle a una empresa «esto
pasó, esto hicimos y esta fue la razón», y lo que protege a Renaser de la acusación de
haber mirado donde no debía.

## Consecuencias para la implementación (resumen)

- Comprobar `organizacion.es_activa` en el login de panel (el hueco abierto) y filtrar las
  vacantes de empresas inactivas del tablón público.
- Endpoints de plataforma para suspender/reactivar y para ajustar topes, todos auditados.
- Ninguna vía nueva a los datos de las empresas: la ausencia de esos endpoints **es** el
  diseño.

---

## El diseño multiempresa, completo

| Pieza | Qué decide | Estado |
|---|---|---|
| A · Instrumental por empresa | Qué es de cada empresa y qué de Renaser | Implementada |
| B · Aislamiento | Ninguna empresa ve nada de otra | Implementada |
| C · Identidad y alta | Cómo nace una empresa y cómo entra su gente | Implementada |
| D · El candidato ante varias empresas | Consentimiento, qué ve cada quien, el currículum | Diseñada |
| E · El coste de la IA | Medir, atribuir y frenar | Diseñada |
| F · Renaser como dueña | El continente, no el contenido | Diseñada |
