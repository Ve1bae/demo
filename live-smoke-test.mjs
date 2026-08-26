const apiBase = 'http://127.0.0.1:18080/api'
const wsBase = 'ws://127.0.0.1:18080'
const userId = 1

const request = async (path, options = {}) => {
  const response = await fetch(`${apiBase}${path}`, options)
  return response.json()
}

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))

const waitFor = async (predicate, timeout = 8000) => {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeout) {
    if (await predicate()) {
      return true
    }
    await sleep(100)
  }
  throw new Error('timeout waiting for condition')
}

const openClient = (roomId, label) => new Promise((resolve, reject) => {
  const socket = new WebSocket(`${wsBase}/ws/live/${roomId}`)
  socket.label = label
  socket.messages = []
  socket.addEventListener('open', () => resolve(socket))
  socket.addEventListener('message', (event) => {
    try {
      socket.messages.push(JSON.parse(event.data))
    } catch {
      socket.messages.push({ raw: event.data })
    }
  })
  socket.addEventListener('error', () => reject(new Error(`${label} websocket error`)))
})

const latestLikeCount = (socket) => {
  const likes = socket.messages.filter((message) => message.type === 'like')
  return likes.length ? likes[likes.length - 1].likeCount : null
}

const record = (summary, id, title, status, actual, note = '') => {
  summary.push({ id, title, status, actual, note })
}

const main = async () => {
  const summary = []

  const created = await request('/live/rooms', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': String(userId)
    },
    body: JSON.stringify({
      title: '全量测试直播间',
      categoryId: 1,
      coverUrl: ''
    })
  })
  const roomId = created.data?.roomId

  const detail = await request(`/live/rooms/${roomId}`)
  const detailOk = detail.code === 200
    && detail.data?.status === 'online'
    && Boolean(detail.data?.pullUrl)
    && Boolean(detail.data?.qualityUrls?.原画)
  record(
    summary,
    'TC-UC08-001',
    '正常观看直播',
    detailOk ? '部分通过' : '失败',
    detailOk ? '创建/查询直播间成功，返回 pullUrl 和 qualityUrls；未启动 SRS，真实画面未播放' : JSON.stringify(detail)
  )

  const missing = await request('/live/rooms/999999')
  record(
    summary,
    'TC-UC08-002',
    '直播间离线或不存在提示',
    missing.code === 404 ? '通过' : '失败',
    missing.code === 404 ? `接口返回 404：${missing.message}` : JSON.stringify(missing)
  )

  record(summary, 'TC-UC08-003', '直播间详情接口异常回退本地缓存', '未执行', '需要浏览器模拟接口异常和本地缓存，本次未做 UI 自动化')
  record(summary, 'TC-UC08-004', '主播未推流时的等待提示', '未执行', '后端始终生成 pullUrl，需要浏览器配合空地址房间验证')
  record(summary, 'TC-UC08-005', '切换直播清晰度', '未执行', '需要真实直播流和浏览器播放器，本次未启动 SRS')

  const historyBefore = await request(`/live/rooms/${roomId}/danmus?limit=50`)
  record(
    summary,
    'TC-UC09-001',
    '加载弹幕历史并建立 WebSocket',
    historyBefore.code === 200 ? '通过' : '失败',
    historyBefore.code === 200 ? `历史弹幕接口正常，初始 ${historyBefore.data?.length ?? 0} 条` : JSON.stringify(historyBefore)
  )

  const clientA = await openClient(roomId, 'A')
  await sleep(800)
  const clientB = await openClient(roomId, 'B')
  await sleep(600)
  const initialOk = clientA.messages.some((message) => message.type === 'online_count')
    && clientA.messages.some((message) => message.type === 'like')
    && clientB.messages.some((message) => message.type === 'online_count')
    && clientB.messages.some((message) => message.type === 'like')
  record(
    summary,
    'TC-UC09-001',
    '加载弹幕历史并建立 WebSocket',
    initialOk ? '通过' : '失败',
    initialOk ? '两个客户端均收到在线人数和点赞数推送' : '未完整收到初始推送'
  )

  clientA.send(JSON.stringify({
    type: 'danmu',
    userId,
    username: '直播测试用户',
    content: '主播晚上好',
    color: '#ffffff'
  }))
  await waitFor(() => clientB.messages.some((message) => (
    message.type === 'danmu' && message.content === '主播晚上好'
  )))
  const historyAfterDanmu = await request(`/live/rooms/${roomId}/danmus?limit=50`)
  const danmuOk = historyAfterDanmu.data?.some((item) => item.content === '主播晚上好')
  record(
    summary,
    'TC-UC09-002',
    '登录用户发送弹幕并广播',
    danmuOk ? '通过' : '失败',
    danmuOk ? '客户端 A 发送后客户端 B 收到，历史接口可查询到' : JSON.stringify(historyAfterDanmu)
  )

  const danmuCountBeforeEmpty = clientB.messages.filter((message) => message.type === 'danmu').length
  clientA.send(JSON.stringify({ type: 'danmu', userId, username: '直播测试用户', content: '   ', color: '#ffffff' }))
  await sleep(600)
  const historyAfterEmpty = await request(`/live/rooms/${roomId}/danmus?limit=50`)
  const emptyOk = clientB.messages.filter((message) => message.type === 'danmu').length === danmuCountBeforeEmpty
    && historyAfterEmpty.data?.length === historyAfterDanmu.data?.length
  record(
    summary,
    'TC-UC09-004',
    '发送空弹幕',
    emptyOk ? '通过' : '失败',
    emptyOk ? '空白弹幕未广播、未持久化' : '空白弹幕被广播或持久化'
  )

  const longContent = 'x'.repeat(300)
  clientA.send(JSON.stringify({ type: 'danmu', userId, username: '直播测试用户', content: longContent, color: '#ffffff' }))
  await sleep(1000)
  const longBroadcast = clientB.messages.some((message) => (
    message.type === 'danmu' && message.content?.length === 300
  ))
  const historyAfterLong = await request(`/live/rooms/${roomId}/danmus?limit=50`)
  const longPersisted = historyAfterLong.data?.some((item) => item.content?.length === 300)
  record(
    summary,
    'TC-UC09-005',
    '发送超长弹幕',
    !longBroadcast && !longPersisted && clientA.readyState === WebSocket.OPEN ? '通过' : '失败',
    longBroadcast || longPersisted ? '超长弹幕被广播或持久化' : `未广播、未持久化；发送方连接状态 ${clientA.readyState}，接收方连接状态 ${clientB.readyState}`
  )

  clientA.close()
  clientB.close()
  await sleep(500)

  const clientA2 = await openClient(roomId, 'A2')
  await sleep(500)
  const clientB2 = await openClient(roomId, 'B2')
  await sleep(500)
  const reconnectOk = clientA2.messages.some((message) => message.type === 'online_count')
    && clientA2.messages.some((message) => message.type === 'like')
  record(
    summary,
    'TC-UC09-003',
    'WebSocket 断开后自动重连',
    reconnectOk ? '通过' : '失败',
    reconnectOk ? '断线后重新建立连接并收到初始推送（协议层验证，前端 3 秒定时器未做 UI 验证）' : '重连后未收到初始推送'
  )

  const likeBefore = (await request(`/live/rooms/${roomId}/like`)).data?.likeCount ?? 0
  clientA2.send(JSON.stringify({ type: 'like', userId }))
  await waitFor(() => latestLikeCount(clientB2) === likeBefore + 1)
  const likeAfterFirst = (await request(`/live/rooms/${roomId}/like`)).data?.likeCount ?? 0
  record(
    summary,
    'TC-UC10-001',
    '点击点赞并广播最新数量',
    likeAfterFirst === likeBefore + 1 ? '通过' : '失败',
    likeAfterFirst === likeBefore + 1 ? `点赞数从 ${likeBefore} 变为 ${likeAfterFirst} 并广播` : `期望 ${likeBefore + 1}，实际 ${likeAfterFirst}`
  )

  const rapidStart = likeAfterFirst
  for (let i = 0; i < 10; i += 1) {
    clientA2.send(JSON.stringify({ type: 'like', userId }))
  }
  await waitFor(() => latestLikeCount(clientB2) >= rapidStart + 10)
  const likeAfterRapid = (await request(`/live/rooms/${roomId}/like`)).data?.likeCount ?? 0
  record(
    summary,
    'TC-UC10-003',
    '快速连续点赞',
    likeAfterRapid === rapidStart + 10 ? '通过' : '失败',
    likeAfterRapid === rapidStart + 10 ? `连续 10 次点赞，数量从 ${rapidStart} 变为 ${likeAfterRapid}` : `期望 ${rapidStart + 10}，实际 ${likeAfterRapid}`
  )

  clientA2.close()
  await sleep(500)
  const clientA3 = await openClient(roomId, 'A3')
  await sleep(600)
  const reconnectedLikeCount = latestLikeCount(clientA3)
  record(
    summary,
    'TC-UC10-005',
    '点赞后断线重连恢复数量',
    reconnectedLikeCount === likeAfterRapid ? '通过' : '失败',
    reconnectedLikeCount === likeAfterRapid ? `重连后收到最新点赞数 ${reconnectedLikeCount}` : `期望 ${likeAfterRapid}，实际 ${reconnectedLikeCount}`
  )

  let concurrencyDetail = ''
  try {
    const [c1, c2] = await Promise.all([
      openClient(roomId, 'C1'),
      openClient(roomId, 'C2')
    ])
    await sleep(1200)
    const bothOpen = c1.readyState === WebSocket.OPEN && c2.readyState === WebSocket.OPEN
    const gotInitial = c1.messages.some((message) => message.type === 'online_count')
      && c2.messages.some((message) => message.type === 'online_count')
    record(
      summary,
      'TC-UC09-008',
      '多个客户端同时接入 WebSocket',
      bothOpen && gotInitial ? '通过' : '失败',
      bothOpen && gotInitial ? '两个并发连接均正常' : `连接状态 C1=${c1.readyState}，C2=${c2.readyState}，收到初始推送=${gotInitial}`
    )
  } catch (error) {
    concurrencyDetail = error.message
    record(summary, 'TC-UC09-008', '多个客户端同时接入 WebSocket', '失败', `并发接入异常：${concurrencyDetail}`)
  }

  const concurrentClients = []
  try {
    for (let i = 0; i < 50; i += 1) {
      const client = await openClient(roomId, `L${i}`)
      concurrentClients.push(client)
      await sleep(20)
    }
    const likeStart = (await request(`/live/rooms/${roomId}/like`)).data?.likeCount ?? 0
    concurrentClients.forEach((client) => client.send(JSON.stringify({ type: 'like', userId })))
    await waitFor(async () => (
      (await request(`/live/rooms/${roomId}/like`)).data?.likeCount === likeStart + 50
    ), 15000)
    const likeAfterConcurrent = (await request(`/live/rooms/${roomId}/like`)).data?.likeCount ?? 0
    record(
      summary,
      'TC-UC10-004',
      '多用户并发点赞',
      likeAfterConcurrent === likeStart + 50 ? '通过' : '失败',
      likeAfterConcurrent === likeStart + 50 ? `50 个客户端各点赞 1 次，数量从 ${likeStart} 变为 ${likeAfterConcurrent}` : `期望 ${likeStart + 50}，实际 ${likeAfterConcurrent}`
    )
  } catch (error) {
    record(
      summary,
      'TC-UC10-004',
      '多用户并发点赞',
      '失败',
      `并发点赞测试异常：${error.message}`
    )
  } finally {
    concurrentClients.forEach((client) => client.close())
  }

  record(summary, 'TC-UC09-006', '连接未建立时发送弹幕', '未执行', '前端 alert 行为需要浏览器验证')
  record(summary, 'TC-UC09-007', '未登录时发送弹幕', '未执行', '登录弹窗需要浏览器验证')
  record(summary, 'TC-UC10-002', 'WebSocket 未连接时保持原数量并提示', '未执行', '前端提示需要浏览器验证')

  console.log(JSON.stringify({ roomId, summary }, null, 2))
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
