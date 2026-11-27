package com.lingXi.manage.mapper;

import com.lingXi.manage.domain.Region;
import java.io.InputStream;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegionMapperSqlTest
{
    private BoundSql regionListSql(String name) throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("Region", Region.class);
        String resource = "mapper/manage/RegionMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        Region region = new Region();
        region.setRegionName(name);
        return configuration.getMappedStatement("com.lingXi.manage.mapper.RegionMapper.selectRegionListVo")
            .getBoundSql(region);
    }

    private void assertListAndPaginationCountParse(BoundSql boundSql)
    {
        String sql = boundSql.getSql();
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql), "区域列表 SQL 必须能正确解析");
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse("select count(0) from (" + sql + ") tmp_count"),
            "聚合列表的分页计数 SQL 必须能正确解析");
    }

    @Test
    void filteredListAndPaginationCountUseValidClauseOrder() throws Exception
    {
        BoundSql sql = regionListSql("北京");
        assertListAndPaginationCountParse(sql);
        assertEquals(1, sql.getParameterMappings().size());
        assertEquals("regionName", sql.getParameterMappings().get(0).getProperty());
        assertFalse(sql.getSql().contains("北京"), "筛选值必须继续使用预编译参数");
    }

    @Test
    void unfilteredListAndPaginationCountRemainValid() throws Exception
    {
        for (String name : new String[] {null, ""})
        {
            BoundSql sql = regionListSql(name);
            assertListAndPaginationCountParse(sql);
            assertEquals(0, sql.getParameterMappings().size());
        }
    }
}
