package cn.hamm.spms.module.asset.material.mapper;

import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.system.datapermission.service.MapperShadowReadService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * <h1>物料 Mapper 读侧 shadow 参数适配</h1>
 *
 * <p>物料自身只负责把 name/code/type/useType 业务筛选转换为 JPA Specification 和 XML 参数；
 * 资源登记、策略树、JPA 编译器、SQL 片段和异步 ORM parity 统一交给
 * {@link MapperShadowReadService}。这样 Material 与后续资源消费同一套 shadow 接线，不复制权限判据。</p>
 *
 * @author Hamm.cn
 */
@Service
public class MaterialReadShadowService {
    public static final String RESOURCE_KEY = "asset.material";
    private final MapperShadowReadService mapperShadowReadService;

    public MaterialReadShadowService(MapperShadowReadService mapperShadowReadService) {
        this.mapperShadowReadService = mapperShadowReadService;
    }

    /**
     * 无业务筛选提交 shadow 对账。
     */
    public void submit() {
        submit(null);
    }

    /**
     * 从当前请求提交带业务筛选的 Material JPA/Mapper COUNT 对账。
     *
     * @param filter 与真实 Mapper page/count/stat 相同的业务筛选；null 表示不筛选
     */
    public void submit(MaterialEntity filter) {
        MaterialEntity normalized = filter == null ? new MaterialEntity() : filter;
        mapperShadowReadService.submit(RESOURCE_KEY, "material", MaterialEntity.class,
                businessFilter(normalized), Map.of("filter", normalized));
    }

    private Specification<MaterialEntity> businessFilter(MaterialEntity filter) {
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(builder.like(root.get("name"), "%" + filter.getName() + "%"));
            }
            if (filter.getCode() != null && !filter.getCode().isBlank()) {
                predicates.add(builder.like(root.get("code"), "%" + filter.getCode() + "%"));
            }
            if (filter.getMaterialType() != null) {
                predicates.add(builder.equal(root.get("materialType"), filter.getMaterialType()));
            }
            if (filter.getUseType() != null) {
                predicates.add(builder.equal(root.get("useType"), filter.getUseType()));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
