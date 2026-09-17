<script lang="ts" setup>
import { computed, onScopeDispose, ref, watch } from 'vue'
import { useAppRoute } from '@/router/appRouter'

/**
 * # 错误页（403 / 404 / 500 兜底）
 *
 * ## 这个页面以前有个坑，别再把坑搬回来
 * 旧实现是本页停留 5 秒就把 airpower 的全局熔断开关 `WebConfig.isTimeout` 置真，
 * 而 `RouterUtil.initVueRouter()` 开头会判这个标志位 —— 一旦置真，**动态菜单路由永久不再注册**
 * （没有任何地方复位的），于是「进错一次页面 → 整个控制台点哪都是 404」。
 * 症状复现成本极高（要刷新整个页面才可能自愈），所以这里记一笔：
 * **错误页只许管自己的展示状态**，任何"全局开关"式的兜底都不该由它来拨。
 *
 * ## 现在的语义（比旧版更准）
 * 旧版是「先等 5 秒，超时了就说服务没响应」，于是访问一个不存在的地址也要先转 5 秒圈、
 * 最后还显示 504 —— 那是 airpower 的超时兜底模型。现在按**路径**直接判：
 * `/403`、`/404`、`/500` 各自成页，其余（catch-all 接住的一切）就是 404。
 * 用户立刻看到结论，且不再有"错误页把人卡住 5 秒"的观感。
 *
 * 页面内所有状态都是本页私有 —— 不读也不写任何全局标志位。
 */
const route = useAppRoute()

const MESSAGES: Record<number, { title: string, desc: string }> = {
  403: { title: '很抱歉，你可能无权访问此服务', desc: '请确认你的身份权限，或' },
  404: { title: '很抱歉，没有找到你想访问的资源', desc: '请确认你的访问地址是否正确，或' },
  500: { title: '很抱歉，服务器发生了一点小错误', desc: '服务器可能正在维护中，或' },
}

/** 从路径里取错误码：`/403` → 403；非数字或不在表内一律 404 */
const errorCode = ref(parseCode(route.path))

function parseCode(path: string): number {
  const code = Number(path.replace(/^\//, '').split('/')[0])
  return MESSAGES[code] ? code : 404
}

const message = computed(() => MESSAGES[errorCode.value])

/**
 * 路径变化时重新判码。
 *
 * `useAppRoute()` 返回的是响应式代理，但这里用 `watch` 而不是在模板里直算：
 * catch-all 路由之间跳转不会重建组件实例，直算会一直显示进入时那个码。
 */
const stopWatch = watch(() => route.path, (path) => {
  errorCode.value = parseCode(path)
})
onScopeDispose(stopWatch)
</script>

<template>
  <div class="error-page">
    <div class="box">
      <div class="code">
        {{ errorCode }}
      </div>
      <div class="title">
        {{ message.title }}
      </div>
      <div class="description">
        {{ message.desc }}
        <router-link to="/">
          返回首页
        </router-link>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.error-page {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  /* 走 EP 页面底色变量，深浅色自动适配 */
  background-color: var(--el-bg-color-page, #f5f7fa);

  .box {
    width: 1200px;
    max-width: 90vw;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    margin-bottom: 100px;

    .code {
      color: var(--el-color-primary);
      font-size: 200px;
      font-weight: bold;
    }

    .title {
      font-size: 20px;
      color: var(--el-text-color-primary);
    }

    .description {
      font-size: 14px;
      color: var(--el-text-color-secondary);
      margin-top: 10px;

      a {
        color: var(--el-color-primary);
        text-decoration: none;
      }

      a:hover {
        text-decoration: underline;
        font-weight: bold;
      }
    }
  }
}
</style>
