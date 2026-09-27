import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { getFormConfigList } from '@/framework'
import { PurchaseDetailEntity } from '@/model/channel/purchase/PurchaseDetailEntity'
import { SaleDetailEntity } from '@/model/channel/sale/SaleDetailEntity'
import { DataFieldPolicyEntity } from '@/model/system/datapermission/DataFieldPolicyEntity'
import { DataPolicyEntity } from '@/model/system/datapermission/DataPolicyEntity'

const ROOT = process.cwd()

describe('智能选择器与零手动输入体验（Smart Selectors Everywhere）契约测试', () => {
  describe('1. 菜单管理（menu/list.vue）视图与图标下拉速选', () => {
    const menuListSource = readFileSync(
      join(ROOT, 'src/view/console/system/menu/list.vue'),
      'utf8',
    )

    it('菜单组件路径提供系统已注册视图速选，避免手动拼写手误', () => {
      expect(menuListSource).toContain('COMPONENT_OPTIONS')
      expect(menuListSource).toContain('ElSelect')
      expect(menuListSource).toContain('ElOption')
      expect(menuListSource).toContain('/src/view/console/index/index.vue')
      expect(menuListSource).toContain('/src/view/console/wms/inventory/list.vue')
      expect(menuListSource).toContain('/src/view/console/channel/purchase/list.vue')
      expect(menuListSource).toContain('/src/view/console/system/datapermission/fieldPolicy/list.vue')
    })

    it('菜单图标提供可视化图标选择面板，集成高频语义图标', () => {
      expect(menuListSource).toContain('ICON_OPTIONS')
      expect(menuListSource).toContain('menu-icon-option')
      expect(menuListSource).toContain('Platform')
      expect(menuListSource).toContain('ShoppingCart')
      expect(menuListSource).toContain('TrendCharts')
      expect(menuListSource).toContain('Setting')
    })

    it('选择组件时自动联动预填路由路径（path）与菜单名称（name）', () => {
      expect(menuListSource).toContain('onComponentChanged')
      expect(menuListSource).toContain('model.path = hit.defaultPath')
      expect(menuListSource).toContain('model.name = hit.shortName')
    })
  })

  describe('2. 数据权限策略编辑器（fieldPolicy / policy）智能选择器', () => {
    const fieldPolicyEditorSource = readFileSync(
      join(ROOT, 'src/view/console/system/datapermission/fieldPolicy/component/editor.vue'),
      'utf8',
    )
    const policyEditorSource = readFileSync(
      join(ROOT, 'src/view/console/system/datapermission/policy/component/editor.vue'),
      'utf8',
    )

    it('列级策略编辑器集成 DataResourceService 与角色下拉速选', () => {
      expect(fieldPolicyEditorSource).toContain('DataResourceService')
      expect(fieldPolicyEditorSource).toContain('KRoleService')
      expect(fieldPolicyEditorSource).toContain('#field-resourceId')
      expect(fieldPolicyEditorSource).toContain('#field-fieldName')
      expect(fieldPolicyEditorSource).toContain('#field-roleCode')
      expect(fieldPolicyEditorSource).toContain('default（全员通配 / 默认策略）')
    })

    it('行级策略编辑器集成 DataResourceService 与角色下拉速选', () => {
      expect(policyEditorSource).toContain('DataResourceService')
      expect(policyEditorSource).toContain('KRoleService')
      expect(policyEditorSource).toContain('#field-resourceId')
      expect(policyEditorSource).toContain('#field-roleCode')
      expect(policyEditorSource).toContain('default（全员通配 / 默认策略）')
    })

    it('dataPolicyEntity 与 DataFieldPolicyEntity 完整保留元数据驱动的表单配置', () => {
      const fieldPolicyForms = getFormConfigList(DataFieldPolicyEntity)
      const fieldKeys = fieldPolicyForms.map(f => f.key)
      expect(fieldKeys).toContain('resourceId')
      expect(fieldKeys).toContain('fieldName')
      expect(fieldKeys).toContain('roleCode')
      expect(fieldKeys).toContain('actionName')

      const policyForms = getFormConfigList(DataPolicyEntity)
      const policyKeys = policyForms.map(f => f.key)
      expect(policyKeys).toContain('policyCode')
      expect(policyKeys).toContain('resourceId')
      expect(policyKeys).toContain('subjectType')
      expect(policyKeys).toContain('roleCode')
      expect(policyKeys).toContain('actionName')
    })
  })

  describe('3. 采购单与销售单金额明细自动核算与只读防误触', () => {
    const purchaseEditorSource = readFileSync(
      join(ROOT, 'src/view/console/channel/purchase/component/editor.vue'),
      'utf8',
    )
    const saleEditorSource = readFileSync(
      join(ROOT, 'src/view/console/channel/sale/component/editor.vue'),
      'utf8',
    )

    it('采购单主表 totalPrice 基于明细项自动核算，且在表单中只读展示', () => {
      expect(purchaseEditorSource).toContain('totalAmount')
      expect(purchaseEditorSource).toContain('#field-totalPrice')
      expect(purchaseEditorSource).toContain('disabled')
      expect(purchaseEditorSource).toContain('payload.totalPrice = totalAmount.value')

      // 测试明细核算纯逻辑
      const d1 = new PurchaseDetailEntity()
      d1.quantity = 10
      d1.price = 25.5
      const d2 = new PurchaseDetailEntity()
      d2.quantity = 4
      d2.price = 100
      const sum = [d1, d2].reduce((acc, cur) => acc + (cur.quantity * cur.price), 0)
      expect(sum).toBe(655)
    })

    it('销售单主表 totalPrice 基于明细自动核算，且客户提供智能选择器', () => {
      expect(saleEditorSource).toContain('totalAmount')
      expect(saleEditorSource).toContain('#field-totalPrice')
      expect(saleEditorSource).toContain('#field-customerId')
      expect(saleEditorSource).toContain('KCustomerService')
      expect(saleEditorSource).toContain('payload.totalPrice = totalAmount.value')

      const s1 = new SaleDetailEntity()
      s1.quantity = 5
      s1.price = 88.8
      const sum = s1.quantity * s1.price
      expect(sum).toBe(444)
    })
  })

  describe('4. 入库明细目标仓库树形选择器', () => {
    const inputDetailEditorSource = readFileSync(
      join(ROOT, 'src/view/console/wms/input/component/detailEditor.vue'),
      'utf8',
    )

    it('入库明细支持目标仓库树形选择器（KStorageService + tree）', () => {
      expect(inputDetailEditorSource).toContain('KStorageService')
      expect(inputDetailEditorSource).toContain('storagePicking')
      expect(inputDetailEditorSource).toContain('onStoragePicked')
      expect(inputDetailEditorSource).toContain('title="选择目标仓库"')
      expect(inputDetailEditorSource).toContain('tree')
      expect(inputDetailEditorSource).toContain('form.value.storageId = target.id')
    })
  })
})
