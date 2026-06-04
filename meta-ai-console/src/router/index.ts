import {createRouter, createWebHistory} from 'vue-router'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',
            redirect: '/chat',
        },
        {
            path: '/chat',
            name: 'chat',
            component: () => import('@/views/ChatWorkspace.vue'),
        },
        {
            path: '/documents',
            name: 'documents',
            component: () => import('@/views/KnowledgeDocuments.vue'),
        },
    ],
})

export default router
