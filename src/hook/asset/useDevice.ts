import type { Ref } from 'vue'
import type { DeviceEntity, DeviceQuery } from '@/api/asset/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { deviceApi } from '@/api/asset'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 设备的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发（iot/monitor 等跨模块视图也从这里取）。
export { DeviceService } from '@/model/asset/device/DeviceService'

export function useDeviceList(query: Ref<DeviceQuery> | DeviceQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: DeviceEntity[], total: number }>({
    queryKey: ['asset:device', 'list', resolvedQuery],
    queryFn: () => deviceApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function useDeviceDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<DeviceEntity>({
    queryKey: ['asset:device', 'detail', id],
    queryFn: () => deviceApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function useDeviceDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deviceApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['asset:device'] }) },
  })
}
