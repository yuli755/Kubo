package cn.hamm.spms.module.channel.sale.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.channel.sale.SaleEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>销售单读侧 Mapper</h1>
 *
 * <p>单据主表的列表、详情和总数使用显式 SQL；明细仍由原明细服务按单据 ID 加载。客户关联主表
 * 只投影 ID，再由 Service 通过客户服务补齐，避免把客户资源 JOIN 到同一条权限 SQL。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "channel.sale", alias = "s")
public interface SaleMapper extends KuboBaseMapper<SaleEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "channel.sale";
    }

    /**
     * @param billCode 单号模糊筛选
     * @param reason   销售说明模糊筛选
     * @param status   单据状态
     * @param offset   分页偏移
     * @param limit    分页大小
     * @return 当前页
     */
    List<SaleEntity> selectPageByPermission(
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
    SaleEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param billCode 单号模糊筛选
     * @param reason   销售说明模糊筛选
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
     * @param reason   销售说明模糊筛选
     * @param status   单据状态
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<SaleEntity> selectForExport(
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
