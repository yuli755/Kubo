package cn.hamm.spms.base.bill;

import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.base.bill.detail.BaseBillDetailEntity;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>单据实体基类</h1>
 *
 * @param <E> 单据实体
 * @param <D> 明细实体
 * @author Hamm.cn
 */
@Getter
@Description("")
public abstract class AbstractBaseBillEntity<
        E extends AbstractBaseBillEntity<E, D>,
        D extends BaseBillDetailEntity<D>
        > extends TenantAwareEntity<E> implements IBaseBillAction {
    @Description("单据明细")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "单据明细不能为空")
    private List<D> details = new ArrayList<>();

    @Description("驳回原因")
    @NotNull(groups = {WhenReject.class}, message = "驳回原因")
    @Length(max = 80, message = "驳回原因仅支持{max}个字符")
    private String rejectReason;

    /**
     * 获取单据编号
     *
     * @return 单据编号
     */
    public abstract String getBillCode();

    /**
     * 设置驳回原因
     *
     * @param rejectReason 驳回原因
     * @return 单据实体
     */
    @SuppressWarnings("UnusedReturnValue")
    public E setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
        //noinspection unchecked
        return (E) this;
    }

    /**
     * 设置单据明细
     *
     * @param details 明细
     * @return 单据实体
     */
    public E setDetails(List<D> details) {
        this.details = details;
        //noinspection unchecked
        return (E) this;
    }

    /**
     * 获取状态
     *
     * @return 状态
     */
    public abstract Integer getStatus();

    /**
     * 设置状态
     *
     * @param status 状态值
     * @return 单据实体
     */
    public abstract E setStatus(Integer status);
}
