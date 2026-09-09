package cn.hamm.spms.module.channel.purchase.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.channel.purchase.PurchaseEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>采购单读侧 Mapper</h1>
 *
 * <p>单据主表的列表、详情和总数使用显式 SQL；明细仍由原明细服务按单据 ID 加载。采购单无跨资源
 * 关联主表（无 {@code @ManyToOne} 关系字段），故 resultMap 只含主表列。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "channel.purchase", alias = "p")
public interface PurchaseMapper extends KuboBaseMapper<PurchaseEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "channel.purchase";
    }

    /**
     * @param billCode 单号模糊筛选
     * @param reason   采购事由模糊筛选
     * @param status   单据状态
     * @param offset   分页偏移
     * @param limit    分页大小
     * @return 当前页
     */
    List<PurchaseEntity> selectPageByPermission(
            @Param("billCode") String billCode,
            @Param("reason") String reason,
            @Param("status") Integer status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    PurchaseEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param billCode 单号模糊筛选
     * @param reason   采购事由模糊筛选
     * @param status   单据状态
     * @return 行数
     */
    Long countByPermission(
            @Param("billCode") String billCode,
            @Param("reason") String reason,
            @Param("status") Integer status
    );

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(
            @Param("billCode") String billCode,
            @Param("reason") String reason,
            @Param("status") Integer status
    );

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，业务筛选与分页一致，固定 id 倒序。
     *
     * @param billCode 单号模糊筛选
     * @param reason   采购事由模糊筛选
     * @param status   单据状态
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<PurchaseEntity> selectForExport(
            @Param("billCode") String billCode,
            @Param("reason") String reason,
            @Param("status") Integer status
    );

    /**
     * @param parameters fragment 与业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
