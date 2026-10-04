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
 * 独立挂载（无外壳）判定：只有顶层 `/403`（name '无权限'）在 Console 之外。
 * 壳内的 `:catchAll(.*)` 没有 name，父路由 name 是 'console'。
 * 用途只有一个 —— 独立页要自己铺满一屏（min-height:100vh），壳内页交给滚动区，
 * 不需要（也不该）再撑一屏高，见 style 里的注释。
 */
const isStandalone = computed(() => route.name === '无权限')

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
  <div
    class="error-page"
    :class="{ 'error-page--standalone': isStandalone }"
  >
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
/**
 * ## 为什么这里曾经是 `position: fixed` 铺满视口 —— 以及为什么必须改掉
 * 本页有两种挂载方式：独立页（`/403`，没有外壳）和**壳内**页（`/` 下的 `:catchAll(.*)`，
 * 渲染在 KAppShell 的滚动区里）。fixed 铺满在两种挂载下都成立，但壳内时它同时踩中三个坑
 * （真机实测，用户原话「滚轮没反应 / 页面内容看不全 / 底部栏不见了」）：
 * 1. fixed 层盖住侧栏、顶栏和 `.k-shell__footer` —— 底部栏「消失」；
 * 2. fixed 层自身不是滚动容器，也不给外层滚动区贡献 scrollHeight —— 滚轮无效；
 * 3. `justify-content: center` 在内容高于容器时**两端都裁**（flex 溢出经典坑），
 *    「返回首页」链接正好在被裁掉的那一端，短视口下永远点不到 —— 内容「丢失」。
 * 改成普通文档流后：壳内它就是滚动区里的一段内容（页脚/侧栏都在，滚轮自然有效）；
 * 独立页时 min-height:100vh 依旧铺满整屏，观感不变。
 * 居中一律用 `margin: auto` 而不是 `justify-content: center`：内容超高时 auto 边距
 * 归零、从顶部开始可滚，永不裁剪。
 */
.error-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  box-sizing: border-box;
  padding: 24px 16px;
  /* 走 EP 页面底色变量，深浅色自动适配 */
  background-color: var(--el-bg-color-page, #f5f7fa);

  /* 独立页（无外壳，目前只有 /403 本身）：自己铺满一屏，垂直居中交给 .box 的 margin: auto */
  &--standalone {
    min-height: 100vh;
  }

  .box {
    width: 1200px;
    max-width: 90vw;
    display: flex;
    flex-direction: column;
    align-items: center;
    margin: auto;

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
