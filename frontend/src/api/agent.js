import api from './index'

export const agentChat = (conversationId, message, withRag = false) => {
  const url = withRag ? '/agent/chat/with-rag' : '/agent/chat'
  return fetch(`/api${url}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message })
  })
}

export const getTools = () => api.get('/agent/tools')

export const getAgentStats = () => api.get('/agent/stats')

export const getExecutionLogs = (page = 1, pageSize = 20) =>
  api.get('/agent/execution-logs', { params: { page, pageSize } })
