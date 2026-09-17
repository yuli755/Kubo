package cn.hamm.spms.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * <h1>测试夹具 SQL 的加载器（SQL 分层门禁的配套件）</h1>
 *
 * <p>规则（2026-09-18）：<b>禁止 {@code .java} 文件编写 SQL 语句，只能使用专属文件编写</b>。
 * 生产代码的专属文件是 {@code src/main/resources/mapper/**&#47;*.xml}；测试夹具是
 * {@code src/test/resources/sql/**&#47;*.sql} —— 本类就是测试侧唯一的入口。</p>
 *
 * <p>为什么测试里也不许写：夹具 SQL 里就写着<b>表名与列名</b>（H2 的建表语句是"这段代码认为
 * 数据库长什么样"的最直白声明）。它散在 Java 字符串里时没人会去核；写进 {@code .sql} 文件后，
 * 改一个列名会同时出现在 diff 里，且可以用 {@code grep} 一次看全。这个仓已经因为"注解/迁移脚本/
 * 测试夹具三处各写一份列定义"吃过亏，所以这一层也按同一规则收口。</p>
 *
 * <p>实现直接用 Spring 自己的 {@link ResourceDatabasePopulator}（不手搓分号切分）：
 * 它认得 {@code --} 注释、引号里的分号与 {@code ;} 结尾，行为与 {@code @Sql} 注解一致。</p>
 *
 * @author Hamm.cn
 */
public final class SqlFixtures {
    /**
     * 夹具文件根目录（classpath 相对路径）
     */
    private static final String BASE = "sql/";

    private SqlFixtures() {
    }

    /**
     * 执行一个夹具脚本（脚本里可以写多条语句，用分号分隔）
     *
     * @param jdbcTemplate JdbcTemplate（取它的 DataSource）
     * @param relativePath 相对 {@code src/test/resources/sql/} 的路径，如 {@code mapper-runtime/storage.sql}
     */
    public static void execute(JdbcTemplate jdbcTemplate, String relativePath) {
        execute(jdbcTemplate, relativePath, Map.of());
    }

    /**
     * 执行一个夹具脚本，并替换脚本里的 {@code ${key}} 占位符
     *
     * @param jdbcTemplate JdbcTemplate
     * @param relativePath 相对 {@code src/test/resources/sql/} 的路径
     * @param placeholders 占位符（值来自代码，不是拼 SQL —— 语句本身仍在 .sql 文件里）
     */
    public static void execute(JdbcTemplate jdbcTemplate, String relativePath, Map<String, String> placeholders) {
        String script = text(relativePath);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            script = script.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(
                        script.getBytes(StandardCharsets.UTF_8)));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(Objects.requireNonNull(
                jdbcTemplate.getDataSource(), "JdbcTemplate 没有可用的 DataSource"));
    }

    /**
     * 读取一个 SQL 片段（用于把"验证查询"也放进专属文件：断言执行的语句同样不该写在 Java 里）
     *
     * @param relativePath 相对 {@code src/test/resources/sql/} 的路径
     * @return 文件内容（已去掉首尾空白与结尾分号）
     */
    public static String text(String relativePath) {
        try (InputStream in = new ClassPathResource(BASE + relativePath).getInputStream()) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // 去掉注释行与首尾空白，便于直接交给 queryForList/queryForObject
            StringBuilder cleaned = new StringBuilder();
            for (String line : sql.split("\n")) {
                if (line.trim().startsWith("--")) {
                    continue;
                }
                cleaned.append(line).append('\n');
            }
            return cleaned.toString().trim().replaceAll(";\\s*$", "");
        } catch (IOException exception) {
            throw new IllegalStateException("读不到夹具 SQL：" + relativePath, exception);
        }
    }

    /**
     * 按分号切分脚本（需要逐条执行时用；{@link #execute} 是常规入口）
     *
     * @param relativePath 相对路径
     * @return 语句数组
     */
    public static String[] statements(String relativePath) {
        return ScriptUtils.splitSqlScript(text(relativePath), ";").toArray(new String[0]);
    }
}
