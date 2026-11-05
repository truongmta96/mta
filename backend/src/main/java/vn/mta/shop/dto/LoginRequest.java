package vn.mta.shop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record LoginRequest(
    @NotBlank @Email @Size(max = 190) String email,
    @NotBlank @Size(max = 72) String password,
    @Pattern(regexp = "ADMIN|CUSTOMER") String portal) {}
