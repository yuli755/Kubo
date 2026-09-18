package cn.hamm.spms.base.bill.detail;

import cn.hamm.spms.base.BaseRepository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.util.List;

/**
 * <h1>单据明细的标准接口</h1>
 *
 * @param <E> 明细实体
 * @author Hamm.cn
 */
public interface BaseBillDetailRepository<E extends BaseBillDetailEntity<E>> extends BaseRepository<E> {
    /**
     * 根据单据 ID 查询所有明细
     *
     * @param billId 单据 ID
     * @return 明细
     * @apiNote 2026-09-18 J2：原来是 Spring Data 的**派生查询**（按方法名生成 SQL）。
     * 换成 MyBatis-Plus 后派生查询不存在了，这里用 {@code default} 实现 —— 它是本批
     * 30 个派生方法里的第一个，判据见 {@code maskContract.spec.ts}：
     * "声明了派生查询名却没有实现"会变成启动/首次调用才炸的 {@code Invalid bound statement}。
     */
    default List<E> getAllByBillId(Long billId) {
        return selectList(new QueryWrapper<E>().eq("bill_id", billId));
    }
}
