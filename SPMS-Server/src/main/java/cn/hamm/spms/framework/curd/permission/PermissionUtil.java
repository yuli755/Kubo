package cn.hamm.spms.framework.curd.permission;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名：
 *   cn.hamm.airpower.core  → cn.hamm.spms.core
 *   cn.hamm.airpower.api   → cn.hamm.spms.framework.api
 *   cn.hamm.airpower.curd  → cn.hamm.spms.framework.curd
 *   cn.hamm.airpower.redis → cn.hamm.spms.framework.redis
 * 代码逻辑一字未改（JPA→MyBatis-Plus 的持久化替换是紧随其后的独立一批，见 docs）。
 * 从此这一层由本仓拥有：改代码改这里，不再有"上游"。
 */

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import cn.hamm.spms.framework.api.annotation.Api;
import cn.hamm.spms.core.DictionaryUtil;
import cn.hamm.spms.core.ReflectUtil;
import cn.hamm.spms.framework.curd.base.Curd;
import cn.hamm.spms.framework.curd.model.Access;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.hamm.spms.common.exception.Errors.PARAM_MISSING;
import static org.springframework.core.io.support.ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX;

/**
 * <h1>权限处理工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class PermissionUtil {
    /**
     * {@code Controller}
     */
    private static final String CONTROLLER = "Controller";

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private PermissionUtil() {

    }

    /**
     * 获取需要被授权的类型
     *
     * @param clazz  类
     * @param method 方法
     * @return 需要授权的选项
     */
    public static @NotNull Access getWhatNeedAccess(@NotNull Class<?> clazz, @NotNull Method method) {
        // 默认无标记时，不需要登录和授权
        Access access = new Access();

        // 判断类是否标记访问权限
        Permission permissionClass = clazz.getAnnotation(Permission.class);
        if (Objects.nonNull(permissionClass)) {
            // 类做了标记 先记下来 后续可能被方法覆盖
            access.setLogin(permissionClass.login());
            // 需要登录时 RBAC选项才能启用
            access.setAuthorize(permissionClass.login() && permissionClass.authorize());
        }
        // 如果方法也标注了 方法将覆盖类的注解配置
        Permission permissionMethod = method.getAnnotation(Permission.class);
        if (Objects.nonNull(permissionMethod)) {
            // 方法标记覆盖类的配置
            access.setLogin(permissionMethod.login());
            access.setAuthorize(permissionMethod.login() && permissionMethod.authorize());
        }
        return access;
    }

    /**
     * 获取权限标识
     *
     * @param clazz  类
     * @param method 方法
     * @return 权限标识
     */
    public static @NotNull String getPermissionIdentity(@NotNull Class<?> clazz, @NotNull Method method) {
        return StringUtils.uncapitalize(clazz.getSimpleName()
                .replace(CONTROLLER, "")) +
                "_" + method.getName();
    }

    /**
     * 扫描并返回权限列表
     *
     * @param clazz           入口类
     * @param permissionClass 权限类
     * @param <P>             权限类型
     * @return 权限列表
     */
    public static <P extends IPermission<P>> @NotNull List<P> scanPermission(
            @NotNull Class<?> clazz, Class<P> permissionClass
    ) {
        return scanPermission(clazz.getPackageName(), permissionClass);
    }

    /**
     * 扫描并返回权限列表
     *
     * @param packageName     包名
     * @param permissionClass 权限类
     * @param <P>             权限类型
     * @return 权限列表
     */
    public static <P extends IPermission<P>> @NotNull List<P> scanPermission(
            String packageName, Class<P> permissionClass
    ) {
        List<P> permissions = new ArrayList<>();
        try {
            ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
            String pattern = CLASSPATH_ALL_URL_PREFIX +
                    ClassUtils.convertClassNameToResourcePath(packageName) + "/**/*" + CONTROLLER + ".class";
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);

            for (Resource resource : resources) {
                // 用于读取类信息
                MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                // 扫描到的类
                String className = metadataReader.getClassMetadata().getClassName();
                Class<?> clazz = Class.forName(className);

                Api api = clazz.getAnnotation(Api.class);
                if (Objects.isNull(api)) {
                    // 不是 Rest 控制器或者是指定的几个白名单控制器
                    continue;
                }

                String customClassName = ReflectUtil.getDescription(clazz);
                String identity = clazz.getSimpleName().replace(CONTROLLER, "");
                P permission = permissionClass.getConstructor().newInstance();

                permission.setName(customClassName).setIdentity(identity).setChildren(new ArrayList<>());

                String apiPath = identity + "_";

                // 取出所有控制器方法
                Method[] methods = clazz.getMethods();

                // 取出控制器类上的Extends注解 如自己没标 则使用父类的
                List<Curd> curdList = Curd.getCurdList(clazz);
                for (Method method : methods) {
                    try {
                        Curd current = DictionaryUtil.getDictionary(Curd.class, Curd::getMethodName, method.getName());
                        if (!curdList.contains(current)) {
                            continue;
                        }
                    } catch (Exception ignored) {
                    }
                    String subIdentity = getMethodPermissionIdentity(method);
                    if (Objects.isNull(subIdentity)) {
                        continue;
                    }
                    subIdentity = apiPath + subIdentity;
                    String customMethodName = ReflectUtil.getDescription(method);
                    Access accessConfig = getWhatNeedAccess(clazz, method);
                    if (!accessConfig.isLogin()) {
                        // 无需登录 不扫描权限
                        continue;
                    }
                    if (!accessConfig.isAuthorize()) {
                        // 无需授权 不扫描权限
                        continue;
                    }
                    P subPermission = permissionClass.getConstructor().newInstance();
                    subPermission.setIdentity(subIdentity).setName(customClassName + "-" + customMethodName);
                    permission.getChildren().add(subPermission);
                }
                permissions.add(permission);
            }
        } catch (Exception exception) {
            log.error("扫描权限出错", exception);
        }
        return permissions;
    }

    /**
     * 获取方法权限标识
     *
     * @param method 方法
     * @return 权限标识
     */
    private static @Nullable String getMethodPermissionIdentity(Method method) {
        RequestMapping requestMapping = ReflectUtil.getAnnotation(RequestMapping.class, method);
        PostMapping postMapping = ReflectUtil.getAnnotation(PostMapping.class, method);
        GetMapping getMapping = ReflectUtil.getAnnotation(GetMapping.class, method);

        if (Objects.isNull(requestMapping) && Objects.isNull(postMapping) && Objects.isNull(getMapping)) {
            return null;
        }
        return method.getName();
    }


    /**
     * 密码和盐获取密码的散列摘要
     *
     * @param password 明文密码
     * @param salt     盐
     * @return {@code sha1} 散列摘要
     */
    public static @NotNull String encodePassword(@NotNull String password, @NotNull String salt) {
        PARAM_MISSING.whenEmpty(password, "密码不能为空");
        PARAM_MISSING.whenEmpty(salt, "盐不能为空");
        return sha1Hex(
                sha1Hex(password + salt) + sha1Hex(salt + password)
        );
    }

    /**
     * SHA-1 十六进制摘要（小写、定长 40 字符）。
     *
     * <p><b>为什么不用 commons-codec 的 {@code DigestUtils.sha1Hex}</b>：本仓自持框架层之后
     * 少一个依赖就少一份要跟着 Spring Boot BOM 走的东西，而这里只用到一行能力。
     * 语义与 {@code DigestUtils.sha1Hex(String)} 逐字节一致（UTF-8 字节 → SHA-1 → 小写十六进制，前导零补齐），
     * 也就是**已落库的密码哈希不会失效**。{@code HexFormat} 是 JDK 17 自带的，零依赖。</p>
     */
    private static @NotNull String sha1Hex(@NotNull String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-1 是 JDK 必须实现的算法，走到这里说明运行环境本身坏了 —— 不要吞掉
            throw new IllegalStateException("当前运行环境不支持 SHA-1", exception);
        }
    }
}
