package cn.hamm.spms.module.mes.operation.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.mes.operation.OperationEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>工序读侧 Mapper</h1>
 *
 * <p>工序（{@code operation}）是单表、无关联主表，resultMap 只含主表列；写路径仍走 AirPower JPA。
 * 权限条件由 MyBatis-Plus 运行时拦截器按 statement ID 注入。</p>
 *
 * @author Hamm.cn
 */
@DataResource(key = "mes.operation", alias = "o")
public interface OperationMapper extends KuboBaseMapper<OperationEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "mes.operation";
    }

    /**
     * @param filter 工序筛选（名称/编码）
     * @param offset 分页偏移
     * @param limit  分页大小
     * @return 当前页
     */
    List<OperationEntity> selectPageByPermission(
            @Param("filter") OperationEntity filter,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * @param id 主键
     * @return 详情或 null
     */
    OperationEntity selectDetailByPermission(@Param("id") Long id);

    /**
     * @param filter 工序筛选
     * @return 行数
     */
    Long countByPermission(@Param("filter") OperationEntity filter);

    /**
     * 与总数完全相同的统计出口；只返回行数与已发布行数。
     */
    ReadStat statByPermission(@Param("filter") OperationEntity filter);

    /**
     * 真实导出取数出口；权限条件由运行时拦截器按 statement ID 注入，固定 id 倒序。
     *
     * @param filter 工序筛选
     * @return 当前主体可见且满足筛选条件的全部行
     */
    List<OperationEntity> selectForExport(@Param("filter") OperationEntity filter);

    /**
     * @param parameters fragment 与业务筛选参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
