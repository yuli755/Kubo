package cn.hamm.spms.framework.json;

import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionChangedEvent;
import cn.hamm.spms.module.system.datapermission.service.OperationLogSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * <h1>内部 JSON 协议的共享可执行契约</h1>
 *
 * <p>使用正式 Jackson 和真实生产事件 DTO（由正式 Lombok 编译），不是框架 stub。
 * 参考 mapper 只冻结原有三项配置，不声称执行了旧 Json 类或完整 Spring/Redis 链路。</p>
 *
 * @author Hamm.cn
 */
public final class JsonContracts {
    private static final ObjectMapper REFERENCE = referenceMapper();

    private JsonContracts() {
    }

    @FunctionalInterface
    public interface Check {
        void run() throws Exception;
    }

    public record Case(String name, Check check) {
    }

    public static List<Case> cases() {
        return List.of(
                new Case("json/managed-jackson-version", () -> {
                    equal("2.18.3", new ObjectMapper().version().toString());
                    equal("2.18.3", new JsonFactory().version().toString());
                }),
                new Case("json/null-json-vs-null-java-input", () -> {
                    equal("null", JsonCodec.write(null));
                    equal(null, JsonCodec.read("null", DataPermissionChangedEvent.class));
                    fails(IllegalArgumentException.class, () -> JsonCodec.read(null, DataPermissionChangedEvent.class));
                }),
                new Case("json/strings-control-characters-and-unicode", () -> {
                    String value = "客户\n\t\"甲\"\\\u0000😀";
                    equal(value, JsonCodec.read(JsonCodec.write(value), String.class));
                    equal(REFERENCE.writeValueAsString(value), JsonCodec.write(value));
                }),
                new Case("json/map-entries-are-not-incorrectly-pruned", () -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("null", null);
                    map.put("empty", "");
                    map.put("list", List.of());
                    map.put("map", Map.of());
                    map.put("zero", 0);
                    map.put("false", false);
                    equal("{\"null\":null,\"empty\":\"\",\"list\":[],\"map\":{},\"zero\":0,\"false\":false}", JsonCodec.write(map));
                }),
                new Case("json/map-property-inclusion-is-preserved", () -> {
                    equal("{\"text\":null,\"empty\":\"\",\"list\":[]}", JsonCodec.write(new MapProperties()));
                    equal(REFERENCE.writeValueAsString(new MapProperties()), JsonCodec.write(new MapProperties()));
                }),
                new Case("json/empty-bean-remains-empty-object", () -> equal("{}", JsonCodec.write(new Object()))),
                new Case("json/jackson-field-annotations-still-apply", () -> {
                    equal("{\"wireName\":\"visible\"}", JsonCodec.write(new AnnotatedBean()));
                    equal("changed", JsonCodec.read("{\"wireName\":\"changed\",\"secret\":\"ignored\"}", AnnotatedBean.class).name);
                }),
                new Case("json/unknown-properties-compatible-with-rolling-upgrades", () -> {
                    var event = JsonCodec.read("{\"eventType\":\"FULL_REFRESH\",\"timestamp\":9,\"futureField\":{\"x\":1}}", DataPermissionChangedEvent.class);
                    equal(DataPermissionChangedEvent.EventType.FULL_REFRESH, event.getEventType());
                    equal(9L, event.getTimestamp());
                }),
                new Case("json/unknown-enum-still-rejected", () ->
                        fails(JsonCodecException.class, () -> JsonCodec.read("{\"eventType\":\"FUTURE_EVENT\"}", DataPermissionChangedEvent.class))),
                new Case("json/legacy-numeric-enum-input-policy", () -> {
                    var event = JsonCodec.read("{\"eventType\":0,\"timestamp\":9}", DataPermissionChangedEvent.class);
                    equal(DataPermissionChangedEvent.EventType.RESOURCE_CHANGED, event.getEventType());
                }),
                new Case("json/default-typing-not-enabled", () -> {
                    var event = JsonCodec.read("{\"@class\":\"not.a.loadable.Class\",\"eventType\":\"FULL_REFRESH\",\"timestamp\":9}", DataPermissionChangedEvent.class);
                    equal(DataPermissionChangedEvent.class, event.getClass());
                }),
                new Case("json/malformed-input-keeps-cause-and-prefix", () -> {
                    var error = fails(JsonCodecException.class, () -> JsonCodec.read("{", DataPermissionChangedEvent.class));
                    check(error.getMessage().startsWith("JSON 反序列化失败，"));
                    check(error.getCause() instanceof JsonProcessingException);
                    fails(JsonCodecException.class, () -> JsonCodec.read("", DataPermissionChangedEvent.class));
                }),
                new Case("json/serialization-failure-keeps-cause-and-prefix", () -> {
                    var error = fails(JsonCodecException.class, () -> JsonCodec.write(new BrokenGetter()));
                    check(error.getMessage().startsWith("JSON 序列化失败，"));
                    check(error.getCause() instanceof JsonProcessingException);
                }),
                new Case("json/no-implicit-java-time-module", () ->
                        fails(JsonCodecException.class, () -> JsonCodec.write(LocalDateTime.of(2026, 9, 20, 12, 0)))),
                new Case("json/numbers-and-date-wire-shapes", () -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", 9007199254740993L);
                    map.put("price", new BigDecimal("1.2300"));
                    map.put("date", new Date(0));
                    equal("{\"id\":9007199254740993,\"price\":1.2300,\"date\":0}", JsonCodec.write(map));
                }),
                new Case("json/array-roundtrip", () -> {
                    String[] values = {"菜单", null, "quote\""};
                    equal(java.util.Arrays.asList(values), java.util.Arrays.asList(JsonCodec.read(JsonCodec.write(values), String[].class)));
                }),
                new Case("json/real-event-wire-fields-and-no-http-envelope", () -> {
                    var event = DataPermissionChangedEvent.resourceChanged("工厂.仓库").setTimestamp(9L);
                    equal("{\"eventType\":\"RESOURCE_CHANGED\",\"resourceKey\":\"工厂.仓库\",\"policyId\":null,\"actions\":null,\"tenantId\":null,\"timestamp\":9}", JsonCodec.write(event));
                    JsonNode node = REFERENCE.readTree(JsonCodec.write(event));
                    equal(6, node.size());
                    check(!node.has("code") && !node.has("message") && !node.has("data") && !node.has("traceId"));
                }),
                new Case("json/real-event-cross-reader-compatibility", () -> {
                    var source = policyEvent(9007199254740993L);
                    equal(source, JsonCodec.read(REFERENCE.writeValueAsString(source), DataPermissionChangedEvent.class));
                    equal(source, REFERENCE.readValue(JsonCodec.write(source), DataPermissionChangedEvent.class));
                    equal(source.hashCode(), JsonCodec.read(JsonCodec.write(source), DataPermissionChangedEvent.class).hashCode());
                }),
                new Case("json/real-event-factories-roundtrip", () -> {
                    for (var event : List.of(DataPermissionChangedEvent.resourceChanged("r"),
                            DataPermissionChangedEvent.fieldPolicyChanged("r"), policyEvent(10L),
                            DataPermissionChangedEvent.fullRefresh(), DataPermissionChangedEvent.departmentTreeChanged(),
                            DataPermissionChangedEvent.storageTreeChanged())) {
                        event.setTimestamp(12L);
                        equal(event, JsonCodec.read(JsonCodec.write(event), DataPermissionChangedEvent.class));
                    }
                }),
                new Case("json/redis-utf8-independent-of-platform-charset", () -> {
                    var event = DataPermissionChangedEvent.resourceChanged("渠道.客户").setTimestamp(3L);
                    equal(event, JsonCodec.readUtf8(JsonCodec.write(event).getBytes(StandardCharsets.UTF_8), DataPermissionChangedEvent.class));
                }),
                new Case("json/legacy-trailing-token-policy-is-not-silently-changed", () -> {
                    String text = "{\"eventType\":\"FULL_REFRESH\",\"timestamp\":9} ignored";
                    equal(REFERENCE.readValue(text, DataPermissionChangedEvent.class), JsonCodec.read(text, DataPermissionChangedEvent.class));
                }),
                new Case("json/actual-audit-snapshot-still-projects-scalars", () -> {
                    JsonNode snapshot = REFERENCE.readTree(OperationLogSnapshot.of(new AuditRow()));
                    equal("客户", snapshot.get("name").asText());
                    equal("parent", snapshot.get("parent").asText());
                    equal(12L, snapshot.get("id").asLong());
                    check(!snapshot.has("relation") && !snapshot.has("list") && !snapshot.has("map") && !snapshot.has("STATIC"));
                }),
                new Case("json/actual-audit-failure-fallback", () -> {
                    equal(null, OperationLogSnapshot.of(null));
                    equal("{\"snapshot-error\":\"TimeAudit\"}", OperationLogSnapshot.of(new TimeAudit()));
                }),
                new Case("json/actual-audit-label-order", () -> {
                    equal("C001", OperationLogSnapshot.labelOf(new AuditLabel()));
                    equal(null, OperationLogSnapshot.labelOf(null));
                }),
                new Case("json/shared-mapper-concurrent-callers", JsonContracts::concurrent)
        );
    }

    public static void main(String[] args) throws Exception {
        int passed = 0;
        for (Case test : cases()) {
            test.check().run();
            System.out.println("PASS " + test.name());
            passed++;
        }
        if (passed == 0) {
            throw new AssertionError("JSON 契约没有执行");
        }
        System.out.println("JSON contracts: " + passed + " passed (real Jackson/DTO subset; not Maven HTTP/Redis integration)");
    }

    private static ObjectMapper referenceMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configOverride(Map.class).setInclude(JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, null));
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return mapper;
    }

    private static DataPermissionChangedEvent policyEvent(long id) {
        return DataPermissionChangedEvent.policyChanged("asset.material", id, EnumSet.of(DataPermissionAction.QUERY, DataPermissionAction.EXPORT))
                .setTenantId(7L).setTimestamp(1730000000123L);
    }

    private static void concurrent() throws Exception {
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (long i = 0; i < 120; i++) {
                final long id = i;
                tasks.add(() -> {
                    var event = policyEvent(id);
                    equal(event, JsonCodec.read(JsonCodec.write(event), DataPermissionChangedEvent.class));
                    return null;
                });
            }
            for (var result : executor.invokeAll(tasks)) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }

    private static void check(boolean value) {
        if (!value) {
            throw new AssertionError("JSON contract failed");
        }
    }

    private static <T extends Throwable> T fails(Class<T> type, Check action) throws Exception {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return type.cast(error);
            }
            throw error;
        }
        throw new AssertionError("expected " + type.getName());
    }

    public static class MapProperties {
        public String text;
        public String empty = "";
        public Map<String, String> map = Map.of();
        public Map<String, String> absentMap;
        public List<String> list = List.of();
    }

    public static class AnnotatedBean {
        @JsonProperty("wireName")
        public String name = "visible";
        @JsonIgnore
        public String secret = "never-serialize";
    }

    public static class BrokenGetter {
        public String getValue() {
            throw new IllegalStateException("getter failed");
        }
    }

    public static class AuditParent {
        private String parent = "parent";
    }

    public static class AuditRow extends AuditParent {
        private static final String STATIC = "not-a-field";
        private Long id = 12L;
        private String name = "客户";
        private Object relation = new Object();
        private List<String> list = List.of("not-a-scalar");
        private Map<String, String> map = Map.of("not", "a-scalar");

        public Object getRelation() {
            throw new AssertionError("snapshot must not trigger a lazy getter");
        }
    }

    public static class TimeAudit {
        private LocalDateTime time = LocalDateTime.of(2026, 9, 20, 12, 0);
    }

    public static class AuditLabel {
        public String getCode() { return "C001"; }
        public String getName() { return "fallback"; }
        public long getId() { return 12L; }
    }
}
