<template>
  <n-config-provider :theme="darkTheme" :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN">
    <n-message-provider container-class="center-message-container">
      <n-dialog-provider>
        <n-layout class="app-shell" has-sider>
          <n-layout-sider
              bordered
              collapse-mode="width"
              :collapsed-width="72"
              :width="232"
              class="app-sider"
          >
            <div class="brand">
              <div class="brand-mark">M</div>
              <div class="brand-text">
                <strong>MetaAI</strong>
                <span>Console</span>
              </div>
            </div>
            <n-menu
                :value="activeMenu"
                :options="menuOptions"
                @update:value="handleNavigate"
            />
          </n-layout-sider>

          <n-layout>
            <n-layout-header class="app-header">
              <div>
                <div class="page-title">{{ routeTitle }}</div>
                <div class="page-subtitle">{{ workspace.chatId }}</div>
              </div>
              <div class="workspace-bar">
                <n-input
                    v-model:value="workspace.tenantId"
                    placeholder="租户 ID"
                    size="small"
                    @blur="workspace.persist"
                />
                <n-input
                    v-model:value="workspace.userId"
                    placeholder="用户 ID"
                    size="small"
                    @blur="workspace.persist"
                />
                <n-input
                    v-model:value="workspace.deptIds"
                    placeholder="部门 ID，多个用英文逗号分隔"
                    size="small"
                    @blur="workspace.persist"
                />
                <n-input
                    v-model:value="workspace.knowledgeBaseId"
                    placeholder="知识库 ID"
                    size="small"
                    @blur="workspace.persist"
                />
                <n-button size="small" secondary @click="workspace.applyContext">
                  应用配置
                </n-button>
                <n-button size="small" tertiary @click="workspace.resetDefaults">
                  重置默认
                </n-button>
              </div>
            </n-layout-header>
            <n-layout-content class="app-content">
              <router-view/>
            </n-layout-content>
          </n-layout>
        </n-layout>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import {computed, h} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'
import {Bot, Database} from 'lucide-vue-next'
import {
  darkTheme,
  dateZhCN,
  NIcon,
  zhCN,
  type MenuOption,
  type GlobalThemeOverrides,
} from 'naive-ui'

import {useWorkspaceStore} from '@/stores/workspace'

const route = useRoute()
const router = useRouter()
const workspace = useWorkspaceStore()
workspace.persist()

function renderIcon(icon: typeof Bot) {
  return () => h(NIcon, null, {default: () => h(icon)})
}

const menuOptions: MenuOption[] = [
  {
    label: () => h(RouterLink, {to: '/chat'}, {default: () => '聊天工作台'}),
    key: 'chat',
    icon: renderIcon(Bot),
  },
  {
    label: () => h(RouterLink, {to: '/documents'}, {default: () => '知识库文件'}),
    key: 'documents',
    icon: renderIcon(Database),
  },
]

const activeMenu = computed(() => route.name?.toString() || 'chat')
const routeTitle = computed(() => (route.name === 'documents' ? '知识库文件' : '聊天工作台'))

function handleNavigate(key: string) {
  router.push(key === 'documents' ? '/documents' : '/chat')
}

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#41d6b7',
    primaryColorHover: '#63e6ca',
    primaryColorPressed: '#24b99a',
    borderRadius: '6px',
    bodyColor: '#0d1117',
    cardColor: '#151a21',
    modalColor: '#151a21',
  },
}
</script>

<style>
.center-message-container.n-message-container {
  top: 50%;
  right: 0;
  left: 0;
  transform: translateY(-50%);
}

.center-message-container .n-message-wrapper {
  justify-content: center;
}
</style>
