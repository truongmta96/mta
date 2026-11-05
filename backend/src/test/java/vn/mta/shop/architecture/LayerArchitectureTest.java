package vn.mta.shop.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.mta.shop.dto.PlaceOrderRequest;
import vn.mta.shop.security.SessionManager;
import vn.mta.shop.service.impl.OrderServiceImpl;
import vn.mta.shop.service.impl.PaymentServiceImpl;

/** Guardrail kiến trúc: chạy bằng mvn test/package, không cần database hay Spring context. */
class LayerArchitectureTest {
  private static final Path SOURCE = Path.of("src/main/java/vn/mta/shop");

  private List<Path> sources(String subpackage) throws Exception {
    try (var files = Files.walk(SOURCE.resolve(subpackage))) {
      return files.filter(p -> p.toString().endsWith(".java")).toList();
    }
  }

  private Class<?> type(Path file) throws Exception {
    String name =
        SOURCE
            .relativize(file)
            .toString()
            .replace('\\', '.')
            .replace('/', '.')
            .replace(".java", "");
    return Class.forName("vn.mta.shop." + name);
  }

  @Test
  void controllersOnlyDependOnServiceContractsAndHttpSessionAdapter() throws Exception {
    var files = sources("controller");
    assertFalse(files.isEmpty());
    for (var file : files) {
      for (var field : type(file).getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) continue;
        Class<?> dependency = field.getType();
        assertTrue(
            (dependency.isInterface() && dependency.getPackageName().equals("vn.mta.shop.service"))
                || dependency == SessionManager.class,
            file + " references wrong layer: " + dependency);
      }
      assertFalse(Files.readString(file).contains("@Transactional"), file + " owns a transaction");
    }
  }

  @Test
  void servicesHaveInterfacesAndImplementations() throws Exception {
    var contracts =
        sources("service").stream()
            .filter(p -> p.getParent().equals(SOURCE.resolve("service")))
            .toList();
    assertFalse(contracts.isEmpty());
    for (var file : contracts) {
      Class<?> contract = type(file);
      assertTrue(contract.isInterface(), file.toString());
      Class<?> implementation =
          Class.forName("vn.mta.shop.service.impl." + contract.getSimpleName() + "Impl");
      assertTrue(contract.isAssignableFrom(implementation));
      assertTrue(implementation.isAnnotationPresent(Service.class));
      assertFalse(
          Files.readString(
                  SOURCE.resolve("service/impl/" + implementation.getSimpleName() + ".java"))
              .contains("vn.mta.shop.controller"));
    }
  }

  @Test
  void jdbcAndSqlOnlyAppearInRepositories() throws Exception {
    Pattern sql =
        Pattern.compile(
            "\"(?:SELECT\\s|INSERT\\s+INTO\\s|UPDATE\\s+\\w+\\s+SET\\s|DELETE\\s+FROM\\s)",
            Pattern.CASE_INSENSITIVE);
    int repositories = 0;
    for (var file : sources("")) {
      String content = Files.readString(file);
      if (file.startsWith(SOURCE.resolve("repository"))) {
        repositories++;
        assertFalse(content.contains("vn.mta.shop.service"), file.toString());
        assertFalse(content.contains("vn.mta.shop.controller"), file.toString());
        continue;
      }
      assertFalse(
          content.contains("JdbcTemplate")
              || content.contains("JdbcOperations")
              || content.contains("java.sql.Connection"),
          file.toString());
      assertFalse(sql.matcher(content).find(), "SQL outside repository: " + file);
    }
    assertTrue(repositories > 0);
  }

  @Test
  void writeUseCasesKeepTransactionsAtServiceBoundary() throws Exception {
    assertNotNull(
        OrderServiceImpl.class
            .getMethod("place", long.class, String.class, PlaceOrderRequest.class)
            .getAnnotation(Transactional.class));
    assertNotNull(
        OrderServiceImpl.class.getMethod("detail", long.class).getAnnotation(Transactional.class));
    assertNotNull(
        PaymentServiceImpl.class
            .getMethod("webhook", String.class, String.class)
            .getAnnotation(Transactional.class));
    // completeSandbox gọi webhook nội bộ: bắt buộc có transaction ngoài để tránh self-invocation
    // bypass proxy.
    var boundary =
        PaymentServiceImpl.class
            .getMethod("completeSandbox", long.class, String.class, String.class)
            .getAnnotation(Transactional.class);
    assertNotNull(boundary);
    assertTrue(Arrays.asList(boundary.rollbackFor()).contains(Exception.class));
  }

  @Test
  void schedulerAndBootstrapDoNotOwnBusinessLogicOrTransactions() throws Exception {
    for (String adapter : List.of("scheduler", "bootstrap")) {
      for (var file : sources(adapter)) {
        String content = Files.readString(file);
        assertFalse(content.contains("@Transactional"), file.toString());
        assertFalse(content.contains("Repository"), file.toString());
        assertFalse(content.contains("PasswordEncoder"), file.toString());
        assertTrue(content.contains("vn.mta.shop.service."), file.toString());
      }
    }
  }
}
