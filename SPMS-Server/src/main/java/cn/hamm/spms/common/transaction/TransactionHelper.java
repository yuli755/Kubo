package cn.hamm.spms.common.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h1>编程式事务执行助手</h1>
 *
 * <p>提供独立事务模板方法，支持显式包裹可重读事务块。
 * 替代旧 {@code cn.hamm.airpower.curd.helper.TransactionHelper}。</p>
 *
 * @author Hamm.cn
 */
@Service
public class TransactionHelper {
    /**
     * 在 REPEATABLE_READ 隔离级别的只读/写事务中执行一段逻辑，遇到任何异常自动回滚
     *
     * @param task 事务任务
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public void run(Task task) {
        if (task != null) {
            task.run();
        }
    }

    /**
     * 事务执行函数接口
     */
    @FunctionalInterface
    public interface Task {
        void run();
    }
}
