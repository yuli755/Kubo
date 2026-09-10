package cn.hamm.spms.common.errorreport;

import cn.hamm.airpower.api.ApiController;
import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.curd.permission.Permission;
import cn.hamm.spms.common.security.RateLimitHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * <h1>前端错误上报端点</h1>
 *
 * <p>生产级可观测性闭环（本会话「生产级能力补全」第 3 项）：把前端
 * {@code errorHandler.ts} 预留的上报钩子真正接到后端——前端未捕获异常 / 主动上报的
 * 结构化错误经此端点落日志，由已配好的 Loki（promtail 采集）统一查询，不再停留在
 * 「只落浏览器 console」。</p>
 *
 * <h2>安全设计（未登录可访问的端点，必须防滥用）</h2>
 * <ul>
 *   <li><b>限流</b>：按客户端 IP 固定窗口计数（复用 {@link RateLimitHelper}），1 分钟内超过
 *       {@value #MAX_REPORTS_PER_MINUTE} 条即拒绝，防「错误风暴」把后端日志/采集打爆；</li>
 *   <li><b>脱敏</b>：{@code message}/{@code stack} 截断到 {@value #MAX_MESSAGE_LENGTH} 字符，
 *       并去除 URL 查询参数里的 token（accessToken/authorization），避免堆栈把令牌泄进日志；</li>
 *   <li><b>不落库</b>：仅落结构化日志（{@code log.error} + 字段 + traceId），不引入新表与迁移，
 *       由 Loki 承担查询/统计职责。</li>
 * </ul>
 *
 * <h2>Redis 不可用降级</h2>
 * <p>单机调试未配 Redis 时跳过限流（保持可上报），与防爆破守卫同口径；生产 Redis 是硬依赖，
 * 限流必然生效。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Api("error-report")
@Description("前端错误上报")
public class ErrorReportController extends ApiController {

    /** 单 IP 每分钟最多上报条数（固定窗口） */
    private static final int MAX_REPORTS_PER_MINUTE = 60;
    /** message / stack 落日志前截断长度（字符） */
    private static final int MAX_MESSAGE_LENGTH = 2000;
    /** 限流窗口（秒） */
    private static final int RATE_WINDOW_SECONDS = 60;
    /** 限流计数 key 前缀 */
    private static final String RATE_KEY_PREFIX = "kubo:error-report:rate:";

    @Autowired
    private RateLimitHelper rateLimitHelper;

    @Description("上报前端错误")
    @Permission(login = false)
    @PostMapping("")
    public Json report(@RequestBody FrontendErrorReport report, HttpServletRequest request) {
        if (!rateLimitHelper.allowByIp(request, RATE_KEY_PREFIX, MAX_REPORTS_PER_MINUTE, RATE_WINDOW_SECONDS)) {
            return Json.success("已限流");
        }
        // 脱敏后落结构化日志（traceId 由 logback 自动附加）
        log.error("[frontend-error] source={} context={} time={} message={} stack={}",
                report.getSource(),
                report.getContext(),
                report.getTime(),
                sanitize(report.getMessage()),
                sanitize(report.getStack()));
        return Json.success("上报成功");
    }

    /** 截断 + 去除 URL 中的 token 参数，避免堆栈/URL 把敏感信息泄进日志。 */
    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.length() > MAX_MESSAGE_LENGTH
                ? value.substring(0, MAX_MESSAGE_LENGTH) + "…(truncated)"
                : value;
        // 去除 accessToken / authorization 等查询参数与 Bearer 头（大小写不敏感）
        return trimmed
                .replaceAll("(?i)(accessToken|authorization|token)=[^&\\s\"']+", "$1=***")
                .replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._-]+", "$1***");
    }
}
