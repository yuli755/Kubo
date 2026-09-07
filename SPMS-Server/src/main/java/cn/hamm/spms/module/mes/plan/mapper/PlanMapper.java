package cn.hamm.spms.module.mes.plan.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.mes.plan.PlanEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;

import java.util.Map;

/**
 * 生产计划读侧 Mapper；只承载 XML 读语句与 ORM parity 探针，写路径仍走 JPA。
 *
 * @author Hamm.cn
 */
@DataResource(key = "mes.plan", alias = "plan")
public interface PlanMapper extends KuboBaseMapper<PlanEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "mes.plan";
    }

    /**
     * 按统一权限片段统计生产计划行数。
     *
     * @param parameters fragment 与命名参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}
