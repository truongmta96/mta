package vn.mta.shop.cache;

/** Chỉ được phát trong transaction ghi dữ liệu; listener xóa cache sau commit thành công. */
// Không mang payload sản phẩm hay mật khẩu; chỉ là tín hiệu "metadata public vừa thay đổi".
public record PublicCatalogChanged() {}
