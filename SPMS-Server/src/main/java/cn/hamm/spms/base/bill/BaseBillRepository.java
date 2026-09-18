package cn.hamm.spms.base.bill;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.base.bill.detail.BaseBillDetailEntity;

/**
 * <h1>单据的标准接口</h1>
 *
 * @param <E> 单据实体
 * @param <D> 明细实体
 * @author Hamm.cn
 */
public interface BaseBillRepository<
        E extends AbstractBaseBillEntity<E, D>,
        D extends BaseBillDetailEntity<D>
        > extends BaseRepository<E> {
}
