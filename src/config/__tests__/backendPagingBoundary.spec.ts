import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve('SPMS-Server/src/main/java/cn/hamm/spms')
const resources = [
  ['asset/material', 'Material', 'filter', true],
  ['asset/contract', 'Contract', 'filter', false],
  ['channel/customer', 'Customer', 'filter', false],
  ['channel/supplier', 'Supplier', 'filter', false],
  ['channel/purchase', 'Purchase', 'filter.getBillCode(), filter.getReason(), filter.getStatus()', false],
  ['channel/sale', 'Sale', 'filter.getBillCode(), filter.getReason(), filter.getStatus()', false],
  ['channel/purchaseprice', 'PurchasePrice', 'filter', true],
  ['channel/saleprice', 'SalePrice', 'filter', true],
  ['factory/storage', 'Storage', 'filter', false],
  ['factory/structure', 'Structure', 'filter', false],
  ['wms/input', 'Input', 'filter.getBillCode(), filter.getStatus(), filter.getType()', false],
  ['wms/output', 'Output', 'filter.getBillCode(), filter.getStatus(), filter.getType()', false],
  ['wms/move', 'Move', 'filter.getBillCode(), filter.getStatus()', false],
  ['wms/inventory', 'Inventory', 'type, storageIds, structureIds', true],
] as const

function code(file: string) {
  return readFileSync(resolve(root, file), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
}

function pageBody(source: string) {
  const signature = source.indexOf(' getMapperPage(')
  expect(signature).toBeGreaterThanOrEqual(0)
  const start = source.indexOf('{', signature)
  let depth = 1
  let end = start + 1
  while (depth > 0 && end < source.length) {
    if (source[end] === '{')
      depth++
    else if (source[end] === '}')
      depth--
    end++
  }
  expect(depth).toBe(0)
  return source.slice(start, end).replace(/\s+/g, ' ')
}

describe('b4a · 分页收口不改变读路径与权限查询', () => {
  it.each(resources)('%s 保留回退、筛选、影子采样、关系补齐与响应边界', (directory, name, filter, hydrate) => {
    const source = code(`module/${directory}/${name}Service.java`)
    const body = pageBody(source)
    const prefix = name[0]!.toLowerCase() + name.slice(1)
    expect(source).not.toMatch(/import cn\.hamm\.airpower\.curd\.model\.query\.(?:Page|Sort);/)
    expect(body).toContain('if (!isMapperReadEnabled()) { return getPage(normalized); }')
    const fallback = body.indexOf('return getPage(normalized)')
    const normalize = body.indexOf('mapperReadPage(normalized.getPage())')
    const shadow = body.indexOf(`${prefix}ReadShadowService.submit(`)
    const select = body.indexOf(`${prefix}Mapper.selectPageByPermission(`)
    const count = body.indexOf(`${prefix}Mapper.countByPermission(`)
    const response = body.indexOf('return mapperPageResponse(')
    expect(fallback).toBeGreaterThanOrEqual(0)
    expect(normalize).toBeGreaterThan(fallback)
    expect(shadow).toBeGreaterThan(normalize)
    expect(select).toBeGreaterThan(shadow)
    expect(count).toBeGreaterThan(select)
    expect(response).toBeGreaterThan(count)
    expect(body).toContain(`${prefix}Mapper.selectPageByPermission(${filter}, paging.offset(), paging.pageSize())`)
    expect(body).toContain(`${prefix}Mapper.countByPermission(${filter})`)
    expect(body).toMatch(/mapperPageResponse\(paging, (?:rows|list), total, normalized\.getSort\(\)\)/)
    expect(body.includes('.map(this::hydrateRelations)')).toBe(hydrate)
    expect(body).not.toMatch(/Math\.toIntExact|new Page\(|new Sort\(|MAX_READ_PAGE_SIZE|DEFAULT_READ_PAGE_SIZE/)
  })

  it('base 响应边界保留旧 wire 字段，复用行/排序引用，创建新的 Page', () => {
    const source = code('base/BaseService.java')
    expect(source).toContain('ReadPage.from(page.getPageNum(), page.getPageSize())')
    expect(source).toContain('ReadPage.Counts counts = paging.counts(total)')
    expect(source).toContain('response.setList(rows)')
    expect(source).toContain('response.setTotal(counts.total())')
    expect(source).toContain('response.setPageCount(counts.pageCount())')
    expect(source).toContain('new Page().setPageNum(paging.pageNum()).setPageSize(paging.pageSize())')
    expect(source).toContain('response.setSort(requestedSort == null ? new Sort() : requestedSort)')
  })
})
