package cn.hamm.spms.framework.curd.base;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名：
 *   cn.hamm.airpower.core  → cn.hamm.spms.core
 *   cn.hamm.airpower.api   → cn.hamm.spms.framework.api
 *   cn.hamm.airpower.curd  → cn.hamm.spms.framework.curd
 *   cn.hamm.airpower.redis → cn.hamm.spms.framework.redis
 * 代码逻辑一字未改（JPA→MyBatis-Plus 的持久化替换是紧随其后的独立一批，见 docs）。
 * 从此这一层由本仓拥有：改代码改这里，不再有"上游"。
 */

import cn.hamm.spms.core.CollectionUtil;
import cn.hamm.spms.core.ReflectUtil;
import cn.hamm.spms.core.TaskUtil;
import cn.hamm.spms.core.TraceUtil;
import cn.hamm.spms.core.exception.ServiceException;
import cn.hamm.spms.framework.curd.annotation.NullEnable;
import cn.hamm.spms.framework.curd.helper.ExportHelper;
import cn.hamm.spms.framework.curd.helper.TransactionHelper;
import cn.hamm.spms.framework.curd.model.query.*;
import cn.hamm.spms.framework.curd.service.RootService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.persistence.Column;
import jakarta.validation.constraints.Null;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static cn.hamm.spms.common.exception.Errors.*;

/**
 * <h1>实体根服务</h1>
 *
 * @param <E> 实体
 * @param <R> 数据源
 * @author Hamm.cn
 * @see #getList(QueryListRequest) 通用列表查询 <code>getList(QueryListRequest)</code>
 * @see #getPage(QueryPageRequest) 通用分页查询 <code>getPage(QueryPageRequest)</code>
 * @see #filter(CurdEntity) 实体强匹配列表搜索 <code>filter(CurdEntity)</code>
 * @see #query(CurdEntity) 实体模糊匹配列表搜索 <code>query(CurdEntity)</code>
 * @see #repository 还实现不了？<code>repository</code> 给你，你自己来
 */
@Slf4j
public class CurdService<E extends CurdEntity<E>, R extends ICurdRepository<E>> extends RootService<E> {
    /**
     * 提交的数据不允许为空
     */
    private static final String DATA_REQUIRED = "提交的数据不允许为空";
    /**
     * 数据源
     */
    @Autowired(required = false)
    protected R repository;
    /**
     * 事务管理器
     */
    @Autowired
    protected TransactionHelper transactionHelper;

    /**
     * 查询辅助
     */
    @Autowired
    private QueryHelper queryHelper;

    /**
     * 添加一条数据 {@code 触发前后置}
     *
     * @param source 原始实体
     * @return 主键 ID
     * @apiNote 如需绕过前后置处理，请使用 {@link #addToDatabase(E)}
     * @see #beforeAdd(E)
     * @see #beforeSaveToDatabase(E)
     * @see #afterAdd(long, E)
     * @see #afterSaved(long, E)
     */
    public final long add(@NotNull E source) {
        source = beforeAdd(source);
        SERVICE_ERROR.whenNull(source, DATA_REQUIRED);

        // 新增不允许带主键
        source.setId(null);
        long id = addToDatabase(source);
        final E finalSource = source;

        // 新增完毕后的一些后置处理
        TaskUtil.run(
                () -> afterAdd(id, finalSource),
                () -> afterSaved(id, finalSource)
        );
        return id;
    }

    /**
     * 添加到数据库 {@code 不触发前后置}
     *
     * @param source 原始实体
     * @return 添加的主键
     * @see #add(CurdEntity) 触发前后置的添加方法
     */
    public final long addToDatabase(@NotNull E source) {
        PARAM_MISSING.whenNotNull(source.getId(), String.format("添加失败，请不要传入%s的ID!", getEntityDescription()));
        return saveToDatabase(source, false);
    }

    /**
     * 添加并获取
     *
     * @param source 原始实体
     * @return 添加后的实体
     */
    public final @NotNull E addAndGet(@NotNull E source) {
        return get(add(source));
    }

