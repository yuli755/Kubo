import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

/**
 * # 顶部导航进度条（D3 · 冷启动与懒加载的「有人在处理」反馈）
 *
 * ## 为什么需要它
 * 本项目路由组件由 `import.meta.glob` 懒加载（`Console.vue` → `initVueRouter`），
 * 慢网络下点菜单会有 1~3 秒的**无任何反馈**空档；冷启动更久（`Console.vue` 的
 * `routesReady` 注释实测约 5s）。空档期用户无法区分「在加载」与「卡死了」，
 * 于是刷新、再点、甚至关页面 —— 一个纯观感问题最终演变成重复请求与误操作。
 *
 * ## 为什么复用 nprogress 而不是自研
 * 0.2.0 已发布多年、API 冻结、gzip ~2KB、零依赖，且业界（含对标的
 * `law-firm-platform/law-firm-web/src/router/permission.js`）都这么用 —— 这类
 * 纯装饰控件没有自研价值。**版本冻结正是它的优点**：不会被上游改动打崩。
 *
 * ## 与对标实现的两处差异（都是它踩过的坑）
 * 1. **补 `router.onError` 收尾**：对标只在 `beforeEach` start、`afterEach`/各处
 *    `next('/xxx')` 分支手工 done。vue-router 的守卫**抛错**时不会走 `afterEach`
 *    （走 `router.onError`），漏掉 done 就是进度条永久停在 99% 的死条。
 *    这里 `afterEach` 无条件 done（取消/重定向也算失败导航，同样会触发 afterEach）
 *    + `onError` done —— 两条腿都收口，不依赖任何"每个 next 分支记得调"的人肉约定。
 * 2. **`minimum` 不给 0.3**：对标配了 0.3，意味着进度条一上来就"已经走了 30%"，
 *    慢请求时它反而长时间不动 —— 那是假进度，比没有更糟。取保守的 0.1，
 *    让"卡住不动"忠实反映"确实卡住"。
 *
 * ## 可访问性
 * `prefers-reduced-motion: reduce` 时用 CSS 整条隐藏（见 `styles/motion.scss`）：
 * 进度条的价值在**动画**，去掉动画只剩一条静止的线，对晕动症用户是纯噪声。
 * 同时给容器打 `aria-hidden` —— nprogress 的 DOM 没有任何进度语义，
 * 读屏器读到的是空气，不如明确告知"这里没有信息"。
 */

/** 配置项（默认值即生产取值，测试注入用） */
export interface NavProgressOptions {
  /** 起始最小进度（不给高，见文件头差异 2） */
  minimum?: number
  /** 自动 trickle 的间隔毫秒（越短越顺滑，200ms 是 nprogress 官方推荐区间） */
  trickleSpeed?: number
}

/** 是否已经安装过（重复调用会把守卫装两遍，进度条行为不变但白挂监听） */
let installed = false

/**
 * 安装顶部导航进度条。在 `createAppRouter` 之后、`app.use(router)` 前后均可。
 *
 * @return 卸载函数（测试用；生产不需要）
 */
export function installNavProgress(router: Router, options: NavProgressOptions = {}): () => void {
  if (installed) {
    return () => {}
  }
  installed = true

  NProgress.configure({
    showSpinner: false,
    trickleSpeed: options.trickleSpeed ?? 200,
    minimum: options.minimum ?? 0.1,
    // 速度：导航通常 1~3s，太快会"先满了还没到页"，太慢显得没反应
    speed: 400,
  })

  const beforeUnload = router.beforeEach(() => {
    NProgress.start()
    // nprogress 的容器是 start() 时才创建的，所以只能在 start 之后补 aria 标记
    document.getElementById('nprogress')?.setAttribute('aria-hidden', 'true')
  })
  // afterEach 对「成功 / 取消 / 中止 / 重定向」都会触发 ⇒ 无条件 done 即全覆盖
  const afterUnload = router.afterEach(() => {
    NProgress.done()
  })
  // 守卫抛错不会走 afterEach —— 缺这一条就是「永久 99% 死条」
  const errorUnload = router.onError(() => {
    NProgress.done()
  })

  return () => {
    installed = false
    beforeUnload()
    afterUnload()
    errorUnload()
    NProgress.remove()
  }
}

/** 仅供测试复位安装标志 */
export function __resetNavProgressForTests(): void {
  installed = false
}
