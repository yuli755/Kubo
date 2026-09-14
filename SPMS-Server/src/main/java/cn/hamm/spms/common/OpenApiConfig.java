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
                        .title("Kubo 智能生产管理系统 API 文档")
                        .description("""
                                Kubo 智能生产管理系统后端接口文档

                                ## 功能模块
                                - **系统管理**: 用户、角色、权限、菜单、字典、日志
                                - **人事管理**: 员工、组织架构、考勤、薪资
                                - **仓储管理**: 入库、出库、库存、盘点、波次
                                - **制造执行**: 工单、工序、排程、质检、设备
                                - **工厂建模**: 车间、产线、工位、BOM、工艺
                                - **资产设备**: 台账、点检、维修、备件
                                - **采购管理**: 询价、订单、到货、对账
                                - **销售管理**: 报价、订单、发货、开票、回款
                                - **财务核算**: 凭证、科目、成本、报表
                                - **物联网关**: 设备接入、遥测、报警、规则

                                ## 认证方式
                                - **JWT Bearer Token**: 标准 OAuth2.1 / OIDC 认证
                                - **刷新令牌**: 支持 Access Token 无感刷新

                                ## 权限模型
                                - **RBAC**: 基于角色的访问控制
                                - **行级权限**: 数据隔离 (仓库/车间/租户)
                                - **列级权限**: 字段级脱敏 (金额/手机号/身份证)

                                ## 幂等与事务
                                - **幂等键**: `Idempotency-Key` Header 自动幂等
                                - **分布式事务**: Seata AT 模式强一致
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
                        .addSecuritySchemes("idempotencyKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("Idempotency-Key")
                                .description("幂等键，用于防止重复提交，建议使用 UUID v4"))
                )
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addSecurityItem(new SecurityRequirement().addList("idempotencyKey"));
    }
}