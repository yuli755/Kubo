package cn.hamm.spms.framework.util;

import cn.hamm.spms.common.AppConstant;
import cn.hamm.spms.module.personnel.user.enums.UserTokenType;
import cn.hamm.spms.module.system.coderule.enums.CodeRuleParam;
import cn.hamm.spms.module.system.file.enums.FileCategory;
import cn.hamm.spms.module.system.file.enums.FileUploadPlatform;
import cn.hamm.spms.module.system.permission.enums.PermissionType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static cn.hamm.spms.framework.util.UtilityContracts.check;
import static cn.hamm.spms.framework.util.UtilityContracts.equal;

/**
 * <h1>脱离字典接口的真实业务枚举契约</h1>
 *
 * <p>测试编译的是生产源码，不为旧接口、Lombok 或 Spring 生成占位类。</p>
 *
 * @author Hamm.cn
 */
final class DomainEnumContracts {
    private static final List<Class<?>> TYPES = List.of(UserTokenType.class, CodeRuleParam.class,
            FileCategory.class, FileUploadPlatform.class, PermissionType.class);

    private DomainEnumContracts() {
    }

    static List<UtilityContracts.Case> cases() {
        return List.of(
                new UtilityContracts.Case("domain-enum/token-wire-values", () -> {
                    equal(List.of("NORMAL:0:普通令牌", "PERSONAL:1:私人令牌", "OAUTH2:2:第三方令牌"),
                            Arrays.stream(UserTokenType.values()).map(value -> value.name() + ':' + value.getKey() + ':' + value.getLabel()).toList());
                    equal("type", UserTokenType.TYPE);
                }),
                new UtilityContracts.Case("domain-enum/token-name-binding", () -> {
                    for (UserTokenType value : UserTokenType.values()) {
                        check(UserTokenType.valueOf(value.name()) == value);
                    }
                }),
                new UtilityContracts.Case("domain-enum/code-param-golden", () -> equal(List.of(
                        "FULL_YEAR:1:yyyy:完整年份:2023", "YEAR:2:yy:年份:23", "MONTH:3:mm:月份:12",
                        "DATE:4:dd:日期:31", "HOUR:5:hh:小时:20"), Arrays.stream(CodeRuleParam.values())
                        .map(value -> value.name() + ':' + value.getKey() + ':' + value.getLabel() + ':' + value.getDesc() + ':' + value.getDemo()).toList())),
                new UtilityContracts.Case("domain-enum/code-param-wire-options", () -> {
                    var options = codeParamOptions();
                    equal(5, options.size());
                    equal(List.of("key", "label", "desc", "demo"), List.copyOf(options.get(0).keySet()));
                    equal(Map.of("key", 1, "label", "yyyy", "desc", "完整年份", "demo", "2023"), options.get(0));
                    equal(Map.of("key", 5, "label", "hh", "desc", "小时", "demo", "20"), options.get(4));
                }),
                new UtilityContracts.Case("domain-enum/file-categories-golden", () -> equal(List.of(
                        "TEMP:0:临时文件:false", "NORMAL:1:普通文件:false", "AVATAR:1001:头像:false", "CONTRACT_ATTACHMENT:2001:合同附件:true"),
                        Arrays.stream(FileCategory.values()).map(value -> value.name() + ':' + value.getKey() + ':' + value.getLabel() + ':' + value.getIsProtected()).toList())),
                new UtilityContracts.Case("domain-enum/default-upload-whitelist", () -> {
                    List<String> expected = List.of("jpg", "jpeg", "png", "gif", "bmp", "mp4", "mp3", "wav", "wma",
                            "zip", "rar", "7z", "tar", "gz", "pdf", "doc", "docx", "xls", "xlsx", "markdown");
                    equal(expected, Arrays.asList(FileCategory.NORMAL.getExtensions()));
                    equal(expected, Arrays.asList(FileCategory.TEMP.getExtensions()));
                    check(AppConstant.DEFAULT_EXTENSIONS == FileCategory.NORMAL.getExtensions());
                    check(AppConstant.DEFAULT_EXTENSIONS == FileCategory.TEMP.getExtensions());
                }),
                new UtilityContracts.Case("domain-enum/category-specific-whitelists", () -> {
                    equal(List.of("jpg", "jpeg", "png"), Arrays.asList(FileCategory.AVATAR.getExtensions()));
                    equal(List.of("jpg", "jpeg", "png", "bmp", "pdf", "zip"), Arrays.asList(FileCategory.CONTRACT_ATTACHMENT.getExtensions()));
                }),
                new UtilityContracts.Case("domain-enum/category-extension-matching", () -> {
                    var avatar = Arrays.asList(FileCategory.AVATAR.getExtensions());
                    var attachment = Arrays.asList(FileCategory.CONTRACT_ATTACHMENT.getExtensions());
                    check(avatar.contains(FilePaths.getExtension("avatar.PNG")));
                    check(!avatar.contains(FilePaths.getExtension("avatar.svg")));
                    check(!avatar.contains(FilePaths.getExtension("avatar.pdf")));
                    check(attachment.contains(FilePaths.getExtension("contract.PDF")));
                    check(!attachment.contains(FilePaths.getExtension("contract.exe")));
                }),
                new UtilityContracts.Case("domain-enum/local-platform-binding", () -> {
                    equal(1, FileUploadPlatform.values().length);
                    equal(0, FileUploadPlatform.LOCAL.getKey());
                    equal("本地上传", FileUploadPlatform.LOCAL.getLabel());
                    check(FileUploadPlatform.valueOf("LOCAL") == FileUploadPlatform.LOCAL);
                }),
                new UtilityContracts.Case("domain-enum/api-permission-key-stays-zero", () -> {
                    equal(1, PermissionType.values().length);
                    equal(0, PermissionType.API.getKey());
                    equal("API 权限", PermissionType.API.getLabel());
                }),
                new UtilityContracts.Case("domain-enum/lookup-roundtrip", () -> {
                    roundtrip(UserTokenType.class, UserTokenType::getKey);
                    roundtrip(CodeRuleParam.class, CodeRuleParam::getKey);
                    roundtrip(FileCategory.class, FileCategory::getKey);
                    roundtrip(FileUploadPlatform.class, FileUploadPlatform::getKey);
                    roundtrip(PermissionType.class, PermissionType::getKey);
                }),
                new UtilityContracts.Case("domain-enum/unknown-key-does-not-coerce-to-ordinal", () -> {
                    check(EnumValues.find(FileCategory.class, 2, FileCategory::getKey).isEmpty());
                    check(EnumValues.find(UserTokenType.class, 999, UserTokenType::getKey).isEmpty());
                    check(EnumValues.find(CodeRuleParam.class, 0, CodeRuleParam::getKey).isEmpty());
                    check(EnumValues.find(FileUploadPlatform.class, 1, FileUploadPlatform::getKey).isEmpty());
                    check(EnumValues.find(PermissionType.class, 1, PermissionType::getKey).isEmpty());
                }),
                new UtilityContracts.Case("domain-enum/public-getter-signatures", () -> {
                    for (Class<?> type : TYPES) {
                        equal(int.class, type.getMethod("getKey").getReturnType());
                        equal(String.class, type.getMethod("getLabel").getReturnType());
                    }
                    equal(Boolean.class, FileCategory.class.getMethod("getIsProtected").getReturnType());
                    equal(String[].class, FileCategory.class.getMethod("getExtensions").getReturnType());
                    equal(String.class, CodeRuleParam.class.getMethod("getDesc").getReturnType());
                    equal(String.class, CodeRuleParam.class.getMethod("getDemo").getReturnType());
                }),
                new UtilityContracts.Case("domain-enum/ordinary-enums-not-replacement-interface-shells", () -> {
                    for (Class<?> type : TYPES) {
                        check(type.isEnum());
                        equal(0, type.getInterfaces().length);
                    }
                }),
                new UtilityContracts.Case("domain-enum/options-do-not-mutate-constants", () -> {
                    var first = codeParamOptions();
                    first.get(0).put("label", "changed");
                    equal("yyyy", CodeRuleParam.FULL_YEAR.getLabel());
                    equal("yyyy", codeParamOptions().get(0).get("label"));
                })
        );
    }

    private static <E extends Enum<E>> void roundtrip(Class<E> type, ToIntFunction<E> key) {
        for (E value : type.getEnumConstants()) {
            check(EnumValues.find(type, key.applyAsInt(value), key).orElseThrow() == value);
        }
    }

    private static List<Map<String, Object>> codeParamOptions() {
        return EnumValues.options(CodeRuleParam.class,
                EnumValues.column("key", CodeRuleParam::getKey),
                EnumValues.column("label", CodeRuleParam::getLabel),
                EnumValues.column("desc", CodeRuleParam::getDesc),
                EnumValues.column("demo", CodeRuleParam::getDemo));
    }
}
