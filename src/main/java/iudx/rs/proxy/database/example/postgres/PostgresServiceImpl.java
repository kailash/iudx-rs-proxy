package iudx.rs.proxy.database.example.postgres;

import static iudx.rs.proxy.database.example.postgres.Constants.AFTER;
import static iudx.rs.proxy.database.example.postgres.Constants.ATTRS;
import static iudx.rs.proxy.database.example.postgres.Constants.BEFORE;
import static iudx.rs.proxy.database.example.postgres.Constants.DATABASE_IP;
import static iudx.rs.proxy.database.example.postgres.Constants.DATABASE_NAME;
import static iudx.rs.proxy.database.example.postgres.Constants.DATABASE_PASSWORD;
import static iudx.rs.proxy.database.example.postgres.Constants.DATABASE_PORT;
import static iudx.rs.proxy.database.example.postgres.Constants.DATABASE_USERNAME;
import static iudx.rs.proxy.database.example.postgres.Constants.END_TIME;
import static iudx.rs.proxy.database.example.postgres.Constants.ID;
import static iudx.rs.proxy.database.example.postgres.Constants.POOL_SIZE;
import static iudx.rs.proxy.database.example.postgres.Constants.PSQL_SELECT_QUERY;
import static iudx.rs.proxy.database.example.postgres.Constants.PSQL_TABLE_EXISTS_QUERY;
import static iudx.rs.proxy.database.example.postgres.Constants.PSQL_TEMPORAL_CONDITION;
import static iudx.rs.proxy.database.example.postgres.Constants.PSQL_ATTR_CONDITION;
import static iudx.rs.proxy.database.example.postgres.Constants.TIME;
import static iudx.rs.proxy.database.example.postgres.Constants.TIME_REL;
import static iudx.rs.proxy.database.example.postgres.Constants.SEARCH_TYPE;
import static iudx.rs.proxy.database.example.postgres.Constants.TEMPORAL_SEARCH_REGEX;
import static iudx.rs.proxy.database.example.postgres.Constants.GEOSEARCH_REGEX;
import static iudx.rs.proxy.database.example.postgres.Constants.ATTRIBUTE_SEARCH_REGEX;
import static iudx.rs.proxy.database.example.postgres.Constants.ATTR_QUERY;
import static iudx.rs.proxy.database.example.postgres.Constants.ATTRIBUTE;
import static iudx.rs.proxy.database.example.postgres.Constants.OPERATOR;
import static iudx.rs.proxy.database.example.postgres.Constants.VALUE;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.serviceproxy.ServiceException;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import iudx.rs.proxy.common.ServiceExceptionMessage;
import iudx.rs.proxy.database.DatabaseService;

public class PostgresServiceImpl implements DatabaseService {

  private final PgPool pgClient;
  private PgConnectOptions connectOptions;
  private PoolOptions poolOptions;

  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseUserName;
  private String databasePassword;
  private int poolSize;

  private boolean exists;

  public PostgresServiceImpl(Vertx vertx, JsonObject config) {

    databaseIP = config.getString(DATABASE_IP);
    databasePort = config.getInteger(DATABASE_PORT);
    databaseName = config.getString(DATABASE_NAME);
    databaseUserName = config.getString(DATABASE_USERNAME);
    databasePassword = config.getString(DATABASE_PASSWORD);
    poolSize = config.getInteger(POOL_SIZE);

    this.connectOptions =
        new PgConnectOptions()
            .setPort(databasePort)
            .setHost(databaseIP)
            .setDatabase(databaseName)
            .setUser(databaseUserName)
            .setPassword(databasePassword);

    this.poolOptions = new PoolOptions().setMaxSize(poolSize);
    this.pgClient = PgPool.pool(vertx, connectOptions, poolOptions);
  }

  @Override
  public DatabaseService searchQuery(JsonObject request, Handler<AsyncResult<JsonObject>> handler)
      throws ServiceException {

    if (!request.containsKey(ID)
        || request.getJsonArray(ID).isEmpty()
        || !request.containsKey(SEARCH_TYPE)) {
      throw new ServiceException(HttpStatus.SC_BAD_REQUEST, "message for failure");
    }

    String tableID = request.getJsonArray(ID).getString(0);

    if (!tableExists(tableID)) {
      ServiceExceptionMessage detailedMsg =
          new ServiceExceptionMessage.Builder("urn:dx:rs:DatabaseError")
              .withDetails(new JsonObject()
                  .put("message", "table doesn't exist in the provided schema"))
              .build();
      throw new ServiceException(HttpStatus.SC_NOT_FOUND, "table not found", detailedMsg.toJson());
    }

    String query = queryBuilder(request, false);

    Collector<Row, ?, List<JsonObject>> rowCollector =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    pgClient
        .withConnection(
            connection -> connection.query(query).collecting(rowCollector).execute()
                .map(row -> row.value()))
        .onSuccess(
            successHandler -> {
              long totalHits = successHandler.size();
              JsonArray response = new JsonArray(successHandler);
              handler.handle(
                  Future.succeededFuture(
                      new JsonObject().put("totalHits", totalHits).put("result", response)));
            })
        .onFailure(
            failureHandler -> {
              ServiceExceptionMessage detailedMsg =
                  new ServiceExceptionMessage.Builder("urn:dx:rs:DatabaseError")
                      .withDetails(new JsonObject().put("message", failureHandler.getMessage()))
                      .build();
              throw new ServiceException(HttpStatus.SC_NOT_FOUND, "brief message",
                  detailedMsg.toJson());
            });
    return this;
  }

