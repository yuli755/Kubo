package cn.hamm.spms.module.channel.supplier.mapper;

import cn.hamm.spms.module.channel.supplier.SupplierEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * <h1>供应商 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class SupplierReadShadowService {
    public static final String RESOURCE_KEY = "channel.supplier";
    private final MapperShadowReadService mapperShadowReadService;

    public SupplierReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * @param filter 与真实列表相同的供应商筛选（名称/编码）
     */
    public void submit(SupplierEntity filter) {
        SupplierEntity normalized = filter == null ? new SupplierEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "supplier", SupplierEntity.class,
                businessFilter(normalized), Map.of("filter", normalized));
    }

    private Specification<SupplierEntity> businessFilter(SupplierEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(builder.like(root.get("name"), "%" + filter.getName() + "%"));
            }
            if (filter.getCode() != null && !filter.getCode().isBlank()) {
                predicates.add(builder.like(root.get("code"), "%" + filter.getCode() + "%"));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
