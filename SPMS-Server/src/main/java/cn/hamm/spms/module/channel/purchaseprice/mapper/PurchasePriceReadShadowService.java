package cn.hamm.spms.module.channel.purchaseprice.mapper;

import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * <h1>采购价格 Mapper 读侧 shadow 参数适配</h1>
 *
 * <p>采购价无业务筛选，仅按租户全量 + 行级过滤，业务筛选 Specification 恒为 conjunction。</p>
 *
 * @author Hamm.cn
 */
@Service
public class PurchasePriceReadShadowService {
    public static final String RESOURCE_KEY = "channel.purchasePrice";
    private final MapperShadowReadService mapperShadowReadService;

    public PurchasePriceReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * @param filter 采购价筛选（无业务字段，保留占位）
     */
    public void submit(PurchasePriceEntity filter) {
        PurchasePriceEntity normalized = filter == null ? new PurchasePriceEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "purchase_price", PurchasePriceEntity.class,
                businessFilter(), Map.of("filter", normalized));
    }

    private Specification<PurchasePriceEntity> businessFilter() {
        return (root, query, builder) -> builder.conjunction();
    }
}
