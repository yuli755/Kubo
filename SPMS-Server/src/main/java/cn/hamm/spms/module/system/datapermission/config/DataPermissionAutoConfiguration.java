package cn.hamm.spms.module.system.datapermission.config;

import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleEvaluator;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleTreeBuilder;
import cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>数据权限内核装配</h1>
 *
 * <p>条件树内核（解析 / 渲染 / 求值）都是无状态纯对象，不依赖 Spring；
 * 但<b>必须</b>经 {@code @Bean} 注册而不是在业务类里 {@code new}：一旦两处各 new 一份，
 * 两个实现就会分别演化，而"注入到 SQL 的条件"与"求值器/对账认为的条件是同一棵"正是
 * 这套内核唯一值得信任的不变量（J4 之后全仓只有 {@code SqlPermissionRuleRenderer} 一个出口）。</p>
 *
 * @author Hamm.cn
 */
@Configuration(proxyBeanMethods = false)
public class DataPermissionAutoConfiguration {

    @Bean
    public PermissionRuleParser permissionRuleParser() {
        return new PermissionRuleParser();
    }

    /**
     * 注入侧与对账/探针共享的策略建树器；主体维度与 condition_json 只合成一次。
     */
    @Bean
    public PermissionRuleTreeBuilder permissionRuleTreeBuilder(PermissionRuleParser parser) {
        return new PermissionRuleTreeBuilder(parser);
    }

    /**
     * 唯一的条件渲染出口：wrapper 注入、读侧拦截器、影子对账三处都用它
     */
    @Bean
    public SqlPermissionRuleRenderer sqlPermissionRuleRenderer() {
        return new SqlPermissionRuleRenderer();
    }

    /**
     * 行级复算基准（单元/契约测试与诊断用，不在请求链路上）
     */
    @Bean
    public PermissionRuleEvaluator permissionRuleEvaluator() {
        return new PermissionRuleEvaluator();
    }
}
