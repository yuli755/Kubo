package cn.hamm.spms.common.websocket;

import cn.hamm.airpower.api.ApiController;
import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.curd.permission.Permission;
import cn.hamm.spms.common.security.RateLimitHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

import static cn.hamm.airpower.exception.Errors.UNAUTHORIZED;

/**
 * <h1>WebSocket 一次性连接票据签发端点</h1>
 *
 * <p>审核记录项 3：WS 建连过去把完整 JWT 拼进 URL（{@code ?accessToken=}），令牌会进
 * 访问日志 / Referer。本端点提供「头进 URL 出」的交换：令牌继续走 Authorization 头，
 * URL 里只出现 30 秒即焚、一次性兑现的随机票据（兑现逻辑见
 * {@link WebSocketTicketResolvingDecorator}）。</p>
 *
 * <p>鉴权口径：必须登录（令牌已由 {@code RequestInterceptor} 验签 + 吊销名单核验），
 * 但不要求任何业务权限——票据只能兑现出调用者本人的令牌，签给谁连接就是谁，无越权面。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Api("websocket-ticket")
@Description("WebSocket 连接票据")
public class WebSocketTicketController extends ApiController {

    /**
     * 单 IP 每分钟签发上限。前端只在连接/重连时各取一张，正常用量远低于该值
     */
    private static final int MAX_ISSUE_PER_MINUTE = 30;

    /**
     * 限流窗口（秒）
     */
    private static final int RATE_WINDOW_SECONDS = 60;

    /**
     * 限流计数 key 前缀
     */
    private static final String RATE_KEY_PREFIX = "kubo:ws-ticket:rate:";

    @Autowired
    private WebSocketTicketService webSocketTicketService;

    @Autowired
    private RateLimitHelper rateLimitHelper;

    @Description("换取一次性 WebSocket 连接票据")
    @Permission(login = true, authorize = false)
    @PostMapping("")
    public Json issue(HttpServletRequest request) {
        if (!rateLimitHelper.allowByIp(request, RATE_KEY_PREFIX, MAX_ISSUE_PER_MINUTE, RATE_WINDOW_SECONDS)) {
            log.warn("WebSocket 票据签发被限流: ip={}", request.getRemoteAddr());
            return Json.error("操作过于频繁，请稍后重试", 429);
        }
        String header = request.getHeader("Authorization");
        String accessToken = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : header;
        UNAUTHORIZED.when(!StringUtils.hasText(accessToken), "缺少访问令牌");
        String ticket = webSocketTicketService.issue(accessToken);
        return Json.data(Map.of(WebSocketTicketService.PARAM_NAME, ticket), "ok");
    }
}
