package cn.hamm.spms.base.bill;

import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import cn.hamm.spms.module.wms.input.InputEntity;
import cn.hamm.spms.module.wms.input.InputService;
import cn.hamm.spms.module.wms.input.detail.InputDetailService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * <h1>单据创建后置钩子的同步/异常边界</h1>
 *
 * <p>走真实 afterAppAdd 实现，不造另一个任务工具自测。需要完整 Maven 测试依赖；
 * 这里只隔离数据库读写，不能拿独立 enum runner 的结果冒充本类已执行。</p>
 *
 * @author Hamm.cn
 */
class BillAfterAddHookTest {
    private static final long BILL_ID = 17L;

    @Test
    void hookRunsOnceOnTheCallingThreadAfterDetailsSaved() {
        AtomicReference<Thread> thread = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean detailsSaved = new AtomicBoolean();
        HookProbe service = probe(() -> {
            assertTrue(detailsSaved.get(), "钩子必须在详情保存后执行");
            thread.set(Thread.currentThread());
            calls.incrementAndGet();
        });
        doAnswer(invocation -> {
            detailsSaved.set(true);
            return null;
        }).when(service.detailService).saveDetails(BILL_ID, List.of());
        service.invoke(source());
        assertSame(Thread.currentThread(), thread.get());
        assertEquals(1, calls.get());
        assertEquals(BILL_ID, service.callbackId);
        verify(service.detailService).saveDetails(BILL_ID, List.of());
    }

    @Test
    void ordinaryHookExceptionsKeepTheExistingBestEffortBoundary() {
        HookProbe service = probe(() -> {
            throw new IllegalStateException("after-add hook failed");
        });
        assertDoesNotThrow(() -> service.invoke(source()));
    }

    @Test
    void fatalErrorsAreNotSwallowed() {
        AssertionError failure = new AssertionError("fatal hook failure");
        HookProbe service = probe(() -> { throw failure; });
        assertSame(failure, assertThrows(AssertionError.class, () -> service.invoke(source())));
    }

    @Test
    void databaseReadErrorsAreNotPartOfTheBestEffortHook() {
        AtomicInteger calls = new AtomicInteger();
        HookProbe service = probe(calls::incrementAndGet);
        IllegalStateException failure = new IllegalStateException("read failed");
        doThrow(failure).when(service).get(BILL_ID);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.invoke(source())));
        assertEquals(0, calls.get());
    }

    @Test
    void detailWriteErrorsAreNotSwallowed() {
        AtomicInteger calls = new AtomicInteger();
        HookProbe service = probe(calls::incrementAndGet);
        IllegalStateException failure = new IllegalStateException("details failed");
        doThrow(failure).when(service.detailService).saveDetails(BILL_ID, List.of());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.invoke(source())));
        assertEquals(0, calls.get());
    }

    private static HookProbe probe(Runnable callback) {
        HookProbe service = spy(new HookProbe(callback));
        service.detailService = mock(InputDetailService.class);
        // get(long) 是第三方 final 方法，Mockito 5 的 inline mock 在完整 Maven 测试环境里拦截它。
        doReturn(source()).when(service).get(BILL_ID);
        return service;
    }

    private static InputEntity source() {
        InputEntity source = new InputEntity();
        source.setId(BILL_ID);
        source.setDetails(List.of());
        return source;
    }

    private static class HookProbe extends InputService {
        private final Runnable callback;
        private long callbackId;

        HookProbe(Runnable callback) {
            this.callback = callback;
        }

        void invoke(InputEntity source) {
            super.afterAppAdd(BILL_ID, source);
        }

        @Override
        protected ConfigFlag getAutoAuditConfigFlag() {
            return null;
        }

        @Override
        protected void afterDetailSaved(long billId) {
            // 本测试不涉及数据库衍生更新；详情保存本身仍走 mock 并断言错误会传播。
        }

        @Override
        protected void afterBillAdd(long billId) {
            callbackId = billId;
            callback.run();
        }
    }
}
