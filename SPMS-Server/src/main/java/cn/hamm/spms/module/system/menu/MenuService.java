package cn.hamm.spms.module.system.menu;

import cn.hamm.airpower.core.TreeUtil;
import cn.hamm.spms.base.BaseService;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.hamm.airpower.core.TreeUtil.ROOT_ID;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class MenuService extends BaseService<MenuEntity, MenuRepository> {
    /**
     * 排序字段
     */
    public static final String ORDER_FIELD_NAME = "orderNo";

    @Override
    protected void beforeDelete(@NotNull MenuEntity menu) {
        TreeUtil.ensureNoChildrenBeforeDelete(menu.getId(), (id) -> filter(new MenuEntity().setParentId(id)));
    }

    @Override
    protected @NotNull List<MenuEntity> afterGetList(@NotNull List<MenuEntity> list) {
        list.forEach(MenuEntity::excludeNotMeta);
        return list;
    }

    /**
     * 页面路径 → 视图组件路径。
     *
     * <p>约定：<b>路由路径 == 视图路径</b>，例 {@code /console/wms/input/list}
     * 对应 {@code src/view/console/wms/input/list.vue}。前端 {@code RouterUtil} 的
     * {@code registerRoute()} 取 {@code component || path} 去 {@code /src/view} 下找组件，
     * 因此这里只写 {@code path}，不重复维护 {@code component}，两边不会各写一份而漂移。
     *
     * <p>前端侧的同名契约在 {@code src/demo/fixtures/menu.ts}（演示菜单），
     * CI 由 {@code src/config/__tests__/backendMenuContract.spec.ts} 解析本文件对账，
     * 任何一侧改了名字或路径都会红。
     */
    private MenuEntity listPage(String name, String path, long parentId) {
        return new MenuEntity()
                .setName(name)
                .setPath(path)
                .setParentId(parentId);
    }

    @SuppressWarnings("AlibabaMethodTooLong")
    public final void initMenu() {
        MenuEntity group;
        MenuEntity page;

        // 工作台（唯一一个 path ≠ 视图目录的页面：/console/index → /console/index/index）
        MenuEntity dashboard = new MenuEntity()
                .setName("工作台")
                .setOrderNo(1)
                .setPath("/console/index")
                .setComponent("/console/index/index")
                .setParentId(ROOT_ID);
        add(dashboard);

        // 仓储管理 WMS —— 产品主线
        group = addAndGet(new MenuEntity().setName("仓储管理 WMS").setOrderNo(20).setParentId(ROOT_ID));
        add(listPage("入库单", "/console/wms/input/list", group.getId()));
        add(listPage("出库单", "/console/wms/output/list", group.getId()));
        add(listPage("移库单", "/console/wms/move/list", group.getId()));
        add(listPage("库存查询", "/console/wms/inventory/list", group.getId()));

        // 渠道管理
        group = addAndGet(new MenuEntity().setName("渠道管理").setOrderNo(30).setParentId(ROOT_ID));
        add(listPage("客户管理", "/console/channel/customer/list", group.getId()));
        add(listPage("供应商管理", "/console/channel/supplier/list", group.getId()));
        add(listPage("采购单", "/console/channel/purchase/list", group.getId()));
        add(listPage("采购价", "/console/channel/purchasePrice/list", group.getId()));
        add(listPage("销售单", "/console/channel/sale/list", group.getId()));
        add(listPage("销售价", "/console/channel/salePrice/list", group.getId()));

        // 资产管理
        group = addAndGet(new MenuEntity().setName("资产管理").setOrderNo(40).setParentId(ROOT_ID));
        add(listPage("物料主数据", "/console/asset/material/list", group.getId()));
        add(listPage("合同管理", "/console/asset/contract/list", group.getId()));

        // 工厂与库位
        group = addAndGet(new MenuEntity().setName("工厂与库位").setOrderNo(50).setParentId(ROOT_ID));
        add(listPage("仓库管理", "/console/factory/storage/list", group.getId()));
        add(listPage("库位结构", "/console/factory/structure/list", group.getId()));

        // 人员组织
        group = addAndGet(new MenuEntity().setName("人员组织").setOrderNo(60).setParentId(ROOT_ID));
        add(listPage("用户管理", "/console/personnel/user/list", group.getId()));
        add(listPage("角色管理", "/console/personnel/role/list", group.getId()));
        add(listPage("部门管理", "/console/personnel/department/list", group.getId()));

        // 系统管理（含数据权限子分组）
        group = addAndGet(new MenuEntity().setName("系统管理").setOrderNo(70).setParentId(ROOT_ID));
        add(listPage("计量单位", "/console/system/unit/list", group.getId()));
        add(listPage("权限管理", "/console/system/permission/list", group.getId()));
        add(listPage("菜单管理", "/console/system/menu/list", group.getId()));
        add(listPage("系统配置", "/console/system/config/list", group.getId()));
        add(listPage("编号规则", "/console/system/coderule/list", group.getId()));
        add(listPage("操作日志", "/console/system/log/list", group.getId()));
        // 数据权限（资源登记 / 行级策略 / 列级脱敏策略）
        // 没有这三个页面时策略只能靠 SQL 或 DevDataInitRunner 硬编码来改，"灰度"就无从下手：
        // 观察 shadow 差异、按角色放开明文、给某个资源临时收口，都必须能在界面上完成。
        MenuEntity dataPermission = addAndGet(
                new MenuEntity().setName("数据权限").setOrderNo(8).setParentId(group.getId())
        );
        add(listPage("数据资源", "/console/system/datapermission/resource/list", dataPermission.getId()));
        add(listPage("行级策略", "/console/system/datapermission/policy/list", dataPermission.getId()));
        add(listPage("列级脱敏", "/console/system/datapermission/fieldPolicy/list", dataPermission.getId()));
    }
}
