package com.renaser.ai.ai_engine.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Los contratos del portal del candidato, juntos: son pequeños y se leen mejor así.
public final class DtosPortal {

    private DtosPortal() {}

    // ---------- lo que entra ----------

    // ciudadUbigeo es obligatoria y solo se pide aquí: a quien ya tiene cuenta no se le
    // vuelve a preguntar nunca. Es el único momento en que sale gratis —el formulario ya
    // está abierto— y sin ella el panel no puede filtrar la tanda por dónde vive nadie.
    //
    // aceptaPlataforma se llamaba aceptaProceso hasta la V54, y el nombre venía del tipo
    // de texto que firmaba: el PROCESO de la plataforma, que habla de «esta vacante»
    // cuando en el registro todavía no hay ninguna. Ahora firma el texto PLATAFORMA —la
    // cuenta, el perfil, la IA, los proveedores— y el de la vacante se firma al postular.
    public record CrearCuenta(
            @NotBlank String nombre,
            @NotBlank String apellidos,
            @NotBlank @Email String correo,
            @NotBlank @Size(min = 8, message = "La contraseña necesita al menos 8 caracteres") String contrasena,
            @NotBlank(message = "Hay que decir dónde vives") String ciudadUbigeo,
            @NotNull Boolean aceptaPlataforma,
            Boolean aceptaFuturosContactos) {}

    public record Login(@NotBlank String correo, @NotBlank String contrasena) {}

    public record PedirBorrado(String motivo) {}

    // ---------- lo que sale ----------

    // nombreEmpresa existe porque el tablón mezcla vacantes de todas las empresas: sin
    // él, el candidato no sabría a quién le está mandando su currículum.
    public record VacantePublica(Long id, String titulo, String nombreEmpresa, String descripcion,
                                 String proposito, String responsabilidades, String requisitos,
                                 String modalidad, String horario, String ubicacion,
                                 RemuneracionPublica remuneracion,
                                 List<RequisitoPublico> requisitosObjetivos) {}

    /**
     * Lo que esta vacante paga, tal como el candidato puede verlo (V55).
     *
     * <p>{@code tipo} es {@code OCULTA}, {@code FIJA} o {@code RANGO}. Con {@code OCULTA} los
     * montos van vacíos y {@code texto} dice «No la publica»: el campo viaja igualmente
     * porque el portal tiene que decirlo en voz alta. Un hueco donde debería estar el sueldo
     * se lee como un fallo de carga, y además es justo el dato que explica por qué el
     * formulario de postular no le va a exigir declarar el suyo.
     *
     * <p>{@code texto} llega ya escrito —«S/ 3 500 a 4 200»— para que la frase del dinero se
     * arme una sola vez, en el servidor. El portal y el correo dicen exactamente lo mismo, y
     * los montos sueltos siguen ahí para quien quiera pintarlos de otra forma.
     *
     * <p>{@code actualizadaEn} vacío significa que nunca se tocó desde que se publicó. Con
     * fecha, el portal pinta «actualizado el …» sobre el monto: quien postuló con otro número
     * delante merece enterarse de que cambió, y no descubrirlo en la negociación.
     */
    public record RemuneracionPublica(String tipo, BigDecimal min, BigDecimal max,
                                      String moneda, String texto, Instant actualizadaEn) {

        public static final RemuneracionPublica OCULTA =
                new RemuneracionPublica("OCULTA", null, null, null, "No la publica", null);

        /** ¿Obliga a quien postula a declarar la suya? El trato de la V55, en una línea. */
        public boolean exigePretension() {
            return !"OCULTA".equals(tipo);
        }
    }

    public record RequisitoPublico(Long id, String descripcion) {}

    // Los tres textos de la plataforma: el PLATAFORMA que se acepta al crear la cuenta,
    // el PROCESO que se firma al postular —uno solo para todas las empresas— y el
    // FUTUROS_CONTACTOS opcional. Los lee la política pública, que los enseña tal cual
    // para no escribir por su cuenta un texto que luego se desvía.
    //
    // El de PROCESO sale YA COMPUESTO, con «la empresa que publica la vacante» donde va
    // el nombre: aquí no hay ninguna. El hueco no cruza la frontera del backend.
    public record TextoConsentimientoPublico(String tipo, String version, String texto) {}

    // El texto legal de LA EMPRESA de una vacante, ya compuesto con su nombre. Lleva el
    // nombre aparte porque es lo que la pantalla de postular enseña encima del botón, que
    // es lo que la ley pide decir: quién va a tratar los datos.
    //
    // Lo pide también la política pública cuando se llega a ella desde una vacante
    // (`?vacante=`): es el único sitio donde se puede leer el texto de una empresa que
    // publicó el suyo, porque la lista de textos de la plataforma no lo incluye.
    public record ConsentimientoDeVacante(String nombreEmpresa, String version, String texto) {}

