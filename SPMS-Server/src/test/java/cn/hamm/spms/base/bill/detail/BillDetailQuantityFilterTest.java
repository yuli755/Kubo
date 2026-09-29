package cn.hamm.spms.base.bill.detail;

import cn.hamm.spms.base.bill.AbstractBaseBillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

/**
 * <h1>{@code updateDetailQuantity} 的行筛选语义</h1>
 *
 * <p>这个方法过去收 {@code Consumer<E> detailCheck}，内部 {@code try { accept } catch (Exception) { continue }}，
 * 于是调用方只能靠**抛异常**表达「这一行不参与」—— 异常成了控制流。代价是真实的故障
 * （NPE、懒加载失败、数据权限拒绝）也被静默降级成「跳过这一行」：数量莫名没有被反写，日志里什么都没有。
 * 现在收 {@link Predicate}，返回 {@code false} = 跳过（正常业务分支），抛异常 = 真故障，照常冒泡。</p>
 *
 * <p>这三条断言就是那次改造的**全部行为契约**，任何一条回归都意味着又有人把异常当控制流用。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("BaseBillDetailService · 明细数量反写的行筛选")
class BillDetailQuantityFilterTest {

    private static final long BILL_ID = 1L;

    /** 测试用明细：只实现基类要求的四个抽象方法 */
    static class TestDetail extends BaseBillDetailEntity<TestDetail> {
        private Double quantity;
        private Double finishQuantity;
        private Long materialId;

        TestDetail(Long materialId, Double quantity) {
            this.materialId = materialId;
            this.quantity = quantity;
            this.finishQuantity = 0D;
            // 必须给不同的 id：BaseEntity 的 @EqualsAndHashCode 按字段比较，两行 id 都是 null 时
            // 它们**互相相等**，Mockito 的 verify(..., never()) 会把另一行的调用也算成命中。
            setId(materialId);
            setIsFinished(false);
            setBillId(BILL_ID);
        }

        @Override
        public Double getQuantity() {
            return quantity;
        }

        @Override
        public TestDetail setQuantity(Double quantity) {
            this.quantity = quantity;
            return this;
        }

        @Override
        public Double getFinishQuantity() {
            return finishQuantity;
        }

        @Override
        public TestDetail setFinishQuantity(Double finishQuantity) {
            this.finishQuantity = finishQuantity;
            return this;
        }

        Long getMaterialId() {
            return materialId;
        }
    }

    @SuppressWarnings("unchecked")
    private BaseBillDetailService<TestDetail, BaseBillDetailRepository<TestDetail>> serviceWith(List<TestDetail> details) {
        BaseBillDetailService<TestDetail, BaseBillDetailRepository<TestDetail>> service =
                spy(new BaseBillDetailService<TestDetail, BaseBillDetailRepository<TestDetail>>() {
                });
        doReturn(details).when(service).getAllByBillId(BILL_ID);
        doNothing().when(service).updateToDatabase(any());
        return service;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private AbstractBaseBillService billService() {
        return mock(AbstractBaseBillService.class);
    }

    @Test
    @DisplayName("谓词返回 false 的行被跳过，不写库、不消耗数量")
    void falseFilterSkipsRow() {
        TestDetail matched = new TestDetail(11L, 5D);
        TestDetail unmatched = new TestDetail(22L, 5D);
        BaseBillDetailService<TestDetail, BaseBillDetailRepository<TestDetail>> service =
                serviceWith(List.of(unmatched, matched));

        service.updateDetailQuantity(BILL_ID, 3D, billService(),
                detail -> Objects.equals(detail.getMaterialId(), 11L));

        assertEquals(3D, matched.getFinishQuantity(), "匹配行应当消耗本次数量");
        assertEquals(0D, unmatched.getFinishQuantity(), "不匹配行不该被动过");
        verify(service).updateToDatabase(matched);
        verify(service, never()).updateToDatabase(unmatched);
    }

    @Test
    @DisplayName("谓词抛出的异常必须冒泡（不再被 catch 成「跳过这一行」）")
    void filterExceptionPropagates() {
        TestDetail detail = new TestDetail(11L, 5D);
        BaseBillDetailService<TestDetail, BaseBillDetailRepository<TestDetail>> service =
                serviceWith(List.of(detail));
        Predicate<TestDetail> exploding = ignored -> {
            throw new IllegalStateException("数据权限拒绝 / 懒加载失败 / NPE 都长这样");
        };

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.updateDetailQuantity(BILL_ID, 3D, billService(), exploding));
        assertTrue(error.getMessage().contains("数据权限拒绝"), "应当是回调自己的异常，而不是被吞掉后无事发生");
        verify(service, never()).updateToDatabase(any());
    }

    @Test
    @DisplayName("数量按行依次消耗，全部完成后回写单据状态")
    void quantityIsConsumedAcrossRowsAndBillIsMarkedFinished() {
        TestDetail first = new TestDetail(11L, 4D);
        TestDetail second = new TestDetail(11L, 4D);
        BaseBillDetailService<TestDetail, BaseBillDetailRepository<TestDetail>> service =
                serviceWith(List.of(first, second));
        @SuppressWarnings("rawtypes")
        AbstractBaseBillService billService = billService();

        // 第一次：只够完成第一行
        service.updateDetailQuantity(BILL_ID, 4D, billService, detail -> true);
        assertEquals(4D, first.getFinishQuantity());
        assertTrue(first.getIsFinished(), "数量补足后该行应标记完成");
        assertEquals(0D, second.getFinishQuantity(), "数量耗尽后不该继续消耗下一行");

        // 第二次：完成第二行 → 全部完成 → 回写单据
        doReturn(List.of(first, second)).when(service).getAllByBillId(BILL_ID);
        second.setFinishQuantity(4D).setIsFinished(true);
        service.updateDetailQuantity(BILL_ID, 0D, billService, detail -> true);
        verify(billService).setBillDetailsAllFinished(BILL_ID);
    }

    @Test
    @DisplayName("依赖注入失败这类真故障也必须冒泡，不能被当成「这行不算」")
    void infrastructureFailurePropagates() {
        BaseBillDetailService<TestDetail, BaseBillDetailRepository<TestDetail>> service =
                serviceWith(List.of(new TestDetail(11L, 5D)));
        doThrow(new RuntimeException("repository 未注入")).when(service).updateToDatabase(any());

        assertThrows(RuntimeException.class,
                () -> service.updateDetailQuantity(BILL_ID, 3D, billService(), detail -> true),
                "写库失败必须让调用方知道，静默跳过会导致数量对不上账");
    }
}
