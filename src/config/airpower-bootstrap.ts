/**
 * # airpower 运行时初始化（懒加载）
 *
 * 首屏（main.ts + 登录页）不再静态引入 `@airpower/web` 单体，airpower 的全局配置
 * （`WebConfig`）与整包 CSS 延迟到**业务页首次进入**时初始化。
 *
 * 由 `src/component/Console.vue`（懒加载的主布局）在最顶部 import，保证在业务页
 * 任何 airpower 组件渲染 / Service 调用之前完成：
 *   - `WebConfig.*` 全局配置（appKey / product / uploadUrl / staticUrl / elementPlusLocale）
 *   - `web.css`（经 airpower-css-strip 插件剥离 element 冗余后的 airpower 样式）
 *
 * airpower 模块顶层不读 WebConfig（配置均在组件 setup / Service 调用时读取），
 * 因此 import 顺序不影响正确性，只需保证本模块在业务页渲染前执行即可。
 */
import { WebConfig } from '@airpower/web'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import '@airpower/web/dist/web.css'

WebConfig.elementPlusLocale = zhCn
WebConfig.appKey = 'kubo'
WebConfig.product = 'Kubo'
WebConfig.uploadUrl = '/api/file/upload'
WebConfig.staticUrl = '/static/'
