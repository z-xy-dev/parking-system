import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  { path: '/register', name: 'Register', component: () => import('../views/Register.vue') },
  { path: '/home', name: 'Home', component: () => import('../views/Home.vue') },
  { path: '/detail/:id', name: 'Detail', component: () => import('../views/Detail.vue') },
  { path: '/my-orders', name: 'MyOrders', component: () => import('../views/MyOrders.vue') },
  { path: '/my-parking', name: 'MyParking', component: () => import('../views/MyParking.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
