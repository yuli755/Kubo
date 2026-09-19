package cn.hamm.spms.framework.curd.interceptor;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名：
 *   cn.hamm.airpower.core  → cn.hamm.spms.core
 *   cn.hamm.airpower.api   → cn.hamm.spms.framework.api
 *   cn.hamm.airpower.curd  → cn.hamm.spms.framework.curd
 *   cn.hamm.airpower.redis → cn.hamm.spms.framework.redis
 * 代码逻辑一字未改（JPA→MyBatis-Plus 的持久化替换是紧随其后的独立一批，见 docs）。
 * 从此这一层由本仓拥有：改代码改这里，不再有"上游"。
 */

import cn.hamm.spms.core.Json;
import cn.hamm.spms.core.exception.ServiceException;
import cn.hamm.spms.core.interfaces.IException;
import cn.hamm.spms.common.exception.Errors;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.hamm.spms.common.exception.Errors.*;

/**
 * <h1>全局异常处理拦截器</h1>
 *
 * @author Hamm.cn
 * @see Errors
 */
@ControllerAdvice
@ResponseStatus(HttpStatus.OK)
@ResponseBody
@Slf4j
public class ExceptionInterceptor {
    /**
     * 错误信息和描述
     */
    private static final String MESSAGE_AND_DESCRIPTION = "%s (%s)";

    /**
     * 参数验证失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Json badRequestHandle(@NotNull MethodArgumentNotValidException exception) {
        logException(exception);
        BindingResult result = exception.getBindingResult();
        StringBuilder stringBuilder = new StringBuilder();
        if (!result.hasErrors() || !result.hasFieldErrors()) {
            return responseError(PARAM_INVALID);
        }
        List<FieldError> errors = result.getFieldErrors();
        errors.stream().findFirst().ifPresent(error -> stringBuilder.append(String.format(
                MESSAGE_AND_DESCRIPTION, error.getDefaultMessage(), error.getField()
        )));
        return responseError(PARAM_INVALID, stringBuilder.toString())
                .setData(errors.stream().map(item -> String.format(
                                MESSAGE_AND_DESCRIPTION, item.getDefaultMessage(), item.getField()
                        )).toList()
                );
    }

    /**
     * 参数校验失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Json badRequestHandle(@NotNull ConstraintViolationException exception) {
        logException(exception);
        StringBuilder stringBuilder = new StringBuilder();
        Set<ConstraintViolation<?>> errors = exception.getConstraintViolations();
        errors.stream().findFirst().ifPresent(error -> stringBuilder.append(String.format(
                MESSAGE_AND_DESCRIPTION, error.getMessage(), error.getInvalidValue().toString()
        )));
        return responseError(PARAM_INVALID, stringBuilder.toString());
    }

    /**
     * 删除时的数据关联校验异常
     */
    @ExceptionHandler({SQLIntegrityConstraintViolationException.class, DataIntegrityViolationException.class})
    public Json deleteUsingDataException(@NotNull Exception exception) {
        logException(exception);
        return responseError(FORBIDDEN_DELETE_USED);
    }

    /**
     * 访问的接口没有实现
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Json notFoundHandle(@NotNull NoHandlerFoundException exception) {
        logException(exception);
        return responseError(API_SERVICE_UNSUPPORTED);
    }

    /**
     * 请求的数据不是标准 JSON
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Json dataExceptionHandle(@NotNull HttpMessageNotReadableException exception) {
        logException(exception);
        return responseError(REQUEST_CONTENT_TYPE_UNSUPPORTED,
                "请求参数格式不正确,请检查是否接口支持的JSON");
    }

    /**
     * 不支持的请求方法
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Json methodExceptionHandle(@NotNull HttpRequestMethodNotSupportedException exception) {
        logException(exception);
        String supportedMethod = String.join("/", Objects.requireNonNull(exception.getSupportedMethods()));
        return responseError(REQUEST_METHOD_UNSUPPORTED, String.format(
                "%s 不被支持，请使用 %s 方法请求", exception.getMethod(), supportedMethod
        ));
    }

    /**
     * 不支持的文件上传
     */
    @ExceptionHandler(MultipartException.class)
    public Json multipartExceptionHandle(@NotNull MultipartException exception) {
        logException(exception);
        return responseError(REQUEST_METHOD_UNSUPPORTED, "请使用 multipart 方式上传文件");
    }

