package cn.hamm.spms.module.channel.purchaseprice;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.channel.supplier.SupplierEntity;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>数据库连接信息</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface PurchasePriceRepository extends BaseRepository<PurchasePriceEntity> {
    /**
     * 获取指定供应商和物料的采购价
     *
     * @param supplier 供应商
     * @param material 物料
     * @return 采购价实体
     */
    /**
     * 按供应商 + 物料查询单价
     *
     * @param supplier 供应商
     * @param material 物料
     * @return 单价
     * @apiNote J2：Spring Data 的派生查询名（按方法名生成 SQL）换成 {@code default} + wrapper。
     * 派生名里的 {@code BySupplierAndMaterial} 指"关系相等"＝本表外键列相等（{@code supplier_id}/{@code material_id}），
     * 所以条件落在 FK 列上，而不是 join 关联表 —— 与 Hibernate 的实际做法一致。
     * <p>派生查询名是**隐式 SQL**（{@code getBy}、{@code OrderByIdAsc}、{@code TopN} 都是语义），
     * 换轨时最容易悄悄少一个条件；少了条件不报错，只多出一些行。逐条对读是这一步的纪律。</p>
     */
    default PurchasePriceEntity getBySupplierAndMaterial(SupplierEntity supplier, MaterialEntity material) {
        return selectOne(new QueryWrapper<PurchasePriceEntity>()
                .eq("supplier_id", supplier.getId())
                .eq("material_id", material.getId()));
    }
}
