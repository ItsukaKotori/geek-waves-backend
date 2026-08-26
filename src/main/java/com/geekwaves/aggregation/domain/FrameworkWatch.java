package com.geekwaves.aggregation.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.itsuka.mybatis.plus.domain.BaseDomain;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("framework_watch")
public class FrameworkWatch extends BaseDomain {
    private String name;
    private String githubRepo;
    private String latestVersion;
    private LocalDateTime lastReleaseAt;
}
