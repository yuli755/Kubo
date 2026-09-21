package cn.hamm.spms.framework.error;

/**
 * <h1>项目拥有的业务失败，不继承旧框架异常</h1>
 *
 * <p>仅携带业务 code/message/data。HTTP 状态、trace 与响应序列化由入口层负责。
 * 仍为未检查异常，不能因迁移改变事务对业务失败的默认回滚类别。</p>
 *
 * @author Hamm.cn
 */
public final class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int code;
    private final Object data;

    public BusinessException() {
        this(500, null, null);
    }

    public BusinessException(String message) {
        this(500, message, null);
    }

    public BusinessException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public BusinessException(ErrorCode error) {
        this(error.getCode(), error.getMessage(), null);
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
