package com.geekwaves.ai.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.itsuka.mybatis.plus.domain.BaseDomain;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_provider")
public class AiProvider extends BaseDomain {
    private String name;
    private String vendor;
    private String baseUrl;
    private String apiKeyEnc;
    private String model;
    private Boolean enabled;
    private Boolean isDefault;
}
