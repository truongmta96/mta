package vn.mta.shop.dto;

import jakarta.validation.constraints.NotBlank;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record OrderStatusRequest(@NotBlank String status) {}
