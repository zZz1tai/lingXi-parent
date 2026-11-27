package com.lingXi.mappercontract;

import com.lingXi.aiVedio.domain.AiVideoAsset;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 列表中的文本筛选必须支持包含匹配，同时保持关联与业务定位的精确语义。 */
class FuzzySearchSqlTest
{
    private Configuration mapper(String resource) throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.lingXi.manage.domain");
        configuration.getTypeAliasRegistry().registerAlias("AiVideoAsset", AiVideoAsset.class);
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    @Test
    void textFiltersUseBoundContainsQueriesAndValidPaginationSql() throws Exception
    {
        String[][] cases = {
            {"Channel", "selectChannelList", "channelCode", "innerCode"},
            {"Order", "selectOrderList", "orderNo", "innerCode"},
            {"Task", "selectTaskList", "taskCode", "innerCode", "desc", "addr"},
            {"Task", "selectTaskVoList", "taskCode", "innerCode", "desc", "addr"},
            {"VendingMachine", "selectVendingMachineList", "innerCode"},
            {"VmType", "selectVmTypeList", "model"},
            {"TaskDetails", "selectTaskDetailsList", "channelCode"}
        };
        for (String[] test : cases)
        {
            boolean asset = test[0].equals("AiVideoAsset");
            String domain = asset ? "aiVedio" : "manage";
            Configuration configuration = mapper("mapper/" + domain + "/" + test[0] + "Mapper.xml");
            String statement = "com.lingXi." + domain + ".mapper." + test[0] + "Mapper." + test[1];
            for (int index = 2; index < test.length; index++)
            {
                String field = test[index];
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("params", new HashMap<>());
                parameters.put(field, "北京-12");
                BoundSql sql = configuration.getMappedStatement(statement).getBoundSql(parameters);
                String normalized = sql.getSql().replaceAll("\\s+", " ").toLowerCase();
                assertTrue(normalized.contains("like concat('%', ?, '%')"), statement + ": " + test[index]);
                assertFalse(normalized.contains("北京-12"), "关键词必须使用预编译参数");
                assertTrue(sql.getParameterMappings().stream().anyMatch(p -> p.getProperty().equals(field)),
                    "搜索字段必须绑定到 SQL");
                assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql.getSql()));
                assertDoesNotThrow(() -> CCJSqlParserUtil.parse("select count(0) from (" + sql.getSql() + ") tmp_count"));
            }
        }
    }

    @Test
    void statusAndRegionFiltersAndBusinessIdentityRemainExact() throws Exception
    {
        Configuration configuration = mapper("mapper/manage/TaskMapper.xml");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("params", new HashMap<>());
        parameters.put("taskStatus", 1);
        parameters.put("regionId", 2L);
        String prefix = "com.lingXi.manage.mapper.TaskMapper.";
        String sql = configuration.getMappedStatement(prefix + "selectTaskVoList")
            .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
        assertTrue(sql.contains("task_status = ?"));
        assertTrue(sql.contains("region_id = ?"));
        parameters.put("innerCode", "12");
        parameters.put("productTypeId", 2);
        String identitySql = configuration.getMappedStatement(prefix + "countUnfinishedTasks")
            .getBoundSql(parameters).getSql();
        assertTrue(identitySql.contains("inner_code = ?"));
        assertFalse(identitySql.toLowerCase().contains("like"));
        // 视频历史版本按同一资产编码归组，属于关联定位，不是文本搜索框。
        Configuration assets = mapper("mapper/aiVedio/AiVideoAssetMapper.xml");
        parameters.put("assetCode", "character-1");
        String historySql = assets.getMappedStatement("com.lingXi.aiVedio.mapper.AiVideoAssetMapper.selectAiVideoAssetList")
            .getBoundSql(parameters).getSql();
        assertTrue(historySql.contains("asset_code = ?"));
    }
}