    /**
     * 未选择上传文件
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Json missingServletRequestPartExceptionHandle(@NotNull MissingServletRequestPartException exception) {
        logException(exception);
        return responseError(PARAM_MISSING, String.format(
                "缺少文件 %s",
                Objects.requireNonNull(exception.getRequestPartName())
        ));
    }

    /**
     * 未提交必要参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Json missingServletRequestParameterExceptionHandle(@NotNull MissingServletRequestParameterException exception) {
        logException(exception);
        return responseError(PARAM_MISSING, String.format(
                "缺少参数 %s",
                Objects.requireNonNull(exception.getParameterName())
        ));
    }

    /**
     * 不支持的数据类型
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Json httpMediaTypeNotSupportedExceptionHandle(@NotNull HttpMediaTypeNotSupportedException exception) {
        logException(exception);
        return responseError(REQUEST_CONTENT_TYPE_UNSUPPORTED, String.format(
                "%s 不被支持，请使用JSON请求",
                Objects.requireNonNull(exception.getContentType()).getType()
        ));
    }

    /**
     * 数据库连接发生错误
     */
    @ExceptionHandler(CannotCreateTransactionException.class)
    public Json databaseExceptionHandle(@NotNull CannotCreateTransactionException exception) {
        logException(exception);
        return responseError(DATABASE_ERROR);
    }

    /**
     * Redis 连接发生错误
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public Json redisExceptionHandle(@NotNull RedisConnectionFailureException exception) {
        logException(exception);
        return responseError(REDIS_ERROR);
    }

    /**
     * 系统自定义异常
     */
    @ExceptionHandler(ServiceException.class)
    public Json systemExceptionHandle(@NotNull ServiceException exception) {
        logException(exception);
        return responseError(exception).setData(exception.getData());
    }

    /**
     * ⚠️ 这里原来有一个 {@code PropertyReferenceException}（Spring Data 派生查询的"属性不存在"）处理器，
     * 返回 {@code DATABASE_UNKNOWN_FIELD} + "数据库缺少字段 X"。
     *
     * <p>2026-09-18 已删除：那是 **JPA / Spring Data Repository 时代**的异常 —— 靠方法名派生查询时
     * 才会抛（`findByNameAndXxx` 里的属性写错）。本仓的持久化已经只剩 MyBatis-Plus，
     * 没有派生查询，这个处理器**永远不会被触发**；而"库表没有这个列/表"在 MP 下的真实形状是
     * `BadSqlGrammarException`（`InvalidDataAccessResourceUsageException` 的子类），
     * 已由下面 {@link #invalidDataAccessResourceUsageExceptionHandle} 统一接住。
     * 依赖侧的好处：不用为一行 catch 把 spring-data-commons 拉回 pom（`pnpm deps:check` 钉住）。</p>
     */

    /**
     * 数据表或字段异常
     */
    @ExceptionHandler(value = InvalidDataAccessResourceUsageException.class)
    public Json invalidDataAccessResourceUsageExceptionHandle(
            @NotNull InvalidDataAccessResourceUsageException exception
    ) {
        logException(exception);
        return responseError(DATABASE_TABLE_OR_FIELD_ERROR);
    }

    /**
     * 数据表或字段异常
     */
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public Json maxUploadSizeExceededExceptionHandle(@NotNull MaxUploadSizeExceededException exception) {
        logException(exception);
        return responseError(FORBIDDEN_UPLOAD_MAX_SIZE);
    }

    /**
     * 乐观锁冲突。
     *
     * <p><b>为什么抓的是 {@code org.springframework.dao.OptimisticLockingFailureException}
     * 而不是原来的 {@code org.springframework.orm.ObjectOptimisticLockingFailureException}</b>：
     * 后者住在 <b>spring-orm</b> 里（JPA/ORM 专属包），JPA 已于 2026-09-18 整体退场，
     * 再为一行 catch 把 spring-orm 拉回来是反向依赖；前者是它的父类，住在 spring-tx/spring-dao
     * 的异常族里，任何走 Spring 事务的持久化实现（含 MyBatis-Plus）都抛得出。
     * 行为等价、依赖更干净，`pnpm deps:check` 钉住"不许再 import org.springframework.orm"。</p>
     */
    @ExceptionHandler(value = OptimisticLockingFailureException.class)
    public Json optimisticLockingFailureExceptionHandle(@NotNull OptimisticLockingFailureException exception) {
        logException(exception);
        return responseError(DATABASE_CONCURRENT_ERROR);
    }

    /**
     * 其他异常
     */
    @ExceptionHandler(value = {Exception.class, RuntimeException.class})
    public Object otherExceptionHandle(@NotNull Exception exception) {
        logException(exception);
        return responseError(SERVICE_ERROR);
    }

    /**
     * 记录异常信息
     *
     * @param e 异常
     */
    private void logException(@NotNull Exception e) {
        if (e instanceof IException<?> serviceError) {
            log.error("[{}]{}", serviceError.getCode(), serviceError.getMessage(), e);
            return;
        }
        log.error(e.getMessage(), e);
    }

    /**
     * 返回错误信息
     *
     * @param serviceError 错误信息
     * @return Json
     */
    private Json responseError(IException<?> serviceError) {
        return responseError(serviceError, serviceError.getMessage());
    }

    /**
     * 返回错误信息
     *
     * @param serviceError 错误信息
     * @param message      错误信息
     * @return Json
     */
    private Json responseError(IException<?> serviceError, String message) {
        return Json.error(serviceError, message);
    }
}
