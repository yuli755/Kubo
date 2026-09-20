package cn.hamm.spms.base.bill;

import cn.hamm.spms.common.metadata.LegacyEntityMetadata;
import cn.hamm.airpower.core.interfaces.IDictionary;
import cn.hamm.airpower.curd.base.CurdEntity;
import cn.hamm.airpower.curd.helper.TransactionHelper;
import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.bill.detail.BaseBillDetailEntity;
import cn.hamm.spms.base.bill.detail.BaseBillDetailRepository;
import cn.hamm.spms.base.bill.detail.BaseBillDetailService;
import cn.hamm.spms.module.system.SystemServices;
import cn.hamm.spms.module.system.config.ConfigEntity;
import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN;

/**
 * <h1>单据 Service 基类</h1>
 *
 * @param <E>   单据实体
 * @param <R>   单据数据源
 * @param <D>   明细实体
 * @param <DS>  明细 Service
 * @param <DR>> 明细数据源
 * @author Hamm.cn
 */
@Slf4j
public abstract class AbstractBaseBillService<
        E extends AbstractBaseBillEntity<E, D>,
        R extends BaseRepository<E>,
        D extends BaseBillDetailEntity<D>,
        DS extends BaseBillDetailService<D, DR>,
        DR extends BaseBillDetailRepository<D>
        > extends BaseService<E, R> {

    @Autowired(required = false)
    protected DS detailService;

    @Autowired
    protected TransactionHelper transactionHelper;

    /**
     * 获取自动审核配置
     *
     * @return 配置标识
     */
    protected ConfigFlag getAutoAuditConfigFlag() {
        log.info("获取自动审核配置, 无需自动审核 {}", LegacyEntityMetadata.description(getFirstParameterizedTypeClass()));
        return null;
    }

    /**
     * 设置单据所有明细都已完成
     *
     * @param billId 单据 ID
     */
    public final void setBillDetailsAllFinished(long billId) {
        log.info("标记明细已全部完成 {}，单据ID:{}", LegacyEntityMetadata.description(getFirstParameterizedTypeClass()), billId);
        IDictionary status = getBillDetailsFinishStatus();
        FORBIDDEN.whenNull(status, "没有找到单据的所有明细完成状态");
        updateToDatabase(getEntityInstance(billId).setStatus(status.getKey()));
        afterAllBillDetailFinished(billId);
        if (status.equals(getFinishedStatus())) {
            log.info("明细完成状态是终态");
            setBillFinished(billId);
        }
    }

    /**
     * 设置单据已完成
     *
     * @param billId 单据 ID
     */
    public final void setBillFinished(long billId) {
        log.info("标记单据已完成 {}，单据ID:{}", LegacyEntityMetadata.description(getFirstParameterizedTypeClass()), billId);
        IDictionary status = getFinishedStatus();
        FORBIDDEN.whenNull(status, "标记完成失败，没有找到完成状态");
        beforeBillFinish(billId);
        updateToDatabase(getEntityInstance(billId).setStatus(status.getKey()));
        afterBillFinished(billId);
    }

    /**
     * 单据完成前置方法
     *
     * @param billId 单据 ID
     */
    protected void beforeBillFinish(long billId) {
        log.info("标记单据完成前 {}，单据ID:{}",
                LegacyEntityMetadata.description(getFirstParameterizedTypeClass()),
                billId
        );
    }

    /**
     * 单据是否允许录入明细完成数量
     *
     * <p>状态机闸门（阶段 C-1）：只认「已审核」态（入库执行中/出库执行中/移库中）。
     * 两道事故都出在缺这道闸——
     * ① AUDITING 单直接 addFinish，审批通过前库存已经动了，审核形同追认；
     * ② Move 完成时自动生成的入/出库单状态即 DONE，但明细是 {@code isFinished=false}
     * 的「未完成」态，对它们再发 addFinish 会把已经搬完的库存再搬一遍
     * （实测：转移 10 件后对生成的出库单补录 5 件，源仓净损失 15 件）。</p>
     *
     * @param bill 单据
     * @return 是否允许录入完成数量
     */
    public final boolean canAddFinish(@NotNull E bill) {
        return getAuditedStatus().equalsKey(bill.getStatus());
    }

    /**
     * 添加明细完成数量
     *
     * @param sourceDetail 提交明细
     */
    public final void addDetailFinishQuantity(@NotNull D sourceDetail) {
        Long detailId = sourceDetail.getId();
        D detail = detailService.get(detailId);
        Long billId = detail.getBillId();
        Double finishQuantity = sourceDetail.getQuantity();
        E bill = get(billId);
        FORBIDDEN.when(!canAddFinish(bill), "单据当前状态不允许录入完成数量（需先审核通过）");
        log.info("添加明细数量 {}，单据ID:{}, 明细数量:{}", LegacyEntityMetadata.description(getFirstParameterizedTypeClass()), billId, finishQuantity);
        transactionHelper.run(() -> {
            detailService.addFinishQuantity(detailId, finishQuantity);

            // 明细添加成功后置方法
            afterDetailFinishAdded(detailId, sourceDetail);

            // 开始判断是否整个单据数量已超标
            List<D> details = detailService.getAllByBillId(billId);
            // 判断所有明细是否完成
            boolean isAllFinished = details.stream()
                    .allMatch(BaseBillDetailEntity::getIsFinished);
            log.info("所有明细是否已完成: {}", isAllFinished);
            if (!isAllFinished) {
                return;
            }
            setBillDetailsAllFinished(billId);
        });
    }

    /**
     * 添加完成数量成功后置
     *
     * @param detailId     明细 ID
     * @param sourceDetail 提交明细
     */
    protected void afterDetailFinishAdded(long detailId, @NotNull D sourceDetail) {
        log.info("添加完成数量成功后 {}，明细ID:{}", LegacyEntityMetadata.description(getFirstParameterizedTypeClass()), detailId);
    }

    /**
     * 单据完成的后置方法
     *
     * @param billId 单据 ID
     * @apiNote 一般用于在当前单据完成后同步标记关联的其他单据为完成状态
     * @see #afterAllBillDetailFinished(long)
     */
    protected void afterBillFinished(long billId) {
        log.info("单据已完成 {}，单据ID:{}",
                LegacyEntityMetadata.description(getFirstParameterizedTypeClass()),
                billId
        );
    }

    /**
     * 单据所有明细完成的后置方法
     *
     * @param billId 单据 ID
     * @apiNote 一般用于当前单据的所有明细都已完成，可能会创建其他的单据，也可能去修改其他单据的明细
     * @see #afterBillFinished(long)
     */
    protected void afterAllBillDetailFinished(long billId) {
        log.info("单据所有明细已完成 {}，单据ID:{}",
                LegacyEntityMetadata.description(getFirstParameterizedTypeClass()),
                billId
        );
    }

    /**
     * 单据明细保存后置方法
     *
     * @param billId 单据 ID
     */
    protected void afterDetailSaved(long billId) {
        log.info("单据明细保存成功 {}，单据ID:{}",
                LegacyEntityMetadata.description(getFirstParameterizedTypeClass()),
                billId
        );
    }

    @Override
    protected final E afterAppGet(@NotNull E bill) {
        List<D> details = detailService.getAllByBillId(bill.getId());
        bill.setDetails(details);
        return afterBillGet(bill);
    }

    /**
     * 单据获取后置
     *
     * @param bill 单据
     * @return 单据
     */
    protected E afterBillGet(@NotNull E bill) {
        log.info("查询单据详情后 {}，单据ID:{} {}",
                LegacyEntityMetadata.description(getFirstParameterizedTypeClass()),
                bill.getId(),
                bill.getBillCode()
        );
        return bill;
    }

    @Override
    protected void afterAppAdd(long billId, @NotNull E source) {
        E bill = get(billId);
        saveDetails(billId, source.getDetails());
        ConfigFlag configFlag = getAutoAuditConfigFlag();
        if (Objects.nonNull(configFlag)) {
            ConfigEntity config = SystemServices.getConfigService().get(configFlag);
            if (config.booleanConfig()) {
                audit(bill.getId());
            }
        }
        // 旧 run 实际是同步执行并只捕获 Exception；不新建线程，也不把审核/保存异常吞进来。
        try {
            afterBillAdd(bill.getId());
        } catch (Exception error) {
            log.error(error.getMessage(), error);
        }
    }

    /**
     * 单据添加后置
     *
     * @param billId 单据 ID
     */
    protected void afterBillAdd(long billId) {
        log.info("单据添加后 {}，单据ID:{}",
                LegacyEntityMetadata.description(getFirstParameterizedTypeClass()),
                billId
        );
    }

    @Override
    protected void afterAppUpdate(long billId, @NotNull E source) {
        saveDetails(billId, source.getDetails());
        afterBillUpdate(billId, source);
    }

    /**
     * 单据更新后置
     *
     * @param billId 单据 ID
     * @param source 源数据
     */
    protected void afterBillUpdate(long billId, @NotNull E source) {
        log.info("单据修改后 {}，单据ID:{}",
                LegacyEntityMetadata.description(getFirstParameterizedTypeClass()),
                billId
        );
    }

    /**
     * 保存单据明细
     * <li>
     * 请不要再重写后直接调用 {@link #update(CurdEntity)} ，避免出现调用循环。
     * </li>
     * <li>
     * 如需再次保存，请调用 {@link #updateToDatabase(CurdEntity)} }
     * </li>
     *
     * @param billId  单据 ID
     * @param details 明细列表
     */
    private void saveDetails(long billId, List<D> details) {
        detailService.saveDetails(billId, details);
        afterDetailSaved(billId);
    }

    /**
     * 单据审核
     *
     * <p>阶段 C-4：读状态与写状态必须落在同一把行锁里。原实现是无锁的
     * 「查 → 判 → 写」，并发审核全部通过 canAudit（实测 6 路并发 3 路成功），
     * 销售单双审会派生两张出库单——同一笔订单被备货两次。改为
     * {@code transactionHelper.run + getForUpdate}（与 CodeRuleService 取号同款），
     * 第二路会阻塞到第一路提交，再看到「已审核」态被 403 拦下。</p>
     *
     * <p>后置方法也从 TaskUtil.run（吞异常只记日志）挪进事务内直调：
     * 派生单据创建失败时审核整体回滚，不再出现「状态改了、单没派出来」的静默分裂。</p>
     *
     * @param billId 单据 ID
     */
    protected final void audit(long billId) {
        transactionHelper.run(() -> {
            E bill = getForUpdate(billId);
            FORBIDDEN.when(!canAudit(bill), "该单据状态无法审核");
            setAudited(bill);
            updateToDatabase(bill);
            afterBillAudited(billId);
        });
    }

    /**
     * 单据驳回
     *
     * <p>阶段 C-8 双缺陷：原实现先对实例 setReject，最后却
     * {@code updateToDatabase(getEntityInstance(billId))}——把带着驳回态的那个
     * 实例整个丢掉、更新了一个全新的空实例，实测驳回后 status 原封不动；
     * 且控制器只传 id，请求体里的驳回原因从未落库。连同 C-4 的并发问题
     * 一并按「行锁 + 同实例更新 + 原因入参」重写。</p>
     *
     * @param billId       单据 ID
     * @param rejectReason 驳回原因（可空）
     */
    protected final void reject(long billId, String rejectReason) {
        transactionHelper.run(() -> {
            E bill = getForUpdate(billId);
            FORBIDDEN.when(!canReject(bill), "该单据状态无法驳回");
            setReject(bill);
            bill.setRejectReason(rejectReason);
            updateToDatabase(bill);
        });
    }

    /**
     * 单据审核的后置方法
     *
     * @param billId 单据 ID
     * @apiNote 可以添加一些审核后的业务逻辑
     */
    protected void afterBillAudited(long billId) {
        log.info("单据审核后 {}，单据ID:{}",
                LegacyEntityMetadata.description(getFirstParameterizedTypeClass()),
                billId
        );
    }

    /**
     * 设置为已审核状态
     *
     * @param bill 单据
     */
    public final void setAudited(@NotNull E bill) {
        bill.setStatus(getAuditedStatus().getKey());
    }

    /**
     * 设置为审核中状态
     *
     * @param bill 单据
     */
    public final void setAuditing(@NotNull E bill) {
        bill.setStatus(getAuditingStatus().getKey());
    }

    /**
     * 单据是否可审核
     *
     * @param bill 单据
     * @return 是否审核
     */
    public final boolean canAudit(@NotNull E bill) {
        return getAuditingStatus().equalsKey(bill.getStatus());
    }

    /**
     * 单据是否可驳回
     *
     * @param bill 单据
     * @return 是否可驳回
     */
    public final boolean canReject(@NotNull E bill) {
        return getAuditingStatus().equalsKey(bill.getStatus());
    }

    /**
     * 设置单据为驳回状态
     *
     * @param bill 单据
     */
    public final void setReject(@NotNull E bill) {
        bill.setStatus(getRejectedStatus().getKey());
    }

    /**
     * 单据是否可编辑
     *
     * @param bill 单据
     * @return 是否可编辑
     */
    public final boolean canEdit(@NotNull E bill) {
        return getRejectedStatus().equalsKey(bill.getStatus());
    }

    /**
     * 获取审核中状态
     *
     * @return 审核中状态
     */
    protected abstract IDictionary getAuditingStatus();

    /**
     * 获取已审核状态
     *
     * @return 已审核状态
     */
    protected abstract IDictionary getAuditedStatus();

    /**
     * 获取驳回状态
     *
     * @return 驳回状态
     */
    protected abstract IDictionary getRejectedStatus();

    /**
     * 获取所有明细均已完成的单据状态
     *
     * @return 所有明细均已完成的单据状态
     * @apiNote 可单独配置 {@link #getFinishedStatus()}
     */
    public abstract IDictionary getBillDetailsFinishStatus();

    /**
     * 获取单据已完成状态
     *
     * @return 单据已完成状态
     * @apiNote 默认为 {@link #getBillDetailsFinishStatus()}
     */
    public IDictionary getFinishedStatus() {
        log.info("获取单据已完成状态: {}", getBillDetailsFinishStatus().getLabel());
        return getBillDetailsFinishStatus();
    }
}
