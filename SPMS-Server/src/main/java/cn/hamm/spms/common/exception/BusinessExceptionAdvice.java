package cn.hamm.spms.common.exception;

import cn.hamm.spms.framework.error.BusinessException;
import cn.hamm.spms.framework.http.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * <h1>项目业务异常的 HTTP 边界</h1>
 *
 * <p>必须先于旧框架的 Exception 兜底解析。沿用 HTTP 200 + 业务码，不擅自改成真实 401/403。
 * 只处理已自持的异常，不吞掉尚未迁移的框架/基础设施异常。</p>
 *
 * @author Hamm.cn
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ResponseStatus(HttpStatus.OK)
public class BusinessExceptionAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(BusinessExceptionAdvice.class);

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Object> handle(BusinessException failure) {
        LOG.error("[{}]{}", failure.getCode(), failure.getMessage(), failure);
        return ApiResponse.failure(failure);
    }
}
