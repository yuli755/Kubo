package cn.hamm.spms.common.cookie;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * <h1>与真实 Spring/Servlet runner 共用的 Cookie 契约</h1>
 *
 * @author Hamm.cn
 */
class CookieContractTest {
    @TestFactory
    Stream<DynamicTest> contracts() {
        return CookieContracts.cases().stream().map(test -> DynamicTest.dynamicTest(test.name(), test.check()::run));
    }
}
