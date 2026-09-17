package cn.hamm.spms.module.personnel.user;

import cn.hamm.spms.framework.api.config.ApiConfig;
import cn.hamm.spms.common.cookie.CookieHelper;
import cn.hamm.spms.core.AccessTokenUtil;
import cn.hamm.spms.core.DateTimeUtil;
import cn.hamm.spms.core.RandomUtil;
import cn.hamm.spms.core.TreeUtil;
import cn.hamm.spms.framework.curd.base.CurdEntity;
import cn.hamm.spms.framework.curd.config.AccessConfig;
import cn.hamm.spms.framework.curd.model.query.Sort;
import cn.hamm.spms.framework.curd.permission.PermissionUtil;
import cn.hamm.spms.common.email.EmailHelper;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.common.AppConfig;
import cn.hamm.spms.common.sms.SmsHelper;
import cn.hamm.spms.module.personnel.PersonnelServices;
import cn.hamm.spms.module.personnel.department.DepartmentEntity;
import cn.hamm.spms.module.personnel.user.enums.UserTokenType;
import cn.hamm.spms.module.system.SystemServices;
import cn.hamm.spms.module.system.config.ConfigEntity;
import cn.hamm.spms.base.config.ConfigFlag;
import cn.hamm.spms.module.system.menu.MenuEntity;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import jakarta.mail.MessagingException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.hamm.spms.common.exception.Errors.*;
import static cn.hamm.spms.common.exception.CustomError.*;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
@Slf4j
public class UserService extends BaseService<UserEntity, UserRepository> {
    /**
     * 密码加盐的长度
     */
    public static final int PASSWORD_SALT_LENGTH = 4;

    /**
     * Code 缓存秒数
     */
    private static final int CACHE_CODE_EXPIRE_SECOND = DateTimeUtil.SECOND_PER_MINUTE * 5;

    /**
     * 缓存房间用户
     */
    private final String CACHE_ROOM_KEY = "ROOM_USER_";

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ApiConfig apiConfig;

    @Autowired
    private EmailHelper emailHelper;

    @Autowired
    private SmsHelper smsHelper;

    @Autowired
    private CookieHelper cookieHelper;

