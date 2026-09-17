// 编辑弹窗已不再需要独立组件：新底座页面用 `KFormDialog` 按 `@Form` 元数据自动生成表单
// （原来这里是 `StructureEditor.vue`，只是把 `useEditor` + `ADialog` + `AFormField` 包了一层）。
// 选择器仍在：wms 的入库/移库页还是 airpower 组件，等 F4 迁完 wms 域再换成 KSelector。
import StructureSelector from './selector.vue'

export { StructureSelector }
