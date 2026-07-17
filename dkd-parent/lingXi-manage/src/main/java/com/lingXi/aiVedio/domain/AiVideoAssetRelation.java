package com.lingXi.aiVedio.domain;

import lombok.Data;

/** AI 视频资产之间的引用及血缘关系。 */
@Data
public class AiVideoAssetRelation
{
    private Long relationId;
    private Long projectId;
    private Long fromAssetId;
    private Long toAssetId;
    private String relationType;
    private Integer relationOrder;
    private String metadataJson;
}
