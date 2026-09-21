package cn.hamm.spms.framework.util;

import cn.hamm.spms.common.exception.CustomError;
import cn.hamm.spms.common.exception.Errors;
import cn.hamm.spms.framework.error.BusinessException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static cn.hamm.spms.framework.util.UtilityContracts.check;
import static cn.hamm.spms.framework.util.UtilityContracts.equal;
import static cn.hamm.spms.framework.util.UtilityContracts.fails;

/**
 * <h1>项目错误定义与抛错行为的 JDK-only 契约</h1>
 *
 * @author Hamm.cn
 */
final class ErrorContracts {
    private ErrorContracts() {
    }

    static List<UtilityContracts.Case> cases() {
        return List.of(
                new UtilityContracts.Case("errors/system-code-golden", () -> equal(List.of(
                        201, 301, 302, 4001, 4002, 4003, 4004, 4005, 4006, 4007, 4008, 401,
                        403, 4031, 4032, 4033, 4034, 4035, 4036, 4037, 4038, 4039, 404, 405, 415,
                        500, 5001, 5002, 5003, 501, 5011, 5021, 50211, 50212, 5022, 5023, 5024, 5025),
                        Arrays.stream(Errors.values()).map(Errors::getCode).toList())),
                new UtilityContracts.Case("errors/custom-code-offset", () -> equal(List.of(200001, 200002, 200101, 200102, 200103),
                        Arrays.stream(CustomError.values()).map(CustomError::getCode).toList())),
                new UtilityContracts.Case("errors/aliases-and-default-messages", () -> {
                    for (Errors error : Errors.values()) {
                        equal(error.getCode(), error.getKey());
                        equal(error.getMessage(), error.getLabel());
                        check(error.getMessage() != null && !error.getMessage().isBlank());
                    }
                    for (CustomError error : CustomError.values()) {
                        equal(error.getCode(), error.getKey());
                        equal(error.getMessage(), error.getLabel());
                    }
                    equal("用户账号或密码错误", CustomError.USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID.getMessage());
                }),
                new UtilityContracts.Case("errors/show-default-code-message-data", () -> {
                    BusinessException error = fails(BusinessException.class, Errors.FORBIDDEN::show);
                    equal(403, error.getCode());
                    equal("无权操作", error.getMessage());
                    equal(null, error.getData());
                }),
                new UtilityContracts.Case("errors/custom-message-does-not-mutate-enum", () -> {
                    String initial = Errors.FORBIDDEN.getMessage();
                    BusinessException error = fails(BusinessException.class, () -> Errors.FORBIDDEN.show("局部消息"));
                    equal("局部消息", error.getMessage());
                    equal(initial, Errors.FORBIDDEN.getMessage());
                    check(Arrays.stream(Errors.class.getMethods()).noneMatch(method -> method.getName().equals("setMessage")));
                }),
                new UtilityContracts.Case("errors/data-identity-kept", () -> {
                    Map<String, String> data = Map.of("key", "value");
                    BusinessException error = fails(BusinessException.class, () -> Errors.PARAM_INVALID.show("bad", data));
                    check(error.getData() == data);
                    equal(4002, error.getCode());
                }),
                new UtilityContracts.Case("errors/throwable-is-unchecked-and-owned", () -> {
                    equal(RuntimeException.class, BusinessException.class.getSuperclass());
                    equal(0, BusinessException.class.getInterfaces().length);
                    equal(500, new BusinessException("failure").getCode());
                }),
                new UtilityContracts.Case("errors/null-message-not-normalized", () -> {
                    BusinessException error = fails(BusinessException.class, () -> Errors.SERVICE_ERROR.show(null));
                    equal(null, error.getMessage());
                }),
                new UtilityContracts.Case("errors/suppliers-retain-original-roles", () -> {
                    check(CustomError.EMAIL_SEND_BUSY.get() == CustomError.EMAIL_SEND_BUSY);
                    check(Errors.FORBIDDEN.get() != Errors.FORBIDDEN.get());
                    equal(500, Errors.FORBIDDEN.get().getCode());
                    equal(null, Errors.FORBIDDEN.get().getMessage());
                }),
                new UtilityContracts.Case("errors/when-true-and-false", () -> {
                    Errors.FORBIDDEN.when(false, "ignored", "data");
                    BusinessException error = fails(BusinessException.class, () -> Errors.FORBIDDEN.when(true, "denied", "data"));
                    equal("denied", error.getMessage());
                    equal("data", error.getData());
                }),
                new UtilityContracts.Case("errors/null-presence-guards", () -> {
                    Errors.DATA_NOT_FOUND.whenNull("present");
                    Errors.FORBIDDEN_EXIST.whenNotNull(null);
                    fails(BusinessException.class, () -> Errors.DATA_NOT_FOUND.whenNull(null));
                    fails(BusinessException.class, () -> Errors.FORBIDDEN_EXIST.whenNotNull("present"));
                }),
                new UtilityContracts.Case("errors/empty-is-null-or-blank-stringification", () -> {
                    for (Object value : new Object[]{null, "", "  ", "\n\t", "\u3000", new StringBuilder(" ")}) {
                        fails(BusinessException.class, () -> Errors.PARAM_INVALID.whenEmpty(value));
                    }
                    Errors.PARAM_INVALID.whenEmpty(List.of());
                    Errors.PARAM_INVALID.whenEmpty(0);
                    Errors.PARAM_INVALID.whenEmpty(false);
                }),
                new UtilityContracts.Case("errors/null-toString-is-empty", () -> {
                    Object value = new Object() {
                        @Override
                        public String toString() { return null; }
                    };
                    fails(BusinessException.class, () -> Errors.PARAM_INVALID.whenEmpty(value));
                }),
                new UtilityContracts.Case("errors/objects-comparison-not-coercion", () -> {
                    Errors.FORBIDDEN.whenNotEquals(null, null);
                    Errors.FORBIDDEN.whenNotEquals(7L, 7L);
                    fails(BusinessException.class, () -> Errors.FORBIDDEN.whenNotEquals(7L, 7));
                    fails(BusinessException.class, () -> Errors.FORBIDDEN.whenNotEquals(null, "x"));
                }),
                new UtilityContracts.Case("errors/case-insensitive-null-is-rejected", () -> {
                    Errors.PARAM_INVALID.whenNotEqualsIgnoreCase("aBc", "AbC");
                    fails(BusinessException.class, () -> Errors.PARAM_INVALID.whenNotEqualsIgnoreCase(null, null));
                    fails(BusinessException.class, () -> Errors.PARAM_INVALID.whenNotEqualsIgnoreCase("x", null));
                    fails(BusinessException.class, () -> Errors.PARAM_INVALID.whenNotEqualsIgnoreCase("x", " x"));
                }),
                new UtilityContracts.Case("errors/locale-comparison-compatibility", () -> {
                    Locale before = Locale.getDefault();
                    try {
                        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                        fails(BusinessException.class, () -> Errors.PARAM_INVALID.whenNotEqualsIgnoreCase("I", "i"));
                    } finally {
                        Locale.setDefault(before);
                    }
                })
        );
    }
}
