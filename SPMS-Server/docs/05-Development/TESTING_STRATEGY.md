# Kubo Platform Testing Strategy

> **Version**: 1.0  
> **Status**: 🟢 Active  
> **Last Updated**: 2026-08-02  
> **Scope**: Backend + Frontend + Integration + E2E + Performance + Contract

---

## 🎯 Testing Principles

| Principle | Description |
|-----------|-------------|
| **Testing Pyramid** | 单测 70% + 集成 20% + E2E 10% |
| **Shift Left** | 越早发现 Bug 成本越低 |
| **Automation First** | 所有测试自动化、CI 强制执行 |
| **Deterministic** | 测试可重复、无外部依赖、无随机失败 |
| **Fast Feedback** | 单测 < 1s、集成 < 30s、E2E < 5min |

---

## 📊 Test Layer Distribution

```
                    ┌─────────────┐
                    │   E2E (10%) │  ← 关键业务流程、跨服务
                    │  Playwright │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │   Integration (20%)     │  ← 服务间交互、数据库、MQ
              │  Testcontainers / Pact  │
              └───────────┬─────────────┘
                          │
              ┌───────────┴───────────┐
              │   Unit Tests (70%)    │  ← 业务逻辑、工具函数、组件
              │  JUnit / Vitest       │
              └───────────────────────┘
```

---

## 🧪 Backend Testing (Java)

### 1. Unit Tests (JUnit 5 + Mockito + AssertJ)

```java
// 目标: 单个类/方法、纯逻辑、无外部依赖
// 覆盖率要求: > 80% (核心域 > 95%)

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository repository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private InventoryService inventoryService;

    @Test
    @DisplayName("库存扣减 - 充足库存时成功")
    void deduct_whenSufficientStock_shouldSucceed() {
        // Given
        var cmd = new InventoryDeductCmd(1L, 2L, new BigDecimal("10"));
        given(repository.deductWithVersion(1L, 2L, new BigDecimal("10"), 0))
            .willReturn(1);
        given(redisTemplate.execute(any(), any(), any(), any()))
            .willReturn(true);

        // When
        inventoryService.deduct(cmd, "idem-key-123");

        // Then
        verify(repository).deductWithVersion(1L, 2L, new BigDecimal("10"), 0);
        verify(eventPublisher).publishEvent(any(InventoryDeductedEvent.class));
    }

    @Test
    @DisplayName("库存扣减 - 库存不足抛出异常")
    void deduct_whenInsufficientStock_shouldThrow() {
        // Given
        var cmd = new InventoryDeductCmd(1L, 2L, new BigDecimal("100"));
        given(redisTemplate.execute(any(), any(), any(), any()))
            .willReturn(false);

        // When & Then
        assertThatThrownBy(() -> inventoryService.deduct(cmd, "idem-key"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);
    }

    @ParameterizedTest
    @CsvSource({
        "10, 5, true",
        "5, 10, false",
        "0, 0, false"
    })
    @DisplayName("库存可用性判断")
    void canDeduct_variousQuantities(Long available, Long request, boolean expected) {
        var entity = InventoryEntity.builder()
            .availableQty(BigDecimal.valueOf(available))
            .frozen(false)
            .build();

        assertThat(entity.canDeduct(BigDecimal.valueOf(request))).isEqualTo(expected);
    }
}
```

### 2. Integration Tests (Testcontainers + Spring Boot Test)

```java
// 目标: 真实数据库、Redis、MQ 交互
// 覆盖率要求: 核心链路 100%

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@Transactional
class InventoryControllerIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("kubo_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Container
    static GenericContainer<?> rabbitmq = new GenericContainer<>("rabbitmq:3.12-management-alpine")
        .withExposedPorts(5672, 15672);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getMappedPort(5672));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired InventoryRepository repository;

    @Test
    @DisplayName("完整流程: 创建库存 -> 扣减 -> 查询")
    void fullFlow_createDeductQuery() throws Exception {
        // 1. 创建库存
        var createCmd = new InventoryCreateCmd(1L, 2L, new BigDecimal("100"), new BigDecimal("10"));
        var createResult = mockMvc.perform(post("/api/v1/inventory")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createResult))
            .header("Idempotency-Key", "test-key-1"))
            .andExpect(status().isCreated())
            .andReturn();

        // 2. 扣减库存
        var deductCmd = new InventoryDeductCmd(1L, 2L, new BigDecimal("30"));
        mockMvc.perform(post("/api/v1/inventory/deduct")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(deductCmd))
            .header("Idempotency-Key", "test-key-2"))
            .andExpect(status().isOk());

        // 3. 查询验证
        mockMvc.perform(get("/api/v1/inventory/page")
            .param("warehouseId", "1")
            .param("materialId", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].availableQty").value(70));
    }
}
```

