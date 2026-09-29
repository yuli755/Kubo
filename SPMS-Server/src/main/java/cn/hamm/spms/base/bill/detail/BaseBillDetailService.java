package cn.hamm.spms.base.bill.detail;

import cn.hamm.spms.framework.util.DecimalMath;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.bill.AbstractBaseBillEntity;
import cn.hamm.spms.base.bill.AbstractBaseBillService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN;

/**
 * <h1>单据明细 Service 基类</h1>
 *
 * @param <E> 明细实体
 * @param <R> 明细数据源
 * @author Hamm.cn
 */
@Slf4j
public class BaseBillDetailService<
        E extends BaseBillDetailEntity<E>,
        R extends BaseBillDetailRepository<E>
        > extends BaseService<E, R> {

    /**
     * 根据单据 ID 删除所有明细
     *
     * @param billId 单据 ID
     */
    public final void deleteAllByBillId(Long billId) {
        List<E> details = getAllByBillId(billId);
        details.forEach(detail -> repository.deleteById(detail.getId()));
    }

    /**
     * 查询指定单据的所有明细
     *
     * @param billId 单据 ID
     * @return 明细
     */
    public final List<E> getAllByBillId(Long billId) {
        return repository.getAllByBillId(billId);
    }

    /**
     * 保存指定单据的明细
     *
     * @param billId  单据 ID
     * @param details 明细
     */
    public final void saveDetails(long billId, @NotNull List<E> details) {
        deleteAllByBillId(billId);
        details.forEach(detail -> add(detail.setBillId(billId)));
    }

    /**
     * 添加完成数量
     *
     * <p>阶段 C-5：累计完成量不得超过单据数量。原实现只负责「够了就翻
     * isFinished」，对超出部分照单全收——实测 10 件的入库单可以一笔画进
     * 999 件库存。超发/超收要么是无输入法的误录，要么是绕开单据量的
     * 库存注入，两种都必须拦。</p>
     *
     * @param detailId 明细 ID
     * @param quantity 完成数量
     */
    public final void addFinishQuantity(long detailId, double quantity) {
        updateWithLock(detailId, detail -> {
            FORBIDDEN.when(detail.getIsFinished(), "该明细已标记完成，无法再添加明细完成数量");
            double finishQuantity = DecimalMath.add(detail.getFinishQuantity(), quantity);
            FORBIDDEN.when(finishQuantity > detail.getQuantity(), String.format(
                    "完成数量不得超过单据数量：累计 %s，单据数量 %s", finishQuantity, detail.getQuantity()));
            detail.setFinishQuantity(finishQuantity).setIsFinished(finishQuantity >= detail.getQuantity());
            log.info("添加完成数量:{} 是否完成:{}", finishQuantity, detail.getIsFinished());
        });
    }

    /**
     * 更新明细的数量（按 {@code detailFilter} 选中要消耗数量的明细行）
     *
     * <p>回调是 {@link Predicate} 而不是 {@code Consumer}：调用方要表达的是
     * 「<b>哪些行</b>参与本次数量消耗」（例如出库只反写物料匹配的销售明细），
     * 这是**行筛选**，不是校验。曾经这里收 {@code Consumer} 并 {@code try { accept } catch { continue }}，
     * 于是调用方只能靠**抛异常**来表达「这行不算」——异常被当成控制流吞掉，代价是
     * 真实的故障（NPE、懒加载失败、数据权限拒绝）也会被静默降级成「跳过这一行」，
     * 症状是数量莫名没有被反写、而日志里什么也没有。</p>
     *
     * <p>改成谓词后：返回 {@code false} = 跳过该行（正常业务分支），抛异常 = 真故障，照常冒泡。</p>
     *
     * @param billId       单据 ID
     * @param quantity     本次更新数量
     * @param billService  单据 Service
     * @param detailFilter 明细行筛选谓词：{@code true} 表示该行参与本次数量消耗
     */
    public <B extends AbstractBaseBillEntity<B, ?>, BS extends AbstractBaseBillService<B, ?, ?, ?, ?>> void updateDetailQuantity(
            long billId,
            double quantity,
            @NotNull BS billService,
            @NotNull Predicate<E> detailFilter
    ) {
        List<E> details = getAllByBillId(billId);
        for (E detail : details) {
            if (quantity <= 0) {
                break;
            }
            if (!detailFilter.test(detail)) {
                continue;
            }
            if (detail.getIsFinished()) {
                continue;
            }

            // 还需要完成的数量
            double currentFinish = detail.getFinishQuantity() == null ? 0D : detail.getFinishQuantity();
            double detailNeedQuantity = DecimalMath.subtract(detail.getQuantity(), currentFinish);
            if (quantity < detailNeedQuantity) {
                // 添加单据完成数量
                detail.setFinishQuantity(DecimalMath.add(currentFinish, quantity));
                quantity = 0;
            } else {
                quantity = DecimalMath.subtract(quantity, detailNeedQuantity);
                detail.setFinishQuantity(detail.getQuantity()).setIsFinished(true);
            }
            updateToDatabase(detail);
        }
        // 判断所有明细是否完成
        details = getAllByBillId(billId);
        boolean isAllFinished = details.stream().allMatch(BaseBillDetailEntity::getIsFinished);
        if (isAllFinished) {
            // 明细已全部完成
            billService.setBillDetailsAllFinished(billId);
        }
    }
}
