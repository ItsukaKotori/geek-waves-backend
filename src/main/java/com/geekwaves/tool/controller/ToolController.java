package com.geekwaves.tool.controller;

import com.geekwaves.tool.dto.HttpRequestCommand;
import com.geekwaves.tool.dto.HttpRequestResult;
import com.geekwaves.tool.service.HttpTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.itsuka.core.domain.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {
    private final HttpTestService service;

    @PostMapping("/http-request")
    public Response<HttpRequestResult> httpRequest(@RequestBody @Valid HttpRequestCommand cmd) {
        return Response.success(service.send(cmd).block());
    }
}
