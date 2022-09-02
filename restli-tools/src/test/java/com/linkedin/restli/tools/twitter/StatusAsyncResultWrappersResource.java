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

package com.linkedin.restli.tools.twitter;

import com.linkedin.common.callback.Callback;
import com.linkedin.restli.server.ActionResult;
import com.linkedin.restli.server.BatchFinderResult;
import com.linkedin.restli.server.BatchResult;
import com.linkedin.restli.server.CollectionResult;
import com.linkedin.restli.server.GetResult;
import com.linkedin.restli.server.PagingContext;
import com.linkedin.restli.server.ResourceLevel;
import com.linkedin.restli.server.annotations.Action;
import com.linkedin.restli.server.annotations.ActionParam;
import com.linkedin.restli.server.annotations.AlternativeKey;
import com.linkedin.restli.server.annotations.AlternativeKeys;
import com.linkedin.restli.server.annotations.BatchFinder;
import com.linkedin.restli.server.annotations.CallbackParam;
import com.linkedin.restli.server.annotations.Finder;
import com.linkedin.restli.server.annotations.PagingContextParam;
import com.linkedin.restli.server.annotations.QueryParam;
import com.linkedin.restli.server.annotations.RestLiCollection;
import com.linkedin.restli.server.annotations.RestMethod;
import com.linkedin.restli.server.resources.CollectionResourceAsyncTemplate;
import com.linkedin.restli.tools.twitter.TwitterTestDataModels.Status;
import com.linkedin.restli.tools.twitter.TwitterTestDataModels.User;
import java.util.Set;

/**
 * CollectionResource containing all statuses implemented as an async resource with result wrappers.
 *
 * @author dellamag
 */
@RestLiCollection(name="statusesAsyncWrapped",
    keyName="statusID")
@AlternativeKeys(alternativeKeys = {@AlternativeKey(name="alt", keyCoercer=StringLongCoercer.class, keyType=String.class),
    @AlternativeKey(name="newAlt", keyCoercer=StringLongCoercer.class, keyType=String.class)})
public class StatusAsyncResultWrappersResource extends CollectionResourceAsyncTemplate<Long,Status>
{
  /**
   * Gets a sample of the timeline of statuses generated by all users
   */
  @Finder("public_timeline")
  public void getPublicTimeline(@PagingContextParam PagingContext pagingContext, @CallbackParam Callback<CollectionResult<Status, User>> callback)
  {

  }
  /**
   * Batch finder for statuses
   */
  @BatchFinder(value="batchFinderByAction",  batchParam="criteria")
  public void batchFindStatuses(@QueryParam("criteria") Status[] criteria,
      @CallbackParam Callback<BatchFinderResult<Status, Status, User>> callback)
  {
  }

  /**
   * Gets a batch of statuses
   */
  @RestMethod.BatchGet
  public void batchGetWrapped(Set<Long> ids, @CallbackParam Callback<BatchResult<Long, Status>> callback)
  {

  }

  /**
   * Gets a single status resource
   */
  @RestMethod.Get
  public void getWrapped(Long key, @CallbackParam Callback<GetResult<Status>> callback)
  {

  }

  /**
   * Gets all the resources
   */
  @RestMethod.GetAll
  public void getAllWrapped(@PagingContextParam PagingContext ctx,
      @CallbackParam Callback<CollectionResult<Status, User>> callback) {

  }

  /**
   * Ambiguous action binding test case
   */
  @Action(name="forward",
      resourceLevel= ResourceLevel.ENTITY)
  public void forward(@ActionParam("to") long userID, @CallbackParam Callback<ActionResult<String>> callback)
  {

  }
}
