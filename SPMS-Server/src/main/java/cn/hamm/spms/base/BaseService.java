package cn.hamm.spms.base;

import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.NullEnable;
import cn.hamm.spms.framework.web.annotation.Search;
import cn.hamm.spms.framework.web.annotation.SearchEmpty;
import cn.hamm.spms.common.export.ExportHelper;
import cn.hamm.spms.common.redis.RedisHelper;
import cn.hamm.spms.common.transaction.TransactionHelper;
import cn.hamm.spms.framework.query.QueryExport;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.common.exception.BusinessException;
import cn.hamm.spms.framework.query.Page;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.query.QueryPageResponse;
import cn.hamm.spms.framework.query.ReadPage;
import cn.hamm.spms.framework.query.Sort;
import cn.hamm.spms.module.system.SystemServices;
import cn.hamm.spms.module.system.coderule.CodeRuleService;
import cn.hamm.spms.module.system.datapermission.annotation.OperationLog;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.*;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;

import static cn.hamm.spms.common.exception.Errors.*;

/**
 * <h1>基础服务类</h1>
 *
 * <p>自持核心实体服务基类，替代旧 {@code CurdService}。
 * 提供基于 Spring Data JPA 的增删改查、动态 Specification 构建、悲观锁更新、
 * 数据权限与行级写校验、通用操作审计与导出脱敏机制。</p>
 *
 * @param <E> 实体类型
 * @param <R> 数据源类型
 * @author Hamm.cn
 */
