package cn.hamm.spms.common.security;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.Curd;
import cn.hamm.spms.framework.web.annotation.Permission;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.*;

/**
 * <h1>自持权限元数据扫描器（过渡期控制器扫描与元数据适配）</h1>
 *
 * <p>扫描 Controller 及其端点上的权限注解与元数据，
 * 建立与 RBAC 表结构完全一致的权限树，替代旧 {@code PermissionUtil.scanPermission}。
 * 零第三方加密依赖，不依赖旧 {@code IPermission} 接口与旧工具类。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
public final class PermissionScanner {

    private static final String CONTROLLER_SUFFIX = "Controller";

    private PermissionScanner() {
    }

    /**
     * 权限扫描节点（轻量不可变 DTO）。
     */
    public record ScannedPermission(
            @NotNull String identity,
            @NotNull String name,
            @NotNull List<ScannedPermission> children
    ) {
    }

    /**
     * 访问权限要求。
     */
    public record AccessRequirement(
            boolean login,
            boolean authorize
    ) {
    }

    /**
     * 扫描指定包名下的所有 Controller，生成权限实体树。
     *
     * @param packageName 根包名（如 cn.hamm.spms）
     * @return 权限实体树列表
     */
    public static @NotNull List<PermissionEntity> scanPermission(@NotNull String packageName) {
        List<Class<?>> controllerClasses = findControllerClasses(packageName);
        return scanClasses(controllerClasses).stream()
                .map(PermissionScanner::toEntity)
                .toList();
    }

    /**
     * 扫描一组指定的控制器类，生成权限节点树。
     *
     * @param classes 控制器类集合
     * @return 权限节点树
     */
    public static @NotNull List<ScannedPermission> scanClasses(@NotNull Collection<Class<?>> classes) {
        List<ScannedPermission> results = new ArrayList<>();
        for (Class<?> clazz : classes) {
            if (clazz == null || !clazz.isAnnotationPresent(Api.class)) {
                continue;
            }
            String identity = getControllerIdentity(clazz);
            String className = getControllerDescription(clazz);

            List<Curd> curdList = Curd.getCurdList(clazz);
            List<ScannedPermission> subPermissions = new ArrayList<>();

            for (Method method : clazz.getMethods()) {
                if (!isHttpEndpoint(method)) {
                    continue;
                }
                // 检查是否为 CRUD 内置方法；若为内置方法但被 exclude 则跳过
                Curd curd = findCurdByMethodName(method.getName());
                if (curd != null && !curdList.contains(curd)) {
                    continue;
                }

                AccessRequirement access = getAccessRequirement(clazz, method);
                if (!access.login() || !access.authorize()) {
                    continue;
                }

                String subIdentity = identity + "_" + method.getName();
                String methodName = getMethodDescription(method);
                String subName = className + "-" + methodName;

                subPermissions.add(new ScannedPermission(subIdentity, subName, List.of()));
            }

            // 按权限标识排序，保证扫描结果确定可重现
            subPermissions.sort(Comparator.comparing(ScannedPermission::identity));
            results.add(new ScannedPermission(identity, className, Collections.unmodifiableList(subPermissions)));
        }

        results.sort(Comparator.comparing(ScannedPermission::identity));
        return Collections.unmodifiableList(results);
    }

    /**
     * 解析类与方法组合后的访问控制要求。方法注解优先于类注解。
     */
    public static @NotNull AccessRequirement getAccessRequirement(@NotNull Class<?> clazz, @NotNull Method method) {
        boolean login = false;
        boolean authorize = false;

        Permission classPerm = clazz.getAnnotation(Permission.class);
        if (classPerm != null) {
            login = classPerm.login();
            authorize = login && classPerm.authorize();
        }

        Permission methodPerm = method.getAnnotation(Permission.class);
        if (methodPerm != null) {
            login = methodPerm.login();
            authorize = login && methodPerm.authorize();
        }

        return new AccessRequirement(login, authorize);
    }

    /**
     * 判断方法是否标注了 Spring MVC 的 HTTP 映射注解。
     */
    public static boolean isHttpEndpoint(@NotNull Method method) {
        return method.isAnnotationPresent(RequestMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class);
    }

    /**
     * 获取控制器类对应的权限标识（如 UserController -> User）。
     */
    public static @NotNull String getControllerIdentity(@NotNull Class<?> clazz) {
        return clazz.getSimpleName().replace(CONTROLLER_SUFFIX, "");
    }

    /**
     * 获取控制器描述文案。
     */
    public static @NotNull String getControllerDescription(@NotNull Class<?> clazz) {
        Description desc = clazz.getAnnotation(Description.class);
        if (desc != null && !desc.value().trim().isEmpty()) {
            return desc.value().trim();
        }
        return getControllerIdentity(clazz);
    }

    /**
     * 获取方法描述文案。
     */
    public static @NotNull String getMethodDescription(@NotNull Method method) {
        Description desc = method.getAnnotation(Description.class);
        if (desc != null && !desc.value().trim().isEmpty()) {
            return desc.value().trim();
        }
        return method.getName();
    }

    /**
     * 按方法名匹配标准 Curd 枚举项。
     */
    private static @Nullable Curd findCurdByMethodName(@NotNull String methodName) {
        for (Curd curd : Curd.values()) {
            if (curd.getMethodName().equals(methodName)) {
                return curd;
            }
        }
        return null;
    }

    /**
     * 使用 Spring 类路径扫描器搜寻包下的所有 Controller 类。
     */
    private static @NotNull List<Class<?>> findControllerClasses(@NotNull String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                    + ClassUtils.convertClassNameToResourcePath(packageName)
                    + "/**/*" + CONTROLLER_SUFFIX + ".class";
            Resource[] resources = resolver.getResources(pattern);
            MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);

            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                try {
                    MetadataReader reader = readerFactory.getMetadataReader(resource);
                    String className = reader.getClassMetadata().getClassName();
                    classes.add(Class.forName(className));
                } catch (Exception ex) {
                    log.debug("读取类元数据跳过：{}", resource, ex);
                }
            }
        } catch (Exception ex) {
            log.error("扫描控制器资源失败", ex);
        }
        return classes;
    }

    /**
     * 将轻量扫描节点转换为持久化 PermissionEntity。
     */
    private static @NotNull PermissionEntity toEntity(@NotNull ScannedPermission node) {
        PermissionEntity entity = new PermissionEntity()
                .setIdentity(node.identity())
                .setName(node.name())
                .setIsSystem(true);
        if (node.children() != null && !node.children().isEmpty()) {
            entity.setChildren(node.children().stream()
                    .map(PermissionScanner::toEntity)
                    .toList());
        } else {
            entity.setChildren(new ArrayList<>());
        }
        return entity;
    }
}
