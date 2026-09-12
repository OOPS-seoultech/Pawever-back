package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;

import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Component
@RequiredArgsConstructor
public class LegacyStaffBoundary implements WebMvcConfigurer {
  private final StaffPermissions permissions;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (request.getMethod().equals("OPTIONS")) return true;
                String path = request.getRequestURI();
                if (path.startsWith("/api/admin/orders")
                    && !path.endsWith("/workflow")
                    && !path.endsWith("/payments/confirm")
                    && !path.contains("/workflow/")) {
                  String rest = path.substring("/api/admin/orders".length());
                  if (rest.isEmpty() || rest.startsWith("/start-production")) {
                    permissions.require(VIEW_ALL_ORDERS);
                    permissions.require(VIEW_ORDER_BASIC);
                  } else {
                    String number = rest.split("/")[1];
                    permissions.read(number);
                  }
                  if (!request.getMethod().equals("GET")) {
                    if (path.endsWith("/photo-links")) permissions.require(VIEW_CUSTOMER_PHOTOS);
                    else if (path.endsWith("/photos.zip"))
                      permissions.require(DOWNLOAD_CUSTOMER_PHOTOS);
                    else if (path.endsWith("/tracking"))
                      permissions.require(IMPORT_SHIPMENT_RESULTS);
                    else if (path.endsWith("/pickup-complete"))
                      permissions.require(COMPLETE_PICKUP);
                    else permissions.require(OVERRIDE_WORKFLOW);
                  }
                }
                if (path.startsWith("/api/admin/accounts")) permissions.require(MANAGE_ACCOUNTS);
                return true;
              }
            })
        .addPathPatterns("/api/admin/orders/**", "/api/admin/accounts/**");
  }
}
