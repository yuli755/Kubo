package cn.hamm.spms.common.websocket;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <h1>WebSocket 票据兑现装饰器（审核记录项 3）</h1>
 *
 * <p>airpower 的 {@code WebSocketHandler#afterConnectionEstablished} 是 final：
 * 它把 {@code session.getUri().getQuery()} 整串当作裸令牌交给
 * {@code AccessTokenUtil.verify}（不存在 {@code accessToken=} 参数名——旧前端
 * 按参数名拼接因此从未握手成功，本轮复检实测 1011 关闭即此缺陷）。
 * 与其 fork 框架，不如在注册边缘套一层装饰器：请求携带 {@code ?ticket=} 时，
 * 先向 {@link WebSocketTicketService} 一次性兑现出真实令牌，再把会话的
 * {@code getUri()} 改写为「query = 裸令牌」后交给框架——框架鉴权链路
 * 按原契约工作，公开 URL 上却只出现不可重放的短命票据。</p>
 *
 * <p>不带 {@code ticket} 的连接按「query 即裸令牌」的框架原生契约处理。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
public class WebSocketTicketResolvingDecorator extends WebSocketHandlerDecorator {

    /**
     * 握手线程池：框架在 support=REDIS 时于 afterConnectionEstablished 内直接调用
     * {@code JedisConnection.subscribe}——Jedis 的订阅是阻塞式的（Lettuce 才是注册即返回），
     * 在 Tomcat 分发线程上执行会让握手永不返回，连接虽已建立但后续消息（含 PING/PONG）
     * 全部积压，实测表现为「握手 101 成功、PONG 永不到达」（jstack 证实卡在
     * JedisConnection.subscribe）。框架 jar 不可改，就把整段建连逻辑挪到专用线程：
     * 每条连接占用一个 worker，close 时框架会 close 订阅连接，worker 随之释放。
     */
    private static final ExecutorService HANDSHAKE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "ws-handshake");
        thread.setDaemon(true);
        return thread;
    });

    private final WebSocketTicketService ticketService;

    public WebSocketTicketResolvingDecorator(WebSocketHandler delegate, WebSocketTicketService ticketService) {
        super(delegate);
        this.ticketService = ticketService;
    }

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        String ticket = extractTicket(uri);
        if (ticket == null) {
            // 旧版客户端：仍按「query 即裸令牌」直连（兼容窗口），新前端一律走票据
            handoff(session);
            return;
        }
        String accessToken = ticketService.redeem(ticket);
        if (accessToken == null) {
            // 不存在 / 已兑现过 / 已过期：不回显原因细节，直接关闭
            log.warn("WebSocket 票据兑现失败（未知/重放/过期），关闭连接");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid ticket"));
            return;
        }
        // 框架契约（反编译 AccessTokenUtil.verify 确认）：整个 query 串就是裸令牌
        // （base64url(expireAt.hmac.payload) 形态），不存在 `accessToken=` 参数名。
        // 手工拼接而不用 UriComponentsBuilder：令牌尾部的 base64url `=` 填充在
        // RFC3986 的 query 里合法（sub-delims），但 Spring 会把 query 按
        // name=value 对校验，`=` 触发 IllegalArgumentException（实测 1011 关闭）。
        String raw = uri.toString();
        int mark = raw.indexOf('?');
        URI rewritten = URI.create((mark >= 0 ? raw.substring(0, mark) : raw) + "?" + accessToken);
        handoff(new QueryRewritingSession(session, rewritten));
    }

    /**
     * 把框架建连逻辑（含 Jedis 阻塞订阅）交给专用线程，立刻释放 Tomcat 分发线程，
     * 否则后续消息（PING/PONG）永远排不到分发。异常语义与框架原链路对齐：
     * 记日志并以 1011 关闭。
     */
    private void handoff(WebSocketSession session) {
        HANDSHAKE_EXECUTOR.execute(() -> {
            try {
                super.afterConnectionEstablished(session);
            } catch (Exception e) {
                log.error("WebSocket 建连处理失败: {}", e.getMessage());
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (Exception ignored) {
                    // 已经断了就不用再关一次
                }
            }
        });
    }

    /**
     * 从查询串里取 ticket 参数（手写解析：建连 URI 不保证是合法编码形态，
     * UriComponentsBuilder.parse 对坏字符会抛异常，这里宁可返回 null 走原链路）
     */
    private static String extractTicket(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String pair : uri.getQuery().split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (WebSocketTicketService.PARAM_NAME.equals(pair.substring(0, eq))) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    /**
     * 只改写 {@code getUri()} 的会话包装：其余行为（收发/属性/关闭）原样委托。
     * 框架在 {@code afterConnectionEstablished} 之后只把 session 当作发送通道使用，
     * 改写后的 URI 仅影响建连期的令牌解析。
     */
    private static class QueryRewritingSession extends WebSocketSessionDecorator {

        private final URI uri;

        QueryRewritingSession(WebSocketSession delegate, URI uri) {
            super(delegate);
            this.uri = uri;
        }

        @Override
        public URI getUri() {
            return uri;
        }
    }
}
