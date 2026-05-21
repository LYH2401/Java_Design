import api from './index'

export const streamChat = (conversationId, message, mode = 'normal') => {
  const url = mode === 'agent'
    ? '/agent/chat'
    : mode === 'rag-agent'
      ? '/agent/chat/with-rag'
      : mode === 'rag'
        ? '/chat/rag'
        : '/chat/chat/stream'

  return fetch(`/api${url}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message })
  })
}

export const getConversations = () => api.get('/chat/conversations')

export const createConversation = () => api.post('/chat/conversations')

export const getMessages = (conversationId) =>
  api.get(`/chat/conversations/${conversationId}/messages`)

export const deleteConversation = (id) =>
  api.delete(`/chat/conversations/${id}`)

export const updateConversationTitle = (id, title) =>
  api.post(`/chat/conversations/${id}/title`, { title })
