import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve('SPMS-Server/src/main/java/cn/hamm/spms')
const resources = [
  ['asset/material', 'Material'],
  ['asset/contract', 'Contract'],
  ['channel/customer', 'Customer'],
  ['channel/supplier', 'Supplier'],
  ['channel/purchase', 'Purchase'],
  ['channel/sale', 'Sale'],
  ['channel/purchaseprice', 'PurchasePrice'],
  ['channel/saleprice', 'SalePrice'],
  ['factory/storage', 'Storage'],
  ['factory/structure', 'Structure'],
  ['wms/input', 'Input'],
  ['wms/output', 'Output'],
  ['wms/move', 'Move'],
  ['wms/inventory', 'Inventory'],
] as const

function code(file: string) {
  return readFileSync(resolve(root, file), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

describe('b2b · CSV 换实现不得绕过既有权限/脱敏/回退路径', () => {
  it.each(resources)('%s 取数与遮蔽仍在任务创建之前，只有已遮蔽行交给新编码器', (directory, name) => {
    const service = code(`module/${directory}/${name}Service.java`)
    const start = service.indexOf(`createMapperExportTask(`)
    expect(start).toBeGreaterThanOrEqual(0)
    const body = service.slice(start)
    const select = body.indexOf('.selectForExport(')
    const mask = body.indexOf('ExportColumnMasker.maskForExport(rows)')
    const task = body.indexOf('exportHelper.createExportTask(')
    const serialize = body.indexOf(`EntityCsvExport.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), ${name}Entity.class)`)
    const save = body.indexOf('exportHelper.saveExportFileStream(stream)')
    expect(select).toBeGreaterThanOrEqual(0)
    expect(mask).toBeGreaterThan(select)
    expect(task).toBeGreaterThan(mask)
    expect(serialize).toBeGreaterThan(task)
    expect(save).toBeGreaterThan(serialize)
    expect(service).not.toContain('CollectionUtil')

    const controller = code(`module/${directory}/${name}Controller.java`)
    expect(controller).toMatch(/if\s*\(!service\.isMapperReadEnabled\(\)\)\s*\{\s*return super\.export\(request\);/)
    expect(controller).toContain('Curd.Export.checkApiAvailable(this)')
    expect(controller).toContain('service.createMapperExportTask(request)')
  })

  it('jPA 回退仍保留遮蔽与公式防护，不能因新编码器只接 Mapper 就删掉旧保护', () => {
    expect(code('base/BaseService.java'))
      .toContain('CsvFormulaSanitizer.sanitize(ExportColumnMasker.maskForExport(entities))')
    expect(code('common/CsvFormulaSanitizer.java')).toContain('CsvText.protectFormula(value)')
  })
})