    @Autowired
    private AccessConfig accessConfig;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * 落库前统一保证「凡有 password 必加盐散列」（审核项 A-1 / A-4）
     *
     * <p>密码加盐散列是认证的地基：/user/add、/user/update 走通用 Curd 落库，历史上把请求体里的
     * password 原样写进库（明文 + 空盐）。后果有两层——
     * ① 凭据以明文躺在数据库，任何一次备份/读权限泄露即等于全员密码泄露；
     * ② 登录做 {@code encodePassword(input, salt)} 比对，salt 为空直接抛「盐为空」，
     * 用 /user/add 建的账号根本无法登录（功能级 Bug，黑盒探针实测）。</p>
     *
     * <p>三道闸门的分工：{@link #beforeAdd} / {@link #beforeUpdate} 在「仅提交值」实体上
     * 归一裸密码（A-4 的关键：/user/update 轮换密码时框架 null-忽略合并会带入库里的<b>旧盐</b>，
     * "盐为空"判据在落库点失明，必须赶在合并前散列）；本方法是兜底网，覆盖
     * addToDatabase/updateToDatabase 直存路径。内部流程（resetPassword、modifyPassword、
     * register 系列、DevDataInitRunner）自带「盐 + 40 位十六进制散列」成对凭据，
     * 凭形状识别跳过，不会被二次散列。</p>
     *
     * @param entity 待落库实体
     * @return 处理后的实体
     */
    @Override
    protected @NotNull UserEntity beforeAppSaveToDatabase(@NotNull UserEntity entity) {
        if (StringUtils.hasText(entity.getPassword()) && !isPreHashedPair(entity)) {
            applyFreshPassword(entity);
        }
        return entity;
    }

    @Override
    protected @NotNull UserEntity beforeUpdate(@NotNull UserEntity entity) {
        UserEntity result = super.beforeUpdate(entity);
        normalizeSubmittedPassword(result);
        return result;
    }

    /**
     * 请求侧密码归一：提交实体里出现「非散列形状」的密码即视为裸密码，换成服务端盐 + 散列
     *
     * <p>beforeAdd/beforeUpdate 收到的是"仅提交值"实体（框架在 beforeUpdate 之后才做
     * null 忽略式合并），此时不信任客户端盐：裸密码（无论是否自带盐）一律重新加盐散列，
     * 客户端盐作废。</p>
     */
    private void normalizeSubmittedPassword(@NotNull UserEntity entity) {
        if (StringUtils.hasText(entity.getPassword()) && !isPreHashedPair(entity)) {
            applyFreshPassword(entity);
        }
    }

    /**
     * 生成服务端随机盐并就地散列
     */
    private void applyFreshPassword(@NotNull UserEntity entity) {
        String salt = RandomUtil.randomString(PASSWORD_SALT_LENGTH);
        entity.setSalt(salt);
        entity.setPassword(PermissionUtil.encodePassword(entity.getPassword(), salt));
    }

    /**
     * 是否「服务端已散列凭据对」：salt 非空 + password 为 40 位十六进制散列形状
     *
     * <p>两半都要认：只看 password 形状会给"裸密码恰好是 40 位十六进制"的用户放行明文
     * （encodePassword 的输出必然配一个服务端盐）。盐按「非空」而非定长判断：历史上
     * modifyPassword 生成过非定长盐，存量行的盐长不统一，按形状一刀切会把老凭据二次散列。
     * 用于区分内部流程已散列好的凭据（DevDataInitRunner/register 走公开 add 时自带
     * 盐 + 散列成对提交，绝不能二次散列）与外部提交的裸密码。</p>
     */
    private static boolean isPreHashedPair(@NotNull UserEntity entity) {
        String password = entity.getPassword();
        return StringUtils.hasText(entity.getSalt())
                && password != null && password.matches("[0-9a-f]{40}");
    }

    /**
     * 获取新的验证码
     *
     * @return 验证码
     */
    private static @NotNull String getRandomValidateCode() {
        return RandomUtil.randomNumbers(6);
    }

    /**
     * 获取短信验证码的缓存 key
     *
     * @param phone 手机号
     * @return 缓存 Key
     */
    @Contract(pure = true)
    private static @NotNull String getPhoneCodeCacheKey(String phone) {
        return "sms_code_" + phone;
    }

    /**
     * 获取邮箱验证码的缓存 key
     *
     * @param email 邮箱
     * @return 缓存 Key
     */
    @Contract(pure = true)
    private static @NotNull String getEmailCacheKey(String email) {
        return "email_code_" + email;
    }

    /**
     * 获取 Cookie 的缓存 key
     *
     * @param cookie Cookie
     * @return 缓存 Key
     */
    @Contract(pure = true)
    private static @NotNull String getCookieCodeKey(String cookie) {
        return "cookie_code_" + cookie;
    }

    /**
     * 重置密码
     *
     * @param user        待修改密码的用户
     * @param newPassword 新密码
     */
    private void resetPassword(@NotNull UserEntity user, String newPassword) {
        String salt = RandomUtil.randomString(PASSWORD_SALT_LENGTH);
        user.setSalt(salt);
        user.setPassword(PermissionUtil.encodePassword(newPassword, salt));
        updateToDatabase(user);
    }

    /**
     * 获取登录用户的菜单列表
     *
     * @param userId 用户 ID
     * @return 菜单列表
     */
    public List<MenuEntity> getMenuListByUserId(long userId) {
        UserEntity user = get(userId);
        if (user.isRootUser()) {
            return TreeUtil.buildTreeList(
                    SystemServices.getMenuService().filter(new MenuEntity(), new Sort().setField("orderNo"))
            );
        }
        List<MenuEntity> menuList = new ArrayList<>();
        user.getRoleList().forEach(role -> role.getMenuList()
                .forEach(menu -> {
                    boolean isExist = menuList.stream()
                            .anyMatch(existMenu -> Objects.equals(menu.getId(), existMenu.getId()));
                    if (!isExist) {
                        menuList.add(menu);
                    }
                })
        );
        return TreeUtil.buildTreeList(menuList.stream().peek(item -> {
            item.excludeNotMeta();
            item.setIsPublished(null);
        }).toList());
    }

    /**
     * 获取登录用户的权限列表
     *
     * @param userId 用户 ID
     * @return 权限列表
     */
    public List<PermissionEntity> getPermissionListByUserId(long userId) {
        UserEntity user = get(userId);
        if (user.isRootUser()) {
            return SystemServices.getPermissionService().getList(null);
        }
        List<PermissionEntity> permissionList = new ArrayList<>();
        user.getRoleList().forEach(roleEntity -> roleEntity.getPermissionList()
                .forEach(permission -> {
                    boolean isExist = permissionList.stream()
                            .anyMatch(existPermission -> Objects.equals(permission.getId(), existPermission.getId()));
                    if (!isExist) {
                        permissionList.add(permission);
                    }
                })
        );
        return permissionList;
    }

    /**
     * 修改密码
     *
     * @param userId      用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    public void modifyPassword(long userId, String oldPassword, String newPassword) {
        UserEntity existUser = get(userId);

        // 判断原始密码
        PARAM_INVALID.whenNotEqualsIgnoreCase(
                PermissionUtil.encodePassword(oldPassword, existUser.getSalt()),
                existUser.getPassword(),
                "原密码输入错误，修改密码失败"
        );
        String salt = RandomUtil.randomString(PASSWORD_SALT_LENGTH);
        existUser.setSalt(salt);
        existUser.setPassword(PermissionUtil.encodePassword(newPassword, salt));
        updateToDatabase(existUser);
    }

    /**
     * 通过手机重置密码
     *
     * @param phone       手机号
     * @param code        验证码
     * @param newPassword 新密码
     */
    public void resetPasswordViaPhone(String phone, String code, String newPassword) {
        PARAM_INVALID.whenNull(phone, "手机号不能为空");
        String cacheCode = getSmsCacheCode(phone);
        UserEntity user = repository.getByPhone(phone);
        PARAM_INVALID.whenNull(user, "重置密码失败，用户信息异常");
        PARAM_INVALID.whenNotEqualsIgnoreCase(cacheCode, code, "验证码不正确，请重新获取");
        resetPassword(user, newPassword);
        redisHelper.delete(getPhoneCodeCacheKey(phone));
    }

    /**
     * 通过手机重置密码
     *
     * @param email       邮箱
     * @param code        验证码
     * @param newPassword 新密码
     */
    public void resetPasswordViaEmail(String email, String code, String newPassword) {
        PARAM_INVALID.whenNull(email, "邮箱不能为空");
        String cacheCode = getEmailCacheCode(email);
        UserEntity user = repository.getByEmail(email);
        PARAM_INVALID.whenNull(user, "重置密码失败，用户信息异常");
        PARAM_INVALID.whenNotEqualsIgnoreCase(cacheCode, code, "验证码不正确，请重新获取");
        resetPassword(user, newPassword);
        redisHelper.delete(getEmailCacheKey(email));
    }

    /**
     * 发送邮箱验证码
     *
     * @param email 邮箱
     */
    public void sendEmailCode(String email) throws MessagingException {
        EMAIL_SEND_BUSY.when(redisHelper.hasKey(getEmailCacheKey(email)));
        String code = getRandomValidateCode();
        redisHelper.set(getEmailCacheKey(email), code, CACHE_CODE_EXPIRE_SECOND);
        emailHelper.sendCode(email, "你收到一个邮箱验证码", code, appConfig.getProjectName());
    }

    /**
     * 发送短信验证码
     */
    public void sendSmsCode(String phone) {
        SMS_SEND_BUSY.when(redisHelper.hasKey(getPhoneCodeCacheKey(phone)));
        String code = getRandomValidateCode();
        redisHelper.set(getPhoneCodeCacheKey(phone), code, CACHE_CODE_EXPIRE_SECOND);
        smsHelper.sendCode(phone, code);
    }

    /**
     * 登录并设置 Cookie
     *
     * @param response 响应
     * @param user     用户
     * @return AccessToken
     */
    public final String loginWithCookieAndResponse(@NotNull HttpServletResponse response, @NotNull UserEntity user) {
        // 创建 AccessToken
        String accessToken = createAccessToken(user.getId());

        // 存储 Cookies
        String cookieString = RandomUtil.randomString();
        saveCookie(user.getId(), cookieString);
        Cookie cookie = cookieHelper.getAuthorizeCookie(cookieString);
        // 认证 Cookie 必须 HttpOnly：其值（cookieString）经 Redis 映射到 userId，是 OAuth 授权流程
        // 的「已登录」凭证。前端从不读 document.cookie（登出走 /user/logout 服务端删 Cookie），
        // 故 JS 无需访问；置非 HttpOnly 会让任意 XSS 窃取该凭证、以受害者身份完成第三方应用授权。
        cookie.setHttpOnly(true);
        cookie.setPath(CookieHelper.DEFAULT_PATH);
        response.addCookie(cookie);
        return accessToken;
    }

    /**
     * 存储 Cookie
     *
     * @param userId UserId
     * @param cookie Cookie
     */
    public void saveCookie(Long userId, String cookie) {
        redisHelper.set(getCookieCodeKey(cookie), userId, DateTimeUtil.SECOND_PER_DAY);
    }

    /**
     * 删除 Cookie 会话（登出时使服务端会话失效）
     *
     * <p>登出不能只清浏览器 Cookie：Redis 里的 {@code cookieString → userId} 映射若不删，被窃取的
     * cookie 值在 TTL（1 天）内仍能通过 {@link #getUserIdByCookie} 冒充该用户。此方法与
     * {@link #saveCookie} 对称，负责删除服务端那半边。</p>
     *
     * @param cookie Cookie 字符串
     */
    public void deleteCookie(String cookie) {
        redisHelper.delete(getCookieCodeKey(cookie));
    }

    /**
     * 通过 Cookie 获取一个用户
     *
     * @param cookie Cookie
     * @return UserId
     */
    public Long getUserIdByCookie(String cookie) {
        Object userId = redisHelper.get(getCookieCodeKey(cookie));
        if (Objects.isNull(userId)) {
            return null;
        }
        return Long.valueOf(userId.toString());
    }

    /**
     * 邮箱密码登录
     *
     * @param email    邮箱
     * @param password 密码
     * @return 登录成功的用户
     */
    public UserEntity loginViaEmailAndPassword(String email, String password) {
        PARAM_INVALID.whenEmpty(email, "请确认传入有效的邮箱");
        PARAM_INVALID.whenEmpty(password, "请确认传入有效的密码");
        UserEntity existUser = repository.getByEmail(email);
        USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID.whenNull(existUser);
        // 将用户传入的密码加密与数据库存储匹配
        String encodePassword = PermissionUtil.encodePassword(password, existUser.getSalt());
        USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID.whenNotEqualsIgnoreCase(encodePassword, existUser.getPassword());
        return existUser;
    }

    /**
     * 邮箱验证码登录
     *
     * @param email 邮箱
     * @param code  验证码
     * @return 登录成功的用户
     */
    public UserEntity loginViaEmailAndCode(String email, String code) {
        PARAM_INVALID.whenEmpty(email, "请确认传入有效的邮箱");
        PARAM_INVALID.whenEmpty(code, "请确认传入有效的验证码");
        String cacheCode = getEmailCacheCode(email);
        PARAM_INVALID.whenNotEquals(cacheCode, code, "邮箱验证码不正确");
        UserEntity existUser = repository.getByEmail(email);
        ConfigEntity configuration = SystemServices.getConfigService().get(ConfigFlag.AUTO_REGISTER_EMAIL_LOGIN);
        if (configuration.booleanConfig()) {
            // 注册一个用户
            existUser = registerUserViaEmail(email);
        }
        PARAM_INVALID.whenNull(existUser, "登录的邮箱用户不存在");
        return existUser;
    }

    /**
     * 手机验证码登录
     *
     * @param phone 手机号
     * @param code  验证码
     * @return 登录成功的用户
     */
    public UserEntity loginViaPhoneAndCode(String phone, String code) {
        PARAM_INVALID.whenEmpty(phone, "请确认传入有效的手机号");
        PARAM_INVALID.whenEmpty(code, "请确认传入有效的验证码");
        String cacheCode = getSmsCacheCode(phone);
        PARAM_INVALID.whenNotEquals(cacheCode, code, "短信验证码不正确");
        UserEntity existUser = repository.getByPhone(phone);
        ConfigEntity configuration = SystemServices.getConfigService().get(ConfigFlag.AUTO_REGISTER_PHONE_LOGIN);
        if (configuration.booleanConfig()) {
            // 注册一个用户
            existUser = registerUserViaPhone(phone);
        }
        PARAM_INVALID.whenNull(existUser, "登录的手机用户不存在");
        return existUser;
    }

    /**
     * 手机和随机密码注册
     *
     * @param phone 手机号
     * @return 注册的用户
     */
    public UserEntity registerUserViaPhone(@NotNull String phone) {
        return registerUserViaPhone(phone, RandomUtil.randomString());
    }

    /**
     * 手机和指定密码注册
     *
     * @param phone    手机号
     * @param password 密码
     * @return 注册的用户
     */
    public UserEntity registerUserViaPhone(@NotNull String phone, String password) {
        String nickname = "用户" + phone.substring(phone.length() - 4);
        String salt = RandomUtil.randomString(PASSWORD_SALT_LENGTH);
        UserEntity user = new UserEntity().setPassword(PermissionUtil.encodePassword(password, salt))
                .setSalt(salt)
                .setNickname(nickname)
                .setPhone(phone);
        long id = add(user);
        return get(id);
    }

    /**
     * 邮箱和随机密码注册
     *
     * @param email 邮箱
     * @return 注册的用户
     */
    public UserEntity registerUserViaEmail(@NotNull String email) {
        return registerUserViaEmail(email, RandomUtil.randomString());
    }

    /**
     * 邮箱和指定密码注册
     *
     * @param email    邮箱
     * @param password 密码
     * @return 注册的用户
     */
    public UserEntity registerUserViaEmail(@NotNull String email, String password) {
        // 昵称默认为邮箱 @ 前面的
        String nickname = email.split("@")[0];
        String salt = RandomUtil.randomString(PASSWORD_SALT_LENGTH);
        UserEntity user = new UserEntity().setPassword(PermissionUtil.encodePassword(password, salt))
                .setSalt(salt)
                .setNickname(nickname);
        long id = add(user);
        return get(id);
    }

    /**
     * 创建 AccessToken
     *
     * @param userId 用户 ID
     * @return AccessToken
     */
    public String createAccessToken(long userId) {
        return AccessTokenUtil.create().setPayloadId(userId)
                .setExpireSecond(accessConfig.getAuthorizeExpireSecond())
                .addPayload(UserTokenType.TYPE, UserTokenType.NORMAL.getKey())
                .build(apiConfig.getAccessTokenSecret());
    }

    /**
     * 获取指定邮箱存储的验证码
     *
     * @param email 邮箱
     * @return 验证码
     */
    private String getEmailCacheCode(String email) {
        Object code = redisHelper.get(getEmailCacheKey(email));
        return Objects.isNull(code) ? "" : code.toString();
    }

    /**
     * 获取指定手机存储的验证码
     *
     * @param phone 手机
     * @return 验证码
     */
    private String getSmsCacheCode(String phone) {
        Object code = redisHelper.get(getPhoneCodeCacheKey(phone));
        return Objects.isNull(code) ? "" : code.toString();
    }

    @Override
    protected void beforeDelete(@NotNull UserEntity user) {
        FORBIDDEN_DELETE.when(user.isRootUser(), "系统内置用户无法被删除!");
    }

    @Override
    protected @NotNull UserEntity beforeAdd(@NotNull UserEntity user) {
        // 经 BaseService.beforeAdd：掩码写入拒绝 + 数据权限（CREATE）。此前直挂子类钩子
        // 不调 super，等于新增用户绕过了行级写保护与掩码防护。
        UserEntity result = super.beforeAdd(user);
        UserEntity existUser = repository.getByEmail(result.getEmail());
        FORBIDDEN_EXIST.whenNotNull(existUser, "邮箱已经存在，请勿重复添加用户");
        if (!StringUtils.hasLength(result.getPassword())) {
            // 创建时没有设置密码的话 随机一个密码（随后统一走服务端加盐散列）
            result.setPassword(RandomUtil.randomString());
        }
        normalizeSubmittedPassword(result);
        return result;
    }

    @Override
    protected void beforeDisable(@NotNull UserEntity existUser) {
        FORBIDDEN_DISABLED_NOT_ALLOWED.when(existUser.isRootUser(), "系统内置用户无法被禁用!");
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(@NotNull Root<UserEntity> root, @NotNull CriteriaBuilder builder, @NotNull UserEntity search) {
        List<Predicate> predicateList = new ArrayList<>();

        // 叠加数据权限过滤条件
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "personnel.user", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);

        Long departmentId = search.getDepartmentId();
        if (Objects.isNull(departmentId)) {
            return predicateList;
        }
        Set<Long> departmentIdList = PersonnelServices.getDepartmentService().getListByParentId(departmentId);
        departmentIdList.add(departmentId);
        Join<UserEntity, DepartmentEntity> departmentJoin = root.join("departmentList");
        Predicate inPredicate = departmentJoin.get(CurdEntity.STRING_ID).in(departmentIdList);
        predicateList.add(inPredicate);
        return predicateList;
    }

    /**
     * 获取当前用户所在的房间 ID
     *
     * @param userId 用户 ID
     * @return 房间 ID
     */
    public long getCurrentRoomId(long userId) {
        Object data = redisHelper.get(CACHE_ROOM_KEY + userId);
        if (Objects.isNull(data)) {
            return appConfig.getDefaultRoomId();
        }
        return Integer.parseInt(data.toString());
    }

    /**
     * 保存当前用户所在的房间 ID
     *
     * @param userId 用户 ID
     * @param roomId 房间 ID
     */
    public void saveCurrentRoomId(long userId, long roomId) {
        redisHelper.set(CACHE_ROOM_KEY + userId, roomId, DateTimeUtil.SECOND_PER_DAY * 30);
    }
}