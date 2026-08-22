package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>数据字段权限策略管理服务</h1>
 *
 * @author Hamm.cn
 */
@Service
public class DataFieldPolicyService extends BaseService<DataFieldPolicyEntity, DataFieldPolicyRepository> {

    /**
     * 根据资源键、字段名、角色编码获取启用的脱敏策略
     *
     * @param resourceKey 数据资源标识（如 wms.input）
     * @param fieldName   字段名
     * @param roleCode    角色编码
     * @return 策略列表（按优先级降序）
     */
    public List<DataFieldPolicyEntity> getPoliciesByResourceFieldRole(
            String resourceKey, String fieldName, String roleCode) {
        return repository.findByResourceKeyAndFieldNameAndRoleCodeAndEnabledTrue(
                resourceKey, fieldName, roleCode);
    }
}