@Slf4j
public class BaseService<
        E extends BaseEntity<E>,
        R extends BaseRepository<E>
        > {

    protected static final String DATA_REQUIRED = "提交的数据不允许为空";

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired(required = false)
    protected R repository;

    @Autowired(required = false)
    protected TransactionHelper transactionHelper;

    @Autowired(required = false)
    protected ExportHelper exportHelper;

    @Autowired(required = false)
    protected RedisHelper redisHelper;

    /**
     * 通用操作审计记录器（P3）。可选注入：容器未装配该 Bean 时，审计静默跳过，不影响任何业务路径。
     */
    @Autowired(required = false)
    private OperationLogRecorder operationLogRecorder;

    /**
     * 更新前的「旧值」暂存：{@code beforeUpdate} 回表取到的既存行先存这里，
     * {@code afterUpdate} 再取出来连同提交值一起记账。每个请求线程一次更新一条，单槽足够。
     */
    private final ThreadLocal<E> operationLogBeforeSnapshot = new ThreadLocal<>();

    /**
     * 获取实体类类型
     */
    @SuppressWarnings("unchecked")
    public final Class<E> getEntityClass() {
        Type superclass = getClass().getGenericSuperclass();
        while (superclass instanceof Class<?> clazz && clazz != BaseService.class) {
            superclass = clazz.getGenericSuperclass();
        }
        if (superclass instanceof ParameterizedType parameterizedType) {
            return (Class<E>) parameterizedType.getActualTypeArguments()[0];
        }
        throw new IllegalStateException("无法解析实体类类型: " + getClass().getName());
    }

    /**
     * 获取泛型实体类类型（兼容旧命名）
     */
    public final Class<E> getFirstParameterizedTypeClass() {
        return getEntityClass();
    }

    /**
     * 获取实体新实例
     */
    public final E getEntityInstance() {
        try {
            return getEntityClass().getConstructor().newInstance();
        } catch (Exception e) {
            log.error("创建实体实例失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建实体实例失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取指定 ID 的实体空实例
     */
    public final E getEntityInstance(Long id) {
        E instance = getEntityInstance();
        instance.setId(id);
        return instance;
    }

    /**
     * 获取实体中文描述
     */
    public final String getEntityDescription() {
        Description desc = getEntityClass().getAnnotation(Description.class);
        if (desc != null && StringUtils.hasText(desc.value())) {
            return desc.value().trim();
        }
        return getEntityClass().getSimpleName();
    }

    /**
     * 添加一条数据（触发前后置与通用审计）
     */
    public final long add(@NotNull E source) {
        source = beforeAdd(source);
        SERVICE_ERROR.whenNull(source, DATA_REQUIRED);
        source.setId(null);
        long id = addToDatabase(source);
        final E finalSource = source;
        afterAdd(id, finalSource);
        afterSaved(id, finalSource);
        return id;
    }

    /**
     * 添加到数据库（不触发前后置）
     */
    public final long addToDatabase(@NotNull E source) {
        PARAM_MISSING.whenNotNull(source.getId(), String.format("添加失败，请不要传入%s的ID!", getEntityDescription()));
        return saveToDatabase(source, false);
    }

    /**
     * 添加并获取完整实体
     */
    public final @NotNull E addAndGet(@NotNull E source) {
        return get(add(source));
    }

    /**
     * 修改已存在的数据
     */
    public final void update(@NotNull E source) {
        update(source, false);
    }

    /**
     * 修改已存在的数据（支持空值覆盖开关）
     */
    public final void update(@NotNull E source, boolean withNull) {
        long id = source.getId();
        source = beforeUpdate(source);
        updateToDatabase(source, withNull);
        final E finalSource = source;
        afterUpdate(id, finalSource);
        afterSaved(id, finalSource);
    }

    /**
     * 加锁更新指定 ID 的数据
     */
    public final void updateWithLock(long id, Consumer<E> consumer) {
        if (transactionHelper != null) {
            transactionHelper.run(() -> {
                E exist = getForUpdate(id);
                consumer.accept(exist);
                updateToDatabase(exist);
            });
        } else {
            E exist = getForUpdate(id);
            consumer.accept(exist);
            updateToDatabase(exist);
        }
    }

    /**
     * 更新到数据库（不触发前后置）
     */
    public final void updateToDatabase(@NotNull E source) {
        updateToDatabase(source, false);
    }

    /**
     * 更新到数据库（不触发前后置，支持空值控制）
     */
    public final void updateToDatabase(@NotNull E source, boolean withNull) {
        SERVICE_ERROR.whenNull(source, DATA_REQUIRED);
        PARAM_MISSING.whenNull(source.getId(), String.format("修改失败，请传入%s的ID!", getEntityDescription()));
        saveToDatabase(source, withNull);
    }

    /**
     * 删除指定数据
     */
    public final void delete(long id) {
        E entity = get(id);
        beforeDelete(entity);
        repository.deleteById(id);
        afterDelete(id);
    }

    /**
     * 启用指定数据
     */
    public final void enable(long id) {
        E entity = get(id);
        beforeEnable(entity);
        updateToDatabase(getEntityInstance(id).setIsDisabled(false));
        afterEnable(id);
    }

    /**
     * 禁用指定数据
     */
    public final void disable(long id) {
        E entity = get(id);
        beforeDisable(entity);
        updateToDatabase(getEntityInstance(id).setIsDisabled(true));
        afterDisable(id);
    }

    /**
     * 根据主键查询实体（查不到抛出异常）
     */
    public final @NotNull E get(long id) {
        return afterGet(getById(id));
    }

    /**
     * 根据主键查询实体（查不到返回 null，不抛异常）
     */
    public final @Nullable E getMaybeNull(long id) {
        try {
            return get(id);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 加排他锁获取实体
     */
    public final @NotNull E getForUpdate(long id) {
        if (entityManager != null) {
            entityManager.clear();
        }
        E forUpdate = repository.getForUpdateById(id);
        DATA_NOT_FOUND.whenNull(forUpdate, String.format("没有查询到ID为%s的%s", id, getEntityDescription()));
        return forUpdate;
    }

    /**
     * 查询正常启用的实体
     */
    public final @NotNull E getWithEnable(long id) {
        E entity = get(id);
        FORBIDDEN_DISABLED.when(
                Boolean.TRUE.equals(entity.getIsDisabled()),
                String.format(FORBIDDEN_DISABLED.getMessage(), id, getEntityDescription())
        );
        return entity;
    }

    /**
     * 根据主键查询实体
     */
    private @NotNull E getById(Long id) {
        String description = getEntityDescription();
        PARAM_MISSING.whenNull(id, String.format("查询失败，请传入%s的ID！", description));
        if (entityManager != null) {
            entityManager.clear();
        }
        Optional<E> optional = repository.findById(id);
        if (optional.isEmpty()) {
            throw new BusinessException(DATA_NOT_FOUND, String.format("没有查询到ID为%s的%s", id, description));
        }
        return optional.get();
    }

    /**
     * 列表查询
     */
    public final @NotNull List<E> getList(QueryListRequest<E> queryListRequest) {
        queryListRequest = Objects.requireNonNullElse(queryListRequest, new QueryListRequest<>());
        queryListRequest = beforeGetList(queryListRequest);
        List<E> list = query(queryListRequest.getFilter(), queryListRequest.getSort());
        return afterGetList(list);
    }

    /**
     * 分页查询
     */
    public final @NotNull QueryPageResponse<E> getPage(QueryPageRequest<E> queryPageRequest) {
        queryPageRequest = Objects.requireNonNullElse(queryPageRequest, new QueryPageRequest<>());
        queryPageRequest = beforeGetPage(queryPageRequest);
        Page page = queryPageRequest.getPage() != null ? queryPageRequest.getPage() : new Page();
        Sort sort = queryPageRequest.getSort() != null ? queryPageRequest.getSort() : new Sort();
        Pageable pageable = buildPageable(page, sort);
        org.springframework.data.domain.Page<E> springPage = repository.findAll(
                createSpecification(queryPageRequest.getFilter(), false), pageable);
        QueryPageResponse<E> queryPageResponse = QueryPageResponse.of(
                ReadPage.from(springPage.getNumber() + 1, springPage.getSize()),
                springPage.getContent(),
                springPage.getTotalElements(),
                sort
        );
        return afterGetPage(queryPageResponse);
    }

    /**
     * 精确强匹配列表查询
     */
    public final @NotNull List<E> filter(@Nullable E filter) {
        return filter(filter, null);
    }

    /**
     * 精确强匹配带排序列表查询
     */
    public final @NotNull List<E> filter(@Nullable E filter, @Nullable Sort sort) {
        return repository.findAll(createSpecification(filter, true), buildSpringSort(sort));
    }

    /**
     * 模糊匹配列表查询
     */
    public final @NotNull List<E> query(@Nullable E filter) {
        return query(filter, null);
    }

    /**
     * 模糊匹配带排序列表查询
     */
    public final @NotNull List<E> query(@Nullable E filter, @Nullable Sort sort) {
        return repository.findAll(createSpecification(filter, false), buildSpringSort(sort));
    }

    /**
     * 创建 Specification 查询规格
     */
    protected final Specification<E> createSpecification(@Nullable E filter, boolean isEqual) {
        final E finalFilter = filter != null ? filter : getEntityInstance();
        return (root, criteriaQuery, criteriaBuilder) -> {
            if (criteriaQuery == null) {
                return null;
            }
            E processed = beforeCreatePredicate(finalFilter.copy());
            List<Predicate> predicateList = buildPredicates(root, criteriaBuilder, processed, isEqual);
            predicateList.addAll(addSearchPredicate(root, criteriaBuilder, finalFilter));
            if (predicateList.isEmpty()) {
                return null;
            }
            return criteriaBuilder.and(predicateList.toArray(new Predicate[0]));
        };
    }

    /**
     * 遍历实体属性生成 Criteria Predicate 列表
     */
    protected List<Predicate> buildPredicates(
            From<?, ?> root,
            CriteriaBuilder builder,
            Object filter,
            boolean isEqual
    ) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter == null) {
            return predicates;
        }
        Class<?> current = filter.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object val;
                try {
                    val = field.get(filter);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (val == null) {
                    continue;
                }
                if (field.isAnnotationPresent(Transient.class)
                        || field.isAnnotationPresent(OneToMany.class)
                        || field.isAnnotationPresent(ManyToMany.class)) {
                    continue;
                }
                if (field.isAnnotationPresent(ManyToOne.class)) {
                    Join<?, ?> join = root.join(field.getName(), JoinType.INNER);
                    predicates.addAll(buildPredicates(join, builder, val, isEqual));
                    continue;
                }
                String strVal = val.toString();
                if (!StringUtils.hasText(strVal)) {
                    SearchEmpty searchEmpty = field.getAnnotation(SearchEmpty.class);
                    if (searchEmpty == null || !searchEmpty.value()) {
                        continue;
                    }
                }
                if (isEqual) {
                    predicates.add(builder.equal(root.get(field.getName()), val));
                } else {
                    Search search = field.getAnnotation(Search.class);
                    if (search != null) {
                        if (search.fullLike()) {
                            predicates.add(builder.like(root.get(field.getName()), "%" + strVal + "%"));
                        } else {
                            predicates.add(builder.like(root.get(field.getName()), strVal + "%"));
                        }
                    } else {
                        predicates.add(builder.equal(root.get(field.getName()), val));
                    }
                }
            }
            current = current.getSuperclass();
        }
        return predicates;
    }

    /**
     * 构建 Spring Data Sort
     */
    protected org.springframework.data.domain.Sort buildSpringSort(@Nullable Sort sort) {
        String field = (sort != null && StringUtils.hasText(sort.getField())) ? sort.getField() : "id";
        boolean isAsc = sort != null && sort.isAsc();
        org.springframework.data.domain.Sort springSort = isAsc
                ? org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc(field))
                : org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc(field));
        if (!"id".equals(field)) {
            if (!"createTime".equals(field)) {
                springSort = springSort.and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createTime")));
            }
            springSort = springSort.and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("id")));
        }
        return springSort;
    }

    /**
     * 构建 Spring Data Pageable
     */
    protected Pageable buildPageable(@Nullable Page page, @Nullable Sort sort) {
        int pageNum = (page != null && page.getPageNum() != null && page.getPageNum() > 0) ? page.getPageNum() : 1;
        int pageSize = (page != null && page.getPageSize() != null && page.getPageSize() > 0) ? page.getPageSize() : 20;
        return PageRequest.of(pageNum - 1, pageSize, buildSpringSort(sort));
    }

    /**
     * 写入数据库核心过程（时间戳、唯一性、脏数据隔离）
     */
    private long saveToDatabase(@NotNull E entity, boolean withNull) {
        checkUnique(entity);
        entity.setUpdateTime(System.currentTimeMillis());
        if (Objects.isNull(entity.getId())) {
            entity.setCreateTime(System.currentTimeMillis())
                    .setIsDisabled(false);
            return saveAndFlush(entity);
        }
        entity.setCreateTime(null);
        E existEntity = getById(entity.getId());
        entity = withNull ? entity : getEntityForUpdate(entity, existEntity);
        return saveAndFlush(entity);
    }

    /**
     * 执行保存并强刷
     */
    private long saveAndFlush(@NotNull E entity) {
        E target = getEntityInstance();
        BeanUtils.copyProperties(entity, target);
        target = beforeSaveToDatabase(target);
        target = repository.saveAndFlush(target);
        return target.getId();
    }

    /**
     * 合并待更新实体的属性
     */
    private @NotNull E getEntityForUpdate(@NotNull E source, @NotNull E exist) {
        String[] ignoreProperties = getUpdateIgnoreFields(source);
        BeanUtils.copyProperties(source, exist, ignoreProperties);
        return exist;
    }

    /**
     * 检查唯一约束
     */
    private void checkUnique(@NotNull E entity) {
        Class<?> current = getEntityClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column == null || !column.unique()) {
                    continue;
                }
                field.setAccessible(true);
                Object val;
                try {
                    val = field.get(entity);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (val == null) {
                    continue;
                }
                E search = getEntityInstance();
                try {
                    field.set(search, val);
                } catch (IllegalAccessException ignored) {
                }
                Optional<E> exist = repository.findOne(Example.of(search));
                if (exist.isEmpty()) {
                    continue;
                }
                if (entity.getId() != null && Objects.equals(exist.get().getId(), entity.getId())) {
                    continue;
                }
                Description desc = field.getAnnotation(Description.class);
                String label = desc != null ? desc.value() : field.getName();
                FORBIDDEN_EXIST.show(String.format("%s (%s) 已经存在，请修改后重新提交！", label, val));
            }
            current = current.getSuperclass();
        }
    }

    /**
     * 获取更新时需要忽略的属性（空值且无 @NullEnable）
     */
    private String @NotNull [] getUpdateIgnoreFields(@NotNull E source) {
        BeanWrapper srcBean = new BeanWrapperImpl(source);
        List<String> ignoreList = new ArrayList<>();
        for (PropertyDescriptor pd : srcBean.getPropertyDescriptors()) {
            String name = pd.getName();
            Field field = findField(source.getClass(), name);
            if (field == null) {
                continue;
            }
            NullEnable nullEnable = field.getAnnotation(NullEnable.class);
            if (nullEnable != null && nullEnable.value()) {
                continue;
            }
            if (srcBean.getPropertyValue(name) == null) {
                ignoreList.add(name);
            }
        }
        return ignoreList.toArray(new String[0]);
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * 创建导出任务
     */
    public String createExportTask(QueryPageRequest<E> queryPageRequest) {
        if (exportHelper != null) {
            return exportHelper.createExportTask(() -> {
                return "export_task_" + System.currentTimeMillis();
            });
        }
        return "export_task_" + System.currentTimeMillis();
    }

    /**
     * 查询导出结果
     */
    public final String queryExport(@NotNull QueryExport queryExport) {
        if (exportHelper != null) {
            return exportHelper.getExportFileUrl(queryExport.getFileCode());
        }
        return "";
    }

    /**
     * 仅用于已经选中 Mapper 读路径的分页；JPA 回退必须在调用此方法之前返回。
     * 旧 Page 的创建/默认值留在项目现有 base 边界，不扩散到业务服务。
     */
    protected final ReadPage mapperReadPage(Page requested) {
        Page page = requested == null ? new Page() : requested;
        return ReadPage.from(page.getPageNum(), page.getPageSize());
    }

    /**
     * 将读侧结果接回响应 DTO。保留原行引用、Sort 引用、字段名和 int 总数口径。
     */
    protected final QueryPageResponse<E> mapperPageResponse(ReadPage paging, List<E> rows, long total, Sort requestedSort) {
        ReadPage.Counts counts = paging.counts(total);
        QueryPageResponse<E> response = new QueryPageResponse<>();
        response.setList(rows);
        response.setTotal(counts.total());
        response.setPageCount(counts.pageCount());
        response.setPage(new Page().setPageNum(paging.pageNum()).setPageSize(paging.pageSize()));
        response.setSort(requestedSort == null ? new Sort() : requestedSort);
        return response;
    }

    /**
     * 当前服务的数据库最后一次确认
     */
    protected E beforeAppSaveToDatabase(@NotNull E entity) {
        return entity;
    }

    protected final @NotNull E beforeSaveToDatabase(@NotNull E entity) {
        CodeRuleService codeRuleService = SystemServices.getCodeRuleService();
        codeRuleService.fillFieldAutoCode(entity);
        enforceRowPermission(entity, entity.getId() == null ? DataPermissionAction.CREATE : DataPermissionAction.UPDATE);
        return beforeAppSaveToDatabase(entity);
    }

    /**
     * 写侧行级校验：既存行必须在当前主体的数据范围内
     */
    private void enforceRowPermission(@NotNull E entity, @NotNull DataPermissionAction action) {
        RowWriteGuard guard = SystemServices.getRowWriteGuard();
        if (guard == null) {
            return;
        }
        guard.enforce(entity, DataPermissionResourceResolver.resolve(getClass()), action);
    }

    protected E beforeAdd(@NotNull E entity) {
        SensitiveWriteProtection.rejectMaskedOnAdd(entity);
        enforceDataPermission(DataPermissionAction.CREATE);
        return entity;
    }

    protected E beforeUpdate(@NotNull E entity) {
        protectSensitiveFields(entity);
        protectAttribution(entity);
        enforceDataPermission(DataPermissionAction.UPDATE);
        stashOperationLogBefore(entity);
        return entity;
    }

    /**
     * 归属列防篡改：更新时把 {@code tenant_id}/{@code creator_id} 恢复为库中既存值
     */
    private void protectAttribution(@NotNull E entity) {
        if (!(entity instanceof TenantAwareEntity<?> owned)) {
            return;
        }
        Long id = entity.getId();
        if (id == null) {
            return;
        }
        E stored = repository.findById(id).orElse(null);
        if (!(stored instanceof TenantAwareEntity<?> storedOwned)) {
            return;
        }
        AttributionWriteProtection.restoreOnUpdate(owned, storedOwned.getTenantId(), storedOwned.getCreatorId());
    }

    /**
     * 受限字段的写侧兜底：提交值是脱敏占位时，用库里的原值覆盖回去
     */
    private void protectSensitiveFields(@NotNull E entity) {
        Long id = entity.getId();
        if (id == null) {
            return;
        }
        boolean fromRegistry = SensitiveWriteProtection.guards(entity.getClass())
                && MaskedInputRegistry.hasAny()
                && SensitiveWriteProtection.hasMaskedInput(entity.getClass());
        ColumnMaskingService maskingService = DataPermissionRuntime.maskingService();
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        boolean fromOutputMatch = SensitiveWriteProtection.mayContainMaskedOutput(
                entity.getClass(), maskingService, context);
        if (!fromRegistry && !fromOutputMatch) {
            return;
        }
        E stored = repository.findById(id).orElse(null);
        if (stored == null) {
            if (fromRegistry) {
                PARAM_INVALID.show("记录不存在或无权访问，敏感字段的脱敏值无法安全处理，请刷新后重试");
            }
            return;
        }
        SensitiveWriteProtection.restoreOnUpdate(entity, stored);
        SensitiveWriteProtection.restoreMatchingMaskedOutput(entity, stored, maskingService, context);
        MaskedInputRegistry.clear();
    }

    protected void beforeDelete(@NotNull E entity) {
        enforceDataPermission(DataPermissionAction.DELETE);
        enforceRowPermission(entity, DataPermissionAction.DELETE);
        recordOperationLog(OperationLogRecorder.ACTION_DELETE, entity.getId(), entity, null);
    }

    /**
     * 数据权限写入守卫：校验当前主体是否拥有该资源对应写入动作的权限
     */
    protected void enforceDataPermission(@NotNull DataPermissionAction action) {
        DataPermissionGuard dataPermissionGuard = SystemServices.getDataPermissionGuard();
        if (dataPermissionGuard == null) {
            return;
        }
        String resourceKey = DataPermissionResourceResolver.resolve(getClass());
        if (resourceKey == null) {
            return;
        }
        dataPermissionGuard.enforce(resourceKey, action);
    }

    /**
     * 发布
     */
    public final void publish(long id) {
        E entity = get(id);
        beforePublish(entity);
        updateToDatabase(getEntityInstance(id).setIsPublished(true));
    }

    protected void beforePublish(@NotNull E entity) {
        log.info("单据发布前，ID:{}", entity.getId());
    }

    protected E afterGet(@NotNull E entity) {
        return afterAppGet(entity);
    }

    protected E afterAppGet(@NotNull E entity) {
        return entity;
    }

    protected void afterAdd(long id, @NotNull E source) {
        recordOperationLog(OperationLogRecorder.ACTION_CREATE, id, null, source);
        afterAppAdd(id, source);
    }

    protected void afterAppAdd(long id, @NotNull E source) {
    }

    protected void afterUpdate(long id, @NotNull E source) {
        recordOperationLog(OperationLogRecorder.ACTION_UPDATE, id, operationLogBeforeSnapshot.get(), source);
        operationLogBeforeSnapshot.remove();
        afterAppUpdate(id, source);
    }

    protected void afterAppUpdate(long id, @NotNull E source) {
    }

    protected void afterSaved(long id, @NotNull E source) {
    }

    protected void afterDelete(long id) {
        afterAppDelete(id);
    }

    protected void afterAppDelete(long id) {
    }

    protected void beforeEnable(@NotNull E entity) {
    }

    protected void afterEnable(long id) {
    }

    protected void beforeDisable(@NotNull E entity) {
    }

    protected void afterDisable(long id) {
    }

    protected QueryListRequest<E> beforeGetList(QueryListRequest<E> queryListRequest) {
        return queryListRequest;
    }

    protected List<E> afterGetList(List<E> list) {
        return list;
    }

    protected QueryPageRequest<E> beforeGetPage(QueryPageRequest<E> queryPageRequest) {
        return queryPageRequest;
    }

    protected QueryPageResponse<E> afterGetPage(QueryPageResponse<E> queryPageResponse) {
        return queryPageResponse;
    }

    protected E beforeCreatePredicate(@NotNull E filter) {
        return filter;
    }

    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<E> root,
            @NotNull CriteriaBuilder builder,
            @NotNull E search
    ) {
        return new ArrayList<>();
    }

    protected QueryPageRequest<E> beforeExportQuery(QueryPageRequest<E> queryPageRequest) {
        return queryPageRequest;
    }

    protected List<E> afterExportQuery(List<E> entities) {
        return CsvFormulaSanitizer.sanitize(ExportColumnMasker.maskForExport(entities));
    }

    private void stashOperationLogBefore(@NotNull E entity) {
        if (operationLogRecorder == null || !isOperationLogged(entity)) {
            return;
        }
        Long id = entity.getId();
        operationLogBeforeSnapshot.set(id == null ? null : repository.findById(id).orElse(null));
    }

    private boolean isOperationLogged(@NotNull E entity) {
        return entity.getClass().isAnnotationPresent(OperationLog.class);
    }

    private void recordOperationLog(@NotNull String action, Long targetId, E before, E after) {
        if (operationLogRecorder == null) {
            return;
        }
        E source = after != null ? after : before;
        if (source == null || !isOperationLogged(source)) {
            return;
        }
        String resourceKey = DataPermissionResourceResolver.resolve(getClass());
        if (resourceKey == null) {
            return;
        }
        operationLogRecorder.record(resourceKey, action, targetId, before, after);
    }
}
