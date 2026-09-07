package cn.hamm.spms.module.mes.plan.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.mes.plan.PlanEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <h1>生产计划读侧 Mapper</h1>
 *
 * @author Hamm.cn
 */
@DataResource(key = "mes.plan", alias = "plan")
public interface PlanMapper extends KuboBaseMapper<PlanEntity>, RowParityCounter {
    @Override
    default String parityResourceKey() {
        return "mes.plan";
    }

    List<PlanEntity> selectPageByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    PlanEntity selectDetailByPermission(@Param("id") Long id);

    Long countByPermission(
            @Param("billCode") String billCode,
            @Param("status") Integer status,
            @Param("type") Integer type
    );

    Long countByPermissionFragment(Map<String, Object> parameters);
}
