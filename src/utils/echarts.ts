/**
 * ECharts 按需加载封装
 *
 * 仅注册实际使用的图表与组件，避免全量引入 echarts（原 2.77MB）。
 * 通过动态 import() 触发代码分割，使 ECharts 进入独立 chunk 并懒加载，
 * 仅当用户打开「设备历史」对话框、或首页「数据权限安全态势」看板时才被下载。
 *
 * 注册面（当前三处消费者）：
 * - `history.vue` 设备历史曲线 → LineChart + GridComponent + TooltipComponent
 * - `SecurityDashboard.vue` 档位分布饼图 → PieChart + LegendComponent
 * - `SecurityDashboard.vue` ORM 对账条形图 → BarChart + GridComponent + LegendComponent
 */
import type { EChartsType } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

// 仅注册一次所需模块，后续调用被 echarts 内部去重
use([LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

export { init }
export type { EChartsType }
