package vn.mta.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record RefundRequest(@NotBlank @Size(max = 500) String reason) {}
