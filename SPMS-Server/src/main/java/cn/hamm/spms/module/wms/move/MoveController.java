package cn.hamm.spms.module.wms.move;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.annotation.Extends;
import cn.hamm.spms.base.Curd;
import cn.hamm.spms.base.bill.BaseBillController;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.query.QueryPageResponse;
import cn.hamm.spms.framework.http.ApiResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import cn.hamm.spms.module.wms.move.detail.MoveDetailEntity;
import cn.hamm.spms.module.wms.move.detail.MoveDetailRepository;
import cn.hamm.spms.module.wms.move.detail.MoveDetailService;

import static cn.hamm.spms.base.Curd.GetDetail;
import static cn.hamm.spms.base.Curd.GetPage;
import static cn.hamm.spms.common.exception.Errors.DATA_NOT_FOUND;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("move")
@Description("移库")
@Extends(value = {Curd.Export, Curd.QueryExport}, exclude = Curd.Delete)
public class MoveController extends BaseBillController<MoveEntity, MoveService, MoveRepository, MoveDetailEntity, MoveDetailService, MoveDetailRepository> {
    @Override
    @PostMapping("getPage")
    public ApiResponse<QueryPageResponse<MoveEntity>> getPage(@RequestBody QueryPageRequest<MoveEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return ApiResponse.data(service.getMapperPage(request));
    }

    @Override
    @PostMapping("getDetail")
    public ApiResponse<MoveEntity> getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull MoveEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        MoveEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return ApiResponse.data(entity);
    }

    /**
     * 移库单总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询移库单总数")
    @PostMapping("getCount")
    public ApiResponse<Long> getCount(@RequestBody(required = false) QueryListRequest<MoveEntity> request) {
        MoveEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperCount(filter));
    }

    /**
     * 移库单统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询移库单统计")
    @PostMapping("getStat")
    public ApiResponse<ReadStat> getStat(@RequestBody(required = false) QueryListRequest<MoveEntity> request) {
        MoveEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperStat(filter));
    }

    /**
     * 移库单导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；关闭时回退框架 JPA 导出。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public ApiResponse<String> export(@RequestBody QueryListRequest<MoveEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return ApiResponse.data(service.createMapperExportTask(request), "导出任务创建成功");
    }
}
