package cn.hamm.spms.framework.http;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * <h1>与隔离 HTTP runner 共用的 JUnit 契约</h1>
 *
 * @author Hamm.cn
 */
class HttpErrorContractTest {
    @TestFactory
    Stream<DynamicTest> contracts() {
        return HttpErrorContracts.cases().stream().map(test -> DynamicTest.dynamicTest(test.name(), test.check()::run));
    }
}
