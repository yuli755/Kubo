package cn.hamm.spms.module.factory.structure.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.factory.structure.StructureEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.framework.curd.datapermission.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>生产单元读侧 Mapper</h1>
 *
 * <p>生产单元主表（{@code structure}）的列表 / 详情 / 总数 / 统计 / 导出走显式 SQL。生产单元是树形实体
 * （{@code ITree}），其树导航走 {@code getList}（JPA 路径）；{@code operationList} 是 {@code @ManyToMany}
 * （无外键），resultMap 只投影主表列，工序由 {@code StructureService#getMapperDetail} 沿 JPA 关系补齐。
 * 权限条件由 MyBatis-Plus 运行时拦截器按 statement ID 注入。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "factory.structure", alias = "structure")
public interface StructureMapper extends KuboBaseMapper<StructureEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "factory.structure";
    }

    /**
     * @param filter 生产单元筛选（编码 / 名称 / 类型）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<StructureEntity> selectPageByPermission(
            @Param("filter") StructureEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    StructureEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter 生产单元筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") StructureEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") StructureEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，业务筛选与分页一致，固定 id 倒序。
     *
     * @param filter 生产单元筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<StructureEntity> selectForExport(@Param("filter") StructureEntity filter);

    /**
     * @param parameters fragment 与业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
