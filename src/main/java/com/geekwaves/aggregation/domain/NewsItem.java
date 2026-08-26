package com.geekwaves.aggregation.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.itsuka.mybatis.plus.domain.BaseDomain;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("news_item")
public class NewsItem extends BaseDomain {
    private Long sourceId;
    private String category;
    private String sourceItemId;
    private String title;
    private String url;
    private String summary;
    private String content;
    private String author;
    private String tags;
    private String extraJson;
    private Integer score;
    private LocalDateTime publishedAt;
    private LocalDateTime fetchedAt;
    private String aiStatus;
    private String aiSummary;
    private LocalDateTime aiSummaryAt;
}