  @Override
  public DatabaseService countQuery(JsonObject request, Handler<AsyncResult<JsonObject>> handler)
      throws ServiceException {

    if (!request.containsKey(ID)
        || request.getJsonArray(ID).isEmpty()
        || !request.containsKey(SEARCH_TYPE)) {
      throw new ServiceException(HttpStatus.SC_BAD_REQUEST, "message for failure");
    }

    String tableID = request.getJsonArray(ID).getString(0);

    if (!tableExists(tableID)) {
      ServiceExceptionMessage detailedMsg =
          new ServiceExceptionMessage.Builder("urn:dx:rs:DatabaseError")
              .withDetails(new JsonObject()
                  .put("message", "table doesn't exist in the provided schema"))
              .build();
      throw new ServiceException(HttpStatus.SC_NOT_FOUND, "table not found", detailedMsg.toJson());
    }

    String query = queryBuilder(request, true);

    pgClient
        .withConnection(
            sqlConnection -> sqlConnection
                .query(query)
                .execute()
                .map(rows -> rows.iterator().next().getInteger(0)))
        .onSuccess(
            count -> {
              handler.handle(Future.succeededFuture(new JsonObject().put("totalHits", count)));
            })
        .onFailure(
            failureHandler -> {
              throw new ServiceException(HttpStatus.SC_NOT_FOUND, "message for failure");
            });
    return this;
  }

  private String queryBuilder(JsonObject request, boolean isCount) {
    StringBuilder query;
    String searchType = request.getString(SEARCH_TYPE);
    String tableID = request.getJsonArray(ID).getString(0);
    String[] attrs = (String[]) request.getValue(ATTRS);

    String selection = PSQL_SELECT_QUERY.replace("$$", tableID);

    if (attrs == null || attrs.length == 0) {
      if (isCount) {
        query = new StringBuilder(selection.replace("$1", "count(*)"));
      } else {
        query = new StringBuilder(selection.replace("$1", "*"));
      }
    } else {
      query = new StringBuilder(selection.replace("$1", String.join(",", attrs)));
    }

    if (searchType.matches(TEMPORAL_SEARCH_REGEX)) {
      query = temporalQueryBuilder(request, query);
    }
    if (searchType.matches(GEOSEARCH_REGEX)) {
      query = new StringBuilder(geoQueryBuilder(request, query));
    }
    if (searchType.matches(ATTRIBUTE_SEARCH_REGEX)) {
      query = attrsQueryBuilder(request, query);
    }

    return query.toString();
  }

  private StringBuilder temporalQueryBuilder(JsonObject request, StringBuilder query) {
    String timerel = request.getString(TIME_REL);
    LocalDateTime time, endTime;
    time = LocalDateTime.parse(request.getString(TIME));
    endTime = LocalDateTime.parse(request.getString(END_TIME));

    if (timerel.equalsIgnoreCase(BEFORE)) {
      endTime = time;
      time = time.minusDays(10);
    } else if (timerel.equalsIgnoreCase(AFTER)) {
      endTime = time.plusDays(10);
    }

    query.append(
        PSQL_TEMPORAL_CONDITION
            .replace("$2", time.toString())
            .replace("$3", endTime.toString()));

    return query;
  }

  private StringBuilder geoQueryBuilder(JsonObject request, StringBuilder query) {
    // TODO: implementation
    return null;
  }

  private StringBuilder attrsQueryBuilder(JsonObject request, StringBuilder query) {
    JsonObject attr_query = request.getJsonArray(ATTR_QUERY).getJsonObject(0);
    String attribute = attr_query.getString(ATTRIBUTE);
    String operator = attr_query.getString(OPERATOR);
    double value = attr_query.getDouble(VALUE);

    if (query.toString().contains("WHERE")) {
      query.append(" AND ");
    } else {
      query.append(" WHERE ");
    }
    query.append(
        PSQL_ATTR_CONDITION
            .replace("$4", attribute)
            .replace("$op", operator)
            .replace("$5", String.valueOf(value)));
    return query;
  }

  private boolean tableExists(String tableID) {
    StringBuilder query = new StringBuilder(PSQL_TABLE_EXISTS_QUERY.replace("$1", tableID));

    exists = false;
    pgClient
        .query(query.toString())
        .execute(
            existsHandler -> {
              if (existsHandler.succeeded()) {
                RowSet<Row> rowSet = existsHandler.result();
                rowSet.forEach(
                    row -> {
                      if (row.getBoolean("exists")) {
                        exists = true;
                      }
                    });
              } else {
                throw new ServiceException(HttpStatus.SC_NOT_FOUND, "message for failure");
              }
            });

    return exists;
  }
}