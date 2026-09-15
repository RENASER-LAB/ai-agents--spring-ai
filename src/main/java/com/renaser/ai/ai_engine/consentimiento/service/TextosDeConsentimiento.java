package com.renaser.ai.ai_engine.consentimiento.service;

import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.entity.TipoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * El texto vigente de una organización: el publicado más reciente de ese tipo.
 *
 * <p>Una sola puerta para los cuatro sitios que preguntan lo mismo —el tablón antes de
 * postular, la postulación al firmar, el registro de la cuenta y la política pública—,
 * porque cuatro copias de la misma búsqueda acaban contestando distinto.
 *
 * <p>Vigente significa <b>publicado y el más reciente</b>, no «marcado como activo»: una
 * versión publicada no se modifica nunca —editar crea otra— y los consentimientos ya
 * firmados apuntan a la suya.
 */
@Service
@RequiredArgsConstructor
public class TextosDeConsentimiento {

    /** El hueco del texto de PROCESO, donde va el nombre de quien publica la vacante. */
    public static final String HUECO_EMPRESA = "{EMPRESA}";

    /**
     * Con qué se rellena el hueco cuando no hay vacante de la que sacar el nombre.
     *
     * <p>Lo usa la lista pública de textos, que es la que lee la política de privacidad:
     * ahí no hay ninguna empresa concreta. <b>El hueco no sale nunca del backend</b> —quien
     * sirva un texto lo sirve ya compuesto—, porque el día que el marcador cambie, un
     * cliente que lo sustituya por su cuenta enseñaría «{EMPRESA}» en crudo en una página
     * legal sin que nada falle.
     */
    public static final String EMPRESA_SIN_NOMBRAR = "la empresa que publica la vacante";

    private final TextoConsentimientoRepository textos;
    private final DuenoDelInstrumento duenos;

    /**
     * Un texto listo para enseñar o para firmar.
     *
     * @param fuente la fila de la que sale, que es a la que apunta el consentimiento
     * @param texto  lo que la persona lee de verdad, con el nombre de la empresa ya puesto
     */
    public record ParaFirmar(TextoConsentimiento fuente, String texto) {}

    /** El vigente, si lo hay. Para quien puede seguir sin él. */
    public Optional<TextoConsentimiento> vigente(Long organizacionId, TipoConsentimiento tipo) {
        return textos.findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                organizacionId, tipo.codigo());
    }

    /**
     * El vigente o el corte que explica por qué no se puede seguir.
     *
     * <p>No debería faltar nunca —la V54 publica los tres de la plataforma—, pero si falta
     * es un 409 que dice qué pasa y no un vacío que deja al candidato firmando sin saber
     * quién trata sus datos.
     */
    public TextoConsentimiento exigirVigente(Long organizacionId, TipoConsentimiento tipo) {
        return vigente(organizacionId, tipo).orElseThrow(() -> new IllegalStateException(
                switch (tipo) {
                    case PROCESO -> "No hay texto de consentimiento de proceso publicado: "
                            + "nadie puede postular hasta que lo haya";
                    case PLATAFORMA -> "No hay texto de consentimiento de la plataforma publicado: "
                            + "sin él nadie puede crear una cuenta";
                    case FUTUROS_CONTACTOS -> "No hay texto de consentimiento publicado: "
                            + TipoConsentimiento.FUTUROS_CONTACTOS;
                }));
    }

    /**
     * El texto que firma quien postula a una vacante de esa empresa, con su nombre puesto.
     *
     * <p><b>Es uno solo para todas, y no hay excepción.</b> Lo que cambia de una empresa a
     * otra son las tres palabras de su razón social, así que el texto vive una vez —en la
     * plataforma, con el hueco {@value #HUECO_EMPRESA}— y se compone al leerlo.
     *
     * <p>Antes de la V54 había una fila por empresa y cada una publicaba la suya. Dos
     * consecuencias, las dos malas: corregir una coma obligaba a republicar empresa por
     * empresa, y una empresa recién dada de alta no podía recibir candidatos hasta que
     * alguien redactara y publicara un texto legal. Ninguna lo hacía bien: publicaban la
     * copia de Renaser tal cual, que ni siquiera las nombraba.
     *
     * <p><b>Por eso tampoco se mira si la empresa tiene uno suyo.</b> Un texto por empresa
     * es un texto que nadie revisa y que se queda viejo sin que nadie se entere; el de la
     * plataforma lo mantiene quien tiene el abogado. Publicarlo les está prohibido desde
     * {@code ServicioAdministracionImpl#publicarTextoConsentimiento}.
     *
     * <p>Lo que devuelve lleva las dos cosas: la fila de la que sale —a la que apuntará el
     * consentimiento— y el texto ya compuesto, que es lo que la persona lee y lo que hay
     * que guardar al firmar. Ver {@code consentimiento.texto_firmado} (V54 §5).
     */
    public ParaFirmar procesoDe(String nombreEmpresa) {
        TextoConsentimiento fuente = exigirVigente(
                duenos.plataforma().getId(), TipoConsentimiento.PROCESO);
        return new ParaFirmar(fuente, componer(fuente.getTexto(), nombreEmpresa));
    }

    /**
     * El hueco relleno con el nombre de la empresa.
     *
     * <p>Un texto propio de una empresa no tiene por qué llevar hueco, y entonces esto no
     * toca nada: sustituir lo que no está es devolver lo mismo.
     */
    public String componer(String texto, String nombreEmpresa) {
        return texto.replace(HUECO_EMPRESA, nombreEmpresa);
    }
}
