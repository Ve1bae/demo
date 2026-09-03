#!/usr/bin/env node

// Gateway API contract sweep: every public HTTP route is exercised once.
const base = (process.env.API_REGRESSION_BASE || 'http://127.0.0.1:8080/api').replace(/\/+$/, '')
const id = Date.now()
const user = { username: `api_${id}`, password: 'api-password', nickname: 'API test' }
const request = async (method, path, { body, userId, form } = {}) => {
  const headers = { Accept: 'application/json' }
  if (userId !== undefined) headers['X-User-Id'] = String(userId)
  let payload
  if (form) payload = form
  else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body) }
  const response = await fetch(`${base}${path}`, { method, headers, body: payload })
  const text = await response.text()
  let json; try { json = text ? JSON.parse(text) : null } catch { json = text }
  if (response.status >= 500 || (json && json.code >= 500)) throw new Error(`${method} ${path} -> ${response.status}: ${text}`)
  return { response, json }
}
const ok = async (method, path, options) => { const r = await request(method, path, options); if (r.response.status < 200 || r.response.status >= 400) throw new Error(`${method} ${path} -> ${r.response.status}`); return r.json }

const registered = await ok('POST', '/user/register', { body: user })
const login = await ok('POST', '/user/login', { body: user })
const userId = login.data?.id || login.data?.userId
if (!userId) throw new Error('login did not return user id')
const targetUser = { username: `api_target_${id}`, password: 'api-password', nickname: 'API target' }
await ok('POST', '/user/register', { body: targetUser })
const targetLogin = await ok('POST', '/user/login', { body: targetUser })
const targetUserId = targetLogin.data?.id || targetLogin.data?.userId
if (!targetUserId) throw new Error('target login did not return user id')
await ok('GET', `/user/${userId}/profile?viewerId=${userId}`)
await ok('PUT', `/user/${userId}/avatar`, { body: { avatarUrl: 'https://example.test/api.png' } })
await ok('GET', `/user/${userId}/following`); await ok('GET', `/user/${userId}/followers`)
await ok('GET', `/user/${userId}/preferences`); await ok('GET', `/user/internal/${userId}`)
await ok('POST', `/user/${targetUserId}/follow`, { userId }); await ok('DELETE', `/user/${targetUserId}/follow`, { userId })

let videoId = Number(process.env.API_REGRESSION_VIDEO_ID || 0)
if (!videoId) {
  const form = new FormData(); form.set('title', 'API regression video'); form.set('author', 'api'); form.set('duration', '5'); form.set('userId', String(userId)); form.set('file', new Blob([Buffer.from('api-test-video')], { type: 'video/mp4' }), 'api.mp4')
  const uploaded = await ok('POST', '/videos/upload', { form }); videoId = uploaded.data?.id
}
if (!videoId) throw new Error('upload did not return video id')
await ok('GET', '/videos/recommend?page=1&pageSize=12', { userId }); await ok('GET', `/videos/${videoId}`, { userId })
await ok('GET', `/videos/user/${userId}/uploads`, { userId }); await ok('GET', `/videos/user/${userId}/favorites`, { userId }); await ok('GET', `/videos/user/${userId}/history`)
await ok('POST', `/videos/${videoId}/visibility`, { userId, body: { visible: true } }); await ok('GET', `/videos/${videoId}/status`, { userId })
await ok('POST', `/videos/${videoId}/play`, { userId }); await ok('POST', `/videos/${videoId}/likes`, { userId }); await ok('DELETE', `/videos/${videoId}/likes`, { userId })
await ok('POST', `/videos/${videoId}/favorites`, { userId }); await ok('DELETE', `/videos/${videoId}/favorites`, { userId })
const comment = await ok('POST', `/videos/${videoId}/comments`, { userId, body: { content: 'API regression comment' } }); const commentId = comment.data?.id
await ok('GET', `/videos/${videoId}/comments?page=1&pageSize=20`, { userId }); if (commentId) { await ok('POST', `/comments/${commentId}/likes`, { userId }); await ok('DELETE', `/comments/${commentId}/likes`, { userId }) }
if (commentId) { await ok('POST', `/videos/${videoId}/comments/${commentId}/like`, { userId }); await ok('DELETE', `/videos/${videoId}/comments/${commentId}/like`, { userId }) }
await ok('POST', `/videos/${videoId}/danmakus`, { userId, body: { content: 'API regression danmaku', timeSeconds: 1, color: '#fff' } }); await ok('GET', `/videos/${videoId}/danmakus?limit=20`)
await ok('GET', '/minio/test')
const minioForm = new FormData(); minioForm.set('file', new Blob([Buffer.from('minio-api-test')], { type: 'text/plain' }), 'api-test.txt')
const minioUpload = await ok('POST', '/minio/upload', { form: minioForm }); const objectName = minioUpload.data?.objectName
if (!objectName) throw new Error('minio upload did not return object name')
await ok('GET', `/minio/url?objectName=${encodeURIComponent(objectName)}`); await ok('DELETE', `/minio/delete?objectName=${encodeURIComponent(objectName)}`)
const room = await ok('POST', '/live/rooms', { userId, body: { title: 'API regression room', categoryId: 1 } }); const roomId = room.data?.roomId
if (roomId) { await ok('GET', '/live/rooms?page=1&pageSize=12'); await ok('GET', `/live/rooms/${roomId}`); await ok('GET', `/live/rooms/${roomId}/danmus`); await ok('GET', `/live/rooms/${roomId}/like`); await ok('POST', `/live/rooms/${roomId}/close`, { userId }) }
await ok('GET', '/live/srs/health')
await ok('DELETE', `/videos/${videoId}`, { userId })
console.log(`API regression passed: user=${userId}, video=${videoId}, room=${roomId || 'none'}`)
