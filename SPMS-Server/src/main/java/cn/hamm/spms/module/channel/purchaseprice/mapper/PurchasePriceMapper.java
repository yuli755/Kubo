package cn.hamm.spms.module.channel.purchaseprice.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.framework.curd.datapermission.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>采购价格读侧 Mapper</h1>
 *
 * <p>采购价无业务筛选（列表按租户全量 + 行级过滤），关联主表（物料/供应商）只投影 ID，
 * 由 Service 通过受控详情服务补齐；写路径仍走 AirPower JPA。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "channel.purchasePrice", alias = "pp")
public interface PurchasePriceMapper extends KuboBaseMapper<PurchasePriceEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "channel.purchasePrice";
    }

    /**
     * @param filter 采购价筛选（无业务字段，保留占位）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<PurchasePriceEntity> selectPageByPermission(
            @Param("filter") PurchasePriceEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    PurchasePriceEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter 采购价筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") PurchasePriceEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") PurchasePriceEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，固定 id 倒序。
     *
     * @param filter 采购价筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<PurchasePriceEntity> selectForExport(@Param("filter") PurchasePriceEntity filter);

    /**
     * @param parameters fragment 与业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
