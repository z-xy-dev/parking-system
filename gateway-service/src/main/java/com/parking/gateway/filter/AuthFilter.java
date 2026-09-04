package com.parking.gateway.filter;

import com.parking.common.utils.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST = List.of(
            "/api/user/login",
            "/api/user/register",
            "/api/parking/list",
            "/api/parking/detail"
    );

    // Knife4j / Swagger 文档相关路径（含子路径匹配），网关聚合文档需要免鉴权访问
    private static final List<String> SWAGGER_PATHS = List.of(
            "v3/api-docs",
            "/doc.html",
            "/webjars/",
            "/swagger-resources",
            "/swagger-ui",
            "/favicon.ico"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 服务间内部接口（如车位占用/释放、发起支付）禁止从公网经网关访问。
        // Feign 调用走服务直连，不经过网关，因此不影响内部链路。
        if (path.contains("/internal/")) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return response.setComplete();
        }

        // 白名单放行（前缀匹配）
        if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // Swagger / Knife4j 文档相关路径放行（包含匹配）
        if (SWAGGER_PATHS.stream().anyMatch(path::contains)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        String token = authHeader.substring(7);
        try {
            if (JwtUtil.isExpired(token)) {
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }
            Long userId = JwtUtil.getUserId(token);
            String role = JwtUtil.getRole(token);
            String carPlate = JwtUtil.getCarPlate(token);
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(headers -> {
                        // 必须先清除客户端自带的身份头，否则伪造 X-User-Id 即可越权冒充他人
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Role");
                        headers.remove("X-User-Plate");
                        headers.set("X-User-Id", String.valueOf(userId));
                        if (role != null && !role.isBlank()) {
                            headers.set("X-User-Role", role);
                        }
                        if (carPlate != null && !carPlate.isBlank()) {
                            // HTTP 头仅允许 ASCII，中文需 URL 编码后再透传，避免被按 Latin-1 解析成乱码
                            headers.set("X-User-Plate", URLEncoder.encode(carPlate, StandardCharsets.UTF_8));
                        }
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() { return -100; }
}
