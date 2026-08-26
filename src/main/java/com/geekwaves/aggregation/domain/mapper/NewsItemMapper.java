package com.geekwaves.aggregation.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geekwaves.aggregation.domain.NewsItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NewsItemMapper extends BaseMapper<NewsItem> {
}
