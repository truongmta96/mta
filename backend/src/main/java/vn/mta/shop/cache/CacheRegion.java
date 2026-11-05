package vn.mta.shop.cache;

/** Chỉ cache metadata công khai; không cache session, quyền, tồn kho, quota hoặc thanh toán. */
public enum CacheRegion {
  // Key có namespace ứng dụng và phiên bản định dạng, không nhận key tùy ý từ client.
  PRODUCTS("mta:public:products-metadata:v1", 100),
  CAMPAIGNS("mta:public:campaigns-metadata:v1", 30);
  private final String key;
  // Dùng kiểm tra payload khi đọc cache; phải tương ứng giới hạn query metadata của repository.
  private final int maximumRows;

  CacheRegion(String key, int maximumRows) {
    this.key = key;
    this.maximumRows = maximumRows;
  }

  public String key() {
    return key;
  }

  public int maximumRows() {
    return maximumRows;
  }
}
