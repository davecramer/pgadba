package org.postgresql.sql2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.postgresql.sql2.testutil.FutureUtil.get10;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jdk.incubator.sql2.Connection;
import jdk.incubator.sql2.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.sql2.testutil.CollectorUtils;
import org.postgresql.sql2.testutil.ConnectUtil;
import org.postgresql.sql2.testutil.DatabaseHolder;
import org.testcontainers.containers.PostgreSQLContainer;

public class PgConnectionTlsTest {
  public static PostgreSQLContainer postgresTls = DatabaseHolder.getNewWithTls();
  public static PostgreSQLContainer postgres = DatabaseHolder.getNew();

  private static DataSource ds;
  private static DataSource dsTls;
  private static DataSource dsWithoutTls;

  @BeforeAll
  public static void setUp() {
    ds = ConnectUtil.openDb(postgresTls);
    dsTls = ConnectUtil.openDbWithTls(postgresTls);
    dsWithoutTls = ConnectUtil.openDbWithTls(postgres);

    ConnectUtil.createTable(ds, "tab",
        "id int", "name varchar(100)", "answer int");
  }

  @AfterAll
  public static void tearDown() {
    ds.close();
  }

  @Test
  public void connectWithoutTlsToTlsOnlyDb() throws InterruptedException, ExecutionException, TimeoutException {

    String sql = "select 1 as t";
    try (Connection conn = ds.getConnection()) {
      get10(conn.rowOperation(sql)
          .collect(CollectorUtils.singleCollector(Integer.class))
          .submit().getCompletionStage());
      fail("Exception should have been thrown, as the connection properties doesn't include TLS");
    } catch (ExecutionException e) {
      assertEquals("no pg_hba.conf entry for host \"172.17.0.1\", user \"test\", database \"test\", SSL off",
          e.getCause().getMessage());
    }
  }

  @Test
  public void connectWithTls() throws InterruptedException, ExecutionException, TimeoutException, URISyntaxException {
    URL resource = PgConnectionTlsTest.class.getResource("/keystore.jks");
    System.setProperty("javax.net.ssl.trustStore", String.valueOf(Paths.get(resource.toURI()).toFile()));
    System.setProperty("javax.net.ssl.trustStorePassword","changeit");
    String sql = "select 1 as t";
    try (Connection conn = dsTls.getConnection()) {
      Integer result = conn.<Integer>rowOperation(sql)
          .collect(CollectorUtils.singleCollector(Integer.class))
          .submit().getCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
      assertEquals(Integer.valueOf(1), result);
    }
    System.clearProperty("javax.net.ssl.trustStore");
    System.clearProperty("javax.net.ssl.trustStorePassword");
  }

  @Test
  public void connectWithTlsToDbWithoutTls() throws InterruptedException, TimeoutException, URISyntaxException {
    URL resource = PgConnectionTlsTest.class.getResource("/keystore.jks");
    System.setProperty("javax.net.ssl.trustStore", String.valueOf(Paths.get(resource.toURI()).toFile()));
    System.setProperty("javax.net.ssl.trustStorePassword","changeit");
    String sql = "select 1 as t";
    try (Connection conn = dsWithoutTls.getConnection()) {
      Integer result = conn.<Integer>rowOperation(sql)
          .collect(CollectorUtils.singleCollector(Integer.class))
          .submit().getCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
      assertEquals(Integer.valueOf(1), result);
    } catch (ExecutionException e) {
      assertEquals("server doesn't support TLS, but TLS was required",
          e.getCause().getMessage());
    }
    System.clearProperty("javax.net.ssl.trustStore");
    System.clearProperty("javax.net.ssl.trustStorePassword");
  }
}