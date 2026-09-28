package cn.hamm.spms.module.personnel.user.token;

import cn.hamm.spms.framework.error.BusinessException;
import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.annotation.Extends;
import cn.hamm.spms.base.Curd;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.web.annotation.Permission;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.framework.http.ApiResponse;
import cn.hamm.spms.module.personnel.user.UserEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static cn.hamm.spms.common.exception.Errors.API_SERVICE_UNSUPPORTED;
import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_EDIT;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("personalToken")
@Description("私人令牌")
@Extends({Curd.Disable, Curd.Enable})
public class PersonalTokenController extends BaseController<PersonalTokenEntity, PersonalTokenService, PersonalTokenRepository> {
    @Description("获取我的私人令牌列表")
    @Permission(authorize = false)
    @PostMapping("getMyList")
    // 阶段 E-M1：@ExposeAll(PersonalTokenEntity.class) 会绕过实体上的
    // @Desensitize（airpower RootModel 对「列入 exposeAll 的类」跳过脱敏），
    // 导致列表接口把可用凭据明文返回、可被 shoulder-surfing / 截图 / XSS 收割。
    // 去掉后 token 按 head6+尾2 脱敏；完整值仍只在 createMy 一次性返回，
    // 与前端「后续将不再显示令牌」的文案约定一致。
    public ApiResponse<List<PersonalTokenEntity>> getMyList() {
        UserEntity user = new UserEntity().setId(getCurrentUserId());
        List<PersonalTokenEntity> list = service.filter(new PersonalTokenEntity().setUser(user));
        return ApiResponse.success(list);
    }

    @Description("禁用我的令牌")
    @Permission(authorize = false)
    @PostMapping("disableMy")
    public ApiResponse<Void> disableMy(@RequestBody @Validated(WhenIdRequired.class) PersonalTokenEntity personalToken) {
        PersonalTokenEntity exist = service.get(personalToken.getId());
        FORBIDDEN_EDIT.whenNotEquals(exist.getUser().getId(), getCurrentUserId(), "你没有权限禁用此令牌");
        service.disable(personalToken.getId());
        return ApiResponse.success("禁用成功");
    }

    /**
     * 私人令牌只提供「我的」系列端点（getMyList / createMy / enableMy / disableMy / deleteMy）：
     * 令牌必须绑定当前登录用户，由管理端代增删改查没有业务意义，也会绕过
     * 「完整值仅在 createMy 一次性返回」的凭据暴露约束。
     *
     * <p>但继承自 {@code BaseController} 的通用 CRUD 端点<b>仍被 Spring 映射</b>
     * （因此照样出现在 /v3/api-docs 里，调用方能打到），只能在钩子里显式拒绝。</p>
     *
     * <p>此前抛的是 {@link IllegalStateException}，被全局异常处理器兜成 <b>HTTP 500</b>：
     * 一个「设计上就不提供」的端点表现得像服务端故障，会污染错误率监控与 canary 告警，
     * 调用方也拿不到可判读的业务码。改为 501 {@code API_SERVICE_UNSUPPORTED}，
     * 与本项目对 {@code queryExport} 等「接口未实现」场景的既有口径一致。</p>
     *
     * @param useInstead 应当改用的端点名
     * @return 待抛出的业务异常
     */
    private static BusinessException unsupported(@NotNull String useInstead) {
        return new BusinessException(API_SERVICE_UNSUPPORTED, "私人令牌不支持此操作，请改用 " + useInstead);
    }

    @Override
    protected PersonalTokenEntity beforeAdd(@NotNull PersonalTokenEntity entity) {
        throw unsupported("createMy");
    }

    @Override
    protected void beforeDisable(@NotNull PersonalTokenEntity entity) {
        throw unsupported("disableMy");
    }

    @Override
    protected QueryPageRequest<PersonalTokenEntity> beforeGetPage(QueryPageRequest<PersonalTokenEntity> queryPageRequest) {
        throw unsupported("getMyList");
    }

    @Override
    protected QueryListRequest<PersonalTokenEntity> beforeGetList(QueryListRequest<PersonalTokenEntity> queryListRequest) {
        throw unsupported("getMyList");
    }

    @Override
    protected void beforeEnable(@NotNull PersonalTokenEntity entity) {
        throw unsupported("enableMy");
    }

    @Override
    protected void beforeAppDelete(@NotNull PersonalTokenEntity entity) {
        throw unsupported("deleteMy");
    }

    /**
     * 阶段 D-3：通用的 update/getDetail 没有属主过滤，拿到 id 就能改/读任何人的
     * 令牌行（IDOR，全靠没给角色授 personalToken_update 权限侥幸兜底）。
     * 令牌是个人凭据，属主校验不能是「指望不授权」。
     */
    @Override
    protected PersonalTokenEntity beforeAppUpdate(@NotNull PersonalTokenEntity entity, @NotNull PersonalTokenEntity exist) {
        FORBIDDEN_EDIT.whenNotEquals(exist.getUser().getId(), getCurrentUserId(), "你没有权限修改此令牌");
        return entity;
    }

    @Override
    protected PersonalTokenEntity afterGetDetail(PersonalTokenEntity entity) {
        FORBIDDEN_EDIT.whenNotEquals(entity.getUser().getId(), getCurrentUserId(), "你没有权限查看此令牌");
        return entity;
    }

    @Description("启用我的令牌")
    @Permission(authorize = false)
    @PostMapping("enableMy")
    public ApiResponse<Void> enableMy(@RequestBody @Validated(WhenIdRequired.class) PersonalTokenEntity personalToken) {
        PersonalTokenEntity exist = service.get(personalToken.getId());
        FORBIDDEN_EDIT.whenNotEquals(exist.getUser().getId(), getCurrentUserId(), "你没有权限启用此令牌");
        service.enable(personalToken.getId());
        return ApiResponse.success("启用成功");
    }

    @Description("删除我的令牌")
    @Permission(authorize = false)
    @PostMapping("deleteMy")
    public ApiResponse<Void> deleteMy(@RequestBody @Validated(WhenIdRequired.class) PersonalTokenEntity personal) {
        PersonalTokenEntity exist = service.get(personal.getId());
        FORBIDDEN_EDIT.whenNotEquals(exist.getUser().getId(), getCurrentUserId(), "你没有权限删除此令牌");
        service.delete(personal.getId());
        return ApiResponse.success("删除成功");
    }

    @Description("创建我的令牌")
    @Permission(authorize = false)
    @PostMapping("createMy")
    public ApiResponse<String> createMy(@RequestBody @Validated(WhenAdd.class) PersonalTokenEntity personal) {
        PersonalTokenEntity personalToken = service.addAndGet(personal.setUser(new UserEntity().setId(getCurrentUserId())));
        String token = personalToken.getToken();
        return ApiResponse.success("创建成功", token);
    }
}
