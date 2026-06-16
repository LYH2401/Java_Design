import { createRouter, createWebHashHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'
import EvalView from '../views/EvalView.vue'
import SolverView from '../views/SolverView.vue'

const routes = [
  { path: '/', name: 'chat', component: ChatView },
  { path: '/solver', name: 'solver', component: SolverView },
  { path: '/repair', name: 'repair', component: () => import('../views/RepairView.vue') },
  { path: '/eval', name: 'eval', component: EvalView },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
