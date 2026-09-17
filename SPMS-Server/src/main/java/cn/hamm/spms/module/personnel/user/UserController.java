package cn.hamm.spms.module.personnel.user;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.spms.common.cookie.CookieConfig;
import cn.hamm.spms.common.cookie.CookieHelper;
import cn.hamm.spms.core.Json;
import cn.hamm.spms.core.StringUtil;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.ExposeAll;
import cn.hamm.spms.core.exception.ServiceException;
import cn.hamm.airpower.curd.annotation.Extends;
import cn.hamm.airpower.curd.base.Curd;
import cn.hamm.airpower.curd.permission.Permission;
import cn.hamm.airpower.redis.RedisHelper;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.common.security.LoginBruteForceGuard;
import cn.hamm.spms.module.personnel.user.enums.UserLoginType;
import cn.hamm.spms.module.system.menu.MenuEntity;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import cn.hamm.spms.module.system.tenant.TenantEntity;
import cn.hamm.spms.module.system.tenant.TenantOption;
import cn.hamm.spms.module.system.tenant.TenantRepository;
import cn.hamm.spms.module.system.tenant.UserTenantService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_DISABLED;
import static cn.hamm.spms.common.exception.Errors.PARAM_INVALID;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("user")
@Description("用户")
@Extends({Curd.Disable, Curd.Enable})
@Slf4j
public class UserController extends BaseController<UserEntity, UserService, UserRepository> implements IUserAction {
    @Autowired
    private CookieHelper cookieHelper;

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserTenantService userTenantService;

    @Autowired
    private cn.hamm.spms.common.security.AccessTokenRevocationService accessTokenRevocationService;

    @Autowired
    private cn.hamm.airpower.api.config.ApiConfig apiConfig;

    @Autowired
    private cn.hamm.airpower.curd.config.AccessConfig accessConfig;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private LoginBruteForceGuard loginBruteForceGuard;

    @Autowired
    private CookieConfig cookieConfig;

    @Description("获取我的信息")
    @Permission(authorize = false)
    @PostMapping("getMyInfo")
    @ExposeAll(UserEntity.class)
    public Json getMyInfo() {
        return Json.data(service.get(getCurrentUserId()));
    }

