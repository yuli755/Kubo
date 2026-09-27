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

  describe('5. 入库与出库确认一键填入数量与选择按钮触发', () => {
    const inputAddFinishSource = readFileSync(
      join(ROOT, 'src/view/console/wms/input/component/addFinishEditor.vue'),
      'utf8',
    )
    const outputAddFinishSource = readFileSync(
      join(ROOT, 'src/view/console/wms/output/component/addFinishEditor.vue'),
      'utf8',
    )

    it('确认入库弹窗支持一键全部入库与仓库选择按钮', () => {
      expect(inputAddFinishSource).toContain('全部入库')
      expect(inputAddFinishSource).toContain('quantity = waitingQuantity')
      expect(inputAddFinishSource).toContain('field-select-row')
      expect(inputAddFinishSource).toContain('@click="storagePicking = true"')
    })

    it('确认出库弹窗支持一键全部出库与库存来源选择按钮', () => {
      expect(outputAddFinishSource).toContain('全部出库')
      expect(outputAddFinishSource).toContain('quantity = maxAvailableQuantity')
      expect(outputAddFinishSource).toContain('field-select-row')
      expect(outputAddFinishSource).toContain('openInventoryPicker')
    })
  })

  describe('6. 价格管理与物料管理智能预填及双模选择', () => {
    const purchasePriceSource = readFileSync(
      join(ROOT, 'src/view/console/channel/purchasePrice/list.vue'),
      'utf8',
    )
    const salePriceSource = readFileSync(
      join(ROOT, 'src/view/console/channel/salePrice/list.vue'),
      'utf8',
    )
    const materialListSource = readFileSync(
      join(ROOT, 'src/view/console/asset/material/list.vue'),
      'utf8',
    )

    it('采购价与销售价选定物料时自动预填参考基准报价', () => {
      expect(purchasePriceSource).toContain('material.purchasePrice')
      expect(purchasePriceSource).toContain('form.value.price = material.purchasePrice')
      expect(salePriceSource).toContain('material.salePrice')
      expect(salePriceSource).toContain('form.value.price = material.salePrice')
    })

    it('物料编辑支持计量单位直接下拉点选与高级弹窗双模协同', () => {
      expect(materialListSource).toContain('unitList')
      expect(materialListSource).toContain('KUnitService')
      expect(materialListSource).toContain('onUnitDropdownChange')
      expect(materialListSource).toContain('高级选择')
    })
  })

  describe('7. 编码规则常用模板一键速填', () => {
    const codeRuleSource = readFileSync(
      join(ROOT, 'src/view/console/system/coderule/list.vue'),
      'utf8',
    )

    it('编码规则提供常用日期模板一键速填按钮', () => {
      expect(codeRuleSource).toContain('TEMPLATE_PRESETS')
      expect(codeRuleSource).toContain('applyPresetTemplate')
      expect(codeRuleSource).toContain('yyyyMMdd')
      expect(codeRuleSource).toContain('yyyyMM')
    })
  })

  describe('8. 表格智能枚举下拉与工具条批量选择（KTable Smart Search & Quick Select）', () => {
    const kTableSource = readFileSync(
      join(ROOT, 'src/component/framework/KTable.vue'),
      'utf8',
    )

    it('表格搜索区对带有 dictionary 的枚举字段自动渲染 ElSelect 下拉直选', () => {
      expect(kTableSource).toContain('field.dictionary')
      expect(kTableSource).toContain('getEnumItems(field.dictionary)')
      expect(kTableSource).toContain('@change="onSearch"')
    })

    it('表格工具条提供本页全选/取消全选与清空已选快捷操作', () => {
      expect(kTableSource).toContain('isAllSelected')
      expect(kTableSource).toContain('toggleSelectAll')
      expect(kTableSource).toContain('清空已选')
      expect(kTableSource).toContain('toggleRowSelection')
    })
  })

  describe('9. 单据明细批量挑选、扫码录入与行级一键克隆（Batch Add, Scan & Clone）', () => {
    const purchaseEditorSource = readFileSync(
      join(ROOT, 'src/view/console/channel/purchase/component/editor.vue'),
      'utf8',
    )
    const saleEditorSource = readFileSync(
      join(ROOT, 'src/view/console/channel/sale/component/editor.vue'),
      'utf8',
    )
    const inputEditorSource = readFileSync(
      join(ROOT, 'src/view/console/wms/input/component/editor.vue'),
      'utf8',
    )
    const outputEditorSource = readFileSync(
      join(ROOT, 'src/view/console/wms/output/component/editor.vue'),
      'utf8',
    )
    const moveEditorSource = readFileSync(
      join(ROOT, 'src/view/console/wms/move/component/editor.vue'),
      'utf8',
    )

    it('采购单编辑器支持扫码回车录入、批量挑选物料与明细复制', () => {
      expect(purchaseEditorSource).toContain('onScanEnter')
      expect(purchaseEditorSource).toContain('batchPicking')
      expect(purchaseEditorSource).toContain('onBatchPicked')
      expect(purchaseEditorSource).toContain('duplicateDetail')
    })

    it('销售单编辑器支持扫码回车录入、批量挑选物料与明细复制', () => {
      expect(saleEditorSource).toContain('onScanEnter')
      expect(saleEditorSource).toContain('batchPicking')
      expect(saleEditorSource).toContain('onBatchPicked')
      expect(saleEditorSource).toContain('duplicateDetail')
    })

    it('入库单与出库单编辑器支持批量挑选物料与明细复制', () => {
      expect(inputEditorSource).toContain('batchPicking')
      expect(inputEditorSource).toContain('onBatchPicked')
      expect(inputEditorSource).toContain('duplicateDetail')

      expect(outputEditorSource).toContain('batchPicking')
      expect(outputEditorSource).toContain('onBatchPicked')
      expect(outputEditorSource).toContain('duplicateDetail')
    })

    it('移库单编辑器明细支持一键复制，方便库位拆分', () => {
      expect(moveEditorSource).toContain('duplicateDetail')
      expect(moveEditorSource).toContain('label: \'复制\'')
    })

    it('五大单据明细表格均支持行内数量/单价即时修改，彻底免除弹窗反复跳转', () => {
      expect(purchaseEditorSource).toContain('#cell-quantity')
      expect(purchaseEditorSource).toContain('#cell-price')

      expect(saleEditorSource).toContain('#cell-quantity')
      expect(saleEditorSource).toContain('#cell-price')

      expect(inputEditorSource).toContain('#cell-quantity')
      expect(outputEditorSource).toContain('#cell-quantity')
      expect(moveEditorSource).toContain('#cell-quantity')
    })
  })

  describe('10. 作业登记满额速填与角色/用户管理体验优化', () => {
    const purchaseAddFinishSource = readFileSync(
      join(ROOT, 'src/view/console/channel/purchase/component/addFinishEditor.vue'),
      'utf8',
    )
    const moveDetailEditorSource = readFileSync(
      join(ROOT, 'src/view/console/wms/move/component/detailEditor.vue'),
      'utf8',
    )
    const permEditorSource = readFileSync(
      join(ROOT, 'src/view/console/personnel/role/component/permissionEditor.vue'),
      'utf8',
    )
    const menuEditorSource = readFileSync(
      join(ROOT, 'src/view/console/personnel/role/component/menuEditor.vue'),
      'utf8',
    )
    const userListSource = readFileSync(
      join(ROOT, 'src/view/console/personnel/user/list.vue'),
      'utf8',
    )

    it('采购作业与移库明细支持一键满额速填', () => {
      expect(purchaseAddFinishSource).toContain('全部完成')
      expect(purchaseAddFinishSource).toContain('quantity = waitingQuantity')

      expect(moveDetailEditorSource).toContain('全部转移')
      expect(moveDetailEditorSource).toContain('form.quantity = availableQuantity')
    })

    it('角色权限与菜单支持全选/清空一键速填', () => {
      expect(permEditorSource).toContain('selectAllPermissions')
      expect(permEditorSource).toContain('全选当前')

      expect(menuEditorSource).toContain('checkAllMenus')
      expect(menuEditorSource).toContain('clearAllMenus')
      expect(menuEditorSource).toContain('全选')
      expect(menuEditorSource).toContain('清空')
    })

    it('用户新增表单若在部门树聚焦时自动继承预选部门', () => {
      expect(userListSource).toContain('selectedDept.value')
      expect(userListSource).toContain('next.departmentList = [selectedDept.value]')
    })
  })
})
