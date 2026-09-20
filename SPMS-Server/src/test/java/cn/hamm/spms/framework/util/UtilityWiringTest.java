package cn.hamm.spms.framework.util;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.Export;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.common.export.EntityCsvExport;
import cn.hamm.spms.common.metadata.LegacyEntityMetadata;
import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.service.ColumnMaskingService;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionAudit;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionRuntime;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.common.AppConfig;
import cn.hamm.spms.common.exception.CustomError;
import cn.hamm.spms.common.exception.Errors;
import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import cn.hamm.spms.module.system.config.enums.ConfigType;
import cn.hamm.spms.common.cookie.CookieConfig;
import cn.hamm.spms.common.dictionary.EnumDictionaries;
import cn.hamm.spms.common.tree.TreeDeletionGuard;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.factory.structure.StructureEntity;
import cn.hamm.spms.module.personnel.department.DepartmentEntity;
import cn.hamm.spms.module.personnel.user.enums.UserTokenType;
import cn.hamm.spms.module.system.file.enums.FileCategory;
import cn.hamm.spms.module.system.coderule.CodeRuleController;
import cn.hamm.spms.module.system.coderule.enums.CodeRuleField;
import cn.hamm.spms.module.system.coderule.enums.CodeRuleParam;
import cn.hamm.spms.module.system.coderule.enums.SerialNumberUpdate;
import cn.hamm.spms.module.system.menu.MenuEntity;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <h1>工具与真实实体、错误边界、接口选项字段的接线契约</h1>
 *
 * <p>需要 Maven 依赖；不能把独立工具 runner 通过当成本类已执行。</p>
 *
 * @author Hamm.cn
 */
class UtilityWiringTest {
    @Test
    void realEntitiesImplementIndependentTreeContract() {
        assertRoot(new MenuEntity().setId(1L));
        assertRoot(new DepartmentEntity().setId(1L));
        assertRoot(new StorageEntity().setId(1L));
        assertRoot(new StructureEntity().setId(1L));
        // PermissionEntity 仍保留 IPermission 供旧权限扫描使用，两份接口应兼容。
        assertRoot(new PermissionEntity().setId(1L));
    }

    @Test
    void enumLookupUsesExistingKeys() {
        assertSame(SerialNumberUpdate.DAY, EnumDictionaries.require(SerialNumberUpdate.class,
                SerialNumberUpdate.DAY.getKey(), SerialNumberUpdate::getKey, SerialNumberUpdate::getLabel));
    }

    @Test
    void invalidDictionaryValuePreservesCodeMessageAndOptionData() {
        ServiceException error = assertThrows(ServiceException.class, () -> EnumDictionaries.require(
                SerialNumberUpdate.class, -999, SerialNumberUpdate::getKey, SerialNumberUpdate::getLabel));
        assertEquals(500, error.getCode());
        assertEquals("传入的值(SerialNumberUpdate=-999)不在字典可选范围内", error.getMessage());
        List<Map<String, Object>> options = new ArrayList<>();
        for (SerialNumberUpdate value : SerialNumberUpdate.values()) {
            options.add(Map.of("key", value.getKey(), "label", value.getLabel()));
        }
        assertEquals(options, error.getData());
    }

    @Test
    void ordinaryBusinessEnumsWorkAtTheExistingErrorBoundary() {
        assertSame(FileCategory.CONTRACT_ATTACHMENT, EnumDictionaries.require(FileCategory.class, 2001,
                FileCategory::getKey, FileCategory::getLabel));
        assertSame(UserTokenType.PERSONAL, EnumDictionaries.require(UserTokenType.class, 1,
                UserTokenType::getKey, UserTokenType::getLabel));
        ServiceException error = assertThrows(ServiceException.class, () -> EnumDictionaries.require(
                UserTokenType.class, 999, UserTokenType::getKey, UserTokenType::getLabel));
        assertEquals(500, error.getCode());
        assertEquals("传入的值(UserTokenType=999)不在字典可选范围内", error.getMessage());
        assertEquals(List.of(
                Map.of("key", 0, "label", "普通令牌"),
                Map.of("key", 1, "label", "私人令牌"),
                Map.of("key", 2, "label", "第三方令牌")), error.getData());
    }

