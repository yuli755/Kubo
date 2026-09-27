package cn.hamm.spms.common.websocket;

import cn.hamm.airpower.core.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>WebSocket 一次性连接票据（审核记录项 3 的收口）</h1>
 *
 * <p>背景：浏览器原生 WebSocket 无法携带自定义请求头，airpower 的
 * {@code WebSocketHandler#afterConnectionEstablished}（final，不可覆写）直接把
 * {@code session.getUri().getQuery()} 整串交给 {@code AccessTokenUtil.verify}，
 * 于是历史实现的 WS 建连是 {@code /websocket?accessToken=<JWT>} —— 完整令牌会进
 * 网关/nginx 访问日志、代理 Referer 与浏览器历史，等于第二个令牌存储点。</p>
 *
 * <p>本服务把「长期有效的 JWT」换成「短期一次性票据」：客户端经已鉴权的
 * {@code POST /websocket-ticket}（Authorization 头，不落 URL）换取 24 字节随机票据，
 * 建连时以 {@code ?ticket=<opaque>} 携带；{@link WebSocketTicketResolvingDecorator}
 * 在容器边缘一次性兑现成真实令牌后替换查询串，框架内部鉴权链路零改动。</p>
 *
 * <p>安全属性：票据不可猜测（SecureRandom）、默认 30 秒过期、读出即失效（remove 原子兑现），
 * 泄进日志的票据既换不回令牌也重放不了。存储为进程内存——多实例部署需把票据放进共享存储
 * （Redis），或以 sticky session 保证建连与签发同节点。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class WebSocketTicketService {

    /**
     * 票据查询参数名
     */
    public static final String PARAM_NAME = "ticket";

    /**
     * 票据有效期（毫秒）。签发与建连在同一用户操作内完成，30 秒已覆盖弱网重传，
     * 再长只是扩大被日志留存后的可利用窗口。
     */
    private static final long DEFAULT_TTL_MILLIS = 30_000L;

    /**
     * 在途票据上限。防止刷端点造成内存无界增长；到限直接拒绝新签发
     * （正常前端只在连接/重连时各取一张，远达不到该量级）。
     */
    private static final int MAX_LIVE_TICKETS = 10_000;

    /**
     * 票据字节数（hex 编码后 48 字符）
     */
    private static final int TICKET_BYTES = 24;

    private final SecureRandom secureRandom = new SecureRandom();

    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();

    private final long ttlMillis;

    public WebSocketTicketService() {
        this(DEFAULT_TTL_MILLIS);
    }

    /**
     * 仅供测试：注入自定义有效期以验证过期语义
     */
    WebSocketTicketService(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    private record Ticket(String accessToken, long expireAt) {
    }

    /**
     * 为一张已鉴权请求签发一次性票据
     *
     * @param accessToken 客户端提交的裸访问令牌（已剥 {@code Bearer } 前缀）
     * @return 票据（URL 安全的 hex 串）
     */
    public String issue(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new ServiceException("缺少访问令牌");
        }
        purgeExpired();
        if (tickets.size() >= MAX_LIVE_TICKETS) {
            log.warn("WebSocket 票据在途数量达到上限 {}，拒绝新签发", MAX_LIVE_TICKETS);
            throw new ServiceException("系统繁忙，请稍后重试");
        }
        byte[] buffer = new byte[TICKET_BYTES];
        secureRandom.nextBytes(buffer);
        String ticket = HexFormat.of().formatHex(buffer);
        tickets.put(ticket, new Ticket(accessToken, System.currentTimeMillis() + ttlMillis));
        return ticket;
    }

    /**
     * 兑现票据：原子取出并删除（重放必然落空）
     *
     * @param ticket 票据
     * @return 签发时记录的访问令牌；票据不存在 / 已用过 / 已过期返回 {@code null}
     */
    public String redeem(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        Ticket entry = tickets.remove(ticket);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            return null;
        }
        return entry.accessToken();
    }

    /**
     * 当前在途票据数（仅供测试与监控）
     */
    public int liveCount() {
        purgeExpired();
        return tickets.size();
    }

    /**
     * 清空全部票据（仅供测试；登出吊销不依赖这里——票据只映射令牌，
     * 令牌本身的吊销仍由 {@code AccessTokenRevocationService} 在兑现后由框架校验兜底）
     */
    public void clearForTest() {
        tickets.clear();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(entry -> entry.getValue().expireAt() < now);
    }
}
