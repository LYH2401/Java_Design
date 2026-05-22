import { createRouter, createWebHashHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'
import EvalView from '../views/EvalView.vue'
import SolverView from '../views/SolverView.vue'

const routes = [
  { path: '/', name: 'chat', component: ChatView },
  { path: '/eval', name: 'eval', component: EvalView },
  { path: '/solver', name: 'solver', component: SolverView },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
