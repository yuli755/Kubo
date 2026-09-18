package cn.hamm.spms.module.asset.material.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * <h1>物料读侧 Mapper（P2 第一个资源）</h1>
 *
 * <p>选它做第一个换轨对象是因为它已经是 {@code TenantAwareEntity} 的试点：
 * {@code tenant_id / creator_id} 在实体上真映射了，行级条件绑得上列 —— 这是"读侧能算出条件"的前提，
 * 24 个资源里只有它先满足。</p>
 *
 * <p>所有业务读出口都显式写在本接口的 XML 中：列表、详情、统计、总数各有自己的
 * {@code mappedStatementId}，因此每一条 SQL 都会经过同一个资源绑定和数据权限拦截器。
 * 写路径仍然只走写侧仓储（`ICurdRepository` + mapper XML）；本 Mapper 不承载新增、修改或删除。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "asset.material", alias = "material")
public interface MaterialMapper extends KuboBaseMapper<MaterialEntity> {

    /**
     * 真实列表读出口；权限条件由运行时拦截器按 statement ID 注入。
     *
     * @param filter 物料筛选条件
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    java.util.List<MaterialEntity> selectPageByPermission(
            @Param("filter") MaterialEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 真实详情读出口；不存在或无权行均返回 null，由 Service 转为统一的数据不存在错误。
     *
     * @param id 主键
     * @return 详情
     */
    MaterialEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，业务筛选与列表一致。
     *
     * <p>导出不需要分页：拦截器已经把行集收窄到当前主体的可见范围，这里只保证顺序稳定
     * （固定 {@code id} 倒序）并返回全部可见行。CSV 组装与列级遮蔽在 Service 的请求线程完成。</p>
     *
     * @param filter 物料筛选条件
     * @return 当前主体可见、且满足筛选条件的全部行（按 id 倒序）
     */
    java.util.List<MaterialEntity> selectForExport(@Param("filter") MaterialEntity filter);

    /**
     * 与列表筛选条件一致的总数出口。
     *
     * @param filter 物料筛选条件
     * @return 权限范围内总数
     */
    Long countByPermission(@Param("filter") MaterialEntity filter);

    /**
     * 与列表筛选条件一致的统计出口。
     *
     * @param filter 物料筛选条件
     * @return 权限范围内统计
     */
    ReadStat statByPermission(@Param("filter") MaterialEntity filter);

}
