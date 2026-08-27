package com.geekwaves.aggregation.controller;

import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.dto.FrameworkBrief;
import com.geekwaves.aggregation.dto.NewsSourceBrief;
import com.geekwaves.aggregation.service.NewsQueryService;
import lombok.RequiredArgsConstructor;
import org.itsuka.core.domain.Response;
import org.itsuka.web.dto.PageParam;
import org.itsuka.web.dto.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {
    private final NewsQueryService queryService;

    @GetMapping
    public Response<PageResult<NewsItem>> list(@RequestParam(required = false) String category,
                                               @RequestParam(required = false) Long sourceId,
                                               PageParam param) {
        return Response.success(queryService.page(category, sourceId, param.getCurrent(), param.getSize()));
    }

    @GetMapping("/{id}")
    public Response<NewsItem> detail(@PathVariable Long id) {
        return Response.success(queryService.detail(id));
    }

    @GetMapping("/sources")
    public Response<List<NewsSourceBrief>> sources() {
        return Response.success(queryService.enabledSources());
    }

    @GetMapping("/frameworks")
    public Response<List<FrameworkBrief>> frameworks() {
        return Response.success(queryService.frameworkBriefs());
    }
}