    /**
     * 获取当前用户可切换的租户。
     *
     * <p>普通用户只返回 {@code user_tenant} 中已绑定且启用的租户；根账号返回全部启用租户。
     * 请求头仍不会在这里被信任，真正的租户上下文由 {@code TenantContextResolver} 再次校验。</p>
     */
    @Description("获取我的租户")
    @Permission(authorize = false)
    @PostMapping("getMyTenantList")
    public Json getMyTenantList() {
        long userId = getCurrentUserId();
        UserEntity user = service.get(userId);
        Long defaultTenantId = userTenantService.defaultTenantId(userId);
        List<TenantEntity> tenants;
        if (user != null && user.isRootUser()) {
            tenants = tenantRepository.findAll();
        } else {
            Set<Long> tenantIds = userTenantService.tenantIds(userId);
            tenants = tenantRepository.findAllById(tenantIds);
        }
        List<TenantOption> options = tenants.stream()
                .filter(Objects::nonNull)
                .filter(tenant -> Objects.equals(tenant.getStatus(), 1))
                .sorted(Comparator.comparing(TenantEntity::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(tenant -> new TenantOption()
                        .setId(tenant.getId())
                        .setCode(tenant.getCode())
                        .setName(tenant.getName())
                        .setIsDefault(Objects.equals(defaultTenantId, tenant.getId())))
                .toList();
        return Json.data(options, "获取成功");
    }

    @Description("修改我的信息")
    @Permission(authorize = false)
    @PostMapping("updateMyInfo")
    public Json updateMyInfo(@RequestBody @Validated(WhenUpdateMyInfo.class) UserEntity user) {
        user.setId(getCurrentUserId());
        user.setPhone(null).setEmail(null).setRealName(null).setIdCard(null);
        service.update(user);
        return Json.success("资料修改成功");
    }

    @Description("获取我的菜单")
    @Permission(authorize = false)
    @PostMapping("getMyMenuList")
    @ExposeAll(MenuEntity.class)
    public Json getMyMenuList() {
        String userMenuCacheKey = getUserMenuCacheKey(getCurrentUserId());
        Object object = redisHelper.get(userMenuCacheKey);
        if (Objects.nonNull(object)) {
            return Json.data(Json.parseList(object.toString(), MenuEntity[].class), "获取成功");
        }
        List<MenuEntity> menuListByUserId = service.getMenuListByUserId(getCurrentUserId());
        redisHelper.set(userMenuCacheKey, Json.toString(menuListByUserId));
        return Json.data(menuListByUserId, "获取成功");
    }

    @Description("获取我的权限")
    @Permission(authorize = false)
    @PostMapping("getMyPermissionList")
    public Json getMyPermissionList() {
        String userPermissionCacheKey = getUserPermissionCacheKey(getCurrentUserId());
        Object object = redisHelper.get(userPermissionCacheKey);
        if (Objects.nonNull(object)) {
            return Json.data(Json.parseList(object.toString(), String[].class), "获取成功");
        }
        List<PermissionEntity> permissionList = service.getPermissionListByUserId(getCurrentUserId());
        List<String> permissions = permissionList.stream()
                .map(PermissionEntity::getIdentity)
                .collect(Collectors.toList());
        redisHelper.set(userPermissionCacheKey, Json.toString(permissions));
        return Json.data(permissions, "获取成功");
    }

    /**
     * 获取用户权限缓存 Key
     *
     * @return key
     */
    @Contract(pure = true)
    private @NotNull String getUserPermissionCacheKey(long userId) {
        return "user_permission_" + userId;
    }

    /**
     * 获取用户菜单缓存 Key
     *
     * @return key
     */
    @Contract(pure = true)
    private @NotNull String getUserMenuCacheKey(long userId) {
        return "user_menu_" + userId;
    }


    @Description("修改我的密码")
    @Permission(authorize = false)
    @PostMapping("updateMyPassword")
    public Json updateMyPassword(@RequestBody @Validated(WhenUpdateMyPassword.class) UserEntity user) {
        service.modifyPassword(getCurrentUserId(), user.getOldPassword(), user.getPassword());
        return Json.success("密码修改成功");
    }

    @Description("返回密码")
    @Permission(login = false)
    @PostMapping("resetMyPassword")
    public Json resetMyPassword(@RequestBody @Validated(WhenResetMyPassword.class) UserEntity user) {
        String phone = user.getPhone();
        String email = user.getEmail();
        String code = user.getCode();
        String newPassword = user.getPassword();
        if (StringUtil.hasText(phone)) {
            service.resetPasswordViaPhone(phone, code, newPassword);
        } else if (StringUtil.hasText(email)) {
            service.resetPasswordViaEmail(email, code, newPassword);
        } else {
            PARAM_INVALID.show("请传入邮箱或手机号码");
        }
        return Json.success("密码重置成功");
    }

    @Description("账号密码登录")
    @Permission(login = false)
    @PostMapping("login")
    public Json login(@RequestBody @Validated(WhenLogin.class) UserEntity user,
                      HttpServletResponse httpServletResponse,
                      HttpServletRequest httpServletRequest) {
        return handleLoginRequest(UserLoginType.VIA_ACCOUNT_PASSWORD, user, httpServletResponse, httpServletRequest);
    }

    @Description("退出登录")
    @Permission(login = false)
    @PostMapping("logout")
    public Json logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        // 使服务端会话失效：只清浏览器 Cookie 不够，Redis 里的 cookie→userId 映射必须一并删除，
        // 否则被窃取的 cookie 值在 TTL（1 天）内仍能通过 getUserIdByCookie 冒充该用户。
        Cookie[] cookies = httpServletRequest.getCookies();
        if (!Objects.isNull(cookies)) {
            String cookieString = Arrays.stream(cookies)
                .filter(cookie -> Objects.equals(cookieConfig.getAuthCookieName(), cookie.getName()))
                .findFirst().map(Cookie::getValue)
                .orElse(null);
            if (StringUtils.hasText(cookieString)) {
                service.deleteCookie(cookieString);
            }
        }
        Cookie cookie = cookieHelper.getAuthorizeCookie("");
        cookie.setHttpOnly(false);
        cookie.setPath(CookieHelper.DEFAULT_PATH);
        // 移除 cookie
        cookie.setMaxAge(0);
        httpServletResponse.addCookie(cookie);
        // 吊销访问令牌：cookie 清了，但前端走的是 Authorization 头里的自包含令牌，
        // 不拉黑的话被盗令牌在有效期内仍能直连 API。取令牌方式与框架 preHandle 一致
        // （先 Authorization 头，再同名 query 参数），并剥离 Bearer 前缀。
        String rawToken = httpServletRequest.getHeader(apiConfig.getAuthorizeHeader());
        if (!StringUtils.hasText(rawToken)) {
            rawToken = httpServletRequest.getParameter(apiConfig.getAuthorizeHeader());
        }
        if (rawToken != null && rawToken.startsWith("Bearer ")) {
            rawToken = rawToken.substring(7).trim();
        }
        if (StringUtils.hasText(rawToken)) {
            accessTokenRevocationService.revoke(rawToken, accessConfig.getAuthorizeExpireSecond());
        }
        return Json.success("退出登录成功");
    }

    @Description("邮箱验证码登录")
    @Permission(login = false)
    @PostMapping("loginViaEmail")
    public Json loginViaEmail(@RequestBody @Validated(WhenLoginViaEmail.class) UserEntity user,
                              HttpServletResponse httpServletResponse,
                              HttpServletRequest httpServletRequest) {
        return handleLoginRequest(UserLoginType.VIA_EMAIL_CODE, user, httpServletResponse, httpServletRequest);
    }

    @Description("手机验证码登录")
    @Permission(login = false)
    @PostMapping("loginViaPhone")
    public Json loginViaPhone(@RequestBody @Validated(WhenLoginViaPhone.class) UserEntity user,
                              HttpServletResponse httpServletResponse,
                              HttpServletRequest httpServletRequest) {
        return handleLoginRequest(UserLoginType.VIA_PHONE_CODE, user, httpServletResponse, httpServletRequest);
    }

    @Description("发送邮件")
    @Permission(login = false)
    @PostMapping("sendEmail")
    public Json sendEmail(@RequestBody @Validated(WhenSendEmail.class) UserEntity user) throws MessagingException {
        service.sendEmailCode(user.getEmail());
        return Json.success("发送成功");
    }

    @Description("发送短信")
    @Permission(login = false)
    @PostMapping("sendSms")
    public Json sendSms(@RequestBody @Validated(WhenSendSms.class) UserEntity user) {
        service.sendSmsCode(user.getPhone());
        return Json.success("发送成功");
    }

    /**
     * <h1>处理用户登录</h1>
     *
     * @param userLoginType 登录方式
     * @param user          登录数据
     * @param response      响应
     * @param request       请求（用于解析客户端 IP，防爆破计数维度）
     * @return JsonData
     */
    private Json handleLoginRequest(@NotNull UserLoginType userLoginType, UserEntity user,
                                    HttpServletResponse response, HttpServletRequest request) {
        String account = resolveLoginAccount(userLoginType, user);
        String clientIp = loginBruteForceGuard.resolveClientIp(request);
        // 锁定检查须在失败计数之外：锁定期直接拒绝，不计入「失败次数」（否则锁定永续）
        loginBruteForceGuard.assertNotLocked(account, clientIp);
        try {
            UserEntity exist = switch (userLoginType) {
                case VIA_ACCOUNT_PASSWORD -> service.loginViaEmailAndPassword(user.getEmail(), user.getPassword());
                case VIA_EMAIL_CODE -> service.loginViaEmailAndCode(user.getEmail(), user.getCode());
                case VIA_PHONE_CODE -> service.loginViaPhoneAndCode(user.getPhone(), user.getCode());
            };
            FORBIDDEN_DISABLED.when(exist.getIsDisabled(), "登录失败，你的账号已被禁用");
            redisHelper.delete(getUserPermissionCacheKey(exist.getId()));
            redisHelper.delete(getUserMenuCacheKey(exist.getId()));
            // 登录成功：清零该账号与 IP 的失败计数
            loginBruteForceGuard.clear(account, clientIp);
            return Json.data(userService.loginWithCookieAndResponse(response, exist), "登录成功");
        } catch (ServiceException exception) {
            // 登录失败（账号/密码/验证码错误、账号禁用等）：计数，达阈值即锁定
            loginBruteForceGuard.recordFailure(account, clientIp);
            throw exception;
        }
    }

    /**
     * 解析登录账号（防爆破的账号维度）。账号密码 / 邮箱验证码登录以邮箱为维度，
     * 手机验证码登录以手机号为维度；为空时守卫内部会跳过（由参数校验拦截）。
     *
     * @param userLoginType 登录方式
     * @param user          登录数据
     * @return 登录账号（邮箱或手机号）
     */
    private String resolveLoginAccount(@NotNull UserLoginType userLoginType, UserEntity user) {
        return switch (userLoginType) {
            case VIA_ACCOUNT_PASSWORD, VIA_EMAIL_CODE -> user.getEmail();
            case VIA_PHONE_CODE -> user.getPhone();
        };
    }
}