package vn.mta.shop.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.mta.shop.repository.*;
import vn.mta.shop.service.*;
import vn.mta.shop.service.impl.ShopServiceImpl;

class ShopCacheBoundaryTest {
  private final CampaignRepository campaigns = mock(CampaignRepository.class);
  private final InventoryRepository inventory = mock(InventoryRepository.class);
  private final ReadCacheService cache = mock(ReadCacheService.class);
  private final ShopServiceImpl shop =
      new ShopServiceImpl(
          campaigns,
          cache,
          inventory,
          mock(CustomerRepository.class),
          mock(OrderRepository.class),
          mock(ProductRepository.class),
          mock(SystemRepository.class),
          mock(AccountService.class),
          mock(OrderService.class),
          mock(PaymentService.class));

  // Hai lần đọc chung metadata nhưng stock/giá sale thay đổi: API phải trả số mới từ DB.
  @Test
  void cachedMetadataNeverFreezesStockOrSalePriceAndIsNotMutated() {
    var metadata =
        new LinkedHashMap<String, Object>(
            Map.of("id", 1L, "name", "Phone", "price", new BigDecimal("100")));
    when(cache.getOrLoad(eq(CacheRegion.PRODUCTS), any())).thenReturn(List.of(metadata));
    when(inventory.findAvailableForSkus(List.of(1L)))
        .thenReturn(
            List.of(Map.of("sku_id", 1L, "available", 5)),
            List.of(Map.of("sku_id", 1L, "available", 4)));
    when(campaigns.findCurrentPriceForSku(1L))
        .thenReturn(
            List.of(),
            List.of(
                Map.of(
                    "id", 8L, "name", "Sale", "sale_price", new BigDecimal("80"), "remaining", 2)));
    var first = shop.products().get(0);
    var second = shop.products().get(0);
    assertEquals(5, first.get("available"));
    assertEquals(new BigDecimal("100"), first.get("price"));
    assertEquals(2, second.get("available"));
    assertEquals(new BigDecimal("80"), second.get("price"));
    assertEquals(new BigDecimal("100"), metadata.get("price"));
    assertFalse(metadata.containsKey("available"));
    assertFalse(metadata.containsKey("campaign_id"));
  }

  // Cache lịch sale không được giữ giờ cũ, quota cũ hoặc làm chiến dịch hết hạn tiếp tục xuất hiện.
  @Test
  void campaignCacheDoesNotFreezeServerTimeQuotaOrExpiredSchedules() {
    var active =
        new LinkedHashMap<String, Object>(
            Map.of(
                "id",
                1L,
                "name",
                "Sale",
                "status",
                "ACTIVE",
                "starts_at",
                Instant.now().minusSeconds(60).toString(),
                "ends_at",
                Instant.now().plusSeconds(60).toString()));
    var expired = new LinkedHashMap<String, Object>(active);
    expired.put("id", 2L);
    expired.put("ends_at", Instant.now().minusSeconds(1).toString());
    when(cache.getOrLoad(eq(CacheRegion.CAMPAIGNS), any())).thenReturn(List.of(active, expired));
    when(campaigns.findPublicItems(1L))
        .thenReturn(List.of(Map.of("reserved", 0)), List.of(Map.of("reserved", 1)));
    var first = shop.campaigns();
    var second = shop.campaigns();
    assertEquals(1, first.size());
    assertEquals(List.of(Map.of("reserved", 1)), second.get(0).get("items"));
    assertNotNull(second.get(0).get("server_time"));
    assertFalse(active.containsKey("server_time"));
    assertFalse(active.containsKey("items"));
  }
}
