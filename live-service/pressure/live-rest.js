import http from 'k6/http'
import { check, sleep } from 'k6'

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:8090').replace(/\/+$/, '')
const roomId = __ENV.ROOM_ID || '1'

export const options = {
  scenarios: {
    room_reads: {
      executor: 'ramping-vus',
      exec: 'roomReads',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '60s', target: 30 },
        { duration: '60s', target: 50 },
        { duration: '30s', target: 0 }
      ],
      gracefulRampDown: '10s'
    },
    control_reads: {
      executor: 'constant-vus',
      exec: 'controlReads',
      vus: 5,
      duration: '180s',
      startTime: '10s'
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.99']
  }
}

function assertResponse(response, name, bodyCheck) {
  return check(response, {
    [`${name}: HTTP 200`]: (r) => r.status === 200,
    [`${name}: business code 200`]: (r) => r.json('code') === 200,
    [`${name}: response shape`]: bodyCheck
  })
}

function assertHealth(response) {
  return check(response, {
    'health: HTTP 200': (r) => r.status === 200,
    'health: status UP': (r) => r.json('status') === 'UP'
  })
}

export function roomReads() {
  const responses = http.batch([
    ['GET', `${baseUrl}/api/live/rooms?page=1&pageSize=50`, null, { tags: { endpoint: 'rooms-list' } }],
    ['GET', `${baseUrl}/api/live/rooms/${roomId}`, null, { tags: { endpoint: 'room-detail' } }],
    ['GET', `${baseUrl}/api/live/rooms/${roomId}/danmus?limit=100`, null, { tags: { endpoint: 'danmu-history' } }],
    ['GET', `${baseUrl}/api/live/rooms/${roomId}/like`, null, { tags: { endpoint: 'like-count' } }]
  ])

  assertResponse(responses[0], 'rooms-list', (r) => Array.isArray(r.json('data.list')))
  assertResponse(responses[1], 'room-detail', (r) => Number(r.json('data.roomId')) === Number(roomId))
  assertResponse(responses[2], 'danmu-history', (r) => Array.isArray(r.json('data')))
  assertResponse(responses[3], 'like-count', (r) => Number.isInteger(r.json('data.likeCount')))
  sleep(0.1)
}

export function controlReads() {
  const responses = http.batch([
    ['GET', `${baseUrl}/actuator/health`, null, { tags: { endpoint: 'health' } }],
    ['GET', `${baseUrl}/api/live/srs/health`, null, { tags: { endpoint: 'srs-health' } }]
  ])

  assertHealth(responses[0])
  assertResponse(responses[1], 'srs-health', (r) => r.json('data') !== null)
  sleep(0.2)
}
