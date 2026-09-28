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

    /**
     * 用错误码的 code、但调用方指定的文案抛错。
     *
     * <p>补齐 airpower 退场时遗漏的构造器：{@code ErrorCode} 的 javadoc 已约定
     * 「各次抛错新建异常，传入文案不修改枚举单例」，但此前只有 {@code (ErrorCode)}
     * 与 {@code (int, String, Object)} 两个入口，导致 {@code new BusinessException(Errors.X, "文案")}
     * 这类调用全部编译不过。</p>
     *
     * @param error   错误码，仅取其 code
     * @param message 本次抛错使用的文案，不回写枚举
     */
    public BusinessException(ErrorCode error, String message) {
        this(error.getCode(), message, null);
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
