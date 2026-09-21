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
                        .title("Kubo 智能仓储管理系统 API 文档")
                        .description("""
                                Kubo 智能仓储与供应链管理系统后端接口文档

                                ## 业务模块
                                - **系统管理**: 租户管理、权限字典、系统参数配置、单据编码规则、计量单位、文件上传
                                - **人员组织**: 用户管理、组织架构部门树、角色与功能授权、个人访问令牌
                                - **资产管理**: 物料档案、合同管理、合同文档与参与方
                                - **工厂结构**: 仓库存储结构、生产空间与车间单元
                                - **渠道管理**: 供应商、客户、采购价格库、销售价格库、采购单及明细、销售单及明细
                                - **仓储管理**: 实时库存、入库单（采购/转移/其他）、出库单（销售/转移/其他）、移库单

                                ## 核心安全与控制机制
                                - **多租户与数据隔离**: 行级数据权限、列级字段动态脱敏、影子双跑对账
                                - **写侧防篡改**: 归属字段服务端上下文覆盖、只读保护与回表校验
                                - **事务与高并发防线**: Redis 分布式排他互斥锁与数据库原子条件更新（防超卖与脏读）
                                - **接口认证与安全**: JWT Bearer Token、HttpOnly Cookie 与防暴力破解限频锁定
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