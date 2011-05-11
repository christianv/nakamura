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
package org.sakaiproject.nakamura.search.processors;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.presence.PresenceService;
import org.sakaiproject.nakamura.api.presence.PresenceUtils;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ad602@caret.cam.ac.uk
 */
@Component(metatype = true)
@Service
@Properties({
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_PROCESSOR_NAMES, value = "GeneralFeed")
})
public class GeneralFeedSearchResultProcessor implements SolrSearchResultProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralFeedSearchResultProcessor.class);
    @Reference
    private SolrSearchServiceFactory searchServiceFactory;
    @Reference
    private ProfileService profileService;
    @Reference
    private PresenceService presenceService;

    public GeneralFeedSearchResultProcessor() {
        LOGGER.warn("ADC w/ ctor 1!");
    }

    public GeneralFeedSearchResultProcessor(SolrSearchServiceFactory searchServiceFactory) {
        LOGGER.warn("ADC w/ ctor 2!");

        if (searchServiceFactory == null) {
            throw new IllegalArgumentException("Search Service Factory must be set when not using as a component");
        }
        this.searchServiceFactory = searchServiceFactory;
    }

    public GeneralFeedSearchResultProcessor(SolrSearchServiceFactory searchServiceFactory, ProfileService profileService, PresenceService presenceService) {
        LOGGER.warn("ADC w/ ctor 3!");

        if (searchServiceFactory == null || profileService == null || presenceService == null) {
            throw new IllegalArgumentException("SearchServiceFactory, ProfileService and PresenceService must be set when not using as a component");
        }
        this.searchServiceFactory = searchServiceFactory;
        this.presenceService = presenceService;
        this.profileService = profileService;
    }

    public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {
        return searchServiceFactory.getSearchResultSet(request, query);
    }

    public void writeResult(SlingHttpServletRequest request, JSONWriter write, Result result) throws JSONException {
        javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
        Session session = StorageClientUtils.adaptToSession(jcrSession);

        // TODO write an if to check we're having a file atm.

        // for users and groups
        try {
            AuthorizableManager authManager = session.getAuthorizableManager();

            String authorizableId = (String) result.getFirstValue("path");
            Authorizable auth = authManager.findAuthorizable(authorizableId);

            write.object();
            if (auth != null) {
                ValueMap map = profileService.getProfileMap(auth, jcrSession);
                ExtendedJSONWriter.writeValueMapInternals(write, map);

                // If this is a User Profile, then include Presence data.
                if (!auth.isGroup()) {
                    PresenceUtils.makePresenceJSON(write, authorizableId, presenceService, true);
                }
            }
            write.endObject();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        LOGGER.warn("ADC has left the building!");
    }
}