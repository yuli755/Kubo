package cn.hamm.spms.common.exception;

import cn.hamm.spms.framework.http.ApiResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * <h1>请求绑定 / 协议类异常的项目协议映射</h1>
 *
 * <h2>为什么需要它</h2>
 * <p>此前全局只有两个 advice：{@link BusinessExceptionAdvice}（只管 {@code BusinessException}）
 * 与 {@link DataIntegrityViolationAdvice}（只管数据库约束冲突）。Spring MVC 在
 * <b>进入控制器方法之前</b>抛出的那一批异常无人接管，全部落到容器默认错误页：</p>
 *
 * <pre>
 * {"timestamp":"...","status":400,"error":"Bad Request","path":"/sale/add"}
 * </pre>
 *
 * <p>这个响应体<b>没有 {@code code}、也没有 {@code message} 字段</b>，而前端
 * {@code src/api/axios.ts} 的错误分支读的正是 {@code data?.message}，取不到就退回泛化文案
 * （如「参数校验失败」）。直接后果是：<b>实体层几十条手写校验消息全部无法到达用户</b>——
 * 例如 {@code SaleEntity.customer} 上的
 * {@code @NotNull(groups = {WhenAdd.class}, message = "客户不能为空")}，
 * 用户实际只看到「参数校验失败」，排查方向被抹平。</p>
 *
 * <p>2026-09-28 真机联调实测：以 {@code {}} 调 28 个 {@code getDetail} 端点，全部返回
 * {@code http=400} 且无业务码；缺 {@code @RequestParam} 必填参数的
 * {@code /dataFieldPolicy/my-mask-map} 同样如此。</p>
 *
 * <h2>响应形态</h2>
 * <p>与 {@link DataIntegrityViolationAdvice} 注明的房内规则一致：
 * <b>HTTP 200 + 业务错误码 body，不真返回 4xx/5xx</b>（前端拦截器按 code 处理）。
 * {@code traceId} 由 {@code ApiResponseAdvice} 统一注入，此处无需关心。</p>
 *
 * <h2>为什么不做 {@code Exception} 兜底</h2>
 * <p>{@code backendHttpBoundary.spec.ts} 对该边界有明文约束：advice「仅处理自持异常」，
 * 且断言不得出现 {@code @ExceptionHandler(Exception.class)}。保持这一纪律有两点实际收益：</p>
 * <ul>
 *   <li><b>意外异常仍产生真实 HTTP 500</b>，基础设施侧（网关、Prometheus
 *       {@code http_server_requests}、部署预演）不会因全量转 200 而致盲；
 *       这类异常本来也没有可转达给用户的具体消息，完整堆栈进日志即可。</li>
 *   <li>本 advice 只覆盖<b>可枚举、语义明确</b>的客户端错误，改动面小，
 *       不会把尚未自持的框架/基础设施异常一并吞进项目协议。</li>
 * </ul>
 *
 * <h2>顺序</h2>
 * <p>{@code LOWEST_PRECEDENCE}：让 {@link BusinessExceptionAdvice} 与
 * {@link DataIntegrityViolationAdvice}（均为 {@code HIGHEST_PRECEDENCE}）先接管各自的异常，
 * 本 advice 只兜住剩余的绑定/协议类异常。</p>
 *
 * @author Hamm.cn
 * @see BusinessExceptionAdvice
 * @see DataIntegrityViolationAdvice
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@ResponseStatus(HttpStatus.OK)
public class RequestBindingExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(RequestBindingExceptionAdvice.class);

    /** 消息里最多附带的字段数，避免批量校验失败时把整个表单错误糊成一句话 */
    private static final int MAX_REPORTED_FIELDS = 3;

    /**
     * 处理 {@code @RequestBody} 上的 Bean Validation 失败。
     *
     * <p>{@code MethodArgumentNotValidException} 是 {@link BindException} 的子类，
     * 因此这里声明父类即可同时覆盖「JSON 请求体校验」与「表单/查询对象绑定校验」两种来源，
     * 不必写两个几乎相同的处理器。</p>
     *
     * @param ex 绑定异常
     * @return 带具体字段消息的业务错误响应
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBind(BindException ex) {
        String message = summarizeFieldErrors(ex);
        log.warn("请求参数校验失败：{}", message);
        return ApiResponse.error(Errors.PARAM_INVALID, message);
    }

    /**
     * 处理控制器方法形参上的约束校验失败（{@code @Validated} 直接标注在方法/形参上）。
     *
     * <p>Spring 6.1 起这类失败可能抛 {@link HandlerMethodValidationException}
     * 而非 {@link ConstraintViolationException}，两者都覆盖。</p>
     *
     * @param ex 约束校验异常
     * @return 带具体约束消息的业务错误响应
     */
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ApiResponse<Void> handleConstraintViolation(Exception ex) {
        String message = Errors.PARAM_INVALID.getMessage();
        if (ex instanceof ConstraintViolationException cve && !cve.getConstraintViolations().isEmpty()) {
            message = cve.getConstraintViolations().stream()
                    .map(RequestBindingExceptionAdvice::violationText)
                    .limit(MAX_REPORTED_FIELDS)
                    .reduce((a, b) -> a + "；" + b)
                    .orElse(message);
        }
        log.warn("请求参数约束校验失败：{}", message);
        return ApiResponse.error(Errors.PARAM_INVALID, message);
    }

    /**
     * 处理缺少必填请求参数 / 路径变量。
     *
     * <p>用 {@code PARAM_MISSING(4001)} 而非 {@code PARAM_INVALID(4002)}：
     * 「没传」与「传了但不合法」是两种不同的客户端错误，分开才能让人一眼看出该补字段还是改取值。
     * 消息里带上参数名，否则调用方只知到「缺参数」而不知缺哪个。</p>
     *
     * @param ex 缺参异常
     * @return 指明缺失参数名的业务错误响应
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class})
    public ApiResponse<Void> handleMissingParameter(Exception ex) {
        String name = ex instanceof MissingServletRequestParameterException m
                ? m.getParameterName()
                : ((MissingPathVariableException) ex).getVariableName();
        String message = String.format("缺少必要的请求参数「%s」", name);
        log.warn("请求缺少必填参数：{}", name);
        return ApiResponse.error(Errors.PARAM_MISSING, message);
    }

    /**
     * 处理参数类型不匹配（如把非数字传给 {@code Long id}）。
     *
     * @param ex 类型不匹配异常
     * @return 指明参数名与期望类型的业务错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() == null ? "指定类型" : ex.getRequiredType().getSimpleName();
        String message = String.format("参数「%s」取值不合法，应为%s", ex.getName(), expected);
        log.warn("请求参数类型不匹配：{}", message);
        return ApiResponse.error(Errors.PARAM_INVALID, message);
    }

    /**
     * 处理请求体不可读。这一类异常<b>混装了两种完全不同的客户端错误</b>，必须按 cause 分流，
     * 否则会把「值填错了」误报成「你发的不是 JSON」，把人往错误的方向带：
     *
     * <ul>
     *   <li><b>JSON 语法本身非法</b>（cause 是 {@code JsonParseException} 等非
     *       {@code MismatchedInputException}）→ {@code JSON_DECODE_FAIL(5003)}。
     *       实测消息形如 {@code Unexpected character ('n' (code 110))}。</li>
     *   <li><b>JSON 合法但某个值转不成目标类型</b>（cause 是
     *       {@code MismatchedInputException}/{@code InvalidFormatException}）
     *       → {@code PARAM_INVALID(4002)}，并从 Jackson 的路径里点出是哪个字段。
     *       实测消息形如 {@code Cannot deserialize value of type `java.lang.Long` from String}
     *       （把 {@code {"id":"abc"}} 发给 {@code Long id}）。</li>
     * </ul>
     *
     * <p>后者本质上等同于 {@link MethodArgumentTypeMismatchException}，只是发生在反序列化阶段
     * 而非参数绑定阶段；给同一个码，调用方就不必区分两条路径。</p>
     *
     * @param ex 请求体不可读异常
     * @return 业务错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof MismatchedInputException mismatch) {
            String field = jacksonPath(mismatch);
            String message = field.isEmpty()
                    ? "请求体中的字段取值与期望类型不符，请检查提交内容"
                    : String.format("字段「%s」取值与期望类型不符，请检查提交内容", field);
            log.warn("请求体字段类型不匹配：{}（{}）", message, mismatch.getOriginalMessage());
            return ApiResponse.error(Errors.PARAM_INVALID, message);
        }
        log.warn("请求体缺失或 JSON 语法非法：{}", ex.getMessage());
        return ApiResponse.error(Errors.JSON_DECODE_FAIL, "请求体缺失或不是合法的 JSON，请检查提交内容");
    }

    /**
     * 从 Jackson 的引用路径还原字段名（嵌套对象用 {@code .} 连接，数组下标用 {@code [i]}）。
     *
     * <p>只取路径不取原始消息：Jackson 的原文含内部类全限定名
     * （如 {@code cn.hamm.spms.framework.query.Page}），对用户是噪音，对攻击者还泄露包结构。</p>
     *
     * @param mismatch 类型不匹配的输入异常
     * @return 字段路径；无路径信息时返回空串
     */
    private static String jacksonPath(MismatchedInputException mismatch) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : mismatch.getPath()) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            }
            else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    /**
     * 处理请求方法不被支持（如对只暴露 POST 的端点发 GET）。
     *
     * @param ex 方法不支持异常
     * @return 指明实际支持方法的业务错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String supported = ex.getSupportedHttpMethods() == null
                ? ""
                : "，本接口支持：" + ex.getSupportedHttpMethods();
        log.warn("请求方法不支持：{}{}", ex.getMethod(), supported);
        return ApiResponse.error(Errors.REQUEST_METHOD_UNSUPPORTED,
                String.format("不支持 %s 请求方法%s", ex.getMethod(), supported));
    }

    /**
     * 处理 Content-Type 不被支持。
     *
     * @param ex 媒体类型不支持异常
     * @return 业务错误响应
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ApiResponse<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("请求 Content-Type 不支持：{}", ex.getContentType());
        return ApiResponse.error(Errors.REQUEST_CONTENT_TYPE_UNSUPPORTED,
                String.format("不支持的请求数据类型：%s，请改用 application/json", ex.getContentType()));
    }

    /**
     * 把字段级校验错误汇总成一句人话：优先用注解里手写的 {@code message}
     * （例如「客户不能为空」），并带上字段名，最多列 {@value #MAX_REPORTED_FIELDS} 个。
     *
     * @param ex 绑定异常
     * @return 汇总后的用户可读消息
     */
    private static String summarizeFieldErrors(BindException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(RequestBindingExceptionAdvice::fieldErrorText)
                .limit(MAX_REPORTED_FIELDS)
                .reduce((a, b) -> a + "；" + b)
                .orElse(Errors.PARAM_INVALID.getMessage());
    }

    /**
     * 单个字段错误的文案：{@code 字段名: 消息}；消息缺失时退回默认文案。
     *
     * @param error 字段错误
     * @return 用户可读文案
     */
    private static String fieldErrorText(FieldError error) {
        String text = error.getDefaultMessage();
        if (text == null || text.isBlank()) {
            text = Errors.PARAM_INVALID.getMessage();
        }
        return error.getField() + ": " + text;
    }

    /**
     * 单条约束违规的文案：优先属性路径末段 + 消息。
     *
     * @param violation 约束违规
     * @return 用户可读文案
     */
    private static String violationText(ConstraintViolation<?> violation) {
        String path = String.valueOf(violation.getPropertyPath());
        int dot = path.lastIndexOf('.');
        String field = dot >= 0 ? path.substring(dot + 1) : path;
        String message = violation.getMessage();
        return field + ": " + (message == null || message.isBlank()
                ? Errors.PARAM_INVALID.getMessage()
                : message);
    }
}
