import { createRouter, createWebHashHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'

const routes = [
  { path: '/', name: 'chat', component: ChatView },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
