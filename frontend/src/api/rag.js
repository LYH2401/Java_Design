import api from './index'

export const ragChat = (conversationId, message) => {
  return fetch('/api/rag/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message })
  })
}

export const getSources = (keyword) =>
  api.get('/knowledge/search', { params: { keyword, limit: 5 } })
