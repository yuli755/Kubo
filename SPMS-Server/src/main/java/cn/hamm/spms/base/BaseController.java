package cn.hamm.spms.base;

import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.annotation.Extends;
import cn.hamm.spms.base.Curd;
import cn.hamm.spms.framework.query.QueryExport;
import cn.hamm.spms.framework.web.annotation.Permission;
import cn.hamm.spms.framework.http.ApiResponse;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.query.QueryPageResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

import static cn.hamm.spms.common.exception.Errors.API_SERVICE_UNSUPPORTED;
import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_EDIT;

/**
 * <h1>实体控制器基类</h1>
 *
 * <p>自持核心实体控制器基类，替代旧 {@code CurdController}。
 * 统一暴露标准 {@link ApiResponse} 契约端点，支持 {@link Extends} 黑白名单控制与生命周期钩子。</p>
 *
 * @param <E> 实体类型
 * @param <S> 实体 Service 类型
 * @param <R> 实体 Repository 类型
 * @author Hamm.cn
 */
@Slf4j
@Permission
@Extends(exclude = {Curd.Export, Curd.QueryExport, Curd.Disable, Curd.Enable})
public class BaseController<
        E extends BaseEntity<E>,
        S extends BaseService<E, R>,
        R extends BaseRepository<E>
        > extends ApiController implements CurdAction {

    @Autowired(required = false)
    protected S service;

    /**
     * 校验当前控制器是否暴露该 CRUD 接口
     */
    protected void checkApiAvailable(@NotNull Curd curd) {
        List<Curd> curdList = Curd.getCurdList(getClass());
        API_SERVICE_UNSUPPORTED.when(!curdList.contains(curd), "当前接口不支持此操作");
    }

    /**
     * 获取实体类型
     */
    @SuppressWarnings("unchecked")
    public Class<E> getEntityClass() {
        Type superclass = getClass().getGenericSuperclass();
        while (superclass instanceof Class<?> clazz && clazz != BaseController.class) {
            superclass = clazz.getGenericSuperclass();
        }
        if (superclass instanceof ParameterizedType parameterizedType) {
            return (Class<E>) parameterizedType.getActualTypeArguments()[0];
        }
        throw new IllegalStateException("无法解析实体类类型: " + getClass().getName());
    }

    @Description("添加")
    @PostMapping("add")
    public ApiResponse<E> add(@RequestBody @Validated(WhenAdd.class) E source) {
        checkApiAvailable(Curd.Add);
        source.excludeReadOnly();
        source = beforeAdd(source);
        long id = service.add(source);
        afterAdd(id, source);
        afterSaved(id, source);
        return ApiResponse.data(service.getEntityInstance(id), "添加成功");
    }

    @Description("修改")
    @PostMapping("update")
    public ApiResponse<E> update(@RequestBody @Validated(WhenUpdate.class) @NotNull E source) {
        checkApiAvailable(Curd.Update);
        source.excludeReadOnly();
        source = beforeUpdate(source);
        service.update(source);
        afterUpdate(source.getId(), source);
        afterSaved(source.getId(), source);
        return ApiResponse.data(service.getEntityInstance(source.getId()), "修改成功");
    }

    @Description("删除")
    @PostMapping("delete")
    public ApiResponse<E> delete(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        checkApiAvailable(Curd.Delete);
        E entity = service.get(source.getId());
        beforeDelete(entity);
        service.delete(entity.getId());
        afterDelete(entity.getId());
        return ApiResponse.data(service.getEntityInstance(entity.getId()), "删除成功");
    }

    @Description("查询详情")
    @PostMapping("getDetail")
    public ApiResponse<E> getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        checkApiAvailable(Curd.GetDetail);
        return ApiResponse.data(afterGetDetail(service.get(source.getId())));
    }

    @Description("不分页查询")
    @PostMapping("getList")
    public ApiResponse<List<E>> getList(@RequestBody QueryListRequest<E> queryListRequest) {
        checkApiAvailable(Curd.GetList);
        QueryListRequest<E> req = beforeGetList(Objects.requireNonNullElse(queryListRequest, new QueryListRequest<>()));
        return ApiResponse.data(afterGetList(service.getList(req)));
    }

    @Description("分页查询")
    @PostMapping("getPage")
    public ApiResponse<QueryPageResponse<E>> getPage(@RequestBody QueryPageRequest<E> queryPageRequest) {
        checkApiAvailable(Curd.GetPage);
        QueryPageRequest<E> req = beforeGetPage(Objects.requireNonNullElse(queryPageRequest, new QueryPageRequest<>()));
        return ApiResponse.data(afterGetPage(service.getPage(req)));
    }

    @Description("启用")
    @PostMapping("enable")
    public ApiResponse<E> enable(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        checkApiAvailable(Curd.Enable);
        long id = source.getId();
        E entity = service.get(id);
        beforeEnable(entity);
        service.enable(id);
        afterEnable(id);
        return ApiResponse.data(service.getEntityInstance(id), "启用成功");
    }

    @Description("禁用")
    @PostMapping("disable")
    public ApiResponse<E> disable(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        checkApiAvailable(Curd.Disable);
        long id = source.getId();
        E entity = service.get(id);
        beforeDisable(entity);
        service.disable(id);
        afterDisable(id);
        return ApiResponse.data(service.getEntityInstance(id), "禁用成功");
    }

    @Description("发布")
    @PostMapping("publish")
    public ApiResponse<Void> publish(@RequestBody @Validated(WhenIdRequired.class) E entity) {
        service.publish(entity.getId());
        return ApiResponse.success("发布成功");
    }

    @Description("创建导出任务")
    @PostMapping("export")
    public ApiResponse<String> export(@RequestBody QueryListRequest<E> queryListRequest) {
        checkApiAvailable(Curd.Export);
        QueryPageRequest<E> pageReq = new QueryPageRequest<>();
        pageReq.setSort(queryListRequest.getSort());
        pageReq.setFilter(queryListRequest.getFilter());
        pageReq = beforeExportQuery(pageReq);
        return ApiResponse.data(service.createExportTask(pageReq), "导出任务创建成功");
    }

    @Description("查询异步导出结果")
    @PostMapping("queryExport")
    @Permission(authorize = false)
    public ApiResponse<String> queryExport(@RequestBody @Validated QueryExport queryExport) {
        checkApiAvailable(Curd.QueryExport);
        return ApiResponse.data(service.queryExport(queryExport), "请下载导出的文件");
    }

    protected E beforeAdd(@NotNull E entity) {
        return entity;
    }

    protected void afterAdd(long id, @NotNull E source) {
    }

    protected E beforeUpdate(@NotNull E entity) {
        E exist = service.get(entity.getId());
        FORBIDDEN_EDIT.when(exist.getIsPublished(), "无法修改已经发布的数据");
        return beforeAppUpdate(entity, exist);
    }

    protected E beforeAppUpdate(@NotNull E entity, @NotNull E exist) {
        log.info("修改数据，ID:{}", entity.getId());
        return entity;
    }

    protected void afterUpdate(long id, @NotNull E source) {
    }

    protected void afterSaved(long id, @NotNull E source) {
    }

    protected void beforeDelete(@NotNull E entity) {
        FORBIDDEN_EDIT.when(entity.getIsPublished(), "无法删除已经发布的数据");
        beforeAppDelete(entity);
    }

    protected void beforeAppDelete(@NotNull E entity) {
        log.info("删除数据，ID:{}", entity.getId());
    }

    protected void afterDelete(long id) {
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

    protected E afterGetDetail(@NotNull E entity) {
        return entity;
    }

    protected QueryPageRequest<E> beforeExportQuery(QueryPageRequest<E> queryPageRequest) {
        return queryPageRequest;
    }
}
