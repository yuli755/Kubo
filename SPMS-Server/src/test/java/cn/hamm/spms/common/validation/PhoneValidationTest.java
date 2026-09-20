package cn.hamm.spms.common.validation;

import cn.hamm.spms.framework.util.PhoneRules;
import cn.hamm.spms.module.channel.customer.CustomerEntity;
import cn.hamm.spms.module.channel.supplier.SupplierEntity;
import cn.hamm.spms.module.personnel.user.IUserAction;
import cn.hamm.spms.module.personnel.user.UserEntity;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>真实 Jakarta Validation 提供者与三个实体电话字段的接线</h1>
 *
 * <p>这里验证 null、分组、文案和实际约束类型；独立正则契约不能代替本测试。
 * 不启动数据库，不构造假的约束/实体，完整 Maven 环境可直接运行。</p>
 *
 * @author Hamm.cn
 */
class PhoneValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (factory != null) {
            factory.close();
        }
    }

    @TestFactory
    Stream<DynamicTest> entityConstraintContracts() {
        return Stream.of(
                new Target(CustomerEntity.class, List.of(CustomerEntity.WhenAdd.class, CustomerEntity.WhenUpdate.class),
                        PhoneRules.INVALID_PHONE_MESSAGE),
                new Target(SupplierEntity.class, List.of(SupplierEntity.WhenAdd.class, SupplierEntity.WhenUpdate.class),
                        PhoneRules.INVALID_PHONE_MESSAGE),
                new Target(UserEntity.class, List.of(IUserAction.WhenResetMyPassword.class, IUserAction.WhenSendSms.class),
                        "手机格式不正确")
        ).flatMap(target -> Stream.of(
                DynamicTest.dynamicTest(target.type().getSimpleName() + "/standard-pattern", () -> {
                    Pattern annotation = target.type().getDeclaredField("phone").getAnnotation(Pattern.class);
                    assertEquals(PhoneRules.OPTIONAL_PHONE, annotation.regexp());
                    assertEquals(target.message(), annotation.message());
                    assertEquals(Set.copyOf(target.groups()), Set.of(annotation.groups()));
                    assertEquals(0, annotation.flags().length);
                }),
                DynamicTest.dynamicTest(target.type().getSimpleName() + "/null-and-empty", () -> {
                    for (Class<?> group : target.groups()) {
                        assertTrue(validator.validateValue(target.type(), "phone", null, group).isEmpty());
                        assertTrue(validator.validateValue(target.type(), "phone", "", group).isEmpty());
                    }
                }),
                DynamicTest.dynamicTest(target.type().getSimpleName() + "/mobile-landline-extension", () -> {
                    for (String phone : List.of("13800138000", "+8613800138000", "010-12345678", "1234567-1234", "4001234567")) {
                        for (Class<?> group : target.groups()) {
                            assertTrue(validator.validateValue(target.type(), "phone", phone, group).isEmpty(), phone);
                        }
                    }
                }),
                DynamicTest.dynamicTest(target.type().getSimpleName() + "/invalid-message-and-group", () -> {
                    for (String phone : List.of(" ", "abc", "12800138000", "13800138000\n", "１３８００１３８０００")) {
                        for (Class<?> group : target.groups()) {
                            var violations = validator.validateValue(target.type(), "phone", phone, group);
                            assertEquals(1, violations.size(), phone);
                            var violation = violations.iterator().next();
                            assertEquals(target.message(), violation.getMessage());
                            assertInstanceOf(Pattern.class, violation.getConstraintDescriptor().getAnnotation());
                        }
                    }
                }),
                DynamicTest.dynamicTest(target.type().getSimpleName() + "/no-new-default-group-constraint", () -> {
                    assertTrue(validator.validateValue(target.type(), "phone", "invalid").isEmpty());
                    if (target.type() == UserEntity.class) {
                        assertTrue(validator.validateValue(UserEntity.class, "phone", "invalid", IUserAction.WhenLoginViaPhone.class).isEmpty());
                    }
                })
        ));
    }

    private record Target(Class<?> type, List<Class<?>> groups, String message) {
    }
}
