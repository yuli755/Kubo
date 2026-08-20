import type { Ref } from 'vue'
import type { CodeRuleEntity, CodeRuleField, CodeRuleParam, CodeRuleQuery } from '@/api/system/types'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { codeRuleApi } from '@/api/system'

/**
 * # System 编码规则列表查询 Hook
 */
export function useCodeRuleList(query: Ref<CodeRuleQuery> | CodeRuleQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: CodeRuleEntity[], total: number }>({
    queryKey: ['system:coderule', 'list', resolvedQuery],
    queryFn: () => codeRuleApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # System 编码规则详情查询 Hook
 */
export function useCodeRuleDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<CodeRuleEntity>({
    queryKey: ['system:coderule', 'detail', id],
    queryFn: () => codeRuleApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # System 编码规则字段列表查询 Hook
 */
export function useCodeRuleFieldList() {
  return useQuery<CodeRuleField[]>({
    queryKey: ['system:coderule', 'fieldList'],
    queryFn: () => codeRuleApi.getFieldList(),
  })
}

/**
 * # System 编码规则参数列表查询 Hook
 */
export function useCodeRuleParamList() {
  return useQuery<CodeRuleParam[]>({
    queryKey: ['system:coderule', 'paramList'],
    queryFn: () => codeRuleApi.getParamList(),
  })
}