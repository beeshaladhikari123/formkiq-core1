/**
 * MIT License
 * 
 * Copyright (c) 2018 - 2020 FormKiQ
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.formkiq.stacks.api;

import static com.formkiq.testutils.aws.TestServices.AWS_REGION;
import static com.formkiq.testutils.aws.TestServices.BUCKET_NAME;
import static com.formkiq.testutils.aws.TestServices.STAGE_BUCKET_NAME;
import static org.junit.Assert.assertEquals;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.formkiq.aws.s3.S3Service;
import com.formkiq.aws.services.lambda.ApiGatewayRequestContext;
import com.formkiq.aws.services.lambda.ApiGatewayRequestEvent;
import com.formkiq.aws.services.lambda.AwsServiceCache;
import com.formkiq.aws.sqs.SqsService;
import com.formkiq.lambda.apigateway.util.GsonUtil;
import com.formkiq.stacks.common.objects.DynamicObject;
import com.formkiq.stacks.dynamodb.DocumentService;
import com.formkiq.testutils.aws.DynamoDbTestServices;
import com.formkiq.testutils.aws.LambdaContextRecorder;
import com.formkiq.testutils.aws.LambdaLoggerRecorder;
import com.formkiq.testutils.aws.TestServices;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.utils.IoUtils;

/** Abstract class for testing API Requests. */
public abstract class AbstractRequestHandler {

  /** Documents Table. */
  private static String documentsTable = "Documents";
  /** Cache Table. */
  private static String cacheTable = "Cache";

  // /**
  // * Get App Environment.
  // *
  // * @return {@link String}
  // */
  // public static String getAppenvironment() {
  // return appenvironment;
  // }

  // /**
  // * Get Aws Region.
  // *
  // * @return {@link Region}
  // */
  // public static Region getAwsRegion() {
  // return awsRegion;
  // }

  // /**
  // * Get Sqs Document Formats Queue Url.
  // *
  // * @return {@link String}
  // */
  // public static String getSqsDocumentFormatsQueueUrl() {
  // return sqsDocumentFormatsQueueUrl;
  // }
  //
  // /**
  // * Get SqsWebsocketQueueUrl.
  // * @return {@link String}
  // */
  // public static String getSqsWebsocketQueueUrl() {
  // return sqsWebsocketQueueUrl;
  // }
  /** System Environment Map. */
  private Map<String, String> map = new HashMap<>();

  /** {@link Context}. */
  private Context context = new LambdaContextRecorder();

  /** {@link LambdaLogger}. */
  private LambdaLoggerRecorder logger = (LambdaLoggerRecorder) this.context.getLogger();

  // /** {@link DynamoDbHelper}. */
  // private DynamoDbHelper dbhelper;

  /** {@link AwsServiceCache}. */
  private AwsServiceCache awsServices;

  /**
   * Add header to {@link ApiGatewayRequestEvent}.
   * 
   * @param event {@link ApiGatewayRequestEvent}
   * @param key {@link String}
   * @param value {@link String}
   */
  public void addHeader(final ApiGatewayRequestEvent event, final String key, final String value) {

    if (value != null) {

      Map<String, String> header = event.getHeaders();
      if (header == null) {
        header = new HashMap<>();
      }

      header.put(key, value);
      event.setHeaders(header);
    }
  }

  /**
   * Add parameter to {@link ApiGatewayRequestEvent}.
   * 
   * @param event {@link ApiGatewayRequestEvent}
   * @param parameter {@link String}
   * @param value {@link String}
   */
  public void addParameter(final ApiGatewayRequestEvent event, final String parameter,
      final String value) {
    if (value != null) {
      Map<String, String> queryMap = new HashMap<>();
      if (event.getQueryStringParameters() != null) {
        queryMap.putAll(event.getQueryStringParameters());
      }
          
      queryMap.put(parameter, value);
      event.setQueryStringParameters(queryMap);
    }
  }

  /**
   * Assert Response Headers.
   * 
   * @param obj {@link DynamicObject}
   * 
   */
  public void assertHeaders(final DynamicObject obj) {

    assertEquals("*", obj.getString("Access-Control-Allow-Origin"));
    assertEquals("*", obj.getString("Access-Control-Allow-Methods"));
    assertEquals("Content-Type,X-Amz-Date,Authorization,X-Api-Key",
        obj.getString("Access-Control-Allow-Headers"));
    assertEquals("*", obj.getString("Access-Control-Allow-Origin"));
    assertEquals("application/json", obj.getString("Content-Type"));
  }

  /**
   * before.
   *
   * @throws Exception Exception
   */
  @BeforeEach
  public void before() throws Exception {

    // this.dbhelper = new DynamoDbHelper(TestServices.getDynamoDbConnection(DYNAMODB_PORT));
    // this.dbhelper.truncateDocumentsTable();
    // this.dbhelper.truncateWebhooks();
    // this.dbhelper.truncateConfig();

    this.map.put("APP_ENVIRONMENT", TestServices.FORMKIQ_APP_ENVIRONMENT);
    this.map.put("DOCUMENTS_TABLE", documentsTable);
    this.map.put("CACHE_TABLE", cacheTable);
    this.map.put("DOCUMENTS_S3_BUCKET", BUCKET_NAME);
    this.map.put("STAGE_DOCUMENTS_S3_BUCKET", STAGE_BUCKET_NAME);
    this.map.put("AWS_REGION", AWS_REGION.toString());
    this.map.put("DEBUG", "true");
    this.map.put("SQS_DOCUMENT_FORMATS", TestServices.getSqsDocumentFormatsQueueUrl());
    this.map.put("DISTRIBUTION_BUCKET", "formkiq-distribution-us-east-pro");
    this.map.put("FORMKIQ_TYPE", "core");
    this.map.put("WEBSOCKET_SQS_URL", TestServices.getSqsWebsocketQueueUrl());

    createApiRequestHandler(this.map);

    this.awsServices = new CoreRequestHandler().getAwsServices();

    SqsService sqsservice = this.awsServices.sqsService();
    for (String queue : Arrays.asList(TestServices.getSqsDocumentFormatsQueueUrl())) {
      ReceiveMessageResponse response = sqsservice.receiveMessages(queue);
      while (response.messages().size() > 0) {
        for (Message msg : response.messages()) {
          sqsservice.deleteMessage(queue, msg.receiptHandle());
        }

        response = sqsservice.receiveMessages(queue);
      }
    }
  }

  /**
   * Create Api Request Handler.
   * 
   * @param prop {@link Map}
   * @throws URISyntaxException URISyntaxException
   */
  public void createApiRequestHandler(final Map<String, String> prop) throws URISyntaxException {
    CoreRequestHandler.setUpHandler(prop, DynamoDbTestServices.getDynamoDbConnection(null),
        TestServices.getS3Connection(), TestServices.getSsmConnection(),
        TestServices.getSqsConnection());
  }

  /**
   * Convert JSON to Object.
   * 
   * @param <T> Class Type
   * @param json {@link String}
   * @param clazz {@link Class}
   * @return {@link Object}
   */
  protected <T> T fromJson(final String json, final Class<T> clazz) {
    return GsonUtil.getInstance().fromJson(json, clazz);
  }

  /**
   * Get {@link AwsServiceCache}.
   * 
   * @return {@link AwsServiceCache}
   */
  public AwsServiceCache getAwsServices() {
    return this.awsServices;
  }

  // /**
  // * Documents Bucket Name.
  // *
  // * @return {@link String}
  // */
  // public String getBucketName() {
  // return bucketName;
  // }

  // /**
  // * Get {@link DynamoDbHelper}.
  // *
  // * @return {@link DynamoDbHelper}
  // */
  // public DynamoDbHelper getDbhelper() {
  // return this.dbhelper;
  // }

  /**
   * Get {@link DocumentService}.
   *
   * @return {@link DocumentService}
   */
  public DocumentService getDocumentService() {
    return this.awsServices.documentService();
  }

  /**
   * Get {@link CoreRequestHandler}.
   *
   * @return {@link CoreRequestHandler}
   */
  public CoreRequestHandler getHandler() {
    return new CoreRequestHandler();
  }

  /**
   * Get Response Headers.
   *
   * @return {@link String}
   */
  public String getHeaders() {
    return "\"headers\":{" + "\"Access-Control-Allow-Origin\":\"*\","
        + "\"Access-Control-Allow-Methods\":\"*\"," + "\"Access-Control-Allow-Headers\":"
        + "\"Content-Type,X-Amz-Date,Authorization,X-Api-Key\","
        + "\"Content-Type\":\"application/json\"}";
  }

  /**
   * Get {@link LambdaLoggerRecorder}.
   *
   * @return {@link LambdaLoggerRecorder}
   */
  public LambdaLoggerRecorder getLogger() {
    return this.logger;
  }

  /**
   * Get Environment {@link Map}.
   *
   * @return {@link Map}
   */
  public Map<String, String> getMap() {
    return Collections.unmodifiableMap(this.map);
  }
  
  /**
   * Get Mock {@link Context}.
   *
   * @return {@link Context}
   */
  public Context getMockContext() {
    return this.context;
  }

  /**
   * Get {@link S3Service}.
   *
   * @return {@link S3Service}
   */
  public S3Service getS3() {
    return this.awsServices.s3Service();
  }

  /**
   * Get SSM Parameter.
   * 
   * @param key {@link String}
   * @return {@link String}
   */
  public String getSsmParameter(final String key) {
    return this.awsServices.ssmService().getParameterValue(key);
  }

  // /**
  // * Get Staging Document Bucket Name.
  // *
  // * @return {@link String}
  // */
  // public String getStages3bucket() {
  // return stages3bucket;
  // }

  /**
   * Handle Request.
   *
   * @param event {@link ApiGatewayRequestEvent}
   * @return {@link String}
   * @throws IOException IOException
   */
  public String handleRequest(final ApiGatewayRequestEvent event) throws IOException {

    String s = GsonUtil.getInstance().toJson(event);
    InputStream is = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream outstream = new ByteArrayOutputStream();

    new CoreRequestHandler().handleRequest(is, outstream, getMockContext());

    String response = new String(outstream.toByteArray(), "UTF-8");
    return response;
  }

  /**
   * Handle Request.
   * 
   * @param file {@link String}
   * @param siteId {@link String}
   * @param username {@link String}
   * @param cognitoGroups {@link String}
   * @return {@link DynamicObject}
   * @throws IOException IOException
   */
  @SuppressWarnings("unchecked")
  public DynamicObject handleRequest(final String file, final String siteId, final String username,
      final String cognitoGroups) throws IOException {
    ApiGatewayRequestEvent event = toRequestEvent(file);
    addParameter(event, "siteId", siteId);

    if (username != null) {
      setUsername(event, username);
    }

    if (cognitoGroups != null) {
      setCognitoGroup(event, cognitoGroups);
    }

    String response = handleRequest(event);

    return new DynamicObject(fromJson(response, Map.class));
  }

  /**
   * Put SSM Parameter.
   * 
   * @param name {@link String}
   * @param value {@link String}
   */
  public void putSsmParameter(final String name, final String value) {
    this.awsServices.ssmService().putParameter(name, value);
  }

  /**
   * Remove SSM Parameter.
   * 
   * @param name {@link String}
   */
  public void removeSsmParameter(final String name) {
    try {
      this.awsServices.ssmService().removeParameter(name);
    } catch (ParameterNotFoundException e) {
      // ignore property error
    }
  }


  /**
   * Set Cognito Group.
   * 
   * @param event {@link ApiGatewayRequestEvent}
   * @param cognitoGroups {@link String}
   */
  @SuppressWarnings("unchecked")
  public void setCognitoGroup(final ApiGatewayRequestEvent event, final String... cognitoGroups) {
    
    ApiGatewayRequestContext requestContext = event.getRequestContext();
    Map<String, Object> authorizer = requestContext.getAuthorizer();
    if (authorizer == null) {
      authorizer = new HashMap<>();
    }

    Map<String, Object> claims = (Map<String, Object>) authorizer.get("claims");
    if (claims == null) {
      claims = new HashMap<>();
      authorizer.put("claims", claims);
    }

    claims.put("cognito:groups", cognitoGroups);
    requestContext.setAuthorizer(authorizer);
    event.setRequestContext(requestContext);
  }

  /**
   * Set Environment Variable.
   * @param key {@link String}
   * @param value {@link String}
   */
  public void setEnvironment(final String key, final String value) {
    this.map.put(key, value);
  }

  /**
   * Set Path Parameter.
   * 
   * @param event {@link ApiGatewayRequestEvent}
   * @param parameter {@link String}
   * @param value {@link String}
   */
  public void setPathParameter(final ApiGatewayRequestEvent event, final String parameter,
      final String value) {
    if (event.getPathParameters() == null) {
      event.setPathParameters(new HashMap<>());
    }

    Map<String, String> pathmap = new HashMap<>(event.getPathParameters());
    pathmap.put(parameter, value);
    event.setPathParameters(pathmap);
  }

  /**
   * Set Cognito Group.
   * 
   * @param event {@link ApiGatewayRequestEvent}
   * @param username {@link String}
   */
  @SuppressWarnings("unchecked")
  public void setUsername(final ApiGatewayRequestEvent event, final String username) {
    ApiGatewayRequestContext requestContext = event.getRequestContext();
    Map<String, Object> authorizer = requestContext.getAuthorizer();
    if (authorizer == null) {
      authorizer = new HashMap<>();
    }

    Map<String, Object> claims = (Map<String, Object>) authorizer.get("claims");
    if (claims == null) {
      claims = new HashMap<>();
      authorizer.put("claims", claims);
    }

    claims.put("cognito:username", username);
    
    requestContext.setAuthorizer(authorizer);
    event.setRequestContext(requestContext);
  }

  /**
   * Converts {@link String} filename to {@link ApiGatewayRequestEvent}.
   *
   * @param filename {@link String}
   * @return {@link ApiGatewayRequestEvent}
   * @throws IOException IOException
   */
  public ApiGatewayRequestEvent toRequestEvent(final String filename) throws IOException {
    try (InputStream in = this.context.getClass().getResourceAsStream(filename)) {
      return GsonUtil.getInstance().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
          ApiGatewayRequestEvent.class);
    }
  }

  /**
   * Converts {@link String} filename to {@link InputStream}.
   *
   * @param filename {@link String}
   * @return {@link InputStream}
   * @throws IOException IOException
   */
  public InputStream toStream(final String filename) throws IOException {
    return toStream(filename, null, null);
  }

  /**
   * Converts {@link String} filename to {@link InputStream}.
   *
   * @param filename {@link String}
   * @param regex {@link StringIndexOutOfBoundsException}
   * @param replacement {@link String}
   * @return {@link InputStream}
   * @throws IOException IOException
   */
  public InputStream toStream(final String filename, final String regex, final String replacement)
      throws IOException {

    InputStream in = this.context.getClass().getResourceAsStream(filename);
    String input = IoUtils.toUtf8String(in);

    if (regex != null && replacement != null) {
      input = input.replaceAll(regex, replacement);
    }

    InputStream instream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    in.close();

    return instream;
  }
}