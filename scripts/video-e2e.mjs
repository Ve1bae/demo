#!/usr/bin/env node

const baseUrl = (process.env.E2E_API_BASE || 'http://127.0.0.1:8080/api').replace(/\/+$/, '')
const videoId = Number(process.env.E2E_VIDEO_ID || 1)
const userId = Number(process.env.E2E_USER_ID || 1)

const assertThat = (condition, message) => {
  if (!condition) throw new Error(message)
}

const request = async (method, path, body) => {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(method === 'GET' ? {} : { 'X-User-Id': String(userId) })
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const json = await response.json()
  return { response, json }
}

const video = await request('GET', `/videos/${videoId}`)
assertThat(video.response.status === 200 && video.json.code === 200, 'UC-04 视频详情获取失败')
assertThat(video.json.data?.playUrl || video.json.data?.sources, 'UC-04 视频详情缺少播放源')

const played = await request('POST', `/videos/${videoId}/play`)
assertThat(played.response.status === 200 && played.json.code === 200, 'UC-04 播放计数请求失败')

const commentText = `UC05 E2E comment ${Date.now()}`
const comment = await request('POST', `/videos/${videoId}/comments`, { content: commentText, userId })
assertThat(comment.json.code === 200, `UC-05 评论提交失败：${comment.json.message}`)
const comments = await request('GET', `/videos/${videoId}/comments?page=1&pageSize=50`)
assertThat(comments.json.code === 200, 'UC-05 评论列表获取失败')
assertThat(JSON.stringify(comments.json.data).includes(commentText), 'UC-05 新评论未出现在列表')

const danmakuText = `UC06 E2E danmaku ${Date.now()}`
const danmaku = await request('POST', `/videos/${videoId}/danmakus`, {
  content: danmakuText,
  timeSeconds: 1.5,
  color: '#00ff00',
  userId
})
assertThat(danmaku.json.code === 200, `UC-06 弹幕提交失败：${danmaku.json.message}`)
const danmakus = await request('GET', `/videos/${videoId}/danmakus?startTime=0&endTime=60`)
assertThat(danmakus.json.code === 200, 'UC-06 弹幕列表获取失败')
assertThat(JSON.stringify(danmakus.json.data).includes(danmakuText), 'UC-06 新弹幕未出现在列表')

console.log('UC-04/05/06 HTTP E2E passed')
