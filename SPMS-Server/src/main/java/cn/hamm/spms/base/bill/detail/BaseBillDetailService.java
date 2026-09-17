package cn.hamm.spms.base.bill.detail;

import cn.hamm.airpower.core.NumberUtil;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.bill.AbstractBaseBillEntity;
import cn.hamm.spms.base.bill.AbstractBaseBillService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

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
            double finishQuantity = NumberUtil.add(detail.getFinishQuantity(), quantity);
            FORBIDDEN.when(finishQuantity > detail.getQuantity(), String.format(
                    "完成数量不得超过单据数量：累计 %s，单据数量 %s", finishQuantity, detail.getQuantity()));
            detail.setFinishQuantity(finishQuantity).setIsFinished(finishQuantity >= detail.getQuantity());
            log.info("添加完成数量:{} 是否完成:{}", finishQuantity, detail.getIsFinished());
        });
    }

    /**
     * 更新明细的数量
     *
     * @param billId      单据 ID
     * @param quantity    本次更新数量
     * @param billService 单据 Service
     * @param detailCheck 明细检查函数
     */
    public <B extends AbstractBaseBillEntity<B, ?>, BS extends AbstractBaseBillService<B, ?, ?, ?, ?>> void updateDetailQuantity(
            long billId,
            double quantity,
            @NotNull BS billService,
            Consumer<E> detailCheck
    ) {
        List<E> details = getAllByBillId(billId);
        for (E detail : details) {
            if (quantity <= 0) {
                break;
            }
            try {
                detailCheck.accept(detail);
            } catch (Exception e) {
                continue;
            }
            if (detail.getIsFinished()) {
                continue;
            }

            // 还需要完成的数量
            double detailNeedQuantity = NumberUtil.subtract(detail.getQuantity(), detail.getFinishQuantity());
            if (quantity < detailNeedQuantity) {
                // 添加单据完成数量
                detail.setFinishQuantity(quantity);
            } else {
                quantity = NumberUtil.subtract(quantity, detailNeedQuantity);
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
