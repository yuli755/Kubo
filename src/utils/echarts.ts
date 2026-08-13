/**
 * ECharts 按需加载封装
 *
 * 仅注册 history.vue 实际使用的图表与组件，避免全量引入 echarts（原 2.77MB）。
 * 通过动态 import() 触发代码分割，使 ECharts 进入独立 chunk 并懒加载，
 * 仅当用户打开「设备历史」对话框且参数为数值类型时才会被下载。
 */
import type { EChartsType } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

// 仅注册一次所需模块，后续调用被 echarts 内部去重
use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

export { init }
export type { EChartsType }
