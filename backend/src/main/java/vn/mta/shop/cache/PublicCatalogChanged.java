package vn.mta.shop.cache;

/** Chỉ được phát trong transaction ghi dữ liệu; listener xóa cache sau commit thành công. */
public record PublicCatalogChanged() {}
