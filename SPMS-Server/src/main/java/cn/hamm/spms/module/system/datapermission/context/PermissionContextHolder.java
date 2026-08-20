package cn.hamm.spms.module.system.datapermission.context;

import java.util.Optional;

/**
 * <h1>数据权限上下文持有器</h1>
 *
 * @author Hamm.cn
 */
public final class PermissionContextHolder {
    private static final ThreadLocal<PermissionContext> CONTEXT = new ThreadLocal<>();

    private PermissionContextHolder() {
    }

    public static Scope open(PermissionContext context) {
        PermissionContext previous = CONTEXT.get();
        CONTEXT.set(context);
        return () -> {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        };
    }

    public static Optional<PermissionContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static PermissionContext require() {
        return current().orElseThrow(() -> new IllegalStateException("数据权限上下文不存在"));
    }

    public static void clear() {
        CONTEXT.remove();
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
