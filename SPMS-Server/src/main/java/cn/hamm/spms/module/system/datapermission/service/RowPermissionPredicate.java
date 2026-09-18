package cn.hamm.spms.module.system.datapermission.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <h1>行级权限 SQL 谓词</h1>
 *
 * <p>本类<b>不再自己生成条件</b>。它以前有五个 {@code tenant/owner/department/factory/warehouse}
 * 工厂，把"主体维度对应哪一列、拿什么值比"在 SQL 侧又实现了一遍 —— 那是第二份判据，
 * 与 JPA 侧那份迟早分家（§19.35 就是把它删掉）。现在条件只来自
 * {@link cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer}
 * 渲染 {@link cn.hamm.spms.module.system.datapermission.rule.SubjectDimensionRules} 那棵树，
 * 本类只负责两件事：恒假兜底，以及换写成 MyBatis 的写法。</p>
 *
 * <p>{@link #forMyBatis()} 是本类与 MyBatis 侧唯一的接缝：渲染器产出的是 JPA/命名参数风格
 * （{@code material.tenant_id = :dpRule0}），MyBatis 要 {@code #{dpRule0}}。换写只在这一处做，
 * 并且<b>只换写、不放宽</b>：片段引用了参数表里没有的键、或参数表里有片段没用到的键，都直接抛 ——
 * 后者意味着片段被截断，此时"少一个条件"的表现是<em>多放行一些行</em>，绝不能默认通过。</p>
 *
 * @author Hamm.cn
 */
public record RowPermissionPredicate(String sql, Map<String, Object> parameters) {
    /**
     * 片段里的命名参数（渲染器只会产出 {@code :dpRule0} 与 IN 的 {@code :dpRule0_1} 这一类形式）
     */
    private static final Pattern NAMED_PARAMETER = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");

    /**
     * 片段里禁止出现的记号：语句分隔与注释。片段最终是拼进 SQL 文本的，这两样一旦出现，
     * 说明它不是渲染器产出的（渲染器的列名走白名单、值一律参数化，两处都产不出这些字符）。
     */
    private static final Pattern FORBIDDEN_TOKEN = Pattern.compile("(;|--|/\\*|\\*/)");

    /**
     * MyBatis 语句里承载片段的参数名；XML 中的 {@code ${fragment}} 必须与此同值（由测试对账）
     */
    public static final String FRAGMENT_KEY = "fragment";

    /**
     * 恒假片段：所有"这一维不给看"的分支共用同一个写法
     *
     * <p>它原先在 {@code SqlPermissionRuleRenderer} 里被抄了三遍、又在这份工厂里抄第四遍。
     * 字面量看着无关紧要，但"恒假"是这套判据的兜底出口：哪一处写错成 {@code 1 = 1}，
     * 表现就是那个分支静默放开全表。</p>
     */
    public static final String DENY_SQL = "1 = 0";

    /**
     * @return 恒假谓词（不给看任何行）
     */
    public static RowPermissionPredicate deny() {
        return new RowPermissionPredicate(DENY_SQL, Map.of());
    }

    /**
     * 换写成 MyBatis 的命名参数形式：{@code :dpRule0} → {@code #{dpRule0}}
     *
     * @return 换写后的片段（参数表原样带过去）
     * @throws IllegalArgumentException 片段与参数表不吻合、出现被禁记号、或参数值不是可安全绑定的标量
     */
    public RowPermissionPredicate forMyBatis() {
        if (FORBIDDEN_TOKEN.matcher(sql).find()) {
            throw new IllegalArgumentException("permission fragment contains a forbidden SQL token (';' or a comment)");
        }
        Matcher matcher = NAMED_PARAMETER.matcher(sql);
        StringBuilder translated = new StringBuilder();
        Set<String> referenced = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!parameters.containsKey(name)) {
                throw new IllegalArgumentException("fragment references :" + name + " but no such parameter was provided");
            }
            requireBindable(name, parameters.get(name));
            referenced.add(name);
            matcher.appendReplacement(translated, Matcher.quoteReplacement("#{" + name + "}"));
        }
        matcher.appendTail(translated);
        if (referenced.isEmpty() && !parameters.isEmpty() && alreadyTranslated(parameters.keySet())) {
            // 幂等：换写过的片段（每个参数都以 #{} 的形式在场）再过一次本方法，不该被判成"参数没被引用"。
            // 不加这一条，调用方就必须记住"render 之后只能调一次"，而那种约定一定会在某个
            // 新调用点上忘一次（本格的 CI 红里就有一条是这个形状：测试先 forMyBatis() 再取参数）。
            return this;
        }
        Set<String> unused = new LinkedHashSet<>(parameters.keySet());
        unused.removeAll(referenced);
        if (!unused.isEmpty()) {
            throw new IllegalArgumentException("parameter map carries keys the fragment never references: " + unused);
        }
        return new RowPermissionPredicate(translated.toString(), parameters);
    }

    /**
     * 换写成 MyBatis-Plus {@code wrapper.apply(sql, values…)} 的形式：{@code :dpRule0} → {@code {0}}
     *
     * <p>为什么不能直接拿 {@link #forMyBatis()} 的产物去 apply：wrapper 的 {@code apply} 只认
     * {@code {0}}（按下标）与 {@code {name}}（按 Map）两种占位符，{@code #{}} 会被当成**字面量** ——
     * 不报错、条件恒不成立（§10.8.4 坑 1）。</p>
     *
     * <p>校验口径与 {@link #forMyBatis()} 完全一致（禁记号、参数必须被引用、只许标量），
     * 只是把"查表"从 MyBatis 的命名参数换成了 apply 的下标。两者共用同一张参数表，
     * 所以不可能出现"读侧片段与写侧片段条件不一致"。</p>
     *
     * @return apply 用的 SQL 与按序参数
     * @throws IllegalArgumentException 片段与参数表不吻合、出现被禁记号、或参数值不是可安全绑定的标量
     */
    public ApplyFragment forQueryWrapper() {
        if (FORBIDDEN_TOKEN.matcher(sql).find()) {
            throw new IllegalArgumentException("permission fragment contains a forbidden SQL token (';' or a comment)");
        }
        List<String> ordered = new ArrayList<>(parameters.keySet());
        Matcher matcher = NAMED_PARAMETER.matcher(sql);
        StringBuilder translated = new StringBuilder();
        Set<String> referenced = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            int index = ordered.indexOf(name);
            if (index < 0) {
                throw new IllegalArgumentException("fragment references :" + name + " but no such parameter was provided");
            }
            requireBindable(name, parameters.get(name));
            referenced.add(name);
            matcher.appendReplacement(translated, "{" + index + "}");
        }
        matcher.appendTail(translated);
        if (referenced.isEmpty() && !parameters.isEmpty() && alreadyApplied(ordered)) {
            // 幂等：换写过的片段再过一次本方法不该被判成"参数没被引用"（与 forMyBatis 同一条口径）
            return new ApplyFragment(sql, ordered.stream().map(parameters::get).toArray());
        }
        Set<String> unused = new LinkedHashSet<>(parameters.keySet());
        unused.removeAll(referenced);
        if (!unused.isEmpty()) {
            throw new IllegalArgumentException("parameter map carries keys the fragment never references: " + unused);
        }
        return new ApplyFragment(translated.toString(), ordered.stream().map(parameters::get).toArray());
    }

    /**
     * MyBatis-Plus {@code apply} 的参数：换写后的 SQL 与按序参数
     *
     * @param sql    带 {@code {0}} 占位符的片段
     * @param values 按占位符下标排列的参数值
     */
    public record ApplyFragment(String sql, Object[] values) {
    }

    private boolean alreadyApplied(List<String> ordered) {
        for (int index = 0; index < ordered.size(); index++) {
            if (!sql.contains("{" + index + "}")) {
                return false;
            }
        }
        return true;
    }

    /**
     * 组装 MyBatis 语句的参数对象（{@code fragment} + 片段用到的全部命名参数）
     *
     * @return 可直接交给 Mapper 方法的参数 Map
     */
    public Map<String, Object> myBatisStatementParameters() {
        RowPermissionPredicate translated = forMyBatis();
        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put(FRAGMENT_KEY, translated.sql());
        statement.putAll(translated.parameters());
        return statement;
    }

    private boolean alreadyTranslated(Set<String> keys) {
        for (String key : keys) {
            if (!sql.contains("#{" + key + "}")) {
                return false;
            }
        }
        return true;
    }

    /**
     * 只绑定标量。集合值一律拒绝：渲染器把 {@code IN} 列表逐项展开成 {@code :dpRule0_0} 这样的
     * 独立参数（它自己在注释里写了理由：{@code IN (:list)} 的展开依赖驱动行为，拦截器注入的片段
     * 没有那层处理）。走到这儿还有集合，说明片段不是渲染器产出的，是有人另拼了一份。
     */
    private static void requireBindable(String name, Object value) {
        // 这条 null 判据一开始被我删了，理由是"记录类的紧凑构造器会先挡掉 null"。CI 证伪了它：
        // 本类没有紧凑构造器，null 一路走到换写阶段才被发现（"Expected NPE to be thrown, but nothing was thrown"）。
        // 凭"我以为的类结构"删防线，与凭记忆写第三方类名是同一件事：防线在不在，要看代码、看断言。
        // 值得拦而不是让它绑成 NULL 的理由：col = NULL 在 SQL 里是"未知"，效果与恒假相同，但那份"相同"
        // 寄存在方言行为上；换写是片段离开 JPA 世界前的最后一道闸，闸口宁可抛。
        if (value == null) {
            throw new IllegalArgumentException("parameter " + name + " is null; bind a null only via a deny fragment");
        }
        if (value instanceof Collection<?> || value.getClass().isArray()) {
            throw new IllegalArgumentException("parameter " + name + " is a collection; fragments must come from "
                    + "SqlPermissionRuleRenderer, which expands IN lists into scalar parameters");
        }
        if (!(value instanceof Number || value instanceof String || value instanceof Boolean)) {
            throw new IllegalArgumentException("parameter " + name + " has an unbindable type: "
                    + value.getClass().getName());
        }
    }
}
