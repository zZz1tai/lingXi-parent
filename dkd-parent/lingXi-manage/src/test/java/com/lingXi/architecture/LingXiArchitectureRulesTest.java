package com.lingXi.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 领域包边界与分层规则（对应架构文档阶段 3）。
 * <p>验收：构建阻止 Controller→Mapper 跨层、业务模块反向依赖和领域对象进入 common。</p>
 */
@AnalyzeClasses(packages = "com.lingXi", importOptions = ImportOption.DoNotIncludeTests.class)
class LingXiArchitectureRulesTest
{
    /** 控制器不得直接依赖 Mapper，查询与命令必须经由 Service 层。 */
    @ArchTest
    static final ArchRule CONTROLLERS_MUST_NOT_DEPEND_ON_MAPPERS =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..")
                    .as("控制器不得直接依赖 Mapper，应通过 Service 访问");

    /** AI 视频工作流域必须独立，不得依赖对话域业务、小说域或零售/会话管理域
     * （共享的供应商 HTTP 客户端 com.lingXi.ai.client 属基础设施，不在此列）。 */
    @ArchTest
    static final ArchRule AI_VIDEO_DOMAIN_MUST_STAY_INDEPENDENT =
            noClasses().that().resideInAPackage("com.lingXi.aiVedio..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.lingXi.ai.controller..", "com.lingXi.ai.service..",
                            "com.lingXi.ai.domain..", "com.lingXi.ai.mapper..",
                            "com.lingXi.aiNovel..", "com.lingXi.manage..")
                    .as("AI 视频域不得依赖对话域业务/小说域/管理域");

    /** 零售与业务模块不得依赖 AI 视频工作流域（对话域共享模型配置属基础设施层，不在此列）。 */
    @ArchTest
    static final ArchRule BUSINESS_DOMAINS_MUST_NOT_DEPEND_ON_AI_VIDEO_DOMAIN =
            noClasses().that().resideInAPackage("com.lingXi.manage..")
                    .should().dependOnClassesThat().resideInAPackage("com.lingXi.aiVedio..")
                    .as("管理域不得依赖 AI 视频域");

    /** 基础模块（common）不得依赖任何业务模块，保证可独立复用。 */
    @ArchTest
    static final ArchRule COMMON_MUST_NOT_DEPEND_ON_BUSINESS_MODULES =
            noClasses().that().resideInAPackage("com.lingXi.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.lingXi.manage..", "com.lingXi.ai..", "com.lingXi.aiNovel..",
                            "com.lingXi.aiVedio..", "com.lingXi.framework..",
                            "com.lingXi.system..", "com.lingXi.quartz..", "com.lingXi.generator..")
                    .as("common 不得依赖任何业务或框架模块");
}