    /**
     * Con quien entra, y como se llama.
     *
     * <p>⚠️ <b>El nombre viaja aqui a proposito.</b> Antes el backend devolvia solo el
     * identificador y el portal guardaba el nombre en `localStorage` al registrarse: quien
     * entraba desde otro navegador —o por el enlace del correo, sin haberse registrado
     * nunca— veia el portal sin su nombre. Los dos pueden venir vacios: `persona` los admite.
     */
    public record Sesion(String token, Long usuarioId, String nombre, String apellidos) {}

    /**
     * Como se llama quien esta usando el token que acaba de llegar.
     *
     * <p>⚠️ <b>Es el mismo nombre que devuelve entrar, y hace falta igual.</b> Al entrar el
     * portal se entera una vez; despues vive de un token guardado, y en la siguiente visita
     * —o en otro navegador, o tras vaciar el almacenamiento— ya no habia a quien preguntarle
     * como se llama, asi que la cabecera de su perfil decia «Tu perfil» sobre un disco de
     * iniciales vacio. No lleva token: quien pregunta ya tiene el suyo, y devolverlo solo lo
     * pondria en un cuerpo de respuesta mas.
     */
    public record QuienSoy(Long usuarioId, String nombre, String apellidos) {}

    /**
     * Lo unico que se manda para entrar con el enlace del correo: el token.
     *
     * <p>Va en el cuerpo y no en la URL a proposito. Un token en la query string acaba
     * escrito en el registro del servidor, en el historial del navegador y en la cabecera
     * Referer de cualquier recurso externo que cargue la pagina siguiente.
     */
    public record AccesoPorEnlace(@NotBlank String token) {}

    // empresa por lo mismo que nombreEmpresa en el tablón: «mis postulaciones» mezcla los
    // procesos del candidato en todas las empresas, y cada uno debe decir de quién es.
    /**
     * Una postulación suya, como la ve el candidato.
     *
     * <p>{@code instrumentoEtapaTecnica} dice qué rendirá cuando le toque la etapa de la
     * prueba: {@code PLANTILLA} (la prueba del puesto, con su enunciado y sus entregables) o
     * {@code CUESTIONARIO_TECNICO} (preguntas escritas para esa vacante). Los dos comparten
     * los mismos estados, así que sin este dato el portal no sabría a qué pantalla llevarlo
     * y tendría que adivinarlo pidiendo un examen y mirando si responde 404.
     */
    public record MiPostulacion(String uuid, String vacante, String empresa, String estado,
                                String estadoNombre, String grupoPrioridad, long diasSinCambio,
                                Instant creadoEn, String instrumentoEtapaTecnica,
                                /**
                                 * Cuántos avisos suyos de este proceso siguen sin ver: el
                                 * punto de la fila (V56). Cero = sin punto.
                                 */
                                long avisosSinLeer,
                                /**
                                 * Lo que paga la vacante HOY, con la marca de cuándo cambió.
                                 *
                                 * <p>Viaja en la lista y no solo en el detalle porque el punto
                                 * de la fila tiene que poder explicarse sin abrir nada: quien
                                 * ve el aviso quiere saber el número, y hacerle pulsar para
                                 * enterarse es esconder la noticia detrás de un clic.
                                 */
                                RemuneracionPublica remuneracion,
                                /**
                                 * Lo que él dijo que quería ganar al postular aquí, o
                                 * {@code null} si la vacante tenía el sueldo oculto y no se
                                 * le exigió. Es SUYO: en el portal viaja siempre, sin permiso
                                 * de por medio.
                                 */
                                Pretension miPretension) {}

    /** Un monto con su moneda, ya escrito además en una frase para pintarlo sin traducir. */
    public record Pretension(BigDecimal monto, String moneda, String texto) {}

    /**
     * Un aviso de la campana (V56).
     *
     * <p>{@code postulacionUuid} y no el id interno: es el identificador que el portal ya usa
     * para todo lo del candidato, y el que sabe convertir en una dirección.
     */
    public record AvisoDelPortal(Long id, String tipo, String titulo, String cuerpo,
                                 String postulacionUuid, Long vacanteId,
                                 Instant leidoEn, Instant creadoEn) {}

    /** Lo que pide la campana al abrirse: los avisos y cuántos quedan sin ver. */
    public record MisAvisos(long sinLeer, List<AvisoDelPortal> avisos) {}

    public record PasoHistorial(String estadoAnterior, String estadoNuevo, boolean fueElSistema,
                                Instant ocurridaEn) {}

    public record MiPostulacionDetalle(MiPostulacion resumen, List<PasoHistorial> historial) {}
}