    /**
     * 删除指定的数据
     *
     * @param id 主键
     * @see #beforeDelete(E)
     * @see #afterDelete(long)
     */
    public final void delete(long id) {
        E entity = get(id);
        beforeDelete(entity);
        repository.deleteById(id);
        TaskUtil.run(() -> afterDelete(id));
    }

    /**
     * 修改一条已经存在的数据 {@code 触发前后置}
     *
     * @param source 修改的实体
     * @apiNote 如需绕过前后置处理，请使用 {@link #updateToDatabase(E)}
     * @see #beforeUpdate(E)
     * @see #beforeSaveToDatabase(E)
     * @see #afterUpdate(long, E)
     * @see #afterSaved(long, E)
     */
    public final void update(@NotNull E source) {
        update(source, false);
    }

    /**
     * 修改一条已经存在的数据 {@code 触发前后置}
     *
     * @param source   原始实体
     * @param withNull 是否允许修改为 null
     * @apiNote 如需绕过前后置处理，请使用 {@link #updateToDatabase(E)}
     * @see #beforeUpdate(E)
     * @see #beforeSaveToDatabase(E)
     * @see #afterUpdate(long, E)
     * @see #afterSaved(long, E)
     */
    public final void update(@NotNull E source, boolean withNull) {
        long id = source.getId();
        source = beforeUpdate(source);
        updateToDatabase(source, withNull);
        final E finalSource = source;
        TaskUtil.run(
                () -> afterUpdate(id, finalSource),
                () -> afterSaved(id, finalSource)
        );
    }

    /**
     * 加锁更新指定 ID 的数据 {@code 不触发前后置}、{@code 加锁}
     *
     * @param id       主键 ID
     * @param consumer 可消费实体
     */
    public final void updateWithLock(long id, Consumer<E> consumer) {
        transactionHelper.run(() -> {
            E exist = getForUpdate(id);
            consumer.accept(exist);
            updateToDatabase(exist);
        });
    }

    /**
     * 更新到数据库 {@code 不触发前后置}
     *
     * @param source 原始实体
     * @see #update(CurdEntity) 触发前后置的修改方法
     */
    public final void updateToDatabase(@NotNull E source) {
        updateToDatabase(source, false);
    }

    /**
     * 更新到数据库 {@code 不触发前后置}
     *
     * @param source   原始实体
     * @param withNull 是否更新空值
     */
    public final void updateToDatabase(@NotNull E source, boolean withNull) {
        SERVICE_ERROR.whenNull(source, DATA_REQUIRED);
        PARAM_MISSING.whenNull(source.getId(), String.format("修改失败，请传入%s的ID!", getEntityDescription()));
        saveToDatabase(source, withNull);
    }

    /**
     * 启用指定的数据
     *
     * @param id 主键 ID
     * @see #beforeEnable(E)
     * @see #afterEnable(long)
     */
    public final void enable(long id) {
        E entity = get(id);
        beforeEnable(entity);
        updateToDatabase(getEntityInstance(id).setIsDisabled(false));
        TaskUtil.run(() -> afterEnable(id));
    }

    /**
     * 禁用指定的数据
     *
     * @param id 主键 ID
     * @see #beforeDisable(E)
     * @see #afterDisable(long)
     */
    public final void disable(long id) {
        E entity = get(id);
        beforeDisable(entity);
        updateToDatabase(getEntityInstance(id).setIsDisabled(true));
        TaskUtil.run(() -> afterDisable(id));
    }

    /**
     * 不分页查询数据
     *
     * @param queryListRequest 列表请求对象
     * @return List 数据
     * @see #beforeGetList(QueryListRequest)
     * @see #afterGetList(List)
     */
    public final @NotNull List<E> getList(QueryListRequest<E> queryListRequest) {
        queryListRequest = requireQueryRequestNonNullElse(queryListRequest, new QueryListRequest<>());
        queryListRequest = beforeGetList(queryListRequest);
        List<E> list = query(queryListRequest.getFilter(), queryListRequest.getSort());
        return afterGetList(list);
    }

