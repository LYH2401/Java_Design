import { createRouter, createWebHashHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'
import EvalView from '../views/EvalView.vue'
import SolverView from '../views/SolverView.vue'

const routes = [
  { path: '/', name: 'chat', component: ChatView },
  { path: '/solver', name: 'solver', component: SolverView },
  { path: '/repair', name: 'repair', component: () => import('../views/RepairView.vue') },
  { path: '/eval', name: 'eval', component: EvalView },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue') },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('campus_token')
  if (to.path !== '/login' && to.path !== '/register' && !token) {
    return { path: '/login' }
  }
})

export default router
