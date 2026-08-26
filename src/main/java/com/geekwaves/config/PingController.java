package com.geekwaves.config;

import org.itsuka.core.domain.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {
    @GetMapping("/api/ping")
    public Response<String> ping() {
        return Response.success("pong");
    }
}
