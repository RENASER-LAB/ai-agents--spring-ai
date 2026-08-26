# Pieza C · Identidad y alta de empresas

Fecha: 2026-08-25 · Estado: aprobado en conversación

Tercera pieza del multiempresa. La A repartió los datos, la B garantiza el aislamiento;
esta define cómo nace una empresa y cómo entra su gente.

## Decisión de fondo: RENASER OS no se usa hasta nuevo aviso

El equipo de Renaser **también** entra con correo y contraseña, como cualquier otra
empresa. Renaser es, a efectos de entrada, la primera empresa de su propia plataforma. El
punto de conexión con RENASER OS queda en el código como integración futura, dormido: el
día que se retome será añadir un proveedor de identidad, no rehacer el login.

## 1 · El alta, pensada para crecer

Siempre el mismo camino de tres pasos; lo único que cambiará mañana es quién lo empieza:

1. **Nace la solicitud de empresa** — hoy la crea Renaser desde su panel (nombre, código,
   correo del futuro administrador). Mañana, un formulario público crearía la misma
   solicitud.
2. **Se aprueba** — hoy automático (la creó Renaser). Mañana, bandeja de aprobación.
3. **Sale la invitación** — correo al futuro administrador con un enlace de un solo uso
   que caduca en días. Al abrirlo pone su nombre y contraseña y la empresa queda activa
   con su primer administrador dentro.

El día uno de la empresa activa es el de la pieza A: método compartido, textos legales en
borrador, correos activos, y su lista de tareas antes de publicar la primera vacante.

## 2 · Dos logins, cuentas por invitación

- **Dos páginas de login separadas**: el portal del candidato (se registra solo, como
  hoy) y el panel de empresas (administradores, dirección, reclutadores — de Renaser y de
  todas). Van a sitios distintos, con exigencias distintas; el sistema ya separa los dos
  tipos por dentro.
- **El panel no tiene registro público.** Las cuentas nacen solo por invitación: Renaser
  invita al administrador de la empresa; el administrador invita a su gente y le asigna
  roles. La pantalla de «acepta tu invitación» es donde el invitado pone nombre y
  contraseña.
- **Misma tubería interna para ambos mundos**: cifrado de contraseñas, bloqueo por
  intentos y recuperación por correo se construyen una vez. Un mismo correo puede existir
  en los dos mundos sin chocar (reclutador en una empresa y candidato en otra).
- **Más exigencia en el panel**: ve datos de muchas personas → contraseña mínima más
  larga (12); verificación en dos pasos anotada como mejora futura.
- **El primer administrador de Renaser nace igual que el de cualquier empresa**: por
  invitación generada en el despliegue (correo por parámetro), nunca contraseñas en el
  código ni en migraciones.
- **El login de desarrollo queda solo en desarrollo**: apagado por defecto; encendido
  explícitamente en local y en las pruebas.

## Fuera de esta pieza

Suspensión y supervisión de empresas (pieza F), cobro (pieza E), y el candidato ante
varias empresas (pieza D) — aquí solo se decide que el candidato es **de la plataforma**:
una cuenta, postula a cualquier empresa, y su postulación pertenece a la empresa de la
vacante.
