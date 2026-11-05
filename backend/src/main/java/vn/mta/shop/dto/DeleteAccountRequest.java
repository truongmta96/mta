package vn.mta.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record DeleteAccountRequest(@NotBlank @Size(max = 72) String password) {}
