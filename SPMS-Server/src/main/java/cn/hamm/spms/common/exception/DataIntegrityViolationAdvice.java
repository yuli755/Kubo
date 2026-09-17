package cn.hamm.spms.common.exception;

import cn.hamm.airpower.core.Json;
import cn.hamm.spms.common.exception.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <h1>数据库约束错误精确映射（阶段 F-2）</h1>
 *
 * <p>框架 advice（airpower-curd {@code ExceptionInterceptor#deleteUsingDataException}）把
 * {@link DataIntegrityViolationException}/{@link SQLIntegrityConstraintViolationException}
 * 一律报成 4034「删除失败，数据正在使用中」——唯一键冲突（本该是「数据已存在」）、
 * 字段超长、非空缺失全被说成「正在使用中」，排查方向被带偏（阶段 A-3 的 4034 误报
 * 即源于此）。MySQL 约束冲突全部塌缩到同一异常类型，唯一可靠的信息在消息文本里，
 * 本 advice 在最高优先级截获这两类异常、按消息分流语义。</p>
 *
 * <p>响应形态与框架 advice 完全一致：HTTP 200 + 业务错误码 body（前端拦截器按
 * code 处理，不能真返回 4xx/5xx）。</p>
 *
 * @author Hamm.cn
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ResponseStatus(HttpStatus.OK)
public class DataIntegrityViolationAdvice {

    private static final Logger log = LoggerFactory.getLogger(DataIntegrityViolationAdvice.class);

    /** MySQL：Duplicate entry 'VALUE' for key 'KEY'（8.0.19+ 为 'TABLE.KEY'） */
    private static final Pattern DUPLICATE_ENTRY = Pattern.compile("Duplicate entry '(.*)' for key '([^']+)'");
    /** MySQL：Data too long for column 'COL' ... */
    private static final Pattern DATA_TOO_LONG = Pattern.compile("Data too long for column '([^']+)'");
    /** MySQL：Column 'COL' cannot be null */
    private static final Pattern NOT_NULL = Pattern.compile("Column '([^']+)' cannot be null");

    /**
     * 截获约束类异常（比框架 advice 更精确的语义映射）。
     *
     * @param ex 约束异常（Spring 包装或驱动原生）
     * @return 语义化业务错误响应
     */
    @ExceptionHandler({DataIntegrityViolationException.class, SQLIntegrityConstraintViolationException.class})
    public Json handle(Exception ex) {
        String message = rootMessage(ex);
        log.warn("数据库约束冲突：{}", message);

        Matcher duplicate = DUPLICATE_ENTRY.matcher(message);
        if (duplicate.find()) {
            return Json.error(Errors.FORBIDDEN_EXIST,
                    String.format("数据已存在（唯一约束冲突）：%s，请检查是否重复提交", duplicate.group(1)));
        }
        // 子表引用了不存在的父行：新增/更新侧的外键失败
        if (message.contains("Cannot add or update a child row")) {
            return Json.error(Errors.FORBIDDEN, "关联数据不存在或已被删除，无法保存");
        }
        // 父行被子表引用：这才是「数据正在使用中」的正当场景
        if (message.contains("Cannot delete or update a parent row")) {
            return Json.error(Errors.FORBIDDEN_DELETE_USED, "删除失败：该数据正被其他数据引用，请先处理关联数据");
        }
        Matcher tooLong = DATA_TOO_LONG.matcher(message);
        if (tooLong.find()) {
            return Json.error(Errors.PARAM_INVALID, String.format("字段「%s」内容超出长度限制", tooLong.group(1)));
        }
        Matcher notNull = NOT_NULL.matcher(message);
        if (notNull.find()) {
            return Json.error(Errors.PARAM_INVALID, String.format("字段「%s」为必填项，不能为空", notNull.group(1)));
        }
        // 兜底给通用文案，但绝不回退到「正在使用中」的误导性描述；完整堆栈只进日志。
        // 用 PARAM_INVALID 而非 DATABASE_ERROR（后者文案是「数据库服务连接失败」，
        // 约束冲突几乎总是提交内容问题，不是服务故障）。
        return Json.error(Errors.PARAM_INVALID, "数据保存失败：提交内容与数据库约束冲突，请检查后重试");
    }

    /**
     * 沿 cause 链收集全部消息（Spring 包装层与驱动原生层的消息拼接后做文本判别）。
     *
     * @param ex 顶层异常
     * @return 拼接后的完整消息
     */
    private static String rootMessage(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        for (Throwable current = ex; current != null && current != current.getCause(); current = current.getCause()) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(' ');
            }
        }
        return sb.toString();
    }
}