### 3. Contract Tests (Spring Cloud Contract / Pact)

```yaml
# src/test/resources/contracts/inventory/deduct.groovy
org.springframework.cloud.contract.spec.Contract.make {
    request {
        method 'POST'
        url '/api/v1/inventory/deduct'
        headers {
            contentType(applicationJson())
            header('Idempotency-Key', 'test-idempotency-key')
        }
        body([
            warehouseId: 1,
            materialId: 2,
            quantity: 10
        ])
    }
    response {
        status 200
        body([
            code: 200,
            message: 'success',
            data: null,
            timestamp: regex('[0-9]{4}-[0-9]{2}-[0-9]{2}T.*'),
            traceId: regex('[a-f0-9]{12}')
        ])
    }
}
```

### 4. ArchUnit Architecture Tests

```java
@AnalyzeClasses(packages = "cn.hamm.spms")
class ArchitectureTest {

    @ArchTest
    static final ArchRule controller_only_in_controller_package =
        classes().that().resideInAPackage("..controller..")
            .should().onlyBeAccessed().byClassesThat()
            .resideInAnyPackage("..controller..", "..config..", "..aspect..");

    @ArchTest
    static final ArchRule service_not_access_repository_directly_from_controller =
        classes().that().resideInAPackage("..controller..")
            .shouldNot().accessClassesThat()
            .resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule entity_immutable =
        classes().that().areAnnotatedWith(Entity.class)
            .should().haveOnlyFinalFields();

    @ArchTest
    static final ArchRule no_field_injection =
        classes().that().areAnnotatedWith(Component.class)
            .or().areAnnotatedWith(Service.class)
            .or().areAnnotatedWith(Controller.class)
            .should().notHaveFieldsAnnotatedWith(Autowired.class);
}
```

---

## 🌐 Frontend Testing (TypeScript/Vue)

### 1. Unit Tests (Vitest + Vue Test Utils)

```typescript
// src/components/VirtualTable/VirtualTable.test.ts
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import VirtualTable from './VirtualTable.vue'

describe('VirtualTable', () => {
    const columns = [
        { prop: 'id', label: 'ID' },
        { prop: 'name', label: 'Name' }
    ]
    const data = Array.from({ length: 100 }, (_, i) => ({ id: i, name: `Item ${i}` }))

    it('renders visible rows only', () => {
        const wrapper = mount(VirtualTable, {
            props: { data, columns, itemHeight: 40, visibleCount: 10 }
        })

        // 只渲染可见行 + buffer
        expect(wrapper.findAll('.virtual-row')).toHaveLength(12) // 10 + 2 buffer
    })

    it('scrolls to correct position', async () => {
        const wrapper = mount(VirtualTable, {
            props: { data, columns, itemHeight: 40, visibleCount: 10 }
        })

        await wrapper.vm.scrollToIndex(50)
        expect(wrapper.vm.scrollTop).toBe(50 * 40)
    })

    it('handles empty data', () => {
        const wrapper = mount(VirtualTable, {
            props: { data: [], columns, itemHeight: 40, visibleCount: 10 }
        })
        expect(wrapper.text()).toContain('暂无数据')
    })
})
```

### 2. Component Tests (Vue Test Utils + Happy DOM)

```typescript
// src/views/console/wms/inventory/list.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import InventoryList from './list.vue'
import { inventoryApi } from '@/api/generated'

vi.mock('@/api/generated', () => ({
    inventoryApi: {
        getInventoryList: vi.fn()
    }
}))

describe('InventoryList', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
    })

    it('loads data on mount', async () => {
        inventoryApi.getInventoryList.mockResolvedValue({
            content: [{ id: 1, name: 'Test' }],
            totalElements: 1
        })

        const wrapper = mount(InventoryList, {
            global: { plugins: [createPinia()] }
        })

        await flushPromises()
        expect(wrapper.text()).toContain('Test')
    })

    it('handles search', async () => {
        inventoryApi.getInventoryList.mockResolvedValue({ content: [], totalElements: 0 })

        const wrapper = mount(InventoryList, { global: { plugins: [createPinia()] } })
        await flushPromises()

        await wrapper.findComponent(ProSearch).vm.$emit('search', { keyword: 'test' })
        await flushPromises()

        expect(inventoryApi.getInventoryList).toHaveBeenCalledWith(
            expect.objectContaining({ filter: expect.objectContaining({ keyword: 'test' }) })
        )
    })
})
```

### 3. Composable Tests

