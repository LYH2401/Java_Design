import api from './index'

export const streamChat = (conversationId, message, mode = 'agent', model = 'dashscope') => {
  const url = mode === 'agent'
    ? '/agent/chat'
    : mode === 'rag-agent'
      ? '/agent/chat/with-rag'
      : mode === 'rag'
        ? '/rag/chat'
        : '/chat/stream'

  return fetch(`/api${url}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message, model })
  })
}

export const getConversations = () => api.get('/conversations')

export const createConversation = (mode = 'NORMAL') =>
  api.post('/conversations', null, { params: { mode } })

export const getMessages = (conversationId) =>
  api.get(`/conversations/${conversationId}/messages`)

export const deleteConversation = (id) =>
  api.delete(`/conversations/${id}`)

export const updateConversationTitle = (id, title) =>
  api.post(`/conversations/${id}/title`, null, { params: { title } })
