package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import org.mockito.Mockito;

/**
 * <h1>导出内容遮蔽的行为</h1>
 *
 * <p>这里锁四件事，每件都对应一个"看起来合理但会出事故"的方向：</p>
 * <ol>
 *   <li><b>只改副本</b>：入参实体可能还挂在持久化上下文里，直接改它 = 把掩码写进库；</li>
 *   <li><b>明文授权确实生效</b>：拿到 EXPORT + allowPlaintext 的角色必须拿到原值，
 *       否则这条授权是装饰，而受限角色的导出又会红一大片；</li>
 *   <li><b>mode=OFF 是回滚位</b>：数据权限整体关掉时导出内容也不许被改动，否则"回滚"要重启才行；</li>
 *   <li><b>上下文缺失时不猜</b>：异步导出线程拿不到 {@link PermissionContext} 时原样放行并记账 ——
 *       这是已知边界（类注释里写清了补齐方式），测试的作用是别让人把它误当成"已经处理好了"。</li>
 * </ol>
 *
 * @author Hamm.cn
 */
class ExportColumnMaskerTest {

    private static final String RK = "wms.material";

    /**
     * 测试用实体：一个受限列 + 一个普通列
     */
    public static class Row {

        private Long id;

        @SensitiveField(resourceKey = RK)
        private String phone;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    private ColumnMaskingService maskingService;
    private DataPermissionAudit audit;
    private DataPermissionProperties properties;

    @BeforeEach
    void setUp() {
        maskingService = Mockito.mock(ColumnMaskingService.class);
        audit = Mockito.mock(DataPermissionAudit.class);
        properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.SHADOW);
        // DataPermissionRuntime 是"构造即写静态句柄"的桥（Jackson 自己 new 序列化器，没法注入依赖），
        // 所以单测里也照这个方式接线；静态字段跨用例共享，每个用例都重新赋值，别依赖上一个用例留下的状态。
        new DataPermissionRuntime(maskingService, audit, properties);
        given(maskingService.maskFieldValue(eq(RK), eq("phone"), any(), any()))
                .willAnswer(invocation -> "1******0");
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    private static PermissionContext context(boolean bypass) {
        return new PermissionContext(2L, 1L, Set.of(), Set.of(), Set.of(), Set.of("buyer"), bypass);
    }

    private static Row row(String phone) {
        Row row = new Row();
        row.setId(9L);
        row.setPhone(phone);
        return row;
    }

    @Test
    @DisplayName("受限列在副本上被遮成与界面同形的掩码，入参实体不受影响")
    void masksOnACopyNotOnTheManagedEntity() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context(false))) {
            Row original = row("13800138000");
            List<Row> out = ExportColumnMasker.maskForExport(List.of(original));

            assertEquals("1******0", out.get(0).getPhone(), "导出文件里的手机号必须和界面一致");
            assertEquals(9L, out.get(0).getId(), "非敏感列原样带过去");
            assertEquals("13800138000", original.getPhone(), "绝不能改入参实体：那是持久化上下文里的对象");
            assertNotSame(original, out.get(0));
        }
    }

    @Test
    @DisplayName("拿到 EXPORT 明文授权 → 文件里写原值，且不产生副本")
    void keepsPlaintextWhenExportGrantExists() {
        DataFieldPolicyEntity grant = new DataFieldPolicyEntity();
        grant.setAllowPlaintext(true);
        given(maskingService.getEffectiveMaskPolicy(RK, "phone", ExportBypassGuard.ACTION_EXPORT, context(false)))
                .willReturn(grant);

        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context(false))) {
            Row original = row("13800138000");
            List<Row> out = ExportColumnMasker.maskForExport(List.of(original));

            assertEquals("13800138000", out.get(0).getPhone());
            assertSame(original, out.get(0), "不需要改动时连复制都不做，白造对象只会让导出变慢");
        }
    }

    @Test
    @DisplayName("mode=OFF：整条导出遮蔽退场（回滚位必须真的是回滚位）")
    void offModeLeavesContentUntouched() {
        properties.setMode(DataPermissionProperties.Mode.OFF);
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context(false))) {
            Row original = row("13800138000");
            List<Row> out = ExportColumnMasker.maskForExport(List.of(original));

            assertSame(original, out.get(0));
            assertEquals("13800138000", out.get(0).getPhone());
        }
    }

    @Test
    @DisplayName("旁路角色（系统账号）不遮：他界面看到的就是原值")
    void bypassRoleSeesRawValues() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context(true))) {
            Row original = row("13800138000");
            List<Row> out = ExportColumnMasker.maskForExport(List.of(original));

            assertSame(original, out.get(0));
            Mockito.verifyNoInteractions(maskingService);
        }
    }

    @Test
    @DisplayName("上下文缺失：原样返回 + 记 export-without-context 账，不静默也不硬遮")
    void recordsAndPassesThroughWhenContextMissing() {
        Row original = row("13800138000");
        List<Row> out = ExportColumnMasker.maskForExport(List.of(original));

        assertSame(original, out.get(0), "没有上下文就无从判断该给谁明文，硬遮会误伤已授权角色");
        Mockito.verify(audit).shadow(eq(RK), eq(ExportColumnMasker.REASON_EXPORT_WITHOUT_CONTEXT), any());
        Mockito.verifyNoInteractions(maskingService);
    }

    @Test
    @DisplayName("没有 @SensitiveField 字段的实体直接透传（同一份列表引用）")
    void plainEntitiesPassThrough() {
        List<String> words = List.of("a", "b");
        assertSame(words, ExportColumnMasker.maskForExport(words));
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context(false))) {
            assertSame(words, ExportColumnMasker.maskForExport(words));
        }
    }

    @Test
    @DisplayName("空列表与含 null 的列表都不炸（框架分页尾批常见）")
    void toleratesEmptyAndNullRows() {
        assertEquals(List.of(), ExportColumnMasker.maskForExport(List.of()));
        // 注意别用 List.of(null)：JDK 自己就先抛 NPE，报出来的堆栈与导出遮蔽毫无关系
        //（这个形状本仓在 PermissionRuleKernelTest 里踩过一次）。
        List<Row> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        assertEquals(1, ExportColumnMasker.maskForExport(withNull).size());
    }
}
