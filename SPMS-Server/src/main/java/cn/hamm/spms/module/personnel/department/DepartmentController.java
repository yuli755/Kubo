package cn.hamm.spms.module.personnel.department;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.annotation.Extends;
import cn.hamm.spms.framework.web.annotation.Permission;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.framework.http.ApiResponse;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.Sort;
import cn.hamm.spms.framework.util.Trees;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Objects;

import static cn.hamm.spms.base.Curd.Export;
import static cn.hamm.spms.base.Curd.QueryExport;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("department")
@Description("部门")
@Extends(exclude = {Export, QueryExport})
public class DepartmentController extends BaseController<DepartmentEntity, DepartmentService, DepartmentRepository> {
    @Permission(authorize = false)
    @Override
    public ApiResponse<List<DepartmentEntity>> getList(@RequestBody @NotNull QueryListRequest<DepartmentEntity> queryListRequest) {
        DepartmentEntity filter = queryListRequest.getFilter();
        queryListRequest.setSort(Objects.requireNonNullElse(
                queryListRequest.getSort(),
                new Sort().setField(DepartmentService.ORDER_FIELD_NAME)
        ));
        queryListRequest.setFilter(filter);
        return ApiResponse.data(Trees.buildTreeList(service.getList(queryListRequest)));
    }
}
