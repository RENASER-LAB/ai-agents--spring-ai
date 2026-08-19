import { useCallback, useEffect, useState } from 'react'
import * as api from './api.js'
import Embudo from './Embudo.jsx'
import Ficha from './Ficha.jsx'

// La pantalla que contesta «¿a quién invito primero?».
//
// El orden lo manda el backend y aquí no se reordena: sale de comparar a todos entre sí,
// con el grupo de prioridad por delante de la nota. Reordenar en el navegador daría una
// lista distinta de la que el equipo ve en cualquier otro sitio.

const GRUPOS = {
  ALTA: ['Alta prioridad', 'alta'],
  POTENCIAL_CON_RIESGO: ['Alto potencial con riesgo', 'riesgo'],
  NO_PRIORIZADO: ['No priorizado', 'no'],
  INCOMPATIBLE: ['Incompatibilidad objetiva', 'incompatible'],
}

const SIN_GRUPO = {
  SIN_EMPEZAR: 'Sin calificar',
  EN_CURSO: 'La IA está leyendo',
  FALLIDA: 'La IA falló',
  TERMINADA: 'Sin grupo',
}

// Cuánto lleva trabajando, en algo que se lea de un vistazo. 38 meses no dice nada;
// «3 a 2 m» sí.
function tiempo(meses) {
  if (meses == null) return '—'
  if (meses < 12) return `${meses} m`
  const anios = Math.floor(meses / 12)
  const resto = meses % 12
  return resto === 0 ? `${anios} a` : `${anios} a ${resto} m`
}

function numero(valor) {
  return valor == null ? '—' : Number(valor).toFixed(1)
}

function Entrada({ onEntrar }) {
  const [uid, setUid] = useState('tester-001')
  const [error, setError] = useState(null)
  const [entrando, setEntrando] = useState(false)

  async function entrar(e) {
    e.preventDefault()
    setEntrando(true)
    setError(null)
    try {
      await api.entrar(uid.trim())
      onEntrar()
    } catch (err) {
      setError(err.message)
    } finally {
      setEntrando(false)
    }
  }

  return (
    <form className="entrada" onSubmit={entrar}>
      <h1>Criba de currículums</h1>
      <p className="apagado pequeno" style={{ marginTop: 0, marginBottom: '1.25rem' }}>
        RENASER OS todavía no emite el token, así que se entra con el atajo de desarrollo.
      </p>
      <label htmlFor="uid">Tu id de RENASER OS</label>
      <input id="uid" value={uid} onChange={(e) => setUid(e.target.value)} autoFocus />
      {api.ES_DEMO && (
        <div className="aviso">
          Estás viendo una foto de los resultados, no el sistema en marcha. Los
          números y los retratos son los que la IA produjo el día que se exportó;
          desde aquí no se puede pedir una calificación nueva.
        </div>
      )}
      {error && <div className="aviso error">{error}</div>}
      {aviso && <div className="aviso">{aviso}</div>}
      <button type="submit" disabled={entrando || !uid.trim()}>
        {entrando ? 'Entrando…' : 'Entrar'}
      </button>
    </form>
  )
}

export default function App() {
  const [dentro, setDentro] = useState(Boolean(api.token()))
  const [vacantes, setVacantes] = useState([])
  const [vacanteId, setVacanteId] = useState(null)
  const [tanda, setTanda] = useState(null)
  const [conteo, setConteo] = useState(null)
  const [elegida, setElegida] = useState(null)
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    if (!dentro) return
    api.vacantes()
      .then((lista) => {
        const publicadas = lista.filter((v) => v.estado === 'PUBLICADA')
        setVacantes(publicadas)
        if (publicadas.length > 0) setVacanteId(publicadas[0].id)
      })
      .catch((e) => setError(e.message))
  }, [dentro])

  const recargar = useCallback(() => {
    if (!vacanteId) return
    Promise.all([api.ranking(vacanteId), api.embudo(vacanteId)])
      .then(([r, c]) => {
        setTanda(r)
        setConteo(c)
        setError(null)
        // La fila elegida se refresca sola: si la IA acabó mientras se miraba, los
        // números de la ficha tienen que cambiar sin que haya que volver a hacer clic.
        setElegida((antes) =>
          antes ? r.filas.find((f) => f.postulacionId === antes.postulacionId) || null : null)
      })
      .catch((e) => setError(e.message))
  }, [vacanteId])

  useEffect(() => { recargar() }, [recargar])

  // Mientras quede alguien en la cola, la tanda se refresca sola. Cuando no queda nadie
  // se para: sondear una lista que ya no cambia solo gasta peticiones.
  useEffect(() => {
    if (!tanda || tanda.enCurso === 0) return
    const t = setTimeout(recargar, 8000)
    return () => clearTimeout(t)
  }, [tanda, recargar])

  async function pasada(cual) {
    setOcupado(true)
    setError(null)
    try {
      const r = await cual(vacanteId)
      setAviso(r.mensaje)
      recargar()
    } catch (e) {
      setError(e.message)
    } finally {
      setOcupado(false)
    }
  }

  if (!dentro) return <Entrada onEntrar={() => setDentro(true)} />

  return (
    <div className="marco">
      <div className="cabecera">
        <div>
          <h1>{tanda ? tanda.vacante : 'Criba de currículums'}</h1>
          <div className="apagado pequeno">
            {tanda ? `${tanda.puesto} · nivel ${tanda.nivelPuesto}` : 'Cargando…'}
          </div>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <select value={vacanteId || ''} onChange={(e) => {
            setVacanteId(Number(e.target.value))
            setElegida(null)
          }}>
            {vacantes.map((v) => <option key={v.id} value={v.id}>{v.titulo}</option>)}
          </select>
          {!api.ES_DEMO && (
            <>
              <button onClick={() => pasada(api.cribaRapida)} disabled={ocupado || !vacanteId}>
                1ª pasada · rápida
              </button>
              <button onClick={() => pasada(api.cribaFina)} disabled={ocupado || !vacanteId}>
                2ª pasada · a fondo
              </button>
            </>
          )}
          <button onClick={recargar}>Actualizar</button>
          {!api.ES_DEMO && (
            <button onClick={() => { api.cerrarSesion(); setDentro(false) }}>Salir</button>
          )}
        </div>
      </div>

      {error && <div className="aviso error">{error}</div>}

      {vacantes.length === 0 && !error && (
        <div className="aviso">
          No hay ninguna vacante publicada. Monta la convocatoria con{' '}
          <code>python scripts/cargar-convocatoria.py</code>.
        </div>
      )}

      {tanda && (
        <>
          <div className="tarjetas">
            <div className="tarjeta">
              <div className="cifra">{tanda.total}</div>
              <div className="etiqueta">currículums</div>
            </div>
            <div className="tarjeta">
              <div className="cifra">{tanda.calificados}</div>
              <div className="etiqueta">ya leídos por la IA</div>
            </div>
            <div className="tarjeta">
              <div className="cifra">{tanda.conPasadaFina}</div>
              <div className="etiqueta">mirados a fondo</div>
            </div>
            <div className="tarjeta">
              <div className="cifra">{tanda.enCurso}</div>
              <div className="etiqueta">leyéndose ahora</div>
            </div>
            <div className="tarjeta">
              <div className="cifra">{tanda.fallidos}</div>
              <div className="etiqueta">fallaron y no tienen nota</div>
            </div>
          </div>

          <Embudo conteo={conteo} />

          <table>
            <thead>
              <tr>
                <th>#</th>
                <th />
                <th>Candidato</th>
                <th>Último puesto</th>
                <th className="num">Exp.</th>
                <th className="num">Nota</th>
                <th className="num">Adecuación</th>
                <th className="num">Potencial</th>
                <th className="num">Confianza</th>
                <th>Grupo</th>
                <th className="num">Riesgos</th>
              </tr>
            </thead>
            <tbody>
              {tanda.filas.map((f) => {
                const [texto, clase] = GRUPOS[f.grupoPrioridad]
                  || [SIN_GRUPO[f.estadoCalificacion] || '—', 'sin']
                return (
                  <tr
                    key={f.postulacionId}
                    className={elegida?.postulacionId === f.postulacionId ? 'elegida' : ''}
                    onClick={() => setElegida(f)}
                  >
                    <td className="posicion">{f.puesto}</td>
                    <td className="iconos">
                      {/* El clic no sube a la fila, o se abrirían las dos cosas a la vez. */}
                      {f.cvUrl && (
                        <a
                          href={f.cvUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="icono"
                          title={`Ver el currículum de ${f.datos?.nombre || f.candidato}`}
                          onClick={(e) => e.stopPropagation()}
                        >
                          👁
                        </a>
                      )}
                      <button
                        className="icono"
                        title="Ver el puntaje y lo que la IA encontró"
                        onClick={(e) => { e.stopPropagation(); setElegida(f) }}
                      >
                        📊
                      </button>
                    </td>
                    <td>
                      {f.datos?.nombre || f.candidato}
                      {f.pasada === 'RAPIDA' && (
                        <span className="marca sin" style={{ marginLeft: '0.4rem' }}>
                          provisional
                        </span>
                      )}
                    </td>
                    <td className="apagado pequeno">
                      {f.datos?.ultimoPuesto || '—'}
                      {f.datos?.ultimaEmpresa ? ` · ${f.datos.ultimaEmpresa}` : ''}
                    </td>
                    <td className="num">{tiempo(f.datos?.experienciaMesesTotal)}</td>
                    <td className="num">{numero(f.notaEtapa)}</td>
                    <td className="num">{numero(f.adecuacion)}</td>
                    <td className="num">{numero(f.potencial)}</td>
                    <td className="num">{numero(f.confianzaEvidencia)}</td>
                    <td><span className={`marca ${clase}`}>{texto}</span></td>
                    <td className="num">{f.riesgosCriticos || ''}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {tanda.filas.length === 0 && (
            <p className="apagado pequeno" style={{ marginTop: '1rem' }}>
              Todavía no ha postulado nadie a esta vacante.
            </p>
          )}

          {elegida && (
            <Ficha
              // La clave hace que cambiar de candidato monte una ventana nueva en vez de
              // reusar la anterior, que se quedaría con el retrato del otro medio segundo.
              key={elegida.postulacionId}
              fila={elegida}
              onCambio={recargar}
              onCerrar={() => setElegida(null)}
              carpetaCv={vacantes.find((v) => v.id === vacanteId)?.carpetaCv}
            />
          )}
        </>
      )}
    </div>
  )
}
