package cn.hamm.spms.module.factory.structure.mapper;

import cn.hamm.spms.module.factory.structure.StructureEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * <h1>生产单元 Mapper 读侧 shadow 参数适配</h1>
 *
 * @author Hamm.cn
 */
@Service
public class StructureReadShadowService {
    public static final String RESOURCE_KEY = "factory.structure";
    private final MapperShadowReadService mapperShadowReadService;

    public StructureReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * @param filter 与真实列表相同的生产单元筛选（编码 / 名称 / 类型）
     */
    public void submit(StructureEntity filter) {
        StructureEntity normalized = filter == null ? new StructureEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "structure", StructureEntity.class,
                businessFilter(normalized), Map.of("filter", normalized));
    }

    private Specification<StructureEntity> businessFilter(StructureEntity filter) {
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
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
