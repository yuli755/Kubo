package cn.hamm.spms.framework.http;

import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.spms.common.compat.LegacyApiResponseBridge;
import cn.hamm.spms.common.exception.BusinessExceptionAdvice;
import cn.hamm.spms.common.exception.CustomError;
import cn.hamm.spms.common.exception.DataIntegrityViolationAdvice;
import cn.hamm.spms.common.exception.Errors;
import cn.hamm.spms.framework.error.BusinessException;
import cn.hamm.spms.framework.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.nio.charset.StandardCharsets;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * <h1>真实 Spring MVC + 自持异常/响应 + 显式旧响应桥的兼容契约</h1>
 *
 * <p>测试控制器只用于触发 HTTP 情况，不代替业务控制器或伪造旧框架类。
 * 旧 Json/ServiceException 使用正式 JAR 或逐文件核验的上游固定源码编译结果。</p>
 *
 * @author Hamm.cn
 */
public final class HttpErrorContracts {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CaptureAdvice CAPTURE = new CaptureAdvice();
    private static final MockMvc MVC = MockMvcBuilders.standaloneSetup(new Endpoint())
            .setControllerAdvice(new BusinessExceptionAdvice(), new DataIntegrityViolationAdvice(),
                    new LegacyApiResponseBridge(), CAPTURE, new CompetingAdvice())
            .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8), new ByteArrayHttpMessageConverter(),
                    new MappingJackson2HttpMessageConverter(MAPPER))
            .build();

    private HttpErrorContracts() {
    }

    @FunctionalInterface
    public interface Check { void run() throws Exception; }
    public record Case(String name, Check check) { }

    public static List<Case> cases() {
        return List.of(
                new Case("http/owned-envelope-matches-real-legacy-json", () -> {
                    equal(MAPPER.readTree(MAPPER.writeValueAsString(Json.show(403, "denied", Map.of("reason", "test")))),
                            MAPPER.readTree(MAPPER.writeValueAsString(ApiResponse.of(403, "denied", Map.of("reason", "test")))));
                    equal("{\"code\":200,\"message\":\"ok\",\"data\":null}", MAPPER.writeValueAsString(ApiResponse.success("ok")));
                }),
                new Case("http/trace-null-omitted-and-present-retained", () -> {
                    var original = ApiResponse.data(List.of("value"));
                    check(!MAPPER.readTree(MAPPER.writeValueAsString(original)).has("traceId"));
                    var traced = original.withTraceId("request-7");
                    equal("request-7", MAPPER.readTree(MAPPER.writeValueAsString(traced)).get("traceId").asText());
                    equal(null, original.getTraceId());
                }),
                new Case("http/bridge-supports-only-owned-json-responses", () -> {
                    LegacyApiResponseBridge bridge = new LegacyApiResponseBridge();
                    MethodParameter owned = new MethodParameter(Endpoint.class.getMethod("success"), -1);
                    MethodParameter text = new MethodParameter(Endpoint.class.getMethod("plain"), -1);
                    check(bridge.supports(owned, MappingJackson2HttpMessageConverter.class));
                    check(!bridge.supports(owned, StringHttpMessageConverter.class));
                    check(!bridge.supports(text, MappingJackson2HttpMessageConverter.class));
                }),
                new Case("http/bridge-keeps-data-identity-for-old-filter-chain", () -> {
                    Object data = List.of(Map.of("key", 1));
                    var body = ApiResponse.of(4002, "bad", data).withTraceId("trace");
                    Object adapted = new LegacyApiResponseBridge().beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class, null, null);
                    check(adapted instanceof Json);
                    Json json = (Json) adapted;
                    check(json.getData() == data);
                    equal("trace", json.getTraceId());
                    equal(4002, json.getCode());
                }),
                new Case("http/success-is-one-envelope-not-nested", () -> {
                    JsonNode json = response("/contract/success");
                    equal(200, json.get("code").asInt());
                    equal("获取成功", json.get("message").asText());
                    equal("ok", json.get("data").get("value").asText());
                    check(CAPTURE.body instanceof Json);
                    equal(3, json.size());
                }),
                new Case("http/forbidden-stays-http-200-business-403", () -> {
                    JsonNode json = response("/contract/forbidden");
                    equal(403, json.get("code").asInt());
                    equal("无权操作", json.get("message").asText());
                    check(CAPTURE.body instanceof Json);
                }),
                new Case("http/unauthorized-stays-http-200-business-401", () -> {
                    JsonNode json = response("/contract/unauthorized");
                    equal(401, json.get("code").asInt());
                    equal(Errors.UNAUTHORIZED.getMessage(), json.get("message").asText());
                }),
                new Case("http/custom-login-code-not-collapsed-to-500", () -> {
                    JsonNode json = response("/contract/login-failed");
                    equal(200001, json.get("code").asInt());
                    equal("用户账号或密码错误", json.get("message").asText());
                }),
                new Case("http/error-data-preserved", () -> {
                    JsonNode json = response("/contract/options");
                    equal(500, json.get("code").asInt());
                    equal("bad enum", json.get("message").asText());
                    equal(1, json.get("data").get(0).get("key").asInt());
                }),
                new Case("http/new-specific-advice-wins-over-generic-catch-all", () -> {
                    equal(404, response("/contract/not-found").get("code").asInt());
                }),
                new Case("http/old-exceptions-still-reach-their-handler", () -> {
                    JsonNode json = response("/contract/legacy");
                    equal(4036, json.get("code").asInt());
                    equal("legacy disabled", json.get("message").asText());
                }),
                new Case("http/unrelated-exceptions-not-swallowed-by-new-advice", () -> {
                    equal(5999, response("/contract/unexpected").get("code").asInt());
                }),
                new Case("http/database-duplicate-contract-kept", () -> {
                    JsonNode json = response("/contract/duplicate");
                    equal(4031, json.get("code").asInt());
                    check(json.get("message").asText().contains("数据已存在"));
                }),
                new Case("http/database-child-reference-contract-kept", () -> {
                    equal(403, response("/contract/child-reference").get("code").asInt());
                }),
                new Case("http/database-parent-reference-contract-kept", () -> {
                    equal(4034, response("/contract/parent-reference").get("code").asInt());
                }),
                new Case("http/database-invalid-field-contract-kept", () -> {
                    equal(4002, response("/contract/too-long").get("code").asInt());
                    equal(4002, response("/contract/not-null").get("code").asInt());
                    equal(4002, response("/contract/unknown-constraint").get("code").asInt());
                }),
                new Case("http/cyclic-cause-chain-terminates", () -> {
                    RuntimeException first = new RuntimeException("Duplicate entry 'x' for key 'k'");
                    RuntimeException second = new RuntimeException("wrapped");
                    first.initCause(second);
                    second.initCause(first);
                    equal(4031, new DataIntegrityViolationAdvice().handle(first).getCode());
                }),
                new Case("http/openapi-raw-map-not-wrapped", () -> {
                    JsonNode json = response("/v3/api-docs");
                    equal("3.0.1", json.get("openapi").asText());
                    check(!json.has("code"));
                }),
                new Case("http/text-and-binary-responses-not-rewritten", () -> {
                    equal("plain", raw("/contract/text").getContentAsString(StandardCharsets.UTF_8));
                    equal(List.of((byte) 1, (byte) 2, (byte) 3), bytes(raw("/contract/binary").getContentAsByteArray()));
                }),
                new Case("http/owned-trace-survives-compatibility-bridge", () -> {
                    equal("owned-trace", response("/contract/trace").get("traceId").asText());
                }),
                new Case("http/old-vs-owned-guard-differential", HttpErrorContracts::guardParity)
        );
    }

    public static void main(String[] args) throws Exception {
        int passed = 0;
        for (Case test : cases()) {
            test.check().run();
            System.out.println("PASS " + test.name());
            passed++;
        }
        if (passed == 0) { throw new AssertionError("No HTTP contracts executed"); }
        System.out.println("HTTP error contracts: " + passed + " passed (real Spring MVC and explicit legacy bridge; not full application)");
    }

    private static MockHttpServletResponse raw(String path) throws Exception {
        MockHttpServletResponse response = MVC.perform(get(path)).andReturn().getResponse();
        equal(200, response.getStatus());
        return response;
    }

    private static JsonNode response(String path) throws Exception {
        return MAPPER.readTree(raw(path).getContentAsByteArray());
    }

    private static List<Byte> bytes(byte[] bytes) {
        List<Byte> result = new java.util.ArrayList<>();
        for (byte value : bytes) { result.add(value); }
        return result;
    }

    private record Failure(int code, String message, Object data) { }

    private static Failure owned(Consumer<ErrorCode> check) {
        try { check.accept(Errors.PARAM_INVALID); return null; }
        catch (BusinessException error) { return new Failure(error.getCode(), error.getMessage(), error.getData()); }
    }

    private static Failure previous(Consumer<ServiceException> check) {
        try { check.accept(new ServiceException(4002, Errors.PARAM_INVALID.getMessage())); return null; }
        catch (ServiceException error) { return new Failure(error.getCode(), error.getMessage(), error.getData()); }
    }

    private static void guardParity() {
        for (Object value : new Object[]{null, "", " ", "\u3000", "x", 0, false, List.of()}) {
            equal(previous(error -> error.whenEmpty(value)), owned(error -> error.whenEmpty(value)));
            equal(previous(error -> error.whenNull(value, "missing")), owned(error -> error.whenNull(value, "missing")));
            equal(previous(error -> error.whenNotNull(value)), owned(error -> error.whenNotNull(value)));
            equal(previous(error -> error.whenNotEquals(value, "x")), owned(error -> error.whenNotEquals(value, "x")));
        }
        Locale before = Locale.getDefault();
        try {
            for (Locale locale : List.of(Locale.ROOT, Locale.forLanguageTag("tr-TR"))) {
                Locale.setDefault(locale);
                for (String[] pair : new String[][]{{null, null}, {"aBc", "AbC"}, {"I", "i"}, {"x", null}, {"x", " x"}}) {
                    equal(previous(error -> error.whenNotEqualsIgnoreCase(pair[0], pair[1])), owned(error -> error.whenNotEqualsIgnoreCase(pair[0], pair[1])));
                }
            }
        } finally { Locale.setDefault(before); }
        equal(previous(error -> error.show("bad", List.of(1, 2))), owned(error -> error.show("bad", List.of(1, 2))));
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) { throw new AssertionError("expected=" + expected + ", actual=" + actual); }
    }
    private static void check(boolean value) { if (!value) { throw new AssertionError("HTTP contract failed"); } }

    @RestController
    public static class Endpoint {
        @GetMapping("/contract/success") public ApiResponse<Map<String, String>> success() { return ApiResponse.data(Map.of("value", "ok")); }
        @GetMapping("/contract/forbidden") public ApiResponse<?> forbidden() { Errors.FORBIDDEN.show(); return null; }
        @GetMapping("/contract/unauthorized") public ApiResponse<?> unauthorized() { Errors.UNAUTHORIZED.show(); return null; }
        @GetMapping("/contract/login-failed") public ApiResponse<?> loginFailed() { CustomError.USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID.show(); return null; }
        @GetMapping("/contract/not-found") public ApiResponse<?> notFound() { Errors.DATA_NOT_FOUND.show(); return null; }
        @GetMapping("/contract/options") public ApiResponse<?> options() { Errors.SERVICE_ERROR.show("bad enum", List.of(Map.of("key", 1, "label", "option"))); return null; }
        @GetMapping("/contract/legacy") public Json legacy() { throw new ServiceException(4036, "legacy disabled"); }
        @GetMapping("/contract/unexpected") public Object unexpected() { throw new IllegalStateException("not a business failure"); }
        @GetMapping("/contract/duplicate") public Object duplicate() { throw constraint("Duplicate entry 'x' for key 'idx'"); }
        @GetMapping("/contract/child-reference") public Object childReference() { throw constraint("Cannot add or update a child row"); }
        @GetMapping("/contract/parent-reference") public Object parentReference() { throw constraint("Cannot delete or update a parent row"); }
        @GetMapping("/contract/too-long") public Object tooLong() { throw constraint("Data too long for column 'name'"); }
        @GetMapping("/contract/not-null") public Object notNull() { throw constraint("Column 'name' cannot be null"); }
        @GetMapping("/contract/unknown-constraint") public Object unknownConstraint() { throw constraint("unknown check constraint"); }
        @GetMapping("/v3/api-docs") public Map<String, String> docs() { return Map.of("openapi", "3.0.1"); }
        @GetMapping(value = "/contract/text", produces = "text/plain") public String plain() { return "plain"; }
        @GetMapping(value = "/contract/binary", produces = "application/octet-stream") public byte[] binary() { return new byte[]{1, 2, 3}; }
        @GetMapping("/contract/trace") public ApiResponse<Void> trace() { return ApiResponse.success("ok").withTraceId("owned-trace"); }
        private static DataIntegrityViolationException constraint(String message) { return new DataIntegrityViolationException("wrapped", new SQLIntegrityConstraintViolationException(message)); }
    }

    /** 测试竞争 advice，验证真实 Spring 异常解析优先级；不是旧框架的替代实现。 */
    @RestControllerAdvice
    @Order(Ordered.LOWEST_PRECEDENCE)
    public static class CompetingAdvice {
        @ExceptionHandler(ServiceException.class)
        public Json legacy(ServiceException failure) { return Json.show(failure.getCode(), failure.getMessage(), failure.getData()); }
        @ExceptionHandler(Exception.class)
        public Json fallback(Exception failure) { return Json.show(5999, "fallback", null); }
    }

    /** 只观察桥后的对象类型/引用，不伪造字段脱敏或 legacy interceptor。 */
    @RestControllerAdvice
    @Order(Ordered.LOWEST_PRECEDENCE)
    public static class CaptureAdvice implements ResponseBodyAdvice<Object> {
        private Object body;
        @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
        @Override public Object beforeBodyWrite(Object value, MethodParameter type, MediaType contentType, Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) { body = value; return value; }
    }
}
