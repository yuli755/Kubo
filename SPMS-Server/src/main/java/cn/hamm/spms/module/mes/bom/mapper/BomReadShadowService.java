package cn.hamm.spms.module.mes.bom.mapper;

import cn.hamm.spms.module.mes.bom.BomEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * <h1>BOM Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class BomReadShadowService {
    public static final String RESOURCE_KEY = "mes.bom";
    private final MapperShadowReadService mapperShadowReadService;

    public BomReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * @param filter 与真实列表相同的 BOM 筛选（编码/名称/类型/状态）
     */
    public void submit(BomEntity filter) {
        BomEntity normalized = filter == null ? new BomEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "bom", BomEntity.class,
                businessFilter(normalized), Map.of("filter", normalized));
    }

    private Specification<BomEntity> businessFilter(BomEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getCode() != null && !filter.getCode().isBlank()) {
                predicates.add(builder.like(root.get("code"), "%" + filter.getCode() + "%"));
            }
            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(builder.like(root.get("name"), "%" + filter.getName() + "%"));
            }
            if (filter.getType() != null) {
                predicates.add(builder.equal(root.get("type"), filter.getType()));
            }
            if (filter.getStatus() != null) {
                predicates.add(builder.equal(root.get("status"), filter.getStatus()));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
