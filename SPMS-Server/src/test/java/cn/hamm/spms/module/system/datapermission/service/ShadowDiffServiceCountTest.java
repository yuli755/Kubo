package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.base.EntityRepositories;
import cn.hamm.spms.framework.curd.base.ICurdRepository;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

/**
 * <h1>影子比对：两次 COUNT 与落库（J4 的单路径取证）</h1>
 *
 * <p>这一格回答的是"证据是怎么数出来的、数不出来时留下什么"。它替代的是 J3 之前那个
 * {@code OrmParityEvidenceTest}（JPA 侧 vs Mapper 侧数两遍）—— 那条对账随
 * {@code spring-boot-starter-data-jpa} 一起退场，取而代之的是<b>同一条生产路径数两遍</b>：
 * 不带条件（baseline）与带上 enforce 本会注入的条件（guarded）。</p>
 *
 * <p>用 mock 而不是 H2 的原因：本格要钉的是<b>取数语义与失败语义</b>（解析不到仓储怎么办、
 * 数失败写什么结论），不是 SQL 能不能跑 —— 后者由 {@code *MapperRuntimeInterceptorTest}
 * 在真 MyBatis + H2 上覆盖。两类测试各管一半，别互相替代。</p>
 *
 * <p>同步调 {@link ShadowDiffService#doCompare} 而不是 {@code compare}：后者要先过采样、再进线程池，
 * 断言会变成"等异步"；包级可见的那一半本来就是为这条路径准备的。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShadowDiffService · MP 单路径取证")
class ShadowDiffServiceCountTest {

    private static final String RESOURCE = "asset.material";

    @Mock
    private DataPermissionAudit audit;

    @Mock
    private EntityRepositories entityRepositories;

    @Mock
    private ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider;

    @Mock
    private DataPermissionDiffRepository diffRepository;

    @Mock
    private ICurdRepository<MaterialEntity> repository;

    private DataPermissionProperties properties;
    private ShadowDiffService service;

    @BeforeEach
    void setUp() {
        properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.SHADOW);
        service = new ShadowDiffService(properties, audit, entityRepositories, diffRepositoryProvider);
        lenient().when(diffRepositoryProvider.getIfAvailable()).thenReturn(diffRepository);
    }

    private static RowPermissionPredicate fragment() {
        return new RowPermissionPredicate("(tenant_id = :dpRule0)", Map.of("dpRule0", 10L));
    }

    private static EntityRepositories.Binding bindingOf(ICurdRepository<?> repository) {
        return new EntityRepositories.Binding(repository, "cn.hamm.spms.module.asset.material.MaterialRepository");
    }

    @Test
    @DisplayName("两次 COUNT 走同一个仓储：不带条件 / 带条件，差值落成 EXPECTED_FILTER")
    @SuppressWarnings("unchecked")
    void countsTwiceThroughTheSameRepository() {
        given(entityRepositories.bindingOf(MaterialEntity.class)).willReturn(bindingOf(repository));
        // 第一次（baseline，无条件）= 100；第二次（guarded，挂上片段）= 90
        given(repository.selectCount(any())).willReturn(100L, 90L);

        service.doCompare(RESOURCE, DataPermissionAction.QUERY, MaterialEntity.class,
                List.of(fragment()), 7L, 10L);

        ArgumentCaptor<DataPermissionDiffEntity> captor = ArgumentCaptor.forClass(DataPermissionDiffEntity.class);
        then(diffRepository).should().insert(captor.capture());
        DataPermissionDiffEntity written = captor.getValue();
        assertEquals(100L, written.getBaselineCount());
        assertEquals(90L, written.getGuardedCount(), "少了 10 行 = enforce 之后确实会收窄结果集");
        assertEquals(DataPermissionDiffEntity.VERDICT_EXPECTED_FILTER, written.getVerdict());
        assertEquals(RESOURCE, written.getResourceKey());
        assertEquals(7L, written.getUserId());
        assertEquals(10L, written.getTenantId());
    }

    @Test
    @DisplayName("数出来一样多 → CONSISTENT；但样本仍然要落库（否则放开前置会把它读成「没有证据」）")
    void consistentStillPersists() {
        given(entityRepositories.bindingOf(MaterialEntity.class)).willReturn(bindingOf(repository));
        given(repository.selectCount(any())).willReturn(7L, 7L);

        service.doCompare(RESOURCE, DataPermissionAction.QUERY, MaterialEntity.class,
                List.of(fragment()), 7L, 10L);

        ArgumentCaptor<DataPermissionDiffEntity> captor = ArgumentCaptor.forClass(DataPermissionDiffEntity.class);
        then(diffRepository).should().insert(captor.capture());
        assertEquals(DataPermissionDiffEntity.VERDICT_CONSISTENT, captor.getValue().getVerdict());
    }

    @Test
    @DisplayName("实体没有写侧仓储：记 ERROR 而不是「一致」——没有证据与一致是两种结论")
    void missingRepositoryIsNotEvidence() {
        given(entityRepositories.bindingOf(MaterialEntity.class)).willReturn(null);

        service.doCompare(RESOURCE, DataPermissionAction.QUERY, MaterialEntity.class,
                List.of(fragment()), 7L, 10L);

        then(audit).should().binding(eq(RESOURCE), eq("shadow-compare-failed"), anyString());
        ArgumentCaptor<DataPermissionDiffEntity> captor = ArgumentCaptor.forClass(DataPermissionDiffEntity.class);
        then(diffRepository).should().insert(captor.capture());
        assertEquals(DataPermissionDiffEntity.VERDICT_ERROR, captor.getValue().getVerdict());
        assertNotNull(captor.getValue().getDetail(), "差异说明要留下失败原因，否则面板上只剩一个 ERROR 字样");
    }

    @Test
    @DisplayName("数不出来时不写「两份都是 null 所以一致」这种最危险的结论")
    void errorVerdictIsExplicit() {
        assertEquals(DataPermissionDiffEntity.VERDICT_ERROR, ShadowDiffService.verdictOf(null, null));
        assertEquals(DataPermissionDiffEntity.VERDICT_CONSISTENT, ShadowDiffService.verdictOf(0L, 0L),
                "0 与 0 是「确实只有这些行」，不是「没数出来」");
    }

    @Test
    @DisplayName("实体类解不出来（投影/Object.class）→ 连仓储都不去解析")
    void unresolvableEntityIsSkippedBeforeAnyCount() {
        properties.getShadow().setSampleRate(1.0D);

        service.compare(RESOURCE, DataPermissionAction.QUERY, Object.class, List.of(fragment()), null);

        then(entityRepositories).shouldHaveNoInteractions();
        then(diffRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("采样只在 SHADOW 档打开：OFF 不参与、ENFORCE 已经真注入")
    void samplingOnlyInShadowMode() {
        properties.getShadow().setSampleRate(1.0D);
        assertTrue(service.sampled(RESOURCE), "SHADOW + 采样率 100% 必须采样");

        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        assertFalse(service.sampled(RESOURCE), "enforce 下条件已经注入，再双跑是多打一次全表 COUNT");

        properties.setMode(DataPermissionProperties.Mode.OFF);
        assertFalse(service.sampled(RESOURCE));

        properties.setMode(DataPermissionProperties.Mode.SHADOW);
        properties.getShadow().setSampleRate(0D);
        assertFalse(service.sampled(RESOURCE), "采样率 0 = 关掉观测，不能退回「总是采样」");
    }
}