    /**
     * 分页查询数据
     *
     * @param queryPageRequest 请求的分页对象
     * @return 分页查询列表
     * @see #beforeGetPage(QueryPageRequest)
     * @see #afterGetPage(QueryPageResponse)
     */
    public final @NotNull QueryPageResponse<E> getPage(
            QueryPageRequest<E> queryPageRequest
    ) {
        queryPageRequest = requireQueryRequestNonNullElse(queryPageRequest, new QueryPageRequest<>());
        queryPageRequest = beforeGetPage(queryPageRequest);
        PageData<E> pageData = queryPage(queryPageRequest.getPage(), queryPageRequest.getFilter(), queryPageRequest.getSort());
        // 组装分页数据
        QueryPageResponse<E> queryPageResponse = QueryPageResponse.from(pageData);
        queryPageResponse.setSort(queryPageRequest.getSort());
        queryPageResponse = afterGetPage(queryPageResponse);
        return queryPageResponse;
    }

    /**
     * 根据 ID 查询对应的实体
     *
     * @param id 主键
     * @return 实体
     * @see #getMaybeNull(long)
     * @see #getWithEnable(long)
     */
    public final @NotNull E get(long id) {
        return afterGet(getById(id));
    }

    /**
     * 根据主键查询对应的实体
     *
     * @param id 主键
     * @return 实体
     * @apiNote 查不到返回 {@code null}，不抛异常
     */
    public final @Nullable E getMaybeNull(long id) {
        try {
            return get(id);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 加锁获取指定 ID 的数据用户更新
     *
     * @param id 主键 ID
     * @return 加锁后的数据
     * @see #updateWithLock(long, Consumer)
     */
    public final @NotNull E getForUpdate(long id) {
        // 行锁子句在 autocommit 下等于没加锁：拿不到锁、也不报错，只是并发时静默丢写。
        // 旧实现挂在 Mapper 接口上的 @Lock(PESSIMISTIC_WRITE) 是否真被织入存疑，所以这里
        // 不赌：没有真实事务就直接抛，把"以为是原子的读-改-写"变成一条立刻可见的错误。
        // 报错文案里不出现 SQL 关键字：`pnpm sql:check` 认字面量不认意图，
        // 而这条规则唯一的豁免口径是测试里的"非可执行 SQL"，生产代码不留豁免（见 CODE_STYLE.md）。
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            SERVICE_ERROR.show(String.format(
                    "加锁查询 %s(ID=%s) 必须在事务内调用：当前没有活动事务，语句带的写锁不会生效",
                    getEntityDescription(), id));
        }
        // 语句住在各实体 Mapper XML 的 selectByIdForUpdate 里（SQL 不落 Java），
        // 且带 flushCache="true" —— 它替代了 JPA 时代的 entityManager.clear()：
        // 保证同一事务里重复调用会真的再发一次 SQL，而不是命中 MyBatis 的本地缓存。
        E forUpdate = repository.selectByIdForUpdate(id);
        DATA_NOT_FOUND.whenNull(forUpdate, String.format("没有查询到ID为%s的%s", id, getEntityDescription()));
        return forUpdate;
    }

    /**
     * 根据 ID 查询正常启用的实体
     *
     * @param id 主键
     * @return 实体
     * @see #get(long)
     * @see #getMaybeNull(long)
     */
    public final @NotNull E getWithEnable(long id) {
        E entity = get(id);
        FORBIDDEN_DISABLED.when(
                entity.getIsDisabled(),
                String.format(FORBIDDEN_DISABLED.getMessage(), id, getEntityDescription())
        );
        return entity;
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @return List 数据
     */
    public final @NotNull List<E> filter(@Nullable E filter) {
        return filter(filter, null);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @param sort   排序
     * @return List 数据
     */
    public final @NotNull List<E> filter(@Nullable E filter, @Nullable Sort sort) {
        return find(filter, sort, true);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> filterPage(@Nullable E filter) {
        return filterPage(filter, null, null);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @param page   分页对象
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> filterPage(@Null E filter, @Nullable Page page) {
        return filterPage(filter, page, null);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @param page   分页对象
     * @param sort   排序
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> filterPage(
            @Nullable E filter,
            @Nullable Page page,
            @Nullable Sort sort
    ) {
        return findPage(filter, page, sort, true);
    }

    /**
     * 模糊匹配查询数据
     *
     * @param filter 过滤条件
     * @return 查询结果数据列表
     */
    public final @NotNull List<E> query(@Nullable E filter) {
        return query(filter, null);
    }

    /**
     * 模糊匹配查询数据
     *
     * @param filter 过滤条件
     * @param sort   排序
     * @return 查询结果数据列表
     */
    public final @NotNull List<E> query(E filter, @Nullable Sort sort) {
        return find(filter, sort, false);
    }

    /**
     * 查询分页数据
     *
     * @param page 分页
     * @return 查询结果数据分页对象
     */
    @SuppressWarnings("unused")
    public final @NotNull PageData<E> queryPage(
            @NotNull Page page
    ) {
        return queryPage(page, getEntityInstance());
    }

    /**
     * 查询分页数据
     *
     * @param page   分页
     * @param filter 查询条件
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> queryPage(
            @NotNull Page page,
            @NotNull E filter
    ) {
        return queryPage(page, filter, null);
    }

    /**
     * 查询分页数据
     *
     * @param page   分页
     * @param filter 查询条件
     * @param sort   排序
     * @return 查询结果数据分页对象
     */
    @SuppressWarnings("unused")
    public final @NotNull PageData<E> queryPage(
            @NotNull Page page,
            @NotNull E filter,
            @Nullable Sort sort
    ) {
        return findPage(filter, page, sort, false);
    }

    /**
     * 创建导出任务
     *
     * @param queryPageRequest 请求查询的分页参数
     * @return 导出任务 ID
     */
    public final String createExportTask(QueryPageRequest<E> queryPageRequest) {
        final QueryPageRequest<E> finalQueryPageRequest = requireQueryRequestNonNullElse(queryPageRequest, new QueryPageRequest<>());
        String traceId = TraceUtil.getTraceId();
        return exportHelper.createExportTask(() -> {
            TraceUtil.setTraceId(traceId);
            ExportHelper.ExportFile exportFile = exportHelper.getExportFilePath("csv");
            // 获取导出字段列表
            List<Field> fieldList = CollectionUtil.getExportFieldList(getEntityClass());
            // 获取一行用作于表头
            List<String> rowList = CollectionUtil.getCsvHeaderList(fieldList);
            String headerString = String.join(CollectionUtil.CSV_COLUMN_DELIMITER, rowList);
            List<String> header = new ArrayList<>();
            header.add(headerString);
            // 保存表头到 CSV 文件
            ExportHelper.saveCsvListToFile(exportFile, header);
            // 查询数据并保存到导出文件
            queryPageToSaveExportFile(finalQueryPageRequest, fieldList, exportFile);
            return exportFile.getRelativeFile();
        });
    }

    /**
     * 详情查询后置方法
     *
     * @param entity 查到的数据
     * @return 处理后的数据
     */
    protected E afterGet(@NotNull E entity) {
        return entity;
    }

    /**
     * 不分页查询后置方法
     *
     * @param list 查询到的数据
     * @return 处理后的数据
     * @see #getPage(QueryPageRequest)
     */
    protected @NotNull List<E> afterGetList(@NotNull List<E> list) {
        return list;
    }

    /**
     * 分页查询前置方法
     *
     * @param sourceRequestData 原始请求的数据
     * @return 处理后的请求数据
     */
    protected @NotNull QueryPageRequest<E> beforeGetPage(@NotNull QueryPageRequest<E> sourceRequestData) {
        return sourceRequestData;
    }

    /**
     * 分页查询后置方法
     *
     * @param queryPageResponse 查询到的数据
     * @return 处理后的数据
     */
    protected @NotNull QueryPageResponse<E> afterGetPage(@NotNull QueryPageResponse<E> queryPageResponse) {
        return queryPageResponse;
    }

    /**
     * 数据库操作前的 {@code 最后一次} 确认
     *
     * @return 当前实体
     */
    protected @NotNull E beforeSaveToDatabase(@NotNull E entity) {
        return entity;
    }

    /**
     * 导出查询后置方法
     *
     * @param exportList 导出的数据列表
     * @return 处理后的数据列表
     */
    protected List<E> afterExportQuery(@NotNull List<E> exportList) {
        return exportList;
    }

    /**
     * 导出查询前置方法
     *
     * @param queryPageRequest 查询请求
     * @return 处理后的查询请求
     */
    protected QueryPageRequest<E> beforeExportQuery(QueryPageRequest<E> queryPageRequest) {
        return queryPageRequest;
    }

    /**
     * 添加前置方法
     *
     * @param source 原始实体
     * @return 处理后的实体
     */
    protected @NotNull E beforeAdd(@NotNull E source) {
        return source;
    }

    /**
     * 添加后置方法
     *
     * @param id     主键 ID
     * @param source 原始实体
     */
    protected void afterAdd(long id, @NotNull E source) {
    }

    /**
     * 修改前置方法
     *
     * @param source 原始实体
     * @return 处理后的实体
     */
    protected @NotNull E beforeUpdate(@NotNull E source) {
        return source;
    }

    /**
     * 修改后置方法
     *
     * <p>
     * 请不要在重写此方法后再次调用 {@link #update(E)} 以 {@code 避免循环调用}
     * </p>
     * <p>
     * 如需再次保存，请调用 {@link #updateToDatabase(E)}
     * </p>
     *
     * @param id     更新的 ID
     * @param source 更新前的数据
     */
    protected void afterUpdate(long id, @NotNull E source) {
    }

    /**
     * 保存后置方法
     *
     * @param id     主键 ID
     * @param source 保存前的原数据
     * @apiNote 添加或修改后最后触发
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterSaved(long id, @NotNull E source) {
    }

    /**
     * 禁用前置方法
     *
     * @param entity 禁用的数据
     */
    protected void beforeDisable(@NotNull E entity) {
    }

    /**
     * 禁用后置方法
     *
     * @param id 主键 ID
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterDisable(long id) {
    }

    /**
     * 启用前置方法
     *
     * @param entity 启用的数据
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void beforeEnable(@NotNull E entity) {
    }

    /**
     * 启用后置方法
     *
     * @param id 主键 ID
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterEnable(long id) {
    }

    /**
     * 删除前置方法
     *
     * @param entity 删除的数据
     */
    protected void beforeDelete(@NotNull E entity) {
    }

    /**
     * 删除后置方法
     *
     * @param id 主键
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterDelete(long id) {
    }

    /**
     * 不分页查询前置方法
     *
     * @param sourceRequestData 查询条件
     * @return 处理后的查询条件
     * @see #getList(QueryListRequest)
     */
    protected @NotNull QueryListRequest<E> beforeGetList(@NotNull QueryListRequest<E> sourceRequestData) {
        return sourceRequestData;
    }

    /**
     * 新建一个实体
     *
     * @return 实体
     */
    protected final @NotNull E getEntityInstance() {
        return ReflectUtil.newInstance(getEntityClass());
    }

    /**
     * 新建一个实体
     *
     * @param id 实体主键 ID
     * @return 实体
     */
    protected final @NotNull E getEntityInstance(long id) {
        return getEntityInstance().setId(id);
    }

    /**
     * 查询数据
     *
     * @param filter   过滤条件
     * @param sort     排序
     * @param isEquals 是否全匹配
     * @return 查询结果数据列表
     */
    private @NotNull List<E> find(@Nullable E filter, @Nullable Sort sort, boolean isEquals) {
        QueryWrapper<E> wrapper = createWrapper(filter, isEquals);
        queryHelper.applySort(wrapper, getEntityClass(), sort);
        return repository.selectList(wrapper);
    }

    /**
     * 查询分页数据
     *
     * @param filter   查询条件
     * @param page     分页
     * @param sort     排序
     * @param isEquals 是否全匹配
     * @return 查询结果数据分页对象
     */
    private @NotNull PageData<E> findPage(
            @Nullable E filter,
            @Nullable Page page,
            @Nullable Sort sort,
            boolean isEquals
    ) {
        QueryWrapper<E> wrapper = createWrapper(filter, isEquals);
        queryHelper.applySort(wrapper, getEntityClass(), sort);
        return PageData.newInstance(repository.selectPage(queryHelper.createPage(page), wrapper));
    }

    /**
     * 验证非空查询请求
     *
     * @param queryListRequest 查询请求
     * @param newInstance      新实例
     * @return 检查后的查询请求
     */
    private <Q extends QueryListRequest<E>> @NotNull Q requireQueryRequestNonNullElse(
            Q queryListRequest, Q newInstance) {
        queryListRequest = Objects.requireNonNullElse(queryListRequest, newInstance);
        queryListRequest.setFilter(requireFilterNonNull(queryListRequest.getFilter()));
        queryListRequest.setSort(queryHelper.requireSortNonNull(queryListRequest.getSort()));
        if (queryListRequest instanceof QueryPageRequest<?> queryPageRequest) {
            queryPageRequest.setPage(queryHelper.requirePageNonNull(queryPageRequest.getPage()));
        }
        return queryListRequest;
    }

    /**
     * 验证非空过滤器请求
     *
     * @param filter 过滤器
     * @return 检查后的过滤器
     */
    @Contract("!null -> param1")
    private E requireFilterNonNull(E filter) {
        return Objects.requireNonNullElse(filter, getEntityInstance());
    }

    /**
     * 添加搜索的查询条件
     *
     * @param wrapper 查询条件（直接往上挂：{@code wrapper.eq(...)} / {@code wrapper.apply(...)}）
     * @param search  原始查询对象
     * @apiNote 如需要删除自动添加的查询条件，请调用 {@link #beforeCreatePredicate(CurdEntity)}
     * <p>J2 之前这个钩子返回 {@code List<Predicate>}（JPA Criteria）。换轨时**签名一起换**：
     * 只换引擎不换覆写，覆写会退化成普通方法 —— 编译不报错、运行时永不调用，
     * 查询条件与权限条件一起静默消失。判据见 {@code maskContract.spec.ts}（旧签名残留即红）。</p>
     * <p>参数类型选 {@code QueryWrapper} 而不是 {@code LambdaQueryWrapper}：权限片段必须走
     * {@code apply(sql, values)} 注入，且当前实体还没有标量外键字段（{@code storageId}）可写方法引用；
     * J3 让实体带上外键列后，具体覆写可以逐步换成方法引用。</p>
     */
    protected void addSearchPredicate(@NotNull QueryWrapper<E> wrapper, @NotNull E search) {
    }

    /**
     * 根据主键查询对应的实体
     *
     * @param id 主键
     * @return 实体
     */
    private @NotNull E getById(Long id) {
        String description = getEntityDescription();
        PARAM_MISSING.whenNull(id, String.format("查询失败，请传入%s的ID！", description));
        // 旧实现这里是 entityManager.clear() + findById：clear 是为了躲开 JPA 的一级缓存
        // （同一事务里拿到陈旧实体）。MyBatis 没有一级缓存这回事（本地缓冲随写语句清理），
        // 所以这一句是可以放心删的（§10.3 写明了依据），删掉的后果是"少一次无意义的清理"。
        E entity = repository.selectById(id);
        if (Objects.isNull(entity)) {
            throw new ServiceException(DATA_NOT_FOUND, String.format("没有查询到ID为%s的%s", id, description));
        }
        return entity;
    }

    /**
     * 保存到数据库
     *
     * @param entity   待保存实体
     * @param withNull 是否保存空值
     * @return 保存的主键
     * @see #addToDatabase(CurdEntity)
     * @see #updateToDatabase(CurdEntity)
     */
    private long saveToDatabase(@NotNull E entity, boolean withNull) {
        checkUnique(entity);
        entity.setUpdateTime(System.currentTimeMillis());
        if (Objects.isNull(entity.getId())) {
            // 设置当前时间为创建时间
            entity.setCreateTime(System.currentTimeMillis())
                    .setIsDisabled(false);
            // 新增
            return persist(entity, false);
        }
        // 更新 不允许修改创建时间
        entity.setCreateTime(null);
        // 有ID 走修改 且不允许修改下列字段
        E existEntity = getById(entity.getId());
        entity = withNull ? entity : getEntityForUpdate(entity, existEntity);
        return persist(entity, withNull);
    }

    /**
     * 落库（插入或整行更新）
     *
     * @param entity   保存的实体
     * @param withNull 是否把 null 也真的写进库
     * @return 保存的主键
     * @apiNote 仅供 {@link #saveToDatabase(E, boolean)} 调用；`saveAndFlush` 的语义由
     * {@code ICurdRepository#save/saveWithNull} 两个垫片承担（id 为空 insert、否则 updateById）。
     * MP 的 {@code updateById} 默认跳过 null 列，与"null = 不改这一列"的引擎语义一致；
     * 只有 {@code withNull = true} 这一条路要逐列写（并且跳过 create_time）。
     */
    private long persist(@NotNull E entity, boolean withNull) {
        E target = getEntityInstance();
        BeanUtils.copyProperties(entity, target);
        target = beforeSaveToDatabase(target);
        target = withNull ? repository.saveWithNull(target) : repository.save(target);
        return target.getId();
    }

    /**
     * 获取用于更新的实体
     *
     * @param source 来源实体
     * @param exist  已存在实体
     * @return 目标实体
     */
    @Contract("_, _ -> param2")
    private @NotNull E getEntityForUpdate(@NotNull E source, @NotNull E exist) {
        desensitize(source);
        String[] ignoreProperties = getUpdateIgnoreFields(source);
        BeanUtils.copyProperties(source, exist, ignoreProperties);
        return exist;
    }

    /**
     * 获取实体类
     *
     * @return 实体类
     * @apiNote J2 起改为 {@code protected}：子类的 {@code addSearchPredicate} 要把它交给
     * 数据权限适配层（渲染器要按实体登记的表名/资源名工作）
     */
    protected @NotNull Class<E> getEntityClass() {
        return getFirstParameterizedTypeClass();
    }

    /**
     * 判断是否唯一
     *
     * @param entity 实体
     */
    private void checkUnique(@NotNull E entity) {
        List<Field> fields = ReflectUtil.getFieldList(getEntityClass());
        fields.forEach(field -> {
            Column annotation = ReflectUtil.getAnnotation(Column.class, field);
            if (Objects.isNull(annotation)) {
                // 不是数据库列 不校验
                return;
            }
            if (!annotation.unique()) {
                // 没有标唯一 不校验
                return;
            }
            Object fieldValue = ReflectUtil.getFieldValue(entity, field);
            if (Objects.isNull(fieldValue)) {
                // 没有值 不校验
                return;
            }
            E search = getEntityInstance();
            ReflectUtil.setFieldValue(search, field, fieldValue);
            // 旧实现是 Example.of(search) + findOne：JPA 的 QBE。换成 wrapper 后条件完全相同
            // （唯一列上的等值匹配），只是"修改自己"这一条从"查出 id 再比对"变成 SQL 里的 id <> 自己：
            // 一次查询、且不会因为唯一列上的重复行只取一条而误判。
            QueryWrapper<E> wrapper = queryHelper.createWrapper(search, true);
            if (Objects.nonNull(entity.getId())) {
                wrapper.ne(CurdEntity.STRING_ID, entity.getId());
            }
            Long existCount = repository.selectCount(wrapper);
            if (Objects.isNull(existCount) || existCount == 0) {
                // 没查到 不校验
                return;
            }
            FORBIDDEN_EXIST.show(String.format("%s (%s) 已经存在，请修改后重新提交！",
                    ReflectUtil.getDescription(field), fieldValue)
            );
        });
    }

    /**
     * 获取忽略更新的字段名称列表
     *
     * @param source 来源对象
     * @return 需要忽略更新的属性列表
     */
    private String @NotNull [] getUpdateIgnoreFields(@NotNull E source) {
        // 获取 Bean
        BeanWrapper srcBean = new BeanWrapperImpl(source);
        List<String> ignoreList = new ArrayList<>();
        Arrays.stream(srcBean.getPropertyDescriptors()).map(PropertyDescriptor::getName).forEach(name -> {
            // 获取属性的 Field
            Field field = ReflectUtil.getField(name, source.getClass());
            if (Objects.isNull(field)) {
                // 获取属性失败，允许更新
                return;
            }
            NullEnable nullEnable = ReflectUtil.getAnnotation(NullEnable.class, field);
            if (Objects.nonNull(nullEnable) && nullEnable.value()) {
                // 允许更新 null
                return;
            }
            if (Objects.isNull(srcBean.getPropertyValue(name))) {
                // 没有值 忽略更新
                ignoreList.add(name);
            }
        });
        return ignoreList.toArray(new String[0]);
    }

    /**
     * 在创建查询条件前调用
     *
     * @param filter 过滤器
     * @return 处理后的过滤器
     * @apiNote 此处理不影响 {@link #addSearchPredicate(QueryWrapper, CurdEntity)} 的 {@code search} 参数
     */
    protected E beforeCreatePredicate(@NotNull E filter) {
        return filter;
    }

    /**
     * 创建查询条件
     *
     * @param filter  过滤器对象
     * @param isEqual 是否强匹配
     * @return 查询条件
     */
    @Contract(pure = true)
    private @NotNull QueryWrapper<E> createWrapper(@Nullable E filter, boolean isEqual) {
        final E finalFilter = requireFilterNonNull(filter);
        // 建条件前处理 filter 的**副本**（旧注释写明了：此处理不影响 addSearchPredicate 的 search 参数）
        E searchFilter = beforeCreatePredicate(finalFilter.copy());
        QueryWrapper<E> wrapper = queryHelper.createWrapper(searchFilter, isEqual);
        // 添加更多自定义查询条件：钩子拿到的是原始 filter，不是副本
        addSearchPredicate(wrapper, finalFilter);
        return wrapper;
    }

    /**
     * 分页查询导出数据
     *
     * @param queryPageRequest 查询对象
     */
    private void queryPageToSaveExportFile(QueryPageRequest<E> queryPageRequest, List<Field> fieldList, ExportHelper.ExportFile exportFile) {
        queryPageRequest = beforeExportQuery(queryPageRequest);
        PageData<E> page = queryPage(queryPageRequest.getPage(), queryPageRequest.getFilter(), queryPageRequest.getSort());
        String description = getEntityDescription();
        log.info("导出{} 查询第 {} 页，本页 {} 条", description, page.getPage().getPageNum(), page.getList().size());
        // 当前页查到的数据列表
        List<E> list = page.getList();
        list = afterExportQuery(list);

        // 获取 CSV 值列表
        List<String> valuelist = CollectionUtil.getCsvValueList(list, fieldList);

        // 保存 CSV 数据
        ExportHelper.saveCsvListToFile(exportFile, valuelist);

        if (page.getPage().getPageNum() < page.getPageCount()) {
            // 继续分页
            queryPageRequest.getPage().setPageNum(page.getPage().getPageNum() + 1);
            queryPageToSaveExportFile(queryPageRequest, fieldList, exportFile);
        }
    }

    /**
     * 获取实体描述
     *
     * @return 实体描述
     */
    private String getEntityDescription() {
        return ReflectUtil.getDescription(getEntityClass());
    }
}
