package cn.hamm.spms.module.mes.bom.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.mes.bom.BomEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>BOM 读侧 Mapper</h1>
 *
 * <p>BOM 主表（{@code bom}）的列表 / 详情 / 总数 / 统计 / 导出走显式 SQL；明细是 {@code @ManyToMany}
 * （无 {@code bom_id} 外键），故 resultMap 只投影主表列，明细由 {@code BomService#getMapperDetail}
 * 沿 JPA 关系补齐。权限条件由 MyBatis-Plus 运行时拦截器按 statement ID 注入。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "mes.bom", alias = "b")
public interface BomMapper extends KuboBaseMapper<BomEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "mes.bom";
    }

    /**
     * @param filter BOM 筛选（编码/名称/类型/状态）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<BomEntity> selectPageByPermission(
            @Param("filter") BomEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    BomEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter BOM 筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") BomEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") BomEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，业务筛选与分页一致，固定 id 倒序。
     *
     * @param filter BOM 筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<BomEntity> selectForExport(@Param("filter") BomEntity filter);

    /**
     * @param parameters fragment 与业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
