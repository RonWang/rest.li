/*
   Copyright (c) 2012 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.restli.internal.server.response;


import com.linkedin.data.ByteString;
import com.linkedin.data.DataMap;
import com.linkedin.r2.message.rest.RestException;
import com.linkedin.r2.message.rest.RestRequest;
import com.linkedin.r2.message.rest.RestResponse;
import com.linkedin.r2.message.rest.RestResponseBuilder;
import com.linkedin.restli.common.ActionResponse;
import com.linkedin.restli.common.ContentType;
import com.linkedin.restli.common.HttpStatus;
import com.linkedin.restli.common.ProtocolVersion;
import com.linkedin.restli.common.ResourceMethod;
import com.linkedin.restli.common.RestConstants;
import com.linkedin.restli.internal.common.CookieUtil;
import com.linkedin.restli.internal.server.RestLiInternalException;
import com.linkedin.restli.internal.server.RoutingResult;
import com.linkedin.restli.internal.server.ServerResourceContext;
import com.linkedin.restli.internal.server.methods.MethodAdapterRegistry;
import com.linkedin.restli.internal.server.model.ResourceMethodDescriptor;
import com.linkedin.restli.internal.server.util.DataMapUtils;
import com.linkedin.restli.server.CollectionResult;
import com.linkedin.restli.server.CreateResponse;
import com.linkedin.restli.server.RestLiResponseData;
import com.linkedin.restli.server.RestLiServiceException;
import com.linkedin.restli.server.UpdateResponse;
import com.linkedin.restli.server.resources.CollectionResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpCookie;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.activation.MimeTypeParseException;


/**
 * Interprets the method response to generate a {@link RestResponse}. Per methods on
 * {@link CollectionResource}, response can be any of the following:
 *
 * <ul>
 * <li> V extends RecordTemplate  - get response, custom response
 * <li> Map&lt;K, RecordTemplate&gt; - batch get response
 * <li> List&lt;RecordTemplate&gt; - collection response (no total)
 * <li> {@link CollectionResult} - collection response (includes total)
 * <li> {@link CreateResponse} - create response
 * <li> {@link UpdateResponse} - update response
 * <li> {@link ActionResponse} - action response
 * </ul>
 *
 * @author dellamag
 * @author nshankar
 */
public class RestLiResponseHandler
{
  private final MethodAdapterRegistry _methodAdapterRegistry;
  private final ErrorResponseBuilder _errorResponseBuilder;

  public RestLiResponseHandler(MethodAdapterRegistry methodAdapterRegistry, ErrorResponseBuilder errorResponseBuilder)
  {
    _methodAdapterRegistry = methodAdapterRegistry;
    _errorResponseBuilder = errorResponseBuilder;
  }

  public static class Builder
  {
    private MethodAdapterRegistry _methodAdapterRegistry = null;
    private ErrorResponseBuilder _errorResponseBuilder = null;
    private boolean _permissiveEncoding = false;

    public Builder setMethodAdapterRegistry(MethodAdapterRegistry methodAdapterRegistry)
    {
      _methodAdapterRegistry = methodAdapterRegistry;
      return this;
    }

    public Builder setErrorResponseBuilder(ErrorResponseBuilder errorResponseBuilder)
    {
      _errorResponseBuilder = errorResponseBuilder;
      return this;
    }

    public RestLiResponseHandler build()
    {
      if (_errorResponseBuilder == null)
      {
        _errorResponseBuilder = new ErrorResponseBuilder();
      }
      if (_methodAdapterRegistry == null)
      {
        _methodAdapterRegistry = new MethodAdapterRegistry(_errorResponseBuilder);
      }
      return new RestLiResponseHandler(_methodAdapterRegistry, _errorResponseBuilder);
    }
  }

  /**
   * Build a RestResponse from response object, incoming RestRequest and RoutingResult.
   *
   * TODO: Can zap this method since we have the other two methods.
   *
   * @param request
   *          {@link RestRequest}
   * @param routingResult
   *          {@link RoutingResult}
   * @param responseObject
   *          response value
   * @return {@link RestResponse}
   * @throws IOException
   *           if cannot build response
   */
  public RestResponse buildResponse(final RestRequest request,
                                    final RoutingResult routingResult,
                                    final Object responseObject) throws IOException
  {
    return buildResponse(routingResult,
                         buildPartialResponse(routingResult,
                                              buildRestLiResponseData(request, routingResult, responseObject)));
  }


  /**
   * Build a RestResponse from PartialRestResponse and RoutingResult.
   *
   * @param routingResult
   *          {@link RoutingResult}
   * @param partialResponse
   *          {@link PartialRestResponse}
   * @return
   */
  public RestResponse buildResponse(final RoutingResult routingResult,
                                     PartialRestResponse partialResponse)
  {
    List<String> cookies = CookieUtil.encodeSetCookies(partialResponse.getCookies());
    RestResponseBuilder builder =
        new RestResponseBuilder().setHeaders(partialResponse.getHeaders()).setCookies(cookies).setStatus(partialResponse.getStatus()
                                                     .getCode());
    if (partialResponse.hasData())
    {
      DataMap dataMap = partialResponse.getDataMap();
      String mimeType = ((ServerResourceContext) routingResult.getContext()).getResponseMimeType();
      builder = encodeResult(mimeType, builder, dataMap);
    }
    return builder.build();
  }

