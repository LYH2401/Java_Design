import api from './index'

export const compareRagVsNonRag = (data) =>
  api.post('/evaluation/compare', data)

export const getEvalStats = () =>
  api.get('/evaluation/stats')
