package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

/**
 * <h1>敏感字段写侧保护（掩码回环的完整闭环）</h1>
 *
 * <p>这里刻意<b>不用</b>手写的 {@code "****"} 当输入，而是先让真实的读侧
 * （{@link ColumnMaskingService}）把原值遮出来，再把那串结果喂回 Jackson。
 * 理由是这条链路的全部风险都在"两侧口径不一致"：读侧改成 {@code 受限} 之类
 * 不带星号的写法，写侧就认不出来，掩码直接落库 —— 而这在各自单测里都是绿的。</p>
 *
 * <p>不启动 Spring：{@code @JsonDeserialize} 是 Jackson 自己就会装配的注解，
 * 一个裸 {@link ObjectMapper} 就足以覆盖"反序列化 → 登记 → 恢复/拒绝"整条链，
 * 跑得快才有资格当回归测试。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class SensitiveWriteProtectionTest {

    private static final String RESOURCE_KEY = "demo.sample";

    @Mock
    private DataFieldPolicyProvider fieldPolicyProvider;

    private ColumnMaskingService maskingService;

    private ObjectMapper objectMapper;

    private PermissionContext context;

    /**
     * 敏感字段样本：字符串（可恢复）、显式关闭恢复、数值（只能明确失败）、非敏感字段
     */
    public static class SampleEntity {

        @SensitiveField(resourceKey = RESOURCE_KEY)
        private String phone;

        /**
         * 显式关掉兜底，用来钉住"注解上的开关真的起作用"
         */
        @SensitiveField(resourceKey = RESOURCE_KEY, restoreOnMasked = false)
        private String remark;

        /**
         * 数值字段：本项目真实场景（{@code Double purchasePrice}）
         */
        @SensitiveField(resourceKey = RESOURCE_KEY)
        private Double price;

        /**
         * 未标注：必须完全不受本机制影响
         */
        private String name;

        public String getPhone() {
            return phone;
        }

        public SampleEntity setPhone(String phone) {
            this.phone = phone;
            return this;
        }

        public String getRemark() {
            return remark;
        }

        public SampleEntity setRemark(String remark) {
            this.remark = remark;
            return this;
        }

        public Double getPrice() {
            return price;
        }

        public SampleEntity setPrice(Double price) {
            this.price = price;
            return this;
        }

        public String getName() {
            return name;
        }

        public SampleEntity setName(String name) {
            this.name = name;
            return this;
        }
    }

    public static class PlainEntity {

        private String note;

        public String getNote() {
            return note;
        }

        public PlainEntity setNote(String note) {
            this.note = note;
            return this;
        }
    }

    public static class PrimitiveEntity {

        @SensitiveField(resourceKey = "demo.bad")
        private long amount;

        public long getAmount() {
            return amount;
        }

        public PrimitiveEntity setAmount(long amount) {
            this.amount = amount;
            return this;
        }
    }

    @BeforeEach
    void setUp() {
        maskingService = new ColumnMaskingService(fieldPolicyProvider);
        objectMapper = new ObjectMapper();
        context = new PermissionContext(1L, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("planner"), false);
        MaskedInputRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        // 登记是 ThreadLocal，测试线程同样会被复用；漏清会让下一条用例"莫名被恢复"
        MaskedInputRegistry.clear();
    }

    /**
     * 让读侧对 phone 生效 PARTIAL 策略，返回它实际写出的那串掩码
     */
    private String maskedPhoneOf(String original) {
        given(fieldPolicyProvider.getEnabledPoliciesByResourceAndAction(RESOURCE_KEY, "QUERY"))
                .willReturn(List.of(new DataFieldPolicyEntity()
                        .setFieldName("phone")
                        .setRoleCode("planner")
                        .setMaskType("PARTIAL")
                        .setMaskExpression(null)
                        .setAllowPlaintext(false)
                        .setPriority(100)
                        .setEnabled(true)));
        return String.valueOf(maskingService.maskFieldValue(RESOURCE_KEY, "phone", original, context));
    }

    @Test
    void maskedValueFromReadSideIsNotWrittenIntoEntity() {
        String masked = maskedPhoneOf("13800138000");
        assertTrue(masked.contains("*"), "样本前提：读侧产出必须含星号，否则这条测试没意义");

        SampleEntity incoming = read("{\"phone\":\"" + masked + "\",\"name\":\"只改了昵称\"}");

        assertNull(incoming.getPhone(), "掩码不能落到实体上（否则下一步就是落库）");
        assertEquals("只改了昵称", incoming.getName(), "非敏感字段照常接收");
        assertTrue(MaskedInputRegistry.isMarked(SampleEntity.class, "phone"),
                "置 null 之后必须还认得出\"这是掩码\"，否则无法与用户主动清空区分");
    }

    @Test
    void restoreTakesTheStoredValueForMarkedFieldsOnly() {
        MaskedInputRegistry.mark(SampleEntity.class, "phone");
        SampleEntity incoming = new SampleEntity().setPhone(null).setName("新昵称");
        SampleEntity stored = new SampleEntity().setPhone("13800138000").setName("旧昵称");

        assertTrue(SensitiveWriteProtection.restoreOnUpdate(incoming, stored));

        assertEquals("13800138000", incoming.getPhone(), "受限字段保持库中原值");
        assertEquals("新昵称", incoming.getName(), "本次真的改了的字段必须保留");
    }

    @Test
    void genuineClearIsNotRestored() {
        // 没登记 = 用户显式提交 null（清空），这与"提交掩码"处置相反，是整套设计的分界点
        SampleEntity incoming = read("{\"phone\":null}");
        SampleEntity stored = new SampleEntity().setPhone("13800138000");

        assertFalse(SensitiveWriteProtection.restoreOnUpdate(incoming, stored));
        assertNull(incoming.getPhone(), "主动清空就该写成空，不能被\"恢复\"悄悄改回去");
    }

    @Test
    void realValueIsParsedByJacksonWithoutMarking() {
        SampleEntity incoming = read("{\"phone\":\"13800138000\",\"price\":29999.5}");

        assertEquals("13800138000", incoming.getPhone());
        assertEquals(29999.5, incoming.getPrice(), 1e-9);
        assertFalse(MaskedInputRegistry.hasAny(), "真实值不该被登记，否则下次更新会被无端回表");
    }

    @Test
    void restoreCanBeSwitchedOffPerField() {
        MaskedInputRegistry.mark(SampleEntity.class, "remark");
        SampleEntity incoming = new SampleEntity().setRemark(null);
        SampleEntity stored = new SampleEntity().setRemark("库里旧值");

        assertFalse(SensitiveWriteProtection.restoreOnUpdate(incoming, stored),
                "restoreOnMasked=false 的字段允许被写成空，兜底只在开启时生效");
        assertNull(incoming.getRemark());
    }

    @Test
    void numericFieldFailsInsteadOfSilentlyCorrupting() {
        given(fieldPolicyProvider.getEnabledPoliciesByResourceAndAction(RESOURCE_KEY, "QUERY"))
                .willReturn(List.of(new DataFieldPolicyEntity()
                        .setFieldName("price")
                        .setRoleCode("planner")
                        .setMaskType("FULL")
                        .setMaskExpression(null)
                        .setAllowPlaintext(false)
                        .setPriority(100)
                        .setEnabled(true)));
        String masked = String.valueOf(maskingService.maskFieldValue(RESOURCE_KEY, "price", 29999D, context));

        // 不锁定异常类型：框架会把反序列化期异常再包一层，能锁定的是"绝不静默通过"
        assertThrows(Exception.class, () -> read("{\"price\":\"" + masked + "\"}"),
                "数值字段收到掩码必须失败：置 null 会被 @NotNull 报成\"不能为空\"，写 0 会静默污染金额");
    }

    @Test
    void addRejectsMaskedPlaceholder() {
        MaskedInputRegistry.mark(SampleEntity.class, "phone");
        SampleEntity incoming = new SampleEntity().setPhone(null).setName("新建");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> SensitiveWriteProtection.rejectMaskedOnAdd(incoming));
        assertTrue(ex.getMessage().contains("受限"), "报错要说清是权限问题，而不是让人去查数据格式");
    }

    @Test
    void addAcceptsRealValues() {
        assertDoesNotThrow(() -> SensitiveWriteProtection.rejectMaskedOnAdd(
                new SampleEntity().setPhone("13800138000")));
    }

    @Test
    void entitiesWithoutSensitiveFieldsSkipTheWholePath() {
        assertFalse(SensitiveWriteProtection.guards(PlainEntity.class), "非敏感实体不该触发回表");
        MaskedInputRegistry.mark(SampleEntity.class, "phone");
        assertFalse(SensitiveWriteProtection.hasMaskedInput(PlainEntity.class),
                "回表与否按实体判断：别让用户改价格连带把用户表查一遍");
    }

    @Test
    void primitiveFieldsFailFastAtConfigurationTime() {
        // 基本类型承载不了"未提供"（无法置 null），恢复语义无从表达 —— 配置错误要立刻炸出来，
        // 而不是等到线上某次保存才发现"看起来有保护其实没有"
        assertThrows(IllegalStateException.class,
                () -> SensitiveWriteProtection.bindingsFor(PrimitiveEntity.class));
    }

    /**
     * 非星号遮蔽（{@code maskExpression = "已隐藏"}）：星号口径必然漏，正向复算才拦得住。
     *
     * <p>这一组用例就是 §15.3 那个缺口的收口点：判定不再猜"像不像掩码"，而是问
     * "这个用户此刻从读侧看到的会不会正好就是这一串"。</p>
     */
    @Test
    void nonStarMaskExpressionIsCaughtByRecomputingReadSideOutput() {
        givenPolicy("phone", "FULL", "已隐藏", false);
        Object expected = maskingService.maskFieldValue(RESOURCE_KEY, "phone", "13800138000", context);
        assertEquals("已隐藏", expected, "前提：这类遮蔽不含星号，星号口径认不出来");
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder(expected));

        SampleEntity incoming = new SampleEntity().setPhone(String.valueOf(expected)).setName("只改了昵称");
        SampleEntity stored = new SampleEntity().setPhone("13800138000").setName("旧昵称");
        assertFalse(MaskedInputRegistry.hasAny(), "前提：反序列化阶段没登记（认不出来），只能靠复算兜住");

        assertTrue(SensitiveWriteProtection.mayContainMaskedOutput(SampleEntity.class, maskingService, context),
                "该用户对此资源确有遮蔽策略 -> 值得回表一次");
        assertTrue(SensitiveWriteProtection.restoreMatchingMaskedOutput(incoming, stored, maskingService, context));
        assertEquals("13800138000", incoming.getPhone(), "命中读侧输出 -> 保留库中原值");
        assertEquals("只改了昵称", incoming.getName());
    }

    /**
     * 用户恰好输入了与掩码文本一样的内容时，代价是"这一次输入被丢弃"，
     * 而不是"掩码被写进真实列"。方向必须固定成前者，这里把它钉住。
     */
    @Test
    void coincidentalSameTextKeepsStoredValueOnPurpose() {
        givenPolicy("phone", "FULL", "已隐藏", false);
        SampleEntity incoming = new SampleEntity().setPhone("已隐藏");
        SampleEntity stored = new SampleEntity().setPhone("13800138000");

        assertTrue(SensitiveWriteProtection.restoreMatchingMaskedOutput(incoming, stored, maskingService, context));
        assertEquals("13800138000", incoming.getPhone(), "宁可丢一次输入，也不让遮蔽值落库");
    }

    /**
     * 明文放开的角色不能因为这套机制多付任何代价（既不回表，也不改值）
     */
    @Test
    void plaintextAllowedUsersSkipTheWholeRecomputation() {
        givenPolicy("phone", "FULL", "已隐藏", true);
        assertFalse(SensitiveWriteProtection.mayContainMaskedOutput(SampleEntity.class, maskingService, context),
                "allowPlaintext 的用户看到的就是原值，比对纯属浪费");

        SampleEntity incoming = new SampleEntity().setPhone("13900139000");
        SampleEntity stored = new SampleEntity().setPhone("13800138000");
        assertFalse(SensitiveWriteProtection.restoreMatchingMaskedOutput(incoming, stored, maskingService, context));
        assertEquals("13900139000", incoming.getPhone(), "明文书写必须照常生效");
    }

    /**
     * 复算路径把"没遮蔽"寄托在 {@code maskFieldValue} 返回**同一个对象引用**上（不是 equals）。
     * 这个前提一旦被人改掉（例如"统一返回 value.toString()"），保护不会报错、只会静默失效，
     * 所以单独钉一条，而不是让它躲在其它用例里。
     */
    @Test
    void unmaskedValuesComeBackAsTheSameObjectReference() {
        givenPolicyNone();
        String stored = "13800138000";
        assertSame(stored, maskingService.maskFieldValue(RESOURCE_KEY, "phone", stored, context),
                "无策略：原对象返回");

        givenPolicy("phone", "FULL", "已隐藏", true);
        assertSame(stored, maskingService.maskFieldValue(RESOURCE_KEY, "phone", stored, context),
                "allowPlaintext：同样原对象返回，复算必须跳过");

        givenPolicy("phone", "FULL", "已隐藏", false);
        assertNotSame(stored, maskingService.maskFieldValue(RESOURCE_KEY, "phone", stored, context),
                "真遮蔽：必须不是原对象，否则保护等于没做");
    }

    @Test
    void noPolicyAtAllMeansNoExtraQuery() {
        givenPolicyNone();
        assertFalse(SensitiveWriteProtection.mayContainMaskedOutput(SampleEntity.class, maskingService, context),
                "没配策略 = 读侧不遮蔽 = 不需要复算，正常保存保持零额外查询");
        assertFalse(SensitiveWriteProtection.mayContainMaskedOutput(SampleEntity.class, null, context),
                "容器未就绪时同样跳过（不能因为防护自身不可用而阻断业务）");
        assertFalse(SensitiveWriteProtection.mayContainMaskedOutput(SampleEntity.class, maskingService, null),
                "无权限上下文（内部调用）时跳过");
    }

    private void givenPolicy(String fieldName, String maskType, String expression, boolean allowPlaintext) {
        given(fieldPolicyProvider.getEnabledPoliciesByResourceAndAction(RESOURCE_KEY, "QUERY"))
                .willReturn(List.of(new DataFieldPolicyEntity()
                        .setFieldName(fieldName)
                        .setRoleCode("planner")
                        .setMaskType(maskType)
                        .setMaskExpression(expression)
                        .setAllowPlaintext(allowPlaintext)
                        .setPriority(100)
                        .setEnabled(true)));
    }

    private void givenPolicyNone() {
        given(fieldPolicyProvider.getEnabledPoliciesByResourceAndAction(RESOURCE_KEY, "QUERY"))
                .willReturn(List.of());
    }

    @Test
    void bindingsCoverEveryAnnotatedFieldOfTheHierarchy() {
        List<SensitiveWriteProtection.Binding> bindings = SensitiveWriteProtection.bindingsFor(SampleEntity.class);

        assertEquals(3, bindings.size(), "三个 @SensitiveField 字段都要被收进绑定表");
        assertTrue(bindings.stream().anyMatch(b -> "phone".equals(b.jsonName()) && b.restoreOnMasked()));
        assertTrue(bindings.stream().anyMatch(b -> "remark".equals(b.jsonName()) && !b.restoreOnMasked()));
        assertTrue(bindings.stream().allMatch(b -> RESOURCE_KEY.equals(b.resourceKey())));
        assertFalse(bindings.stream().anyMatch(b -> "name".equals(b.jsonName())), "未标注的字段不参与");
    }

    @Test
    void clearingTheRegistryIsIdempotent() {
        MaskedInputRegistry.mark(SampleEntity.class, "phone");
        assertTrue(MaskedInputRegistry.hasAny());
        MaskedInputRegistry.clear();
        MaskedInputRegistry.clear();
        assertFalse(MaskedInputRegistry.hasAny());
        assertFalse(MaskedInputRegistry.isMarked(SampleEntity.class, "phone"));
    }

    private SampleEntity read(String json) {
        try {
            return objectMapper.readValue(json, SampleEntity.class);
        }
        catch (Exception ex) {
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    /**
     * 字段名对不上策略表就等于"没配策略"（原值输出）—— 这条前提是整个链路的地基，
     * 也是前端门禁（{@code maskContract.spec.ts}）要校验"字段名真实存在"的原因。
     */
    @Test
    void noPolicyForOtherFieldMeansNoMasking() {
        given(fieldPolicyProvider.getEnabledPoliciesByResourceAndAction(RESOURCE_KEY, "QUERY"))
                .willReturn(Arrays.asList());
        assertNull(maskingService.getEffectiveMaskPolicy(RESOURCE_KEY, "name", context),
                "没有匹配字段的策略 = 不遮蔽");
    }
}
