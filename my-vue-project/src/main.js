import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(ElementPlus, { locale: zhCn })
app.use(router)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}

// 忽略无害的 ResizeObserver 循环错误（ECharts 在隐藏容器中渲染时会触发，不影响功能）
window.addEventListener('error', e => {
  if (e.message && e.message.includes('ResizeObserver')) {
    e.stopImmediatePropagation()
    return false
  }
})

app.mount('#app')
