import {createPinia} from 'pinia'
import naive from 'naive-ui'
import {createApp} from 'vue'

import App from './App.vue'
import router from './router'
import './styles/global.css'

createApp(App)
    .use(createPinia())
    .use(naive)
    .use(router)
    .mount('#app')