    @Test
    void deletionGuardPreservesLegacyErrorAndNullableFinderContract() {
        assertDoesNotThrow(() -> TreeDeletionGuard.ensureNoChildrenBeforeDelete(1, ignored -> null));
        assertDoesNotThrow(() -> TreeDeletionGuard.ensureNoChildrenBeforeDelete(1, ignored -> List.of()));
        ServiceException error = assertThrows(ServiceException.class,
                () -> TreeDeletionGuard.ensureNoChildrenBeforeDelete(1, ignored -> List.of("child")));
        assertEquals(500, error.getCode());
        assertEquals("无法删除含有下级的数据，请先删除所有下级！", error.getMessage());
    }

    @Test
    void codeRuleFieldEndpointPreservesDefaultPrefix() {
        List<Map<String, Object>> expected = new ArrayList<>();
        for (CodeRuleField value : CodeRuleField.values()) {
            expected.add(Map.of("key", value.getKey(), "label", value.getLabel(), "defaultPrefix", value.getDefaultPrefix()));
        }
        assertEquals(expected, new CodeRuleController().getFieldList().getData());
    }

    @Test
    void codeRuleParamEndpointPreservesDescAndDemo() {
        List<Map<String, Object>> expected = new ArrayList<>();
        for (CodeRuleParam value : CodeRuleParam.values()) {
            expected.add(Map.of("key", value.getKey(), "label", value.getLabel(), "desc", value.getDesc(), "demo", value.getDemo()));
        }
        assertEquals(expected, new CodeRuleController().getParamList().getData());
    }

    @Test
    void codeRuleSerialNumberEndpointPreservesKeyAndLabel() {
        List<Map<String, Object>> expected = new ArrayList<>();
        for (SerialNumberUpdate value : SerialNumberUpdate.values()) {
            expected.add(Map.of("key", value.getKey(), "label", value.getLabel()));
        }
        assertEquals(expected, new CodeRuleController().getSerialNumberUpdate().getData());
    }

    @Test
    void cookieAndUploadDefaultsRemainUnchanged() {
        assertEquals(86400, new CookieConfig().getCookieMaxAge());
        AppConfig config = new AppConfig();
        assertEquals(10 * 1024 * 1024L, config.getUploadMaxSize());
        assertEquals("upload" + File.separator, config.getUploadDirectory());
    }

    @Test
    void legacyGetterSelectionAndFieldFormattingStaySeparate() throws Exception {
        List<CsvWriter.Column<MetadataRow>> columns = LegacyEntityMetadata.exportColumns(MetadataRow.class);
        assertEquals(List.of("Getter-only", "第一列", "第二列", "父列"), columns.stream().map(CsvWriter.Column::header).toList());
        try (var stream = EntityCsvExport.toCsvInputStream(List.of(new MetadataRow()), MetadataRow.class)) {
            assertEquals("Getter-only,第一列,第二列,父列\n0007,\tA,\tB,\tP",
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void legacyDescriptionsRetainInheritanceAndFallback() throws Exception {
        assertEquals("父类型", LegacyEntityMetadata.description(MetadataRow.class));
        assertEquals("String", LegacyEntityMetadata.description(String.class));
        assertEquals("第一列", LegacyEntityMetadata.description(MetadataRow.class.getDeclaredField("first")));
        assertEquals("noGetter", LegacyEntityMetadata.description(MetadataRow.class.getDeclaredField("noGetter")));
    }

    @Test
    void csvUsesMaskedCopyAndRetainsFormulaProtection() throws Exception {
        var previousMasking = DataPermissionRuntime.maskingService();
        var previousAudit = DataPermissionRuntime.audit();
        var previousProperties = DataPermissionRuntime.properties();
        ColumnMaskingService masking = mock(ColumnMaskingService.class);
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.SHADOW);
        new DataPermissionRuntime(masking, mock(DataPermissionAudit.class), properties);
        when(masking.maskFieldValue(eq("test.export"), eq("phone"), any(), any())).thenReturn("1******0");
        PermissionContext context = new PermissionContext(2L, 1L, Set.of(), Set.of(), Set.of(), Set.of("buyer"), false);
        ExportSensitiveRow row = new ExportSensitiveRow();
        row.setPhone("13800138000");
        row.setName("=cmd");
        try (var ignored = PermissionContextHolder.open(context)) {
            var masked = ExportColumnMasker.maskForExport(List.of(row));
            try (var stream = EntityCsvExport.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), ExportSensitiveRow.class)) {
                String csv = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals("电话,名称\n\t1******0,\t'=cmd", csv);
                assertFalse(csv.contains("13800138000"));
            }
            assertEquals("13800138000", row.getPhone());
            assertEquals("=cmd", row.getName());
        } finally {
            new DataPermissionRuntime(previousMasking, previousAudit, previousProperties);
        }
    }

