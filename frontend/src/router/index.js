import { createRouter, createWebHashHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'
import EvalView from '../views/EvalView.vue'

const routes = [
  { path: '/', name: 'chat', component: ChatView },
  { path: '/eval', name: 'eval', component: EvalView },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
