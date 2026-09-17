package cn.hamm.spms.common.exception;

import cn.hamm.spms.core.Json;
import cn.hamm.spms.core.exception.ServiceException;
import cn.hamm.spms.core.interfaces.IException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * <h1>本仓自有 ServiceException 的响应渲染（后端 airpower 退场 B2）</h1>
 *
 * <p>2026-09-18 起 {@link ServiceException} 与 {@link Json} 都是本仓自己的类，airpower 的
 * advice 只认它自己的 {@code cn.hamm.spms.core.exception.ServiceException}——
 * <b>本仓抛出的异常不再有人管</b>：会被 airpower 的兜底
 * {@code @ExceptionHandler({Exception.class, RuntimeException.class})} 接走，前端收到 500 而不是业务码。
 * 这个 advice 就是补上那一环。</p>
 *
 * <p>渲染形态与框架 advice 的 {@code systemExceptionHandle} **逐行等价**（HTTP 200 + 业务码 body），
 * 因为前端 axios 拦截器按 {@code code} 分流，形态变了等于换了契约：
 * {@code Json.error(exception, message).setData(exception.getData())}。</p>
 *
 * <p><b>为什么 {@code @Order(HIGHEST_PRECEDENCE)} 是必需的</b>：airpower 的
 * {@code ExceptionInterceptor} 带一个 {@code Exception}/{@code RuntimeException} 的兜底处理器，
 * 而它没有声明 order（即 LOWEST_PRECEDENCE）。Spring 解析异常时**按 advice 顺序取第一个命中**，
 * 排在前面才能保证"本仓异常 → 本仓 advice"。同目录的
 * {@link DataIntegrityViolationAdvice} 用的是同一招（那次是为了抢在 4034 误报之前）。</p>
 *
 * @author Hamm.cn
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ResponseStatus(HttpStatus.OK)
@ResponseBody
@Slf4j
public class ServiceExceptionAdvice {

    /**
     * 渲染本仓 ServiceException（业务错误码 + 文案 + 可选 data）。
     *
     * @param exception 业务异常
     * @return 与框架一致的业务错误响应
     */
    @ExceptionHandler(ServiceException.class)
    public Json systemExceptionHandle(@NotNull ServiceException exception) {
        logException(exception);
        return Json.error(exception, exception.getMessage()).setData(exception.getData());
    }

    /**
     * 记日志：是错误码就带上 code（与框架 {@code logException} 一致），否则只记消息。
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
}
