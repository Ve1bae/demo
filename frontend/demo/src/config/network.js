const getApiHost = () => import.meta.env.VITE_API_HOST || window.location.hostname
const getApiPort = () => import.meta.env.VITE_API_PORT || '8080'

export const API_ORIGIN = `${window.location.protocol}//${getApiHost()}:${getApiPort()}`
export const API_BASE = `${API_ORIGIN}/api`

export const getWsOrigin = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${getApiHost()}:${getApiPort()}`
}
