package cn.hamm.spms.common.websocket;

import cn.hamm.airpower.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>审核记录项 3：WebSocket 一次性票据语义单测</h1>
 *
 * <p>WS 建连过去把完整 JWT 拼进 URL（会进访问日志/Referer）。票据方案的安全属性
 * 全部由本用例钉死：不可猜测、一次性兑现（重放必失败）、短 TTL 自动过期、
 * 空白令牌拒绝签发。</p>
 */
class WebSocketTicketServiceTest {

    @Test
    void 签发的票据必须可兑现出原令牌且不可猜测() {
        WebSocketTicketService service = new WebSocketTicketService();
        String ticket = service.issue("jwt-abc");
        // SecureRandom 24 字节 hex：长度固定、只含 hex 字符
        assertEquals(48, ticket.length());
        assertTrue(ticket.matches("^[0-9a-f]{48}$"));
        assertEquals("jwt-abc", service.redeem(ticket));
    }

    @Test
    void 兑现即销毁_重放必须落空() {
        WebSocketTicketService service = new WebSocketTicketService();
        String ticket = service.issue("jwt-abc");
        assertEquals("jwt-abc", service.redeem(ticket));
        // 重放同一票据：必须拿不到令牌（装饰器据此拒绝建连）
        assertNull(service.redeem(ticket));
    }

    @Test
    void 未知与空白票据必须返回空() {
        WebSocketTicketService service = new WebSocketTicketService();
        service.issue("jwt-abc");
        assertNull(service.redeem("deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
        assertNull(service.redeem(null));
        assertNull(service.redeem(""));
    }

    @Test
    void 过期票据必须作废() throws InterruptedException {
        WebSocketTicketService service = new WebSocketTicketService(20L);
        String ticket = service.issue("jwt-abc");
        Thread.sleep(40L);
        assertNull(service.redeem(ticket));
    }

    @Test
    void 空白令牌拒绝签发() {
        WebSocketTicketService service = new WebSocketTicketService();
        assertThrows(ServiceException.class, () -> service.issue(null));
        assertThrows(ServiceException.class, () -> service.issue("  "));
    }

    @Test
    void 并发兑现同一票据只能成功一次() throws InterruptedException {
        WebSocketTicketService service = new WebSocketTicketService();
        String ticket = service.issue("jwt-abc");
        int threads = 8;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger winners = new java.util.concurrent.atomic.AtomicInteger();
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (service.redeem(ticket) != null) {
                    winners.incrementAndGet();
                }
            });
            workers[i].start();
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join(2000);
        }
        // ConcurrentHashMap.remove 的原子性保证：无论多少并发，只有 1 个赢家
        assertEquals(1, winners.get());
    }
}
