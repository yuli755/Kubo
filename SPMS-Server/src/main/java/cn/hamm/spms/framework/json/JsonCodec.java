package cn.hamm.spms.framework.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * <h1>内部事件与存储文本的 JSON 编解码</h1>
 *
 * <p>只处理 JSON 文本，不处理 HTTP 的 code/message/data 封装，也不绑定框架异常。
 * 保留既有存储协议的三项 mapper 设置；不使用 Spring 的 HTTP ObjectMapper，避免模块、
 * 脱敏拦截器或全局 null 配置改变缓存/事件格式。权限条件 DSL 的严格解析器不可改用本类。</p>
 *
 * @author Hamm.cn
 */
public final class JsonCodec {
    // 完成配置后才经类初始化安全发布，外部不能取得 mapper 后改写全局配置。
    private static final ObjectMapper MAPPER = createMapper();

    private JsonCodec() {
    }

    /** 序列化为 JSON 文本；null 输出 JSON null，不偷偷包成响应对象。 */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new JsonCodecException("JSON 序列化失败，", error);
        }
    }

    /** 明确指定目标类型；未知属性沿用兼容忽略，不启用多态默认类型。 */
    public static <T> T read(String text, Class<T> type) {
        try {
            return MAPPER.readValue(text, type);
        } catch (JsonProcessingException error) {
            throw new JsonCodecException("JSON 反序列化失败，", error);
        }
    }

    /** Redis 文本协议固定 UTF-8，不能依赖运行机器的默认字符集。 */
    public static <T> T readUtf8(byte[] bytes, Class<T> type) {
        return read(new String(bytes, StandardCharsets.UTF_8), type);
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // 这是 Map 类型的 property inclusion，不是“去掉 Map 内所有 null/空项”。行为由真实 Jackson 契约锁定。
        mapper.configOverride(Map.class)
                .setInclude(JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, null));
        return mapper;
    }
}