```typescript
// src/composables/useRequest.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useRequest } from '@/composables/useRequest'

describe('useRequest', () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('caches response for staleTime', async () => {
        const mockFn = vi.fn().mockResolvedValue({ data: 'test' })
        const { data, refresh } = useRequest(mockFn, { cache: 'memory', staleTime: 5000 })

        await data.value // 首次请求
        expect(mockFn).toHaveBeenCalledTimes(1)

        await data.value // 缓存命中
        expect(mockFn).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(6000) // 超过 staleTime
        await data.value // 重新请求
        expect(mockFn).toHaveBeenCalledTimes(2)
    })

    it('deduplicates concurrent requests', async () => {
        const mockFn = vi.fn().mockImplementation(() => 
            new Promise(r => setTimeout(() => r({ data: 'ok' }), 100))
        )
        const { execute } = useRequest(mockFn, { deduplicate: true })

        const [p1, p2, p3] = [execute(), execute(), execute()]
        await Promise.all([p1, p2, p3])

        expect(mockFn).toHaveBeenCalledTimes(1)
    })
})
```

### 4. E2E Tests (Playwright)

```typescript
// tests/e2e/inventory.spec.ts
import { test, expect } from '@playwright/test'

test.describe('Inventory Management', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/login')
        await page.fill('[name=username]', 'admin')
        await page.fill('[name=password]', 'Aa123456')
        await page.click('button[type=submit]')
        await page.waitForURL('/home')
    })

    test('create inventory -> deduct -> verify', async ({ page }) => {
        // 导航到库存页面
        await page.goto('/console/wms/inventory')
        await page.waitForSelector('.inventory-page')

        // 点击新建
        await page.click('button:has-text("新建")')
        await page.waitForSelector('.el-dialog')

        // 填写表单
        await page.selectOption('select[name=warehouseId]', '1')
        await page.selectOption('select[name=materialId]', '2')
        await page.fill('input[name=availableQty]', '100')
        await page.fill('input[name=warningThreshold]', '10')
        await page.click('button:has-text("确定")')

        // 验证列表出现
        await expect(page.locator('.inventory-page .el-table')).toContainText('100')

        // 扣减库存
        await page.click('button:has-text("扣减"):first-child')
        await page.waitForSelector('.el-dialog')
        await page.fill('input[name=quantity]', '30')
        await page.click('button:has-text("确定")')

        // 验证扣减结果
        await expect(page.locator('.inventory-page .el-table')).toContainText('70')
    })

    test('handles concurrent deduct correctly', async ({ page, context }) => {
        // 打开两个标签页并发扣减
        const page2 = await context.newPage()
        await page2.goto('/console/wms/inventory')
        
        // 同时点击扣减...
        // 验证最终结果正确
    })
})
```

### 5. Visual Regression Tests

```typescript
// tests/visual/inventory.spec.ts
import { test, expect } from '@playwright/test'

test.describe('Visual Regression - Inventory', () => {
    test('inventory list page', async ({ page }) => {
        await page.goto('/console/wms/inventory')
        await page.waitForLoadState('networkidle')
        
        // 截图对比
        await expect(page).toHaveScreenshot('inventory-list.png', {
            fullPage: true,
            animations: 'disabled',
            threshold: 0.2
        })
    })

    test('inventory editor dialog', async ({ page }) => {
        await page.goto('/console/wms/inventory')
        await page.click('button:has-text("新建")')
        await page.waitForSelector('.el-dialog')
        
        await expect(page.locator('.el-dialog')).toHaveScreenshot('inventory-editor.png', {
            threshold: 0.1
        })
    })
})
```

---

## 🔗 Integration & Contract Testing

### Consumer-Driven Contract (Pact)

```typescript
// tests/contract/inventory-consumer.test.ts
import { Pact } from '@pact-foundation/pact'
import { inventoryApi } from '@/api/generated'

const provider = new Pact({
    consumer: 'kubo-web',
    provider: 'kubo-wms',
    port: 1234,
    logLevel: 'WARN'
})

describe('Inventory API Contract', () => {
    beforeAll(() => provider.setup())
    afterAll(() => provider.finalize())

    test('GET /api/v1/inventory/page returns paginated inventory', async () => {
        await provider.addInteraction({
            state: 'inventory exists',
            uponReceiving: 'a request for inventory list',
            withRequest: {
                method: 'GET',
                path: '/api/v1/inventory/page',
                query: { page: '1', pageSize: '20', warehouseId: '1' },
                headers: { 'Accept': 'application/json' }
            },
            willRespondWith: {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
                body: like({
                    code: 200,
                    message: 'success',
                    data: eachLike({
                        id: 1,
                        warehouseId: 1,
                        materialId: 2,
                        availableQty: 100
                    })
                })
            }
        })

        const result = await inventoryApi.getInventoryList({ page: 1, pageSize: 20, warehouseId: 1 })
        expect(result.content).toHaveLength(1)
    })
})
```

