package cn.hamm.spms.module.mes.routing.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.mes.routing.RoutingEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>生产工艺读侧 Mapper</h1>
 *
 * <p>工艺主表（{@code routing}）的列表 / 详情 / 总数 / 统计 / 导出走显式 SQL；{@code material} /
 * {@code bom} 关联只投影 ID，工序配置列表（{@code @Transient}）由
 * {@code RoutingService#getMapperDetail} 沿原 {@code afterAppGet} 补齐。权限条件由 MyBatis-Plus
 * 运行时拦截器按 statement ID 注入。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "mes.routing", alias = "r")
public interface RoutingMapper extends KuboBaseMapper<RoutingEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "mes.routing";
    }

    /**
     * @param filter 工艺筛选（编码/名称/状态）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<RoutingEntity> selectPageByPermission(
            @Param("filter") RoutingEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    RoutingEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter 工艺筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") RoutingEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") RoutingEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，固定 id 倒序。
     *
     * @param filter 工艺筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<RoutingEntity> selectForExport(@Param("filter") RoutingEntity filter);

    /**
     * @param parameters fragment 与业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
