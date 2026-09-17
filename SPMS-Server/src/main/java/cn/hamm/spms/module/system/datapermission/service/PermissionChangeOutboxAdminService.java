package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.personnel.user.UserService;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.PermissionChangeOutboxEntity;
import cn.hamm.spms.module.system.datapermission.repository.PermissionChangeOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.hamm.spms.common.exception.Errors.PARAM_INVALID;

/**
 * <h1>权限变更 Outbox 死信处置（P5：人工重放 / 丢弃）</h1>
 *
 * <p>{@code Dispatcher} 连续 {@code MAX_RETRY} 次投递失败会把事件置为 {@code DEAD}（死信）。死信
 * 意味着「这条变更通知可能没送到各节点的缓存失效层」，要么等人工确认后重放，要么判定事件已无意义
 * （例如对应策略已经删除）后丢弃。本服务是那两条特权入口，挂在 {@code dataResource} 权限面下，
 * 需要独立的 {@code system:data-permission:outbox} 身份（root 视为持有），每次处置落库审计。</p>
 *
 * <p><b>为什么不自动无限重放</b>：死信多半是 payload 本身有问题或 Redis/网络长期故障；自动重放
 * 会在故障恢复后继续空转，把「死信」变成一个永远消不掉的循环。人工重放让管理员先看
 * {@code last_error} 再决定重放还是丢弃。</p>
 *
 * <p><b>重放语义</b>：只把 {@code DEAD} 行重置为 {@code PENDING} + 重试次数清零 + 下次重试即时到期
 * （走 {@link OutboxDispatchStep#markReplay}，状态迁移单源），让 {@code Dispatcher} 下一轮轮询拉走
 * 重投；不在这里直接投递（保持「谁投递、谁落状态」单一职责，也避免在管理请求线程里依赖 Redis）。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class PermissionChangeOutboxAdminService {

    /**
     * 审计归因用的资源标识（Outbox 不是业务数据资源，用固定串在拒绝/处置日志里定位）
     */
    public static final String RESOURCE_KEY = "permission-change-outbox";

    /**
     * 死信重放请求体
     *
     * @param eventKey 事件唯一键
     */
    public record OutboxReplayRequest(String eventKey) {
    }

    private final PermissionChangeOutboxRepository outboxRepository;
    private final SystemPermissionService systemPermissionService;
    private final UserService userService;
    private final DataPermissionDenyLogRecorder denyLogRecorder;

    public PermissionChangeOutboxAdminService(PermissionChangeOutboxRepository outboxRepository,
                                              SystemPermissionService systemPermissionService,
                                              UserService userService,
                                              DataPermissionDenyLogRecorder denyLogRecorder) {
        this.outboxRepository = outboxRepository;
        this.systemPermissionService = systemPermissionService;
        this.userService = userService;
        this.denyLogRecorder = denyLogRecorder;
    }

    /**
     * 死信列表（最新在前，最多 100 条）
     *
     * @return DEAD 事件
     */
    public List<PermissionChangeOutboxEntity> listDead() {
        requireOutboxPermission("查看死信");
        return outboxRepository.findByStatusOrderByIdDesc(PermissionChangeOutboxStatus.DEAD.name());
    }

    /**
     * 重放单条死信：DEAD → PENDING（重试清零、即时到期），交还 Dispatcher
     *
     * @param eventKey 事件唯一键
     */
    @Transactional
    public void replay(String eventKey) {
        requireOutboxPermission("重放死信");
        PermissionChangeOutboxEntity row = requireDeadRow(eventKey);
        applyReplay(row, System.currentTimeMillis());
        outboxRepository.save(row);
        denyLogRecorder.record(RESOURCE_KEY, DataPermissionAudit.REASON_OUTBOX_REPLAYED,
                "operator=" + currentUserId() + " eventKey=" + eventKey);
        log.warn("[data-permission][outbox] 死信重放：eventKey={}", eventKey);
    }

    /**
     * 重放全部死信，返回重放条数
     *
     * @return 重放条数
     */
    @Transactional
    public int replayAllDead() {
        requireOutboxPermission("重放死信");
        List<PermissionChangeOutboxEntity> dead = outboxRepository
                .findByStatusOrderByIdDesc(PermissionChangeOutboxStatus.DEAD.name());
        long now = System.currentTimeMillis();
        for (PermissionChangeOutboxEntity row : dead) {
            applyReplay(row, now);
        }
        if (!dead.isEmpty()) {
            outboxRepository.saveAll(dead);
        }
        denyLogRecorder.record(RESOURCE_KEY, DataPermissionAudit.REASON_OUTBOX_REPLAYED,
                "operator=" + currentUserId() + " count=" + dead.size());
        log.warn("[data-permission][outbox] 死信批量重放：count={}", dead.size());
        return dead.size();
    }

    /**
     * 丢弃单条死信（管理员判定事件已无意义），落审计后物理删除
     *
     * @param eventKey 事件唯一键
     */
    @Transactional
    public void discard(String eventKey) {
        requireOutboxPermission("丢弃死信");
        PermissionChangeOutboxEntity row = requireDeadRow(eventKey);
        outboxRepository.delete(row);
        denyLogRecorder.record(RESOURCE_KEY, DataPermissionAudit.REASON_OUTBOX_DISCARDED,
                "operator=" + currentUserId() + " eventKey=" + eventKey);
        log.warn("[data-permission][outbox] 死信丢弃：eventKey={}", eventKey);
    }

    private void applyReplay(PermissionChangeOutboxEntity row, long now) {
        OutboxDispatchStep.Result replay = OutboxDispatchStep.markReplay(now);
        row.setStatus(replay.status())
                .setRetryCount(replay.retryCount())
                .setNextRetryAt(replay.nextRetryAt())
                .setLastError(null);
    }

    private PermissionChangeOutboxEntity requireDeadRow(String eventKey) {
        if (eventKey == null || eventKey.isBlank()) {
            PARAM_INVALID.show("死信重放需要 eventKey");
        }
        PermissionChangeOutboxEntity row = outboxRepository.getByEventKey(eventKey);
        if (row == null) {
            PARAM_INVALID.show("找不到该事件：" + eventKey);
        }
        if (!PermissionChangeOutboxStatus.DEAD.name().equals(row.getStatus())) {
            PARAM_INVALID.show("该事件不在死信状态（当前 " + row.getStatus() + "），无需重放/丢弃");
        }
        return row;
    }

    private void requireOutboxPermission(String action) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null) {
            DataPermissionDenial.denied(null, RESOURCE_KEY, DataPermissionAudit.REASON_OUTBOX_DENIED,
                    "无请求上下文", "数据权限不足：" + action + "需要在请求上下文中执行（需 data-permission:outbox）");
            return;
        }
        UserEntity current = userService.get(context.userId());
        if (current == null || (!current.isRootUser()
                && !systemPermissionService.has(current, SystemPermissionIdentities.DATA_PERMISSION_OUTBOX))) {
            DataPermissionDenial.denied(null, RESOURCE_KEY, DataPermissionAudit.REASON_OUTBOX_DENIED,
                    "operator=" + context.userId(),
                    "数据权限不足：" + action + "需要 data-permission:outbox 权限（仅平台管理员可执行）");
            return;
        }
    }

    private Long currentUserId() {
        return PermissionContextHolder.current().map(PermissionContext::userId).orElse(null);
    }
}
