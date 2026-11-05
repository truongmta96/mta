package vn.mta.shop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record ProductRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 80) String brand,
    @Size(max = 10000) String description,
    @Min(1) @Max(4096) int storageGb,
    @NotBlank @Size(max = 40) String color,
    @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal price,
    @Min(0) int onHand) {}
