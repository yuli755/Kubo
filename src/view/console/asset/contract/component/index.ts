import ContractEditor from './editor.vue'
import ContractParticipantEditor from './editParticipant.vue'

// ⚠️ 批 2 的进度：`ContractDetail` 已切到新底座（KDetail + 按 id 重查）。
// `ContractEditor` / `ContractParticipantEditor` 仍是 airpower 组件 —— 编辑器要上传附件，
// 而框架层上传组件尚未实现（见 F4 文档「批 2B」），所以它们连同实体上的 airpower 装饰器一起留到 2B。

// `ContractDetail` 不在这里 re-export：`list.vue` 直接 import 那个 .vue（走 barrel 只会多一层、
// 还容易被死导出门禁误判）。这里只保留两个 airpower 编辑器（批 2B 一起换）。
export { ContractEditor, ContractParticipantEditor }
