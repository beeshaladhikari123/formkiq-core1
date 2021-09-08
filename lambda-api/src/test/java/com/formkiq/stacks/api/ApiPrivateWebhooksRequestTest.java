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

import static com.formkiq.stacks.dynamodb.SiteIdKeyGenerator.createDatabaseKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import com.formkiq.lambda.apigateway.ApiGatewayRequestEvent;
import com.formkiq.lambda.apigateway.util.GsonUtil;
import com.formkiq.stacks.common.objects.DynamicObject;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

/** Unit Tests for request POST /public/webhooks. */
public class ApiPrivateWebhooksRequestTest extends AbstractRequestHandler {

  /** Extension for FormKiQ config file. */
  private static final String FORMKIQ_DOC_EXT = ".fkb64";
 
  /**
   * Post /private/webhooks with enabled=private .
   *
   * @throws Exception an error has occurred
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testPostWebhooks01() throws Exception {
    // given
    createApiRequestHandler(getMap());

    for (String enabled : Arrays.asList("private", "true")) {

      for (String siteId : Arrays.asList(null, UUID.randomUUID().toString())) {

        newOutstream();

        String name = UUID.randomUUID().toString();

        String id =
            getAwsServices().webhookService().saveWebhook(siteId, name, "joe", null, enabled);

        ApiGatewayRequestEvent event = toRequestEvent("/request-post-private-webhooks01.json");
        addParameter(event, "siteId", siteId);
        setPathParameter(event, "webhooks", id);

        // when
        String response = handleRequest(event);

        // then
        Map<String, String> m = fromJson(response, Map.class);
        verifyHeaders(m, "200.0");

        String documentId = verifyDocumentId(m);

        verifyS3File(id, siteId, documentId, name, null, false);
      }
    }
  }
  
  @SuppressWarnings("unchecked")
  private String verifyDocumentId(final Map<String, String> m) {
    Map<String, Object> body = fromJson(m.get("body"), Map.class);
    String documentId = body.get("documentId").toString();
    assertNotNull(documentId);
    return documentId;
  }
  
  private void verifyHeaders(final Map<String, String> map, final String statusCode) {
    final int mapsize = 3;
    assertEquals(mapsize, map.size());
    assertEquals(statusCode, String.valueOf(map.get("statusCode")));
    assertEquals(getHeaders(), "\"headers\":" + GsonUtil.getInstance().toJson(map.get("headers")));
  }
  
  @SuppressWarnings("unchecked")
  private void verifyS3File(final String webhookId, final String siteId, final String documentId,
      final String name, final String contentType, final boolean hasTimeToLive) {
    
    // verify s3 file
    try (S3Client s3 = getS3().buildClient()) {
      
      String key = createDatabaseKey(siteId, documentId + FORMKIQ_DOC_EXT);
      String json = getS3().getContentAsString(s3, getStages3bucket(), key, null);
      
      Map<String, Object> map = fromJson(json, Map.class);
      assertEquals(documentId, map.get("documentId"));
      assertEquals("webhook/" + name, map.get("userId"));
      assertEquals("webhooks/" + webhookId, map.get("path"));
      assertEquals("{\"name\":\"john smith\"}", map.get("content"));
      
      if (contentType != null) {
        assertEquals("application/json", map.get("contentType"));
      }
      
      if (hasTimeToLive) {
        DynamicObject obj = getAwsServices().webhookService().findWebhook(siteId, webhookId);
        assertNotNull(obj.get("TimeToLive"));
        assertEquals(obj.get("TimeToLive"), map.get("TimeToLive"));
      }
      
      s3.deleteObject(DeleteObjectRequest.builder().bucket(getStages3bucket()).key(key).build());
    }
  }
}