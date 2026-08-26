package com.geekwaves.aggregation.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.itsuka.mybatis.plus.domain.BaseDomain;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("info_source")
public class InfoSource extends BaseDomain {
    private String name;
    private String code;
    private String type;
    private String baseUrl;
    private String configJson;
    private Boolean enabled;
    private Integer sortOrder;
    private Integer refreshMinutes;
    private LocalDateTime lastFetchAt;
    private String lastFetchStatus;
    private String lastError;
    private Integer failCount;
}
