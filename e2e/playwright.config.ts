import { defineConfig } from '@playwright/test';

// Pruebas de API: usan el fixture `request`, no abren navegador y pueden correr headless.
// El preview del trabajo llega en E2E_API. Sin valor por defecto a propósito: un fallback
// al 8081 apuntaría al backend de desarrollo compartido y las pruebas pasarían mintiendo.
function destinoDelTrabajo(): string {
  const valor = process.env.E2E_API?.trim();
  if (!valor) throw new Error('Falta E2E_API: declara explícitamente el preview del trabajo.');
  const url = new URL(valor);
  if (!['http:', 'https:'].includes(url.protocol) ||
      !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname) ||
      url.username || url.password || url.search || url.hash) {
    throw new Error('E2E_API debe ser una URL HTTP de loopback sin credenciales, query ni fragmento.');
  }
  return valor;
}

export default defineConfig({
  testDir: './pruebas',
  fullyParallel: false,
  forbidOnly: true,
  // Sin reintentos a propósito: un fallo que se esconde tras un retry no es un fallo menos.
  retries: 0,
  reporter: [['list'], ['junit', { outputFile: 'test-results/junit.xml' }]],
  use: {
    baseURL: destinoDelTrabajo(),
    extraHTTPHeaders: { Accept: 'application/json' },
  },
});
