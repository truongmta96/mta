package vn.mta.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record ProfileRequest(
    @NotBlank @Size(max = 150) String name, @Size(max = 30) String phone) {}
