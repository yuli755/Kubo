package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.web.annotation.Desensitize;
import cn.hamm.spms.framework.web.annotation.DesensitizeType;
import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static cn.hamm.spms.module.system.datapermission.service.OperationLogSnapshot.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作审计快照的脱敏测试
 *
 * <p>钉三件事：</p>
 * <ol>
 *   <li>敏感字段进快照的是<b>摘要</b>而不是原值——审计表不能变成脱敏列的第二份明文副本；</li>
 *   <li>摘要仍然能表达「这个字段变了」——常量占位会让字段变更 Diff 把真实改动报成没改动，
 *       假阴性比不报告更坏；</li>
 *   <li>瞬态请求载体（用户手输的原文密码、邮箱验证码）整条不收。</li>
 * </ol>
 *
 * <p>摘要口径与列级脱敏的 HASH 策略同源（{@link MaskedValueDigest}，
 * 由 {@code ColumnMaskingService.digest} 委托），那一侧的形状断言在
 * {@code ColumnMaskingServiceTest}「HASH 结果带 h: 前缀 / h: + 12 位十六进制」。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("OperationLogSnapshot · 审计快照不留明文敏感值")
class OperationLogSnapshotMaskingTest {

    /**
     * 与列级脱敏 HASH 策略的输出同形：{@code h:} + 12 位十六进制
     */
    private static final Pattern DIGEST = Pattern.compile("^h:[0-9a-f]{12}$");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 探针实体：四类「不许明文出去」的注解各来一个，外加普通字段与空值敏感字段。
     *
     * <p>字段取值刻意用真实世界里会出现的形态（价格、中文姓名、密码、盐），
     * 这样「快照里搜不到原文」这条断言才有意义。</p>
     */
    private static class AuditedRow {

        private String code = "MA20260001";

        private String name = "MacBook Pro M4 Max";

        /**
         * 策略驱动的列级脱敏字段（物料销售价）
         */
        @SensitiveField(resourceKey = "asset.material")
        private Double salePrice = 27999.0D;

        /**
         * 策略驱动但没有配策略时为 null 的敏感字段：null 不能被摘要成 "h:..."
         */
        @SensitiveField(resourceKey = "asset.material")
        private Double purchasePrice;

        /**
         * 框架级固定脱敏字段（中文姓名）
         */
        @Desensitize(DesensitizeType.CHINESE_NAME)
        private String realName = "凌小云";

        /**
         * 只进不出的登录密码
         */
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String password = "Aa123456";

        /**
         * 双向都不序列化的密码盐
         */
        @JsonIgnore
        private String salt = "s3cr3t-salt";

        /**
         * 瞬态请求载体：改密表单里用户手输的原文密码。它不入库，但反射收字段时会收到
         */
        @Transient
        private String oldPassword = "Aa123456";

        /**
         * 瞬态请求载体：邮箱验证码
         */
        @Transient
        private String code2 = "483920";
    }

    private static JsonNode snapshotOf(AuditedRow row) {
        try {
            return MAPPER.readTree(of(row));
        }
        catch (Exception ex) {
            throw new IllegalStateException("快照不是合法 JSON：" + of(row), ex);
        }
    }

    @Test
    @DisplayName("敏感字段存摘要，普通字段照常可读：审计表不是脱敏列的明文副本")
    void sensitiveFieldsAreDigestWhilePlainFieldsStayReadable() {
        String json = of(new AuditedRow());

        // 任何一个原文出现在快照里都算失败：这张表会被 /dataResource/operation-logs 原样返回
        assertFalse(json.contains("27999"), "销售价原文进了审计快照");
        assertFalse(json.contains("凌小云"), "真实姓名原文进了审计快照");
        assertFalse(json.contains("Aa123456"), "密码原文进了审计快照");
        assertFalse(json.contains("s3cr3t-salt"), "密码盐原文进了审计快照");

        JsonNode snapshot = snapshotOf(new AuditedRow());
        assertTrue(DIGEST.matcher(snapshot.get("salePrice").asText()).matches(),
                "@SensitiveField 字段应是 h: + 12 位十六进制摘要，实际：" + snapshot.get("salePrice"));
        assertTrue(DIGEST.matcher(snapshot.get("realName").asText()).matches(),
                "@Desensitize 字段应被摘要，实际：" + snapshot.get("realName"));
        assertTrue(DIGEST.matcher(snapshot.get("password").asText()).matches(),
                "@JsonProperty(WRITE_ONLY) 字段应被摘要，实际：" + snapshot.get("password"));
        assertTrue(DIGEST.matcher(snapshot.get("salt").asText()).matches(),
                "@JsonIgnore 字段应被摘要，实际：" + snapshot.get("salt"));

        assertEquals("MA20260001", snapshot.get("code").asText(), "普通字段必须保持可读，否则审计失去意义");
        assertEquals("MacBook Pro M4 Max", snapshot.get("name").asText(), "普通字段必须保持可读");
    }