    @Description("父类型")
    public static class MetadataParent {
        @Export
        @Description("父列")
        private String parent = "P";

        public String getParent() {
            throw new AssertionError("导出不应调用 getter");
        }
    }

    public static class MetadataRow extends MetadataParent {
        @Description("第一列")
        @Export(sort = 5)
        private String first = "A";
        @Description("第二列")
        @Export(sort = 5)
        private String second = "B";
        @Description("Getter-only")
        private String getterOnly = "0007";
        @Export
        private String getterRemoved = "SECRET";
        @Export(remove = true)
        private String fieldRemoved = "SECRET";
        @Export
        private String noGetter = "SECRET";
        @Export
        private transient String ignoredTransient = "SECRET";

        public String getFirst() { return first; }
        public String getSecond() { return second; }
        @Export(sort = 10)
        public String getGetterOnly() { return getterOnly; }
        @Export(remove = true)
        public String getGetterRemoved() { return getterRemoved; }
        public String getFieldRemoved() { return fieldRemoved; }
        public String getIgnoredTransient() { return ignoredTransient; }
    }

    public static class ExportSensitiveRow {
        @Export
        @Description("电话")
        @SensitiveField(resourceKey = "test.export")
        private String phone;
        @Export
        @Description("名称")
        private String name;

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Test
    void errorEnumsKeepTheirRuntimeContractWithoutDictionaryMarker() {
        for (Errors error : Errors.values()) {
            assertEquals(error.getCode(), error.getKey());
            assertEquals(error.getMessage(), error.getLabel());
        }
        for (CustomError error : CustomError.values()) {
            assertEquals(error.getCode(), error.getKey());
            assertEquals(error.getMessage(), error.getLabel());
            assertSame(error, error.get());
        }
        ServiceException forbidden = assertThrows(ServiceException.class,
                () -> Errors.FORBIDDEN.show("拒绝测试", Map.of("reason", "test")));
        assertEquals(403, forbidden.getCode());
        assertEquals("拒绝测试", forbidden.getMessage());
        assertEquals(Map.of("reason", "test"), forbidden.getData());
        ServiceException login = assertThrows(ServiceException.class,
                () -> CustomError.USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID.show());
        assertEquals(200001, login.getCode());
        assertEquals("用户账号或密码错误", login.getMessage());
    }

    @Test
    void configFlagKeysTypesAndDefaultsRemainUnchanged() {
        assertEquals(List.of(1, 2, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
                java.util.Arrays.stream(ConfigFlag.values()).map(ConfigFlag::getKey).toList());
        assertEquals(List.of("1", "1", "1", "1", "0", "0", "0", "0", "0", "1", "0", "0"),
                java.util.Arrays.stream(ConfigFlag.values()).map(ConfigFlag::getDefaultValue).toList());
        for (ConfigFlag flag : ConfigFlag.values()) {
            assertSame(ConfigType.BOOLEAN, flag.getType());
            assertSame(flag, ConfigFlag.valueOf(flag.name()));
        }
    }

    private static <E extends TreeNode<E>> void assertRoot(E root) {
        assertSame(root, Trees.buildTreeList(List.of(root)).get(0));
    }
}
