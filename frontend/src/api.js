// La única puerta al backend. Todo lo demás del frontend pide por aquí.
//
// Tiene dos modos:
//
//   - **En vivo**, el normal: habla con Spring y se puede pedir que la IA califique.
//   - **Demo**, con VITE_MODO=demo: no hay backend. Lee una foto de los datos que se
//     congeló con scripts/exportar-para-demo.py y queda un sitio estático que se puede
//     publicar en cualquier sitio.
//
// El modo demo existe para enseñar el resultado a quien no va a levantar nada. Es de solo
// mirar: los botones que piden calificar se apagan, porque no hay a quién pedírselo.
//
// El token vive en sessionStorage y no en localStorage a propósito: es una sesión del
// equipo mirando currículums de gente real, y no tiene por qué sobrevivir a cerrar la
// pestaña.

import demo from './datos-demo.json'

const BASE = '/api/v1'
const LLAVE = 'renaser.token'

export const ES_DEMO = import.meta.env.VITE_MODO === 'demo'

export function token() {
  return ES_DEMO ? 'demo' : sessionStorage.getItem(LLAVE)
}

export function cerrarSesion() {
  sessionStorage.removeItem(LLAVE)
}

async function pide(metodo, ruta, cuerpo) {
  const cabeceras = {}
  const t = sessionStorage.getItem(LLAVE)
  if (t) cabeceras.Authorization = `Bearer ${t}`
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json'

  const r = await fetch(`${BASE}${ruta}`, {
    method: metodo,
    headers: cabeceras,
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  })

  if (!r.ok) {
    // El backend responde con ProblemDetail: el detalle es lo único que sirve enseñar.
    let detalle = `${r.status}`
    try {
      const json = await r.json()
      detalle = json.detail || json.title || detalle
    } catch {
      // respuesta sin cuerpo: se queda el código
    }
    if (r.status === 401 || r.status === 403) cerrarSesion()
    throw new Error(detalle)
  }
  if (r.status === 204) return null
  const texto = await r.text()
  return texto ? JSON.parse(texto) : null
}

/** Un dato de la foto. Se devuelve copiado para que nadie lo modifique sin querer. */
function deLaFoto(valor, queEs) {
  if (valor === undefined) {
    throw new Error(`Esto no está en la foto de datos: ${queEs}. `
      + 'Vuelve a exportarla con scripts/exportar-para-demo.py.')
  }
  return structuredClone(valor)
}

const noEnDemo = () => {
  throw new Error('Esta pantalla es una foto de los resultados: no hay backend al que '
    + 'pedirle que la IA vuelva a calificar.')
}

/** Entra como equipo. Es el atajo de desarrollo: RENASER OS todavía no emite el token. */
export async function entrar(uid) {
  const sesion = await pide('POST', '/panel/auth/dev-login', { usuarioRenaserOsId: uid })
  sessionStorage.setItem(LLAVE, sesion.token)
  return sesion
}

export const vacantes = () =>
  ES_DEMO ? Promise.resolve(deLaFoto(demo.vacantes, 'las vacantes'))
    : pide('GET', '/panel/vacantes')

export const ranking = (vacanteId) =>
  ES_DEMO ? Promise.resolve(deLaFoto(demo.rankings[vacanteId], `el ranking de ${vacanteId}`))
    : pide('GET', `/panel/vacantes/${vacanteId}/ranking`)

export const embudo = (vacanteId) =>
  ES_DEMO ? Promise.resolve(deLaFoto(demo.embudos[vacanteId], `el embudo de ${vacanteId}`))
    : pide('GET', `/panel/vacantes/${vacanteId}/embudo`)

export const perfil = (postulacionId) =>
  ES_DEMO ? Promise.resolve(deLaFoto(demo.perfiles[postulacionId], `el perfil de ${postulacionId}`))
    : pide('GET', `/panel/postulaciones/${postulacionId}/perfil-integral`)

export const ficha = (postulacionId) => pide('GET', `/panel/postulaciones/${postulacionId}`)

export const cribar = (postulacionId) =>
  ES_DEMO ? noEnDemo() : pide('POST', `/panel/postulaciones/${postulacionId}/criba-cv`)

export const cribaRapida = (vacanteId) =>
  ES_DEMO ? noEnDemo() : pide('POST', `/panel/vacantes/${vacanteId}/criba-rapida`)

export const cribaFina = (vacanteId) =>
  ES_DEMO ? noEnDemo() : pide('POST', `/panel/vacantes/${vacanteId}/criba-fina`)
