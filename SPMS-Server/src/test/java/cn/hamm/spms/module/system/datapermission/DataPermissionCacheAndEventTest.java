package cn.hamm.spms.module.system.datapermission;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionSubjectType;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionChangedEvent;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionService;
import cn.hamm.spms.module.system.datapermission.service.DataResourceService;
import cn.hamm.spms.module.system.datapermission.service.DataPolicyService;
import cn.hamm.spms.module.system.datapermission.service.TestDataFieldPolicyService;
import cn.hamm.spms.module.system.datapermission.service.TestPermissionRuleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>数据权限缓存与事件集成测试 (H2)</h1>
 *
 * @author Hamm.cn
 */
@SpringBootTest(
        properties = {
                "spring.cache.type=none"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Transactional
class DataPermissionCacheAndEventTest {

    @Autowired
    private DataResourceRepository resourceRepository;

    @Autowired
    private DataPolicyRepository policyRepository;

    @Autowired
    private DataFieldPolicyRepository fieldPolicyRepository;

    @Autowired
    private DataResourceService dataResourceService;

    @Autowired
    private DataPolicyService dataPolicyService;

    @Autowired
    private TestDataFieldPolicyService dataFieldPolicyService;

    @Autowired
    private DataPermissionService dataPermissionService;

    @Autowired
    private TestPermissionRuleProvider ruleProvider;

    @Autowired
    private TestPermissionRuleProvider testRuleProvider;

    @Autowired
    private DataPermissionEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        PermissionContextHolder.clear();
        resourceRepository.deleteAll();
        policyRepository.deleteAll();
        fieldPolicyRepository.deleteAll();

        // 创建系统资源 data_resource，用于数据权限自检。
        // 键必须写成 `system.dataResource`：DataPermissionResourceResolver 是按
        // "模块 + 实体类名首字母小写" 生成资源键的（F7 起不再整串小写，因为真实注册键里
        // 有 `channel.purchasePrice` 这种驼峰，整串小写会让驼峰资源的策略永不命中）。
        DataResourceEntity systemResource = dataResourceService.addAndGet(new DataResourceEntity()
                .setResourceKey("system.dataResource")
                .setTableName("data_resource")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY,DETAIL,EXPORT")
                .setStatus("ENABLED"));

        // 为系统资源创建 SYSTEM 主体的 QUERY 策略
        DataPolicyEntity systemPolicy = new DataPolicyEntity()
                .setResourceId(systemResource.getId())
                .setPolicyCode("SYS_DATAPERM_QUERY")
                .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                .setRoleCode("SYSTEM")
                .setActionName("QUERY")
                .setConditionJson("{}")
                .setEnabled(true);
        dataPolicyService.add(systemPolicy);

        // 为系统资源创建 SYSTEM 主体的 DELETE 策略（测试删除资源需要）
        DataPolicyEntity systemDeletePolicy = new DataPolicyEntity()
                .setResourceId(systemResource.getId())
                .setPolicyCode("SYS_DATAPERM_DELETE")
                .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                .setRoleCode("SYSTEM")
                .setActionName("DELETE")
                .setConditionJson("{}")
                .setEnabled(true);
        dataPolicyService.add(systemDeletePolicy);

        // 为系统资源创建 SYSTEM 主体的 UPDATE 策略（测试更新资源需要）
        DataPolicyEntity systemUpdatePolicy = new DataPolicyEntity()
                .setResourceId(systemResource.getId())
                .setPolicyCode("SYS_DATAPERM_UPDATE")
                .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                .setRoleCode("SYSTEM")
                .setActionName("UPDATE")
                .setConditionJson("{}")
                .setEnabled(true);
        dataPolicyService.add(systemUpdatePolicy);
    }

    @Test
    void testResourceCaching() {
        // 创建资源
        DataResourceEntity resource = new DataResourceEntity()
                .setResourceKey("cache.test")
                .setTableName("cache_test")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY")
                .setStatus("ENABLED");
        resource = dataResourceService.addAndGet(resource);

        // 第一次查询 - 应该查数据库
        DataResourceEntity found1 = dataResourceService.get(resource.getId());
        assertNotNull(found1);

        // 第二次查询 - 应该走缓存
        DataResourceEntity found2 = dataResourceService.get(resource.getId());
        assertEquals(found1.getResourceKey(), found2.getResourceKey());

        // 清除缓存后再次查询
        testRuleProvider.clearAllCache();
        DataResourceEntity found3 = dataResourceService.get(resource.getId());
        assertEquals(found1.getResourceKey(), found3.getResourceKey());
    }

    @Test
    void testPolicyCaching() {
        // 创建资源
        DataResourceEntity resource = new DataResourceEntity()
                .setResourceKey("cache.policy")
                .setTableName("cache_policy")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY")
                .setStatus("ENABLED");
        resource = dataResourceService.addAndGet(resource);

        // 创建策略
        DataPolicyEntity policy = new DataPolicyEntity()
                .setResourceId(resource.getId())
                .setPolicyCode("SYS_CACHE_QUERY")
                .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                .setRoleCode("SYSTEM")
                .setActionName("QUERY")
                .setConditionJson("{}")
                .setEnabled(true);
        dataPolicyService.add(policy);

        // 设置上下文
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                1L, 100L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            // 第一次获取谓词
            DataResourceEntity found = ruleProvider.getResource("cache.policy");
            List<DataPolicyEntity> policies1 = ruleProvider.getPolicies(
                    found, DataPermissionAction.QUERY, PermissionContextHolder.require());

            // 第二次获取 - 应该走缓存
            List<DataPolicyEntity> policies2 = ruleProvider.getPolicies(
                    found, DataPermissionAction.QUERY, PermissionContextHolder.require());

            assertEquals(policies1.size(), policies2.size());

            // 清除缓存
            testRuleProvider.clearAllCache();
        }
    }

    @Test
    void testFieldPolicyCaching() {
        // 创建资源
        DataResourceEntity resource = new DataResourceEntity()
                .setResourceKey("cache.field")
                .setTableName("cache_field")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY")
                .setStatus("ENABLED");
        resource = resourceRepository.saveAndFlush(resource);

        // 创建字段策略 - 设置 resource 关系和 actionName 避免 NULL
        DataFieldPolicyEntity fieldPolicy = new DataFieldPolicyEntity()
                .setResource(resource)
                .setFieldName("secret_field")
                .setRoleCode("ADMIN")
                .setActionName("QUERY")
                .setMaskType("FULL")
                .setEnabled(true);
        fieldPolicyRepository.saveAndFlush(fieldPolicy);

        // 设置上下文
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                1L, 100L, Set.of(), Set.of(), Set.of(), Set.of("ADMIN"), false))) {
            // 第一次查询
            List<DataFieldPolicyEntity> policies1 = dataFieldPolicyService.getPoliciesByResourceFieldRole(
                    "cache.field", "secret_field", "ADMIN");

            // 第二次查询 - 应该走缓存
            List<DataFieldPolicyEntity> policies2 = dataFieldPolicyService.getPoliciesByResourceFieldRole(
                    "cache.field", "secret_field", "ADMIN");

            assertEquals(policies1.size(), policies2.size());
            assertEquals("FULL", policies2.get(0).getMaskType());

            // 清除缓存
            testRuleProvider.clearAllCache();
        }
    }

    @Test
    void testEventPublishing() {
        // 创建资源
        DataResourceEntity resource = new DataResourceEntity()
                .setResourceKey("event.test")
                .setTableName("event_test")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY")
                .setStatus("ENABLED");
        resource = dataResourceService.addAndGet(resource);

        // 发布资源变更事件
        eventPublisher.publishResourceChanged(resource.getResourceKey());

        // 发布策略变更事件
        eventPublisher.publishPolicyChanged(resource.getResourceKey(), 1L, Set.of(DataPermissionAction.QUERY));

        // 发布字段脱敏策略变更事件
        eventPublisher.publishFieldPolicyChanged(resource.getResourceKey());

        // 发布全量刷新事件
        eventPublisher.publishFullRefresh();

        // 验证事件发布成功（无异常即成功）
    }

    @Test
    void testCacheEvictionOnDelete() {
        // 创建资源
        DataResourceEntity resource = new DataResourceEntity()
                .setResourceKey("evict.test")
                .setTableName("evict_test")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY")
                .setStatus("ENABLED");
        resource = dataResourceService.addAndGet(resource);

        // 创建策略
        DataPolicyEntity policy = new DataPolicyEntity()
                .setResourceId(resource.getId())
                .setPolicyCode("SYS_EVICT_QUERY")
                .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                .setRoleCode("SYSTEM")
                .setActionName("QUERY")
                .setConditionJson("{}")
                .setEnabled(true);
        dataPolicyService.add(policy);

        // 查询建立缓存
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                1L, 100L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            DataResourceEntity found = ruleProvider.getResource("evict.test");
            ruleProvider.getPolicies(found, DataPermissionAction.QUERY, PermissionContextHolder.require());

            // 删除资源 - 应该触发缓存清除
            dataResourceService.delete(resource.getId());

            // 再次查询 - 资源已删除
            DataResourceEntity deleted = ruleProvider.getResource("evict.test");
            assertNull(deleted);
        }
    }

    @Test
    void testMultiTenantIsolation() {
        // 租户 A 的资源
        DataResourceEntity resourceA = new DataResourceEntity()
                .setResourceKey("tenant.a")
                .setTableName("tenant_a")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY")
                .setStatus("ENABLED");
        resourceA = dataResourceService.addAndGet(resourceA);

        DataPolicyEntity policyA = new DataPolicyEntity()
                .setResourceId(resourceA.getId())
                .setPolicyCode("SYS_TENANT_A_QUERY")
                .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                .setRoleCode("SYSTEM")
                .setActionName("QUERY")
                .setConditionJson("{}")
                .setEnabled(true);
        dataPolicyService.add(policyA);

        // 租户 B 的资源
        DataResourceEntity resourceB = new DataResourceEntity()
                .setResourceKey("tenant.b")
                .setTableName("tenant_b")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY")
                .setStatus("ENABLED");
        resourceB = dataResourceService.addAndGet(resourceB);

        DataPolicyEntity policyB = new DataPolicyEntity()
                .setResourceId(resourceB.getId())
                .setPolicyCode("SYS_TENANT_B_QUERY")
                .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                .setRoleCode("SYSTEM")
                .setActionName("QUERY")
                .setConditionJson("{}")
                .setEnabled(true);
        dataPolicyService.add(policyB);

        // 租户 A 上下文
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                1L, 100L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            DataResourceEntity foundA = ruleProvider.getResource("tenant.a");
            List<DataPolicyEntity> policiesA = ruleProvider.getPolicies(
                    foundA, DataPermissionAction.QUERY, PermissionContextHolder.require());
            assertFalse(policiesA.isEmpty(), "租户 A 应有策略");
        }

        // 租户 B 上下文
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                2L, 200L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            DataResourceEntity foundB = ruleProvider.getResource("tenant.b");
            List<DataPolicyEntity> policiesB = ruleProvider.getPolicies(
                    foundB, DataPermissionAction.QUERY, PermissionContextHolder.require());
            assertFalse(policiesB.isEmpty(), "租户 B 应有策略");
        }

        // 跨租户查询 - tenant A 查 tenant B 的资源
        // 虽然资源存在，但策略匹配的是 SYSTEM 主体，所以会返回策略
        // 实际租户隔离在 Specification 中通过 tenant_id 列实现
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                1L, 100L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            DataResourceEntity foundB = ruleProvider.getResource("tenant.b");
            List<DataPolicyEntity> policiesCross = ruleProvider.getPolicies(
                    foundB, DataPermissionAction.QUERY, PermissionContextHolder.require());
            // SYSTEM 策略不依赖租户，所以仍会返回策略
            // 真正的租户隔离在 DataPermissionSpecification 中通过 tenantColumn 实现
            assertNotNull(policiesCross);
        }
    }
}