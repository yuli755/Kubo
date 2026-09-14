package cn.hamm.spms.module.channel.saleprice.mapper;

import cn.hamm.spms.module.channel.saleprice.SalePriceEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * <h1>销售价格 Mapper 读侧 shadow 参数适配</h1>
 *
 * <p>销售价无业务筛选，仅按租户全量 + 行级过滤，业务筛选 Specification 恒为 conjunction。</p>
 *
 * @author Hamm.cn
 */
@Service
public class SalePriceReadShadowService {
    public static final String RESOURCE_KEY = "channel.salePrice";
    private final MapperShadowReadService mapperShadowReadService;

    public SalePriceReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * @param filter 销售价筛选（无业务字段，保留占位）
     */
    public void submit(SalePriceEntity filter) {
        SalePriceEntity normalized = filter == null ? new SalePriceEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "sale_price", SalePriceEntity.class,
                businessFilter(), Map.of("filter", normalized));
    }

    private Specification<SalePriceEntity> businessFilter() {
        return (root, query, builder) -> builder.conjunction();
    }
}
