package cn.hamm.spms.module.system.datapermission.config;

import cn.hamm.spms.module.system.datapermission.rule.JpaPermissionRuleCompiler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleEvaluator;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleTreeBuilder;
import cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>数据权限内核装配</h1>
 *
 * <p>条件树内核（解析 / 编译 / 渲染 / 求值）都是无状态纯对象，不依赖 Spring；
 * 但<b>必须</b>经 {@code @Bean} 注册而不是在业务类里 {@code new}：
 * 一旦两处各 new 一份，两个实现就会分别演化，"JPA 与 SQL 判定一致"这条前提立刻失效，
 * 而那正是 P2 换轨唯一值得信任的不变量。</p>
 *
 * @author Hamm.cn
 */
@Configuration(proxyBeanMethods = false)
public class DataPermissionAutoConfiguration {

    @Bean
    public PermissionRuleParser permissionRuleParser() {
        return new PermissionRuleParser();
    }

    @Bean
    public JpaPermissionRuleCompiler jpaPermissionRuleCompiler() {
        return new JpaPermissionRuleCompiler();
    }

    /**
     * JPA 与 MyBatis 共享的策略建树器；主体维度与 condition_json 只合成一次。
     */
    @Bean
    public PermissionRuleTreeBuilder permissionRuleTreeBuilder(PermissionRuleParser parser) {
        return new PermissionRuleTreeBuilder(parser);
    }

    /**
     * 由 mapper-read 条件块中的 MyBatis 拦截器使用；与 JPA 编译器共享解析/建树内核
     */
    @Bean
    public SqlPermissionRuleRenderer sqlPermissionRuleRenderer() {
        return new SqlPermissionRuleRenderer();
    }

    /**
     * 影子双跑的行级复算基准（也用于契约测试）
     */
    @Bean
    public PermissionRuleEvaluator permissionRuleEvaluator() {
        return new PermissionRuleEvaluator();
    }
}
