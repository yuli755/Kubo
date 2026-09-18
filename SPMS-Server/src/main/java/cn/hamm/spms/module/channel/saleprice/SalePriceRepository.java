package cn.hamm.spms.module.channel.saleprice;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.channel.customer.CustomerEntity;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>数据库连接信息</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface SalePriceRepository extends BaseRepository<SalePriceEntity> {
    /**
     * 获取指定客户和物料的销售价
     *
     * @param customer 客户
     * @param material 物料
     * @return 采购价实体
     */
    /**
     * 按客户 + 物料查询单价
     *
     * @param customer 客户
     * @param material 物料
     * @return 单价
     * @apiNote 同 {@code PurchasePriceRepository#getBySupplierAndMaterial}：关系相等 → 本表 FK 列相等
     */
    default SalePriceEntity getByCustomerAndMaterial(CustomerEntity customer, MaterialEntity material) {
        return selectOne(new QueryWrapper<SalePriceEntity>()
                .eq("customer_id", customer.getId())
                .eq("material_id", material.getId()));
    }
}
