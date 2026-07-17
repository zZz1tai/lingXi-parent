1. **分析现有代码**：已确认项目使用Swagger 2.0（io.swagger.annotations包），AiController已有完整注解，app和manage目录下的Controller需要添加注解。

2. **添加类级注解**：为每个Controller类添加`@Api(tags = "xxx")`注解，标签名基于类的功能描述。

3. **添加方法级注解**：为每个Controller方法添加`@ApiOperation("xxx")`注解，描述方法的具体功能。

4. **批量处理**：

   * app目录：TaskController、EmpController、TaskDetailsController

   * manage目录：RoleController、VendingMachineController、TaskTypeController、DashBoardController、SkuController、JobController、TaskDetailsController、ChannelController、SkuClassController、PartnerController、EmpController、OrderController、NodeController、RegionController、VmTypeController、PolicyController

5. **确保一致性**：使用与AiController相同的swagger注解风格，确保项目注解规范统一。

6. **验证**：添加注解后，确保代码能正常编译，swagger文档能正确生成

