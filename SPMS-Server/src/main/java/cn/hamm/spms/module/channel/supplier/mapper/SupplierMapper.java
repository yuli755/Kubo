package cn.hamm.spms.module.channel.supplier.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.channel.supplier.SupplierEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>供应商读侧 Mapper</h1>
 *
 * <p>供应商无跨资源关联主表，故 resultMap 只含主表列；写路径仍走 AirPower JPA。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "channel.supplier", alias = "s")
public interface SupplierMapper extends KuboBaseMapper<SupplierEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "channel.supplier";
    }

    /**
     * @param filter 供应商筛选（名称/编码）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<SupplierEntity> selectPageByPermission(
            @Param("filter") SupplierEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    SupplierEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter 供应商筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") SupplierEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") SupplierEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，固定 id 倒序。
     *
     * @param filter 供应商筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<SupplierEntity> selectForExport(@Param("filter") SupplierEntity filter);

    /**
     * @param parameters fragment 与业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
