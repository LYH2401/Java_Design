import api from './index'

// RAG vs Non-RAG 对比 (GET with query param)
export const compareRagVsNonRag = (question) =>
  api.get('/eval/compare', { params: { question } })

// RAG 使用统计
export const getEvalStats = () =>
  api.get('/eval/stats')
