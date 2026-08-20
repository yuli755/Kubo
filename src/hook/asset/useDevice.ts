import type { Ref } from 'vue'
import type { DeviceEntity, DeviceQuery } from '@/api/asset/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { deviceApi } from '@/api/asset'

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