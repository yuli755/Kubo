package cn.hamm.spms.module.channel.supplier;

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
 * <p>写接口继续复用 AirPower JPA CRUD；读接口在本类显式换到 SupplierMapper，
 * 让 page/detail/stat/count/export 都经过同一条 statement 绑定和行级权限拦截链。</p>
 *
 * @author Hamm.cn
 */
@Api("supplier")
@Description("供应商")
@Extends(value = {Curd.Export, Curd.QueryExport}, exclude = Curd.Delete)
public class SupplierController extends BaseController<SupplierEntity, SupplierService, SupplierRepository> {

    /**
     * 供应商分页读出口（Mapper）。
     *
     * @param request 分页与筛选
     * @return 分页结果
     */
    @Override
    @PostMapping("getPage")
    public ApiResponse<QueryPageResponse<SupplierEntity>> getPage(@RequestBody QueryPageRequest<SupplierEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return ApiResponse.data(service.getMapperPage(request));
    }

    /**
     * 供应商详情读出口（Mapper）。无权行不通过 JPA 回退，直接按不存在处理。
     *
     * @param source 只含 ID 的请求实体
     * @return 详情
     */
    @Override
    @PostMapping("getDetail")
    public ApiResponse<SupplierEntity> getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull SupplierEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        SupplierEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return ApiResponse.data(entity);
    }

    /**
     * 供应商总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询供应商总数")
    @PostMapping("getCount")
    public ApiResponse<Long> getCount(@RequestBody(required = false) QueryListRequest<SupplierEntity> request) {
        SupplierEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperCount(filter));
    }

    /**
     * 供应商统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询供应商统计")
    @PostMapping("getStat")
    public ApiResponse<ReadStat> getStat(@RequestBody(required = false) QueryListRequest<SupplierEntity> request) {
        SupplierEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperStat(filter));
    }

    /**
     * 供应商导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；关闭时回退框架 JPA 导出。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public ApiResponse<String> export(@RequestBody QueryListRequest<SupplierEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return ApiResponse.data(service.createMapperExportTask(request), "导出任务创建成功");
    }
}
