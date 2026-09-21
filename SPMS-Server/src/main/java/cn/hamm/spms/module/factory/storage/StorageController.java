package cn.hamm.spms.module.factory.storage;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.annotation.Extends;
import cn.hamm.spms.base.Curd;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.query.QueryPageResponse;
import cn.hamm.spms.framework.http.ApiResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static cn.hamm.spms.base.Curd.GetDetail;
import static cn.hamm.spms.base.Curd.GetPage;
import static cn.hamm.spms.common.exception.Errors.DATA_NOT_FOUND;

/**
 * <h1>Controller</h1>
 *
 * <p>写接口继续复用 AirPower JPA CRUD；page/detail/stat/count/export 在本类显式换到 StorageMapper。
 * 树导航 {@code getList}（前端 tree selector 用）仍走 JPA，经 {@code beforeGetList}/{@code afterGetList}
 * 递归补 {@code children}。</p>
 *
 * @author Hamm.cn
 */
@Api("storage")
@Description("仓库")
@Extends({Curd.Export, Curd.QueryExport})
public class StorageController extends BaseController<StorageEntity, StorageService, StorageRepository> {

    /**
     * 仓库分页读出口（Mapper）。
     *
     * @param request 分页与筛选
     * @return 分页结果
     */
    @Override
    @PostMapping("getPage")
    public ApiResponse<QueryPageResponse<StorageEntity>> getPage(@RequestBody QueryPageRequest<StorageEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return ApiResponse.data(service.getMapperPage(request));
    }

    /**
     * 仓库详情读出口（Mapper）。无权行不通过 JPA 回退，直接按不存在处理。
     *
     * @param source 只含 ID 的请求实体
     * @return 详情
     */
    @Override
    @PostMapping("getDetail")
    public ApiResponse<StorageEntity> getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull StorageEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        StorageEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return ApiResponse.data(entity);
    }

    /**
     * 仓库总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询仓库总数")
    @PostMapping("getCount")
    public ApiResponse<Long> getCount(@RequestBody(required = false) QueryListRequest<StorageEntity> request) {
        StorageEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperCount(filter));
    }

    /**
     * 仓库统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询仓库统计")
    @PostMapping("getStat")
    public ApiResponse<ReadStat> getStat(@RequestBody(required = false) QueryListRequest<StorageEntity> request) {
        StorageEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperStat(filter));
    }

    /**
     * 仓库导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；关闭时回退框架 JPA 导出。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public ApiResponse<String> export(@RequestBody QueryListRequest<StorageEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return ApiResponse.data(service.createMapperExportTask(request), "导出任务创建成功");
    }
}
