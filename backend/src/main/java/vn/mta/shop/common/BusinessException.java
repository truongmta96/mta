package vn.mta.shop.common;

/** Lỗi nghiệp vụ độc lập controller; ApiErrors chuyển thành HTTP response. */
public class BusinessException extends RuntimeException {
  private final int status;

  private BusinessException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }

  public static BusinessException error(int status, String message) {
    return new BusinessException(status, message);
  }
}
