package vn.mta.shop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record RegistrationRequest(
    @NotBlank @Email @Size(max = 190) String email,
    @NotBlank @Size(min = 10, max = 72) String password,
    @NotBlank @Size(max = 150) String name) {}
