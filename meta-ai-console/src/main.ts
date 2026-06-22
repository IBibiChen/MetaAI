import { createPinia } from 'pinia'
import naive from 'naive-ui'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import './styles/global.css'

const embeddedEntry = window.location.pathname.includes('/embed/')
document.documentElement.classList.toggle('embedded-entry', embeddedEntry)

const app = createApp(App)

app.use(createPinia())
app.use(naive)
app.use(router)

// 首屏等待路由解析完成，避免嵌入页短暂渲染普通控制台外壳
router.isReady().then(() => {
  app.mount('#app')
})
