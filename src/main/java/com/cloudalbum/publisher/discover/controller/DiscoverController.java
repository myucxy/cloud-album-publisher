package com.cloudalbum.publisher.discover.controller;

import com.cloudalbum.publisher.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "局域网发现")
@RestController
@RequestMapping("/api/v1/discover")
public class DiscoverController {

    @Operation(summary = "局域网服务发现")
    @GetMapping
    public Result<Map<String, Object>> discover(HttpServletRequest request) {
        int port = 8910;
        String host = request.getHeader("Host");
        if (host != null) {
            int colonIndex = host.lastIndexOf(':');
            if (colonIndex > 0 && colonIndex < host.length() - 1) {
                try {
                    port = Integer.parseInt(host.substring(colonIndex + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "CloudAlbum");
        info.put("port", port);
        return Result.success(info);
    }
}
