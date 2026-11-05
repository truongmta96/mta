package vn.mta.shop.model;

/** Chỉ cho phép hai cột quota hợp lệ, không nhận tên cột SQL từ request. */
public enum QuotaCounter {
  RESERVED("reserved"),
  SOLD("sold");
  private final String column;

  QuotaCounter(String column) {
    this.column = column;
  }

  @Override
  public String toString() {
    return column;
  }
}
