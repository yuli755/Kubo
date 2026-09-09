package cn.hamm.spms.module.system.dashboard;

import cn.hamm.spms.module.asset.AssetServices;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.channel.ChannelServices;
import cn.hamm.spms.module.channel.customer.CustomerEntity;
import cn.hamm.spms.module.channel.purchase.PurchaseEntity;
import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceEntity;
import cn.hamm.spms.module.channel.sale.SaleEntity;
import cn.hamm.spms.module.channel.saleprice.SalePriceEntity;
import cn.hamm.spms.module.channel.supplier.SupplierEntity;
import cn.hamm.spms.module.mes.MesServices;
import cn.hamm.spms.module.mes.order.OrderEntity;
import cn.hamm.spms.module.mes.picking.PickingEntity;
import cn.hamm.spms.module.mes.plan.PlanEntity;
import cn.hamm.spms.module.wms.WmsServices;
import cn.hamm.spms.module.wms.input.InputEntity;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import cn.hamm.spms.module.wms.move.MoveEntity;
import cn.hamm.spms.module.wms.output.OutputEntity;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>数据看板服务（P2 · 业务概况）</h1>
 *
 * <p>业务概况回答「<b>我</b>能看到的单据 / 库存 / 主数据有多少」，而非跨租户统计。每一档
 * {@code count} 都来自对应资源的 {@code getMapperCount(entity)} —— Mapper 开启时走
 * {@code countByPermission}（权限条件由 MyBatis 拦截器按当前主体注入），关闭时回退 JPA
 * {@code filter().size()}（同样经 {@code addSearchPredicate} 注入权限谓词）。<b>这里不开任何
 * 「不经行级过滤的 JPA COUNT」旁路</b>，否则统计图会把只对本租户可见的数据泄漏出去。</p>
 *
 * <p>资源清单收敛在本服务（一张「资源键 → count 供给」的表），前端只渲染返回数组，不各自维护
 * 「哪些资源要上概览」这份清单 —— 与安全态势看板「只搬运、不判定」同一条纪律。</p>
 *
 * @author Hamm.cn
 */
@Service
public class DashboardService {

    /**
     * @return 当前用户可见范围内的业务概况（14 资源，顺序即展示顺序）
     */
    public List<DashboardOverviewItem> overview() {
        return List.of(
                item("channel.purchase", "采购单", ChannelServices.getPurchaseService().getMapperCount(new PurchaseEntity())),
                item("channel.sale", "销售单", ChannelServices.getSaleService().getMapperCount(new SaleEntity())),
                item("wms.input", "入库单", WmsServices.getInputService().getMapperCount(new InputEntity())),
                item("wms.output", "出库单", WmsServices.getOutputService().getMapperCount(new OutputEntity())),
                item("wms.inventory", "库存", WmsServices.getInventoryService().getMapperCount(new InventoryEntity())),
                item("wms.move", "库存移动", WmsServices.getMoveService().getMapperCount(new MoveEntity())),
                item("mes.order", "生产订单", MesServices.getOrderService().getMapperCount(new OrderEntity())),
                item("mes.plan", "生产计划", MesServices.getPlanService().getMapperCount(new PlanEntity())),
                item("mes.picking", "生产领料", MesServices.getPickingService().getMapperCount(new PickingEntity())),
                item("channel.customer", "客户", ChannelServices.getCustomerService().getMapperCount(new CustomerEntity())),
                item("channel.supplier", "供应商", ChannelServices.getSupplierService().getMapperCount(new SupplierEntity())),
                item("channel.purchasePrice", "采购单价", ChannelServices.getPurchasePriceService().getMapperCount(new PurchasePriceEntity())),
                item("channel.salePrice", "销售单价", ChannelServices.getSalePriceService().getMapperCount(new SalePriceEntity())),
                item("asset.material", "物料", AssetServices.getMaterialService().getMapperCount(new MaterialEntity()))
        );
    }

    private DashboardOverviewItem item(String resourceKey, String label, long count) {
        return new DashboardOverviewItem()
                .setResourceKey(resourceKey)
                .setLabel(label)
                .setCount(count);
    }
}
