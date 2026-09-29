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
    /**
     * 部门树对任何已登录用户开放，这是承重的：不要把它收紧成需要 {@code Department_getList}。
     * <p>
     * 消费方是「用户」编辑页的部门选择器（以及任何需要按部门归集人员的下拉）。持有 {@code User_add} / {@code User_update}
     * 的人事管理员未必同时持有 {@code Department_getList}，一旦这里要求授权，用户编辑表单的部门选择器就会 403，
     * 表现为「能进用户编辑页却选不了部门」——这种半开状态比直接拒绝更难排查。
     * <p>
     * 部门是组织架构元数据（名称 + 层级），本身不含 PII；{@code UserEntity.realName} 等敏感字段的脱敏在各自实体上完成，
     * 不依赖本接口收敛。{@code Export} / {@code QueryExport} 仍被类级 {@code @Extends} 排除，导出部门全量数据需要单独授权。
     *
     * @return 完整的部门树（含当前用户无权管理的部门），按 {@code DepartmentService.ORDER_FIELD_NAME} 排序
     */
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
