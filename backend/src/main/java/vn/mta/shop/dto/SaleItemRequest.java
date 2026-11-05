package vn.mta.shop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record SaleItemRequest(
    @Min(1) long skuId,
    @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal price,
    @Min(1) int quota,
    @Min(1) @Max(5) int perCustomerLimit) {}
