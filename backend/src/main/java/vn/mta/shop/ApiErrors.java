package vn.mta.shop;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<?> conflict(DataIntegrityViolationException ex){return ResponseEntity.status(409).body(Map.of("message","Dữ liệu trùng hoặc không thỏa ràng buộc. Kiểm tra email, quota và dữ liệu liên quan."));}
    @ExceptionHandler(PessimisticLockingFailureException.class) public ResponseEntity<?> busy(PessimisticLockingFailureException ex){return ResponseEntity.status(409).body(Map.of("message","Dữ liệu đang được xử lý đồng thời. Vui lòng thử lại với cùng khóa yêu cầu."));}
}
