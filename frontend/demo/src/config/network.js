const getApiHost = () => import.meta.env.VITE_API_HOST || window.location.hostname
const getApiPort = () => import.meta.env.VITE_API_PORT || (window.location.port === '5173' ? '8080' : window.location.port)

export const API_ORIGIN = import.meta.env.VITE_API_HOST || (window.location.port === '5173' ? `${window.location.protocol}//${getApiHost()}:${getApiPort()}` : window.location.origin)
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
    const isPrivateIpv4 = /^(10\.|127\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(parsed.hostname)
    if (['localhost', '0.0.0.0'].includes(parsed.hostname) || isPrivateIpv4) {
      parsed.hostname = getApiHost()
      return parsed.toString()
    }
  } catch (error) {
    return url
  }

  return url
}
