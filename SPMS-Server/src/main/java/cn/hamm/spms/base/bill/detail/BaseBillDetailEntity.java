package cn.hamm.spms.base.bill.detail;

import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.ReadOnly;
import cn.hamm.spms.base.TenantAwareEntity;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * <h1>单据明细基类</h1>
 *
 * @param <E> 明细实体
 * @author Hamm.cn
 */
@SuppressWarnings({"unchecked", "UnusedReturnValue"})
@Getter
@Description("")
public abstract class BaseBillDetailEntity<
        E extends BaseBillDetailEntity<E>
        > extends TenantAwareEntity<E> implements IBaseBillDetailAction {
    @Description("单据 ID")
    private Long billId;

    @Description("是否已完成")
    @ReadOnly
    private Boolean isFinished;

    /**
     * 设置是否已完成
     *
     * @param isFinished 是否已完成
     * @return 单据明细
     */
    public E setIsFinished(Boolean isFinished) {
        this.isFinished = isFinished;
        return (E) this;
    }

    /**
     * 设置单据 ID
     *
     * @param billId 单据 ID
     * @return 明细实体
     */
    public E setBillId(Long billId) {
        this.billId = billId;
        return (E) this;
    }

    /**
     * 获取数量
     *
     * @return 数量
     */
    @NotNull(groups = {WhenAddFinish.class}, message = "数量不能为空")
    public abstract Double getQuantity();

    /**
     * 设置数量
     *
     * @param quantity 数量
     * @return 明细实体
     */
    public abstract E setQuantity(Double quantity);

    /**
     * 获取已完成数量
     *
     * @return 数量
     */
    public abstract Double getFinishQuantity();

    /**
     * 设置已完成数量
     *
     * @param finishQuantity 已完成数量
     * @return 明细实体
     */
    public abstract E setFinishQuantity(Double finishQuantity);
}
