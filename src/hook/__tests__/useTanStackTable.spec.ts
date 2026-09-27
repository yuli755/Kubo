// @vitest-environment jsdom
import { VueQueryPlugin } from '@tanstack/vue-query'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { UnitService } from '@/hook/system/useUnit'
import { UnitEntity } from '@/model/system/unit/UnitEntity'

/**
 * 锁测：`useTanStackTable` 的查询请求构造（GUI-3 第二波根因回归锁）。
 *
 * 真机 GUI 发现：未传 `:default-filter` 的列表页整页崩溃
 * `Cannot convert undefined or null to object`。
 * 根因链：适配器曾把**实体实例**传给 `new QueryRequestPage(...)`，其构造器执行
 * `new FilterClass()` 抛错并被 try/catch 吞掉 → `request.filter` 恒 undefined →
 * airpower ATable 的 `onSearch` 里 `Object.keys(searchFilter.value)` 崩溃。
 *
 * 锁死：hook 暴露的 `request.value.filter` 必须是实体类实例。
 */
vi.mock('@/api/axios', () => ({
  default: {
    get: vi.fn(async () => ({ data: { list: [], total: 0 } })),
    post: vi.fn(async () => ({ data: {} })),
  },
}))

describe('useTanStackTable 查询请求构造', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('request.filter 初始化为实体实例（ATable onSearch 的 Object.keys 前提）', () => {
    let captured: ReturnType<typeof useTanStackTable<UnitEntity, UnitService>> | null = null
    const Probe = defineComponent({
      setup() {
        captured = useTanStackTable(UnitService, { queryKey: ['system:unit'] })
        return () => null
      },
    })
    mount(Probe, { global: { plugins: [createPinia(), VueQueryPlugin] } })
    expect(captured).not.toBeNull()
    expect(captured!.request.value.filter).toBeInstanceOf(UnitEntity)
  })
})
