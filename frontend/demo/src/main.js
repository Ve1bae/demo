import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Home from './views/Home.vue'
import CreatorCenter from './views/CreatorCenter.vue'

const routes = [
  { path: '/', name: 'Home', component: Home },
  { path: '/creator', name: 'CreatorCenter', component: CreatorCenter },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const app = createApp(App)
app.use(router)
app.mount('#app')
