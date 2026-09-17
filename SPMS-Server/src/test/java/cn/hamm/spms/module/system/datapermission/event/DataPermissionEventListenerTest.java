package cn.hamm.spms.module.system.datapermission.event;

import cn.hamm.spms.framework.redis.RedisHelper;
import cn.hamm.spms.module.factory.storage.StorageHierarchyIndex;
import cn.hamm.spms.module.personnel.department.DepartmentHierarchyIndex;
import cn.hamm.spms.module.system.datapermission.service.DataFieldPolicyService;
import cn.hamm.spms.module.system.datapermission.service.JpaPermissionRuleProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * <h1>DataPermissionEventListener 契约测试</h1>
 *
 * <p>钉两类不变量。<b>启动安全</b>：{@code run()} 必须立刻返回 —— 曾经的回归是
 * 主线程直接 {@code Jedis.subscribe}（阻塞语义），把 DevDataInitRunner 与
 * ApplicationReadyEvent 永久卡死；无 Redis / 无连接工厂时必须安静地 no-op。
 * <b>广播路由</b>：每种事件类型只打掉它该打掉的那一层缓存，
 * FULL_REFRESH 全打 —— 漏打是越权方向（旧树/旧策略继续放行）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataPermissionEventListener · 非阻塞订阅与事件路由")
@ExtendWith(MockitoExtension.class)
class DataPermissionEventListenerTest {

    @Mock
    private RedisHelper redisHelper;

    @Mock
    private JpaPermissionRuleProvider ruleProvider;

    @Mock
    private DataFieldPolicyService dataFieldPolicyService;

    @Mock
    private DepartmentHierarchyIndex departmentHierarchyIndex;

    @Mock
    private StorageHierarchyIndex storageHierarchyIndex;

    @Test
    @DisplayName("无 Redis 时 run() 安静 no-op 且立刻返回（绝不阻塞启动主线程）")
    void runNoOpsWithoutRedisAndReturnsImmediately() {
        given(redisHelper.getRedisTemplate()).willReturn(null);
        DataPermissionEventListener listener = newListener();

        long start = System.nanoTime();
        assertDoesNotThrow(() -> listener.run(null));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 1000, "run() 必须立即返回，实际耗时 " + elapsedMs + "ms");
        verifyNoInteractions(ruleProvider, dataFieldPolicyService, departmentHierarchyIndex, storageHierarchyIndex);
        // 从未建立订阅容器时，关停路径也不许炸（应用正常退出要走到这里）
        assertDoesNotThrow(listener::shutdownSubscription);
    }

    @Test
    @DisplayName("有模板但无连接工厂：同样 no-op，不建容器")
    void runNoOpsWhenConnectionFactoryMissing() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        given(redisHelper.getRedisTemplate()).willReturn(template);
        given(template.getConnectionFactory()).willReturn(null);
        DataPermissionEventListener listener = newListener();

        assertDoesNotThrow(() -> listener.run(null));
        assertDoesNotThrow(listener::shutdownSubscription);
    }

    @Test
    @DisplayName("FULL_REFRESH：规则/字段/部门树/工厂树四层缓存全部打掉")
    void fullRefreshInvalidatesEverything() {
        DataPermissionEventListener listener = newListener();

        listener.onMessage(message("{\"eventType\":\"FULL_REFRESH\"}"), null);

        verify(ruleProvider).clearAllCache();
        verify(dataFieldPolicyService).clearAllCache();
        verify(departmentHierarchyIndex).invalidate();
        verify(storageHierarchyIndex).invalidate();
    }

    @Test
    @DisplayName("DEPARTMENT_TREE_CHANGED 只打掉部门树索引，不牵连其它缓存")
    void departmentTreeChangedScopedToDeptIndex() {
        DataPermissionEventListener listener = newListener();

        listener.onMessage(message("{\"eventType\":\"DEPARTMENT_TREE_CHANGED\"}"), null);

        verify(departmentHierarchyIndex).invalidate();
        verifyNoInteractions(storageHierarchyIndex, ruleProvider, dataFieldPolicyService);
    }

    @Test
    @DisplayName("STORAGE_TREE_CHANGED 只打掉工厂树索引（D13）")
    void storageTreeChangedScopedToStorageIndex() {
        DataPermissionEventListener listener = newListener();

        listener.onMessage(message("{\"eventType\":\"STORAGE_TREE_CHANGED\"}"), null);

        verify(storageHierarchyIndex).invalidate();
        verifyNoInteractions(departmentHierarchyIndex, ruleProvider, dataFieldPolicyService);
    }

    @Test
    @DisplayName("FIELD_POLICY_CHANGED 只清该资源的字段脱敏缓存")
    void fieldPolicyChangedScopedToFieldCache() {
        DataPermissionEventListener listener = newListener();

        listener.onMessage(message("{\"eventType\":\"FIELD_POLICY_CHANGED\",\"resourceKey\":\"wms.inventory\"}"), null);

        verify(dataFieldPolicyService).clearCache("wms.inventory");
        verifyNoInteractions(ruleProvider, departmentHierarchyIndex, storageHierarchyIndex);
    }

    @Test
    @DisplayName("坏报文只记 WARN 不抛出：广播侧的脏数据不能拖垮监听线程")
    void malformedMessageIsSwallowed() {
        DataPermissionEventListener listener = newListener();

        assertDoesNotThrow(() -> listener.onMessage(message("this-is-not-json{{"), null));
    }

    private DataPermissionEventListener newListener() {
        return new DataPermissionEventListener(redisHelper, ruleProvider,
                dataFieldPolicyService, departmentHierarchyIndex, storageHierarchyIndex);
    }

    private static DefaultMessage message(String body) {
        return new DefaultMessage(
                "spms:datapermission:changes".getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }
}
