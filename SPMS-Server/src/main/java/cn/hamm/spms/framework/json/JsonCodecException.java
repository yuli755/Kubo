package cn.hamm.spms.framework.json;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * <h1>JSON 文本编解码失败</h1>
 *
 * <p>保留原因供调用边界诊断；不携带 HTTP code，不继承或调用旧服务异常。</p>
 *
 * @author Hamm.cn
 */
public final class JsonCodecException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    JsonCodecException(String prefix, JsonProcessingException cause) {
        super(prefix + cause.getMessage(), cause);
    }
}