    @Test
    @DisplayName("摘要仍能表达「这个字段变了」：常量占位会把真实改动报成没改动")
    void digestStillRevealsThatAValueChanged() {
        AuditedRow before = new AuditedRow();
        before.salePrice = 480.0D;
        AuditedRow after = new AuditedRow();
        after.salePrice = 455.0D;
        AuditedRow unchanged = new AuditedRow();
        unchanged.salePrice = 480.0D;

        assertNotEquals(snapshotOf(before).get("salePrice").asText(),
                snapshotOf(after).get("salePrice").asText(),
                "改价前后摘要必须不同，否则字段变更 Diff 会把改价报成「没有变动」（假阴性）");
        assertEquals(snapshotOf(before).get("salePrice").asText(),
                snapshotOf(unchanged).get("salePrice").asText(),
                "同值必须同码，否则没改的字段会被报成改了（假阳性）");
    }

    @Test
    @DisplayName("瞬态请求载体整条不收：原文密码与验证码不该落到审计表")
    void transientRequestCarriersAreDroppedEntirely() {
        JsonNode snapshot = snapshotOf(new AuditedRow());

        assertFalse(snapshot.has("oldPassword"), "用户手输的原文密码不该进审计快照");
        assertFalse(snapshot.has("code2"), "邮箱验证码不该进审计快照");
        // 上一条断的是「键不在」，这一条断的是「值也不在」：字段改名不该让原文重新漏出去
        assertFalse(of(new AuditedRow()).contains("483920"), "验证码取值出现在快照文本里");
    }

    @Test
    @DisplayName("空值敏感字段仍是 null：null 不能被摘要成 h: 否则「没填」会被报成「有值」")
    void nullSensitiveValueStaysNull() {
        JsonNode snapshot = snapshotOf(new AuditedRow());

        assertTrue(snapshot.has("purchasePrice"), "字段应存在（键在，值为 null）");
        assertTrue(snapshot.get("purchasePrice").isNull(), "null 应原样为 null，实际：" + snapshot.get("purchasePrice"));
        assertNull(MaskedValueDigest.ofValue(null), "摘要工具对 null 也必须返回 null");
    }

    @Test
    @DisplayName("摘要口径稳定且与观看者无关：同值同码、异值异码、算法输出形状固定")
    void digestIsStableAndViewerIndependent() {
        assertEquals(MaskedValueDigest.of("27999.0"), MaskedValueDigest.of("27999.0"), "同值同码");
        assertNotEquals(MaskedValueDigest.of("27999.0"), MaskedValueDigest.of("29999.0"), "异值异码");
        assertTrue(DIGEST.matcher(MaskedValueDigest.of("27999.0")).matches(),
                "输出必须是 h: + 12 位十六进制，与列级脱敏 HASH 策略同形");
        // 审计在异步线程上落库、且不能因「谁在改」而改变留存内容，所以这里刻意不接受任何身份上下文
        assertTrue(MaskedValueDigest.of("27999.0").startsWith("h:"), "前缀必须是 h:（与 HASH 策略逐字一致）");
        assertEquals(14, MaskedValueDigest.of("27999.0").length(), "总长 = h: 两位 + 12 位十六进制");
    }
}