  /**
   * Build a ParialRestResponse from RestLiResponseDataInternal and RoutingResult.
   *
   * @param routingResult
   *          {@link RoutingResult}
   * @param responseData
   *          response value
   * @return {@link PartialRestResponse}
   * @throws IOException
   *           if cannot build response
   */
  public PartialRestResponse buildPartialResponse(final RoutingResult routingResult,
                                                  final RestLiResponseData responseData)
  {
    if (responseData.isErrorResponse()){
      return _errorResponseBuilder.buildResponse(routingResult, responseData);
    }

    return chooseResponseBuilder(null, routingResult).buildResponse(routingResult, responseData);
  }

  /**
   * Build a RestLiResponseDataInternal from response object, incoming RestRequest and RoutingResult.
   *
   * @param request
   *          {@link RestRequest}
   * @param routingResult
   *          {@link RoutingResult}
   * @param responseObject
   *          response value
   * @return {@link RestLiResponseEnvelope}
   * @throws IOException
   *           if cannot build response
   */
  public RestLiResponseData buildRestLiResponseData(final RestRequest request,
                                                    final RoutingResult routingResult,
                                                    final Object responseObject) throws IOException
  {
    ServerResourceContext context = (ServerResourceContext) routingResult.getContext();
    final ProtocolVersion protocolVersion = context.getRestliProtocolVersion();
    Map<String, String> responseHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    responseHeaders.putAll(context.getResponseHeaders());
    responseHeaders.put(RestConstants.HEADER_RESTLI_PROTOCOL_VERSION, protocolVersion.toString());
    List<HttpCookie> responseCookies = context.getResponseCookies();


    if (responseObject == null)
    {
      //If we have a null result, we have to assign the correct response status
      if (routingResult.getResourceMethod().getType().equals(ResourceMethod.ACTION))
      {
        RestLiResponseDataImpl responseData = new RestLiResponseDataImpl(HttpStatus.S_200_OK, responseHeaders,
                                                                         responseCookies);
        responseData.setResponseEnvelope(new ActionResponseEnvelope(null, responseData));
        return responseData;
      }
      else if (routingResult.getResourceMethod().getType().equals(ResourceMethod.GET))
      {
        throw new RestLiServiceException(HttpStatus.S_404_NOT_FOUND,
            "Requested entity not found: " + routingResult.getResourceMethod());
      }
      else
      {
        //All other cases do not permit null to be returned
        throw new RestLiServiceException(HttpStatus.S_500_INTERNAL_SERVER_ERROR,
            "Unexpected null encountered. Null returned by the resource method: " + routingResult.getResourceMethod());
      }
    }

    RestLiResponseBuilder responseBuilder = chooseResponseBuilder(responseObject, routingResult);

    if (responseBuilder == null)
    {
      // this should not happen if valid return types are specified
      ResourceMethodDescriptor resourceMethod = routingResult.getResourceMethod();
      String fqMethodName =
          resourceMethod.getResourceModel().getResourceClass().getName() + '#'
              + routingResult.getResourceMethod().getMethod().getName();
      throw new RestLiInternalException("Invalid return type '" + responseObject.getClass() + " from method '"
          + fqMethodName + '\'');
    }
    return responseBuilder.buildRestLiResponseData(request, routingResult, responseObject, responseHeaders, responseCookies);
  }

  public RestLiResponseData buildExceptionResponseData(final RestRequest request,
                                                           final RoutingResult routingResult,
                                                           final Object object,
                                                           final Map<String, String> headers,
                                                           final List<HttpCookie> cookies)
  {
    return _errorResponseBuilder.buildRestLiResponseData(request, routingResult, object, headers, cookies);
  }

  public RestException buildRestException(final Throwable e, PartialRestResponse partialResponse)
  {
    List<String> cookies = CookieUtil.encodeSetCookies(partialResponse.getCookies());
    RestResponseBuilder builder =
        new RestResponseBuilder().setHeaders(partialResponse.getHeaders()).setCookies(cookies).setStatus(partialResponse.getStatus()
                .getCode());
    if (partialResponse.hasData())
    {
      DataMap dataMap = partialResponse.getDataMap();
      ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
      DataMapUtils.write(dataMap, null, baos, true); // partialResponse.getSchema()
      builder.setEntity(ByteString.unsafeWrap(baos.toByteArray()));
      builder.setHeader(RestConstants.HEADER_CONTENT_TYPE, ContentType.JSON.getHeaderKey());
    }
    RestResponse restResponse = builder.build();
    RestException restException = new RestException(restResponse, e);
    return restException;
  }

  private RestResponseBuilder encodeResult(String mimeType, RestResponseBuilder builder, DataMap dataMap)
  {
    try
    {
      ContentType type = ContentType.getContentType(mimeType).orElseThrow(
          () -> new RestLiServiceException(HttpStatus.S_406_NOT_ACCEPTABLE,
              "Requested mime type for encoding is not supported. Mimetype: " + mimeType));
      assert type != null;
      builder.setHeader(RestConstants.HEADER_CONTENT_TYPE, type.getHeaderKey());
      // Use unsafe wrap to avoid copying the bytes when request builder creates ByteString.
      builder.setEntity(ByteString.unsafeWrap(DataMapUtils.mapToBytes(dataMap, type.getCodec())));
    }
    catch (MimeTypeParseException e)
    {
      throw new RestLiServiceException(HttpStatus.S_406_NOT_ACCEPTABLE, "Invalid mime type: " + mimeType);
    }

    return builder;
  }

  private RestLiResponseBuilder chooseResponseBuilder(final Object responseObject,
                                                      final RoutingResult routingResult)
  {
    if (responseObject != null && responseObject instanceof RestLiServiceException)
    {
      return _errorResponseBuilder;
    }

    return _methodAdapterRegistry.getResponsebuilder(routingResult.getResourceMethod()
                                                                 .getType());
  }
}
