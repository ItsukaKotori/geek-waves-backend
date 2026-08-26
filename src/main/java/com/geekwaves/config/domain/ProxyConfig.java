package com.geekwaves.config.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.itsuka.mybatis.plus.domain.BaseDomain;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("proxy_config")
public class ProxyConfig extends BaseDomain {
    private Boolean enabled;
    private String host;
    private Integer port;
    private String username;
    private String password;
}
