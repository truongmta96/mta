package vn.mta.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Dữ liệu request; validation HTTP, không chứa xử lý nghiệp vụ. */
public record CampaignRequest(
    @NotBlank @Size(max = 150) String name,
    @NotNull LocalDateTime startsAt,
    @NotNull LocalDateTime endsAt,
    @Pattern(regexp = "DRAFT|SCHEDULED|ACTIVE") @NotNull String status,
    @NotEmpty @Size(max = 100) List<@Valid SaleItemRequest> items) {}
