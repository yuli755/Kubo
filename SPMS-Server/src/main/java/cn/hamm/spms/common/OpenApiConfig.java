package cn.hamm.spms.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * <h1>OpenAPI 3 配置</h1>
 * <p>
 * SpringDoc OpenAPI 3 配置，生成标准 OpenAPI 3.1 规范文档
 * 内置 Swagger UI 因 Spring 6.2 路径模式不兼容已关闭（见 application.yml springdoc.swagger-ui 注释），
 * 文档消费方式：/v3/api-docs JSON → 前端 codegen / 外部 OpenAPI 工具
 * </p>
 *
 * @author Hamm.cn
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @Value("${springdoc.api-docs.path:/v3/api-docs}")
    private String apiDocsPath;

    @Value("${springdoc.swagger-ui.path:/doc.html}")
    private String swaggerUiPath;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Kubo 物流仓储管理系统 API 文档")
                        .description("""
                                Kubo 物流仓储管理系统后端接口文档

                                ## 功能模块（与实际 Controller 一一对应，不写"计划中"的能力）
                                - **系统管理**: 权限、菜单、系统配置、编号规则、操作日志
                                - **数据权限**: 数据资源登记、行级策略、列级脱敏（三种出口：读/写/导出）
                                - **人事组织**: 用户、角色、部门
                                - **仓储管理 WMS**: 入库、出库、移库、库存（含明细）
                                - **渠道管理**: 客户、供应商、采购单/采购价、销售单/销售价
                                - **资产管理**: 物料主数据、合同（含附件与参与人）
                                - **工厂建模**: 仓库、库位结构（树）

                                > 历史上本文档还列过"制造执行 / 质量管理 / 物联网关 / 财务核算"，
                                > 那些模块（MES/QMS/IoT/财务）已在 2026-09 的瘦身中整域拆除
                                > （见 `docs/瘦身执行记录·第三批.md`）。别再按旧文案对接。

                                ## 认证方式
                                - **JWT Bearer Token**: `Authorization: Bearer <token>`（HS256，单密钥，
                                  TTL 默认 1 天）；密钥由 `SPMS_API_ACCESS_TOKEN_SECRET` 注入，生产必配
                                - **没有刷新令牌**：过期即重新登录（全仓无 refresh 端点，见 `docs/JWT密钥轮换SOP.md`）

                                ## 权限模型
                                - **RBAC**: 角色 → 权限标识（`pnpm perms:check` 保证前后端码值一致）
                                - **行级权限**: 主体维度 USER / DEPARTMENT / FACTORY / WAREHOUSE / TENANT；
                                  `kubo.data-permission.mode = off | shadow | enforce` 三态可灰度
                                - **列级权限**: 字段级脱敏（金额 / 手机号 / 身份证），**读、写、导出三个出口分别拦**

                                ## 已知边界（写清楚，免得对接方按想象开发）
                                - **没有** `Idempotency-Key` 实现：重复提交由业务唯一键与状态机兜底（旧文案里那句
                                  "Header 自动幂等"是规划稿，已从本文档移除）；
                                - **没有**分布式事务组件（无 Seata / 无 MQ）：跨表一致性靠单库事务 +
                                  Outbox 表（`permission_change_outbox`，消费侧需自建）。
                                """)
                        .version("1.0.0")
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080" + contextPath)
                                .description("本地开发环境"),
                        new Server()
                                .url("https://api.kubo.example.com" + contextPath)
                                .description("生产环境")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Access Token 认证，格式: Bearer <token>"))
                )
                // 全局只挂 bearerAuth。
                // 这里曾经还声明过一个 `Idempotency-Key` 头并挂成全局 SecurityRequirement，
                // 但仓库里从来没有它的实现（grep `Idempotency-Key` 只有这一处）——
                // 生成式客户端会据此给每个请求塞一个被忽略的头，读者则会以为有幂等保护。
                // 「文档声明了、代码没有」正是我们要清的那类失真，所以直接删掉声明。
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}