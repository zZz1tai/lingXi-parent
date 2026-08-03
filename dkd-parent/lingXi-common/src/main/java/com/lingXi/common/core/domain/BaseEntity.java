package com.lingXi.common.core.domain;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Entity基类
 *
 * @author ruoyi
 */
@Schema(description = "实体基类，包含所有实体通用的基础字段")
public class BaseEntity implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 搜索值
     */
    @Schema(description = "搜索关键字，用于全局搜索功能", hidden = true)
    @JsonIgnore
    private String searchValue;

    /**
     * 创建者
     */
    @Schema(description = "记录创建者的用户名", example = "admin")
    private String createBy;

    /**
     * 创建时间
     */
    @Schema(description = "记录创建时间", example = "2025-09-01 10:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新者
     */
    @Schema(description = "最后更新者的用户名", example = "system")
    private String updateBy;

    /**
     * 更新时间
     */
    @Schema(description = "记录最后更新时间", example = "2025-09-02 15:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 备注
     */
    @Schema(description = "记录的备注信息", example = "这是一条重要记录")
    private String remark;

    /**
     * 请求参数
     */
    @Schema(description = "额外的请求参数，用于传递动态条件")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> params;

    public String getSearchValue()
    {
        return searchValue;
    }

    public void setSearchValue(String searchValue)
    {
        this.searchValue = searchValue;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getUpdateBy()
    {
        return updateBy;
    }

    public void setUpdateBy(String updateBy)
    {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public Map<String, Object> getParams()
    {
        if (params == null)
        {
            params = new HashMap<>();
        }
        return params;
    }

    public void setParams(Map<String, Object> params)
    {
        this.params = params;
    }
}
