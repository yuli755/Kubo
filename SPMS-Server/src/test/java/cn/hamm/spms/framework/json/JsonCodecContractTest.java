package cn.hamm.spms.framework.json;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * <h1>与独立 Jackson runner 共用的 JUnit 契约入口</h1>
 *
 * @author Hamm.cn
 */
class JsonCodecContractTest {
    @TestFactory
    Stream<DynamicTest> contracts() {
        return JsonContracts.cases().stream().map(test -> DynamicTest.dynamicTest(test.name(), test.check()::run));
    }
}
