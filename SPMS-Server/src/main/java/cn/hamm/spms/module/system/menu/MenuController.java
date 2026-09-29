package cn.hamm.spms.module.system.menu;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
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

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("menu")
@Description("菜单")
public class MenuController extends BaseController<MenuEntity, MenuService, MenuRepository> {
    /**
     * 菜单树对任何已登录用户开放，这是承重的：不要把它收紧成需要 {@code Menu_getList}。
     * <p>
     * 真正的消费方不是「菜单管理」页，而是别处的选择器 —— 角色授权（{@code RoleController.authorizeMenu}）要把整棵菜单树
     * 渲染成勾选树。持有 {@code Role_authorizeMenu} 的角色管理员未必同时持有 {@code Menu_getList}，一旦这里要求授权，
     * 授权页的菜单树就会对角色管理员 403，等于把权限管理页面锁在它自己门外。
     * <p>
     * 侧边栏不走这里：它调 {@code /user/getMyMenuList}，只返回当前用户的角色被授到的菜单（实测零授权用户拿到 0 个节点）。
     * 因此本接口的开放面是「导航元数据可被枚举」（页面路径可见），不是业务数据泄露；收敛发生在 {@code getMyMenuList}
     * 和每个业务接口自己的权限码上。真机验证见 {@code /menu/getList} 对零权限用户返回完整 34 节点树。
     *
     * @return 完整的菜单树（含未授权节点），按 {@code MenuService.ORDER_FIELD_NAME} 排序
     */
    @Permission(authorize = false)
    @Override
    public ApiResponse<List<MenuEntity>> getList(@RequestBody @NotNull QueryListRequest<MenuEntity> queryListRequest) {
        MenuEntity filter = queryListRequest.getFilter();
        queryListRequest.setSort(Objects.requireNonNullElse(
                queryListRequest.getSort(),
                new Sort().setField(MenuService.ORDER_FIELD_NAME)
        ));
        queryListRequest.setFilter(filter);
        return ApiResponse.data(Trees.buildTreeList(service.getList(queryListRequest)));
    }
}
