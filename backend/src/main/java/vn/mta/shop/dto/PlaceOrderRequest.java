package vn.mta.shop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record PlaceOrderRequest(
    @Min(1) long skuId,
    @Min(1) @Max(5) int quantity,
    @NotBlank @Size(max = 150) String recipient,
    @NotBlank @Size(max = 30) String phone,
    @NotBlank @Size(max = 500) String address,
    Long campaignId,
    BigDecimal expectedPrice) {}
