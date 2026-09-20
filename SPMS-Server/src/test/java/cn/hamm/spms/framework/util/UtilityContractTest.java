package cn.hamm.spms.framework.util;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * <h1>将独立契约纳入 Maven/Surefire，避免两套断言漂移</h1>
 *
 * @author Hamm.cn
 */
class UtilityContractTest {
    @TestFactory
    Stream<DynamicTest> utilityContracts() {
        return UtilityContracts.cases().stream()
                .map(test -> DynamicTest.dynamicTest(test.name(), () -> test.check().run()));
    }
}
