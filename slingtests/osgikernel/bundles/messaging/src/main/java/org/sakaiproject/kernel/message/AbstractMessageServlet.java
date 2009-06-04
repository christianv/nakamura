/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.kernel.message;

import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.sakaiproject.kernel.util.PathUtils;

/**
 * 
 */
public abstract class AbstractMessageServlet extends SlingAllMethodsServlet {

  /**
   *
   */
  private static final long serialVersionUID = 7894134023341453341L;

  
  /**
   * @param servletPath 
   * @param pathInfo 
   * @return
   */
  protected String toInternalPath(String servletPath, String pathInfo, String selector) {
    String hashedPath = PathUtils.getHashedPath(pathInfo, 4);
    if ( hashedPath.endsWith("/") ) {
      hashedPath = hashedPath.substring(0, hashedPath.length()-2);
    }
    return PathUtils.normalizePath(servletPath  
        + PathUtils.getHashedPath(pathInfo, 4) + selector);  }

}
