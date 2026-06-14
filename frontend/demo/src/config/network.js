const getApiHost = () => import.meta.env.VITE_API_HOST || window.location.hostname
const getApiPort = () => import.meta.env.VITE_API_PORT || '8080'

export const API_ORIGIN = `${window.location.protocol}//${getApiHost()}:${getApiPort()}`
export const API_BASE = `${API_ORIGIN}/api`

export const getWsOrigin = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${getApiHost()}:${getApiPort()}`
}

export const normalizeLanUrl = (url) => {
  if (!url || typeof url !== 'string') {
    return url
  }

  try {
    const parsed = new URL(url)
    if (['localhost', '127.0.0.1', '0.0.0.0'].includes(parsed.hostname)) {
      parsed.hostname = getApiHost()
      return parsed.toString()
    }
  } catch (error) {
    return url
  }

  return url
}
