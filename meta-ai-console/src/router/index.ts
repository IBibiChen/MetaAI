import {createRouter, createWebHistory} from 'vue-router'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
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
            path: '/embed/chat',
            name: 'embedded-chat',
            component: () => import('@/views/EmbeddedChatWorkspace.vue'),
            meta: {
                embedded: true,
            },
        },
        {
            path: '/documents',
            name: 'documents',
            component: () => import('@/views/KnowledgeDocuments.vue'),
        },
    ],
})

export default router
