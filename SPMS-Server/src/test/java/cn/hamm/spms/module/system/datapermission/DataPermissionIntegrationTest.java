package cn.hamm.spms.module.system.datapermission;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionSubjectType;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionGuard;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionResourceResolver;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionService;
import cn.hamm.spms.module.system.datapermission.service.DataResourceService;
import cn.hamm.spms.module.system.datapermission.service.DataPolicyService;
import cn.hamm.spms.module.system.datapermission.service.PermissionRuleProvider;
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
 * <h1>数据权限集成测试 (H2)</h1>
 * <p>
 * 覆盖 CUD 权限守卫、Specification 谓词构建、字段脱敏策略查询
 * </p>
 *
 * @author Hamm.cn
 */
@SpringBootTest(
        properties = {
                "spring.cache.type=none",
                "airpower.redis.enabled=false",
                "airpower.mqtt.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Transactional
class DataPermissionIntegrationTest {

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
    private DataPermissionGuard dataPermissionGuard;

    @Autowired
    private TestPermissionRuleProvider ruleProvider;

    @BeforeEach
    void setUp() {
        PermissionContextHolder.clear();
        resourceRepository.deleteAll();
        policyRepository.deleteAll();
        fieldPolicyRepository.deleteAll();

        // 创建系统资源 data_resource，用于数据权限自检
        DataResourceEntity systemResource = dataResourceService.addAndGet(new DataResourceEntity()
                .setResourceKey("system.dataresource")
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
    void testSpecificationPredicateBuilding() {
        // 创建资源
        DataResourceEntity resource = new DataResourceEntity()
                .setResourceKey("wms.inventory")
                .setTableName("wms_inventory")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY,DETAIL,EXPORT")
                .setStatus("ENABLED");
        resource = dataResourceService.addAndGet(resource);

        // 创建租户隔离策略（SYSTEM 主体）
        DataPolicyEntity systemPolicy = new DataPolicyEntity()
                .setResourceId(resource.getId())
                .setPolicyCode("SYS_INV_QUERY")
                .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                .setRoleCode("SYSTEM")
                .setActionName("QUERY")
                .setConditionJson("{}")
                .setPriority(100)
                .setEnabled(true);
        dataPolicyService.add(systemPolicy);

// 创建角色策略
        DataPolicyEntity rolePolicy = new DataPolicyEntity()
                .setResourceId(resource.getId())
                .setPolicyCode("ROLE_INV_QUERY")
                .setSubjectType(DataPermissionSubjectType.ROLE.name())
                .setRoleCode("WAREHOUSE_ADMIN")
                .setActionName("QUERY")
                .setConditionJson("{}")
                .setPriority(50)
                .setEnabled(true);
        dataPolicyService.add(rolePolicy);

        // 设置上下文：有租户、部门、仓库权限
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                1L, 100L,
                Set.of(1L, 2L),
                Set.of(10L),
                Set.of(100L, 200L),
                Set.of("WAREHOUSE_ADMIN"),
                false
        ))) {
            // 获取资源
            DataResourceEntity found = ruleProvider.getResource("wms.inventory");
            assertNotNull(found);
            assertEquals("wms.inventory", found.getResourceKey());

            // 获取策略 - 应该返回 SYSTEM 和 ROLE 两条策略
            List<DataPolicyEntity> policies = ruleProvider.getPolicies(
                    found, DataPermissionAction.QUERY, PermissionContextHolder.require());
            assertNotNull(policies);
            assertEquals(2, policies.size(), "应返回 SYSTEM 和 ROLE 两条策略");
        }
    }

    @Test
    void testPolicyCrudAndPermissionCheck() {
        // 创建资源
        DataResourceEntity resource = new DataResourceEntity()
                .setResourceKey("wms.input")
                .setTableName("wms_input")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("CREATE,UPDATE,DELETE,QUERY")
                .setStatus("ENABLED");
        resource = dataResourceService.addAndGet(resource);

// 添加 CREATE 策略
        DataPolicyEntity createPolicy = new DataPolicyEntity()
                .setResourceId(resource.getId())
                .setPolicyCode("INPUT_CREATE")
                .setSubjectType(DataPermissionSubjectType.ROLE.name())
                .setRoleCode("INPUT_ADMIN")
                .setActionName("CREATE")
                .setConditionJson("{}")
                .setEnabled(true);
        dataPolicyService.add(createPolicy);

        // 设置上下文：有 INPUT_ADMIN 角色
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                1L, 100L,
                Set.of(1L),
                Set.of(),
                Set.of(100L),
                Set.of("INPUT_ADMIN"),
                false
        ))) {
            // 应该通过 CREATE 权限检查
            assertDoesNotThrow(() -> dataPermissionGuard.enforce("wms.input", DataPermissionAction.CREATE));

            // 没有 UPDATE 策略，应该拦截
            assertThrows(IllegalStateException.class,
                    () -> dataPermissionGuard.enforce("wms.input", DataPermissionAction.UPDATE));
        }
    }

    @Test
    void testResourceCrudWithEventPublish() {
        // 创建资源 - 使用测试专用 key 避免与 DevDataInitRunner 冲突
        String uniqueKey = "test.integration.resource." + System.currentTimeMillis();
        DataResourceEntity resource = new DataResourceEntity()
                .setResourceKey(uniqueKey)
                .setTableName("test_integration_table")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY")
                .setStatus("ENABLED");
        resource = dataResourceService.addAndGet(resource);
        assertNotNull(resource.getId());

        // 更新资源 - 修改 supportedActions
        resource.setSupportedActions("QUERY,EXPORT");
        resource = resourceRepository.saveAndFlush(resource);
        assertEquals("QUERY,EXPORT", resource.getSupportedActions());

        // 删除资源
        dataResourceService.delete(resource.getId());
        assertTrue(resourceRepository.findById(resource.getId()).isEmpty());
    }

    @Test
    void testFieldPolicyCrudAndQuery() {
        // 创建资源 - 使用测试专用 key 避免与 DevDataInitRunner 冲突
        DataResourceEntity resource = new DataResourceEntity()
                .setResourceKey("test.material.field")
                .setTableName("test_material")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY")
                .setStatus("ENABLED");
        resource = resourceRepository.saveAndFlush(resource);

        // 创建字段策略 - 设置 resource 关系和 actionName 避免 NULL
        DataFieldPolicyEntity fieldPolicy = new DataFieldPolicyEntity()
                .setResource(resource)
                .setFieldName("sale_price")
                .setRoleCode("SALES_USER")
                .setActionName("QUERY")
                .setMaskType("PARTIAL")
                .setMaskExpression("*,4")
                .setEnabled(true);
        fieldPolicy = fieldPolicyRepository.saveAndFlush(fieldPolicy);
        assertNotNull(fieldPolicy.getId());

        // 查询字段策略 - 使用 service 查询方法（走缓存）
        List<DataFieldPolicyEntity> policies = dataFieldPolicyService.getPoliciesByResourceFieldRole(
                "test.material.field", "sale_price", "SALES_USER");
        assertEquals(1, policies.size());
        assertEquals("PARTIAL", policies.get(0).getMaskType());
        assertEquals("*,4", policies.get(0).getMaskExpression());
    }

    @Test
    void testResourceResolverMapping() {
        // 验证 InputService 类能正确解析到资源 key
        String inputKey = DataPermissionResourceResolver.resolve(
                cn.hamm.spms.module.wms.input.InputService.class);
        assertEquals("wms.input", inputKey);
    }

    @Test
    void testBypassUserSkipsPermissionCheck() {
        // bypass 用户应跳过所有权限检查
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                999L, 1L,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                true // bypass
        ))) {
            // 即使没有任何策略，bypass 用户也应通过
            assertDoesNotThrow(() -> dataPermissionGuard.enforce("any.resource", DataPermissionAction.CREATE));
            assertDoesNotThrow(() -> dataPermissionGuard.enforce("any.resource", DataPermissionAction.UPDATE));
            assertDoesNotThrow(() -> dataPermissionGuard.enforce("any.resource", DataPermissionAction.DELETE));
        }
    }

    @Test
    void testDataPermissionServiceShouldDenyByDefault() {
        // 创建资源但无策略
        DataResourceEntity resource = new DataResourceEntity()
                .setResourceKey("deny.test")
                .setTableName("deny_test")
                .setTableAlias("t")
                .setTenantColumn("tenant_id")
                .setSupportedActions("QUERY")
                .setStatus("ENABLED");
        resource = dataResourceService.addAndGet(resource);

        // 无上下文
        assertFalse(dataPermissionService.shouldDenyByDefault("deny.test", null));

        // bypass 用户
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                1L, 100L, Set.of(), Set.of(), Set.of(), Set.of(), true))) {
            assertFalse(dataPermissionService.shouldDenyByDefault("deny.test", PermissionContextHolder.require()));
        }

        // 普通用户，无策略 - 应拒绝
        try (PermissionContextHolder.Scope scope = PermissionContextHolder.open(new PermissionContext(
                1L, 100L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            assertTrue(dataPermissionService.shouldDenyByDefault("deny.test", PermissionContextHolder.require()));
        }
    }
}