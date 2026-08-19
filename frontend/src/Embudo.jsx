// El recorrido de la tanda, contado por estados.
//
// Se pinta en el orden real del recorrido y no en el que devuelva el backend: un embudo
// desordenado no es un embudo. Los estados que nadie ocupa no se enseñan, para que con
// tres candidatos no salgan dieciocho cajas vacías.

const RECORRIDO = [
  ['POSTULADA', 'Postuló', 'sistema'],
  ['PERFIL_TURNO_CANDIDATO', 'Le toca al candidato', 'candidato'],
  ['PERFIL_CALIFICANDO', 'La IA está leyendo', 'sistema'],
  ['PERFIL_POR_CONFIRMAR', 'Espera decisión', 'equipo'],
  ['PRUEBA_TURNO_CANDIDATO', 'Prueba del puesto', 'candidato'],
  ['PRUEBA_CALIFICANDO', 'Calificando la prueba', 'sistema'],
  ['PRUEBA_POR_CONFIRMAR', 'Prueba por confirmar', 'equipo'],
  ['SIMULACION_POR_HABILITAR', 'Simulación por habilitar', 'equipo'],
  ['SIMULACION_TURNO_CANDIDATO', 'Simulación', 'candidato'],
  ['SIMULACION_CALIFICANDO', 'Calificando simulación', 'sistema'],
  ['SIMULACION_POR_CONFIRMAR', 'Simulación por confirmar', 'equipo'],
  ['DECISION_POR_CONFIRMAR', 'Decisión final', 'equipo'],
  ['CONTRATADA', 'Contratada', 'equipo'],
  ['NO_CONTINUA', 'No continúa', 'equipo'],
  ['CERRADA', 'Cerrada', 'equipo'],
]

export default function Embudo({ conteo }) {
  if (!conteo) return null
  const porEstado = conteo.porEstado || {}
  const escalones = RECORRIDO
    .map(([codigo, etiqueta, espera]) => [codigo, etiqueta, espera, porEstado[codigo] || 0])
    .filter(([, , , cuantos]) => cuantos > 0)

  if (escalones.length === 0) return null

  return (
    <div className="embudo">
      {escalones.map(([codigo, etiqueta, espera, cuantos]) => (
        <div key={codigo} className={`escalon espera-${espera}`}>
          <div className="cifra">{cuantos}</div>
          <div className="etiqueta">{etiqueta}</div>
        </div>
      ))}
    </div>
  )
}