---

## 🚀 Performance Testing (Gatling)

```scala
// gatling/simulations/InventoryDeductSimulation.scala
class InventoryDeductSimulation extends Simulation {

    val httpProtocol = http
        .baseUrl("https://api.kubo.example.com")
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer ${accessToken}")

    val scn = scenario("Inventory Deduct Under Load")
        .exec(
            http("Login")
                .post("/api/v1/auth/login")
                .body(StringBody("""{"username":"perf","password":"perf123"}"""))
                .check(jsonPath("$.data.token").saveAs("accessToken"))
        )
        .repeat(100) {
            exec(
                http("Deduct Inventory")
                    .post("/api/v1/inventory/deduct")
                    .header("Idempotency-Key", "${uuid()}")
                    .body(StringBody("""{"warehouseId":1,"materialId":2,"quantity":1}"""))
                    .check(status.is(200))
            )
            .pause(100.milliseconds)
        }

    setUp(
        scn.inject(
            rampUsers(100).during(30.seconds),  // 预热
            constantUsersPerSec(50).during(5.minutes),  // 稳态压测
            rampUsers(200).during(1.minute)   // 压力测试
        )
    ).protocols(httpProtocol)
    .assertions(
        global.responseTime.percentile3.lt(200),
        global.responseTime.percentile4.lt(500),
        global.successfulRequests.percent.gt(99.9),
        forAll.failedRequests.count.is(0)
    )
}
```

---

## 📋 Test Coverage Requirements

| Layer | Minimum | Core Domain | Enforcement |
|-------|---------|-------------|-------------|
| **Backend Unit** | 80% | 95% | JaCoCo + CI Gate |
| **Backend Integration** | 核心链路 100% | 100% | 手动 Review |
| **Frontend Unit** | 80% | 90% | Vitest + CI Gate |
| **Frontend Component** | 关键组件 100% | 100% | 手动 Review |
| **E2E** | 核心流程 100% | 100% | Playwright + CI |
| **Contract** | 所有 Provider | 100% | Pact Broker + CI |
| **Architecture** | 所有规则 | 100% | ArchUnit + CI |

---

## 🔄 CI/CD Integration

```yaml
# .github/workflows/test.yml
jobs:
  backend-unit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17' }
      - run: ./mvnw test jacoco:report
      - uses: codecov/codecov-action@v3
        with: { flags: backend-unit, fail_ci_if_error: true }

  backend-integration:
    runs-on: ubuntu-latest
    services:
      mysql: { image: mysql:8.0, ports: [3306], env: { MYSQL_ROOT_PASSWORD: test } }
      redis: { image: redis:7-alpine, ports: [6379] }
      rabbitmq: { image: rabbitmq:3.12-alpine, ports: [5672] }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
      - run: ./mvnw verify -Pintegration-test

  frontend-unit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v4
        with: { node-version: 20, cache: 'pnpm' }
      - run: pnpm install --frozen-lockfile
      - run: pnpm run test:unit --coverage
      - uses: codecov/codecov-action@v3
        with: { flags: frontend-unit }

  frontend-e2e:
    runs-on: ubuntu-latest
    services:
      backend: { image: kubo/backend:dev, ports: [8080] }
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v4
      - run: pnpm install --frozen-lockfile
      - run: pnpm run build
      - run: pnpm run test:e2e
    # 需要前端构建产物 + 后端服务

  contract-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./mvnw test -Pcontract-test  # Provider 端
    # Consumer 端在前端 CI 中运行

  performance:
    if: github.event_name == 'release'  # 仅 Release 触发
    runs-on: [self-hosted, gatling]
    steps:
      - uses: actions/checkout@v4
      - run: gatling.sh -s InventoryDeductSimulation
    # 需要专用压测环境
```

---

## 📊 Test Reporting & Metrics

| Metric | Target | Tool | Dashboard |
|--------|--------|------|-----------|
| **Code Coverage** | > 80% | JaCoCo / Vitest Coverage | Codecov / SonarQube |
| **Mutation Score** | > 60% | Pitest / Stryker | SonarQube |
| **Test Execution Time** | < 10min (全量) | CI Pipeline | GitHub Actions |
| **Flaky Rate** | < 1% | 自动重跑 + 标记 | Allure Report |
| **Contract Compatibility** | 100% | Pact Broker | Pact Broker UI |
| **Architecture Violations** | 0 | ArchUnit | SonarQube |

