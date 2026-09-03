import ws from 'k6/ws'
import { check } from 'k6'

const baseUrl = (__ENV.WS_BASE_URL || 'http://127.0.0.1:8090').replace(/\/+$/, '')
const roomId = __ENV.ROOM_ID || '1'
const userId = __ENV.USER_ID || '910001'

export const options = {
  scenarios: {
    websocket_interaction: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '60s', target: 25 },
        { duration: '60s', target: 50 },
        { duration: '30s', target: 0 }
      ],
      gracefulRampDown: '10s'
    }
  },
  thresholds: {
    checks: ['rate>0.99'],
    ws_connecting: ['p(95)<2000']
  }
}

export default function () {
  const wsUrl = `${baseUrl.replace(/^http/, 'ws')}/ws/live/${roomId}`
  let receivedBroadcast = false
  const response = ws.connect(wsUrl, {}, (socket) => {
    socket.on('open', () => {
      socket.send(JSON.stringify({
        type: 'danmu',
        userId: Number(userId) + __VU,
        username: `压力测试用户${__VU}`,
        content: `live-service websocket pressure ${__ITER}`,
        color: '#ffffff'
      }))
      socket.send(JSON.stringify({
        type: 'like',
        userId: Number(userId) + __VU
      }))
    })
    socket.on('message', (message) => {
      if (message.includes('danmu') || message.includes('like')) receivedBroadcast = true
    })
    socket.setTimeout(() => socket.close(), 3000)
  })

  check(response, {
    'websocket handshake succeeds': (r) => r && r.status === 101,
    'websocket broadcast received': () => receivedBroadcast
  })
}
