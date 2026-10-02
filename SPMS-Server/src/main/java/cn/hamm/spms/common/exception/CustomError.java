package cn.hamm.spms.common.exception;

import cn.hamm.spms.common.AppConstant;
import cn.hamm.spms.framework.error.ErrorCode;

import java.util.function.Supplier;

/**
 * <h1>应用业务错误码的唯一落点</h1>
 *
 * <p>保留 BASE_CUSTOM_ERROR + 声明值，不再依赖旧异常接口；勿新建第二套业务错误码枚举。</p>
 *
 * @author Hamm.cn
 */
public enum CustomError implements ErrorCode, Supplier<CustomError> {
    USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID(1, "用户账号或密码错误"),

    LOGIN_ATTEMPT_TOO_MANY(2, "登录失败次数过多，请稍后再试"),

    EMAIL_SEND_BUSY(101, "发送邮件过于频繁，请稍后再试"),
    SMS_SEND_BUSY(102, "发送短信过于频繁，请稍后再试"),

    REQUEST_TOO_FREQUENT(103, "请求过于频繁，请稍后再试"),

    RESET_ATTEMPT_TOO_MANY(104, "验证码错误次数过多，请稍后再试"),
    ;

    private final int code;
    private final String message;

    CustomError(int code, String message) {
        this.code = code + AppConstant.BASE_CUSTOM_ERROR;
        this.message = message;
    }

    /**
     * 获取枚举的 {@code Key}
     *
     * @return {@code Key}
     */
    public int getKey() {
        return code;
    }

    /**
     * 获取枚举的描述
     *
     * @return 描述
     */
    public String getLabel() {
        return message;
    }
    @Override
    public int getCode() { return code; }

    @Override
    public String getMessage() { return message; }

    @Override
    public CustomError get() { return this; }
}