---

## 📋 Test Data Management

| Strategy | Use Case |
|----------|----------|
| **Test Fixtures** | 单测、组件测试 - 固定数据、可版本控制 |
| **Testcontainers** | 集成测试 - 真实 DB/Redis/MQ、隔离 |
| **Data Builders** | 复杂对象构建 - 流式 API、可读性 |
| **Anonymous Data** | E2E/性能测试 - 生成器模式、无敏感数据 |
| **Production-like** | Staging/预发布 - 脱敏生产数据、定期刷新 |

```java
// Data Builder Pattern 示例
public class InventoryEntityBuilder {
    private Long id = 1L;
    private Long warehouseId = 1L;
    private Long materialId = 1L;
    private BigDecimal availableQty = BigDecimal.valueOf(100);
    private Boolean frozen = false;

    public static InventoryEntityBuilder anInventory() { return new InventoryEntityBuilder(); }
    public InventoryEntityBuilder withId(Long id) { this.id = id; return this; }
    public InventoryEntityBuilder withWarehouse(Long id) { this.warehouseId = id; return this; }
    public InventoryEntityBuilder withQuantity(BigDecimal qty) { this.availableQty = qty; return this; }
    public InventoryEntity build() {
        return InventoryEntity.builder()
            .id(id).warehouseId(warehouseId).materialId(materialId)
            .availableQty(availableQty).frozen(frozen)
            .build();
    }
}

// 使用
var inventory = InventoryEntityBuilder.anInventory()
    .withId(1L)
    .withQuantity(BigDecimal.valueOf(50))
    .build();
```

---

## 🚫 Anti-Patterns to Avoid

| ❌ 反模式 | ✅ 正确做法 |
|----------|-------------|
| 测试实现细节而非行为 | 测试公共 API、输入输出 |
| 过度 Mock 导致测试脆弱 | 集成测试用真实依赖、单测 Mock 边界 |
| 测试相互依赖、共享状态 | 每个测试独立、可并行运行 |
| 硬编码等待时间 | 显式等待条件 |
| 忽略边界值、异常路径 | 参数化测试覆盖边界、异常 |
| 测试数据污染生产/共享环境 | 隔离环境、Testcontainers、事务回滚 |
| 忽略性能/并发测试 | 压力测试、并发测试纳入 CI |

---

## 📚 Tools & Versions

| Category | Tool | Version |
|----------|------|---------|
| **Backend Unit** | JUnit Jupiter | 5.10+ |
| **Mocking** | Mockito | 5.11+ |
| **Assertions** | AssertJ | 3.24+ |
| **Containers** | Testcontainers | 1.19+ |
| **Contract** | Spring Cloud Contract / Pact | 4.1+ / 4.5+ |
| **Architecture** | ArchUnit | 1.2+ |
| **Frontend Unit** | Vitest | 2.0+ |
| **Vue Testing** | Vue Test Utils | 2.4+ |
| **E2E** | Playwright | 1.45+ |
| **Visual** | Playwright + pixelmatch | 1.45+ / 5.3+ |
| **Performance** | Gatling | 3.10+ |
| **Mutation** | Pitest / Stryker | 1.15+ / 1.7+ |
| **Coverage** | JaCoCo / Vitest Coverage | 0.8+ / 2.0+ |

---

## 📋 Checklist for New Tests

```markdown
# 新增测试 Checklist

## 通用
- [ ] 测试命名清晰：`should_<expected>_when_<condition>`
- [ ] 单一职责：每个测试只验证一个行为
- [ ] 无硬编码等待：使用显式等待/条件
- [ ] 独立可并行：无共享状态、无外部依赖
- [ ] 覆盖边界值：空、null、0、最大值、异常输入

## 单元测试
- [ ] 纯逻辑、无外部依赖
- [ ] Mock 仅用于边界依赖
- [ ] 执行时间 < 100ms

## 集成测试
- [ ] 使用 Testcontainers 真实依赖
- [ ] @Transactional 回滚数据
- [ ] 测试真实 SQL/MQ 交互

## E2E
- [ ] 覆盖核心用户旅程
- [ ] 数据隔离：专用测试租户/数据
- [ ] 截图/视频记录失败

## Contract
- [ ] Consumer 定义契约
- [ ] Provider 验证契约
- [ ] Pact Broker 发布/拉取

## 性能
- [ ] 有基线对比
- [ ] 明确 SLA 指标
- [ ] 结果可视化、可追踪
```

---

*Last Updated: 2026-08-02*  
*Enforcement: CI Gate Required*