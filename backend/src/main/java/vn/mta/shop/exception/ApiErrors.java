package vn.mta.shop.exception;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(vn.mta.shop.common.BusinessException.class)
  public ResponseEntity<?> business(
      vn.mta.shop.common.BusinessException ex, jakarta.servlet.http.HttpServletRequest request) {
    return ResponseEntity.status(ex.status())
        .body(
            Map.of(
                "timestamp",
                java.time.Instant.now().toString(),
                "status",
                ex.status(),
                "error",
                org.springframework.http.HttpStatus.valueOf(ex.status()).getReasonPhrase(),
                "message",
                ex.getMessage(),
                "path",
                request.getRequestURI()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> conflict(DataIntegrityViolationException ex) {
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "message",
                "Dữ liệu trùng hoặc không thỏa ràng buộc. Kiểm tra email, quota và dữ liệu liên"
                    + " quan."));
  }

  @ExceptionHandler(PessimisticLockingFailureException.class)
  public ResponseEntity<?> busy(PessimisticLockingFailureException ex) {
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "message",
                "Dữ liệu đang được xử lý đồng thời. Vui lòng thử lại với cùng khóa yêu cầu."));
  }
}
