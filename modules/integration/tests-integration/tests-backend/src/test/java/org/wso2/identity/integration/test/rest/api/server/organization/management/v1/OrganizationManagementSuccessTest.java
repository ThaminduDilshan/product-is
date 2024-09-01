/*
 * Copyright (c) 2023-2024, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.integration.test.rest.api.server.organization.management.v1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ClientCredentialsGrant;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.identity.integration.test.rest.api.server.application.management.v1.model.ApplicationListItem;
import org.wso2.identity.integration.test.rest.api.server.application.management.v1.model.ApplicationModel;
import org.wso2.identity.integration.test.rest.api.server.application.management.v1.model.ApplicationSharePOSTRequest;
import org.wso2.identity.integration.test.rest.api.server.application.management.v1.model.InboundProtocols;
import org.wso2.identity.integration.test.rest.api.server.application.management.v1.model.OpenIDConnectConfiguration;
import org.wso2.identity.integration.test.restclients.OAuth2RestClient;
import org.wso2.identity.integration.test.utils.OAuth2Constant;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.wso2.identity.integration.test.restclients.RestBaseClient.API_SERVER_PATH;
import static org.wso2.identity.integration.test.restclients.RestBaseClient.CONTENT_TYPE_ATTRIBUTE;
import static org.wso2.identity.integration.test.restclients.RestBaseClient.ORGANIZATION_PATH;
import static org.wso2.identity.integration.test.restclients.RestBaseClient.TENANT_PATH;
import static org.wso2.identity.integration.test.scim2.SCIM2BaseTestCase.SCIM2_USERS_ENDPOINT;

/**
 * Tests for successful cases of the Organization Management REST APIs.
 */
public class OrganizationManagementSuccessTest extends OrganizationManagementBaseTest {

    private String organizationID;
    private String childOrganizationID;
    private String selfServiceAppId;
    private String selfServiceAppClientId;
    private String selfServiceAppClientSecret;
    private String m2mToken;
    private String switchedM2MToken;
    private String b2bApplicationID;
    private HttpClient client;
    private List<Map<String, String>> organizations;

    protected OAuth2RestClient restClient;

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public OrganizationManagementSuccessTest(TestUserMode userMode) throws Exception {

        super.init(userMode);
        this.context = isServer;
        this.authenticatingUserName = context.getContextTenant().getTenantAdmin().getUserName();
        this.authenticatingCredential = context.getContextTenant().getTenantAdmin().getPassword();
        this.tenant = context.getContextTenant().getDomain();
    }

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.testInit(API_VERSION, swaggerDefinition, tenant);
        oAuth2RestClient = new OAuth2RestClient(serverURL, tenantInfo);
        client = HttpClientBuilder.create().build();
    }

    @AfterClass(alwaysRun = true)
    public void testConclude() throws Exception {

        super.conclude();
        deleteApplication(selfServiceAppId);
        deleteApplication(b2bApplicationID);
        oAuth2RestClient.closeHttpClient();
    }

    @BeforeMethod(alwaysRun = true)
    public void testInit() {

        RestAssured.basePath = basePath;
    }

    @AfterMethod(alwaysRun = true)
    public void testFinish() {

        RestAssured.basePath = StringUtils.EMPTY;
    }

    @DataProvider(name = "restAPIUserConfigProvider")
    public static Object[][] restAPIUserConfigProvider() {

        return new Object[][]{
                {TestUserMode.SUPER_TENANT_ADMIN},
                {TestUserMode.TENANT_ADMIN}
        };
    }

    @Test
    public void createApplicationForSelfOrganizationOnboardService() throws IOException, JSONException {

        String endpointURL = "applications";
        String body = readResource("create-organization-self-service-app-request-body.json");

        Response response = given().auth().preemptive().basic(authenticatingUserName, authenticatingCredential)
                .contentType(ContentType.JSON)
                .body(body).when().post(endpointURL);
        response.then()
                .log().ifValidationFails().assertThat().statusCode(HttpStatus.SC_CREATED);

        Optional<ApplicationListItem> b2bSelfServiceApp = oAuth2RestClient.getAllApplications().getApplications()
                .stream().filter(application -> application.getName().equals("b2b-self-service-app")).findAny();
        Assert.assertTrue(b2bSelfServiceApp.isPresent(), "B2B self service application is not created");
        selfServiceAppId = b2bSelfServiceApp.get().getId();

        JSONObject jsonObject = new JSONObject(readResource("organization-self-service-apis.json"));

        for (Iterator<String> apiNameIterator = jsonObject.keys(); apiNameIterator.hasNext(); ) {
            String apiName = apiNameIterator.next();
            Object requiredScopes = jsonObject.get(apiName);

            Response apiResource = given().auth().preemptive().basic(authenticatingUserName, authenticatingCredential)
                    .when().queryParam("filter", "identifier eq " + apiName).get("api-resources");
            apiResource.then().log().ifValidationFails().assertThat().statusCode(HttpStatus.SC_OK);
            String apiUUID = apiResource.getBody().jsonPath().getString("apiResources[0].id");

            JSONObject authorizedAPIRequestBody = new JSONObject();
            authorizedAPIRequestBody.put("id", apiUUID);
            authorizedAPIRequestBody.put("policyIdentifier", "RBAC");
            authorizedAPIRequestBody.put("scopes", requiredScopes);

            Response authorizedAPIResponse =
                    given().auth().preemptive().basic(authenticatingUserName, authenticatingCredential)
                            .contentType(ContentType.JSON).body(authorizedAPIRequestBody.toString()).when()
                            .post("applications/" + selfServiceAppId + "/authorized-apis");
            authorizedAPIResponse.then().log().ifValidationFails().assertThat().statusCode(HttpStatus.SC_OK);
        }
    }

    @Test(dependsOnMethods = "createApplicationForSelfOrganizationOnboardService")
    public void getM2MAccessToken() throws Exception {

        OpenIDConnectConfiguration openIDConnectConfiguration = oAuth2RestClient
                                                            .getOIDCInboundDetails(selfServiceAppId);
        selfServiceAppClientId = openIDConnectConfiguration.getClientId();
        selfServiceAppClientSecret = openIDConnectConfiguration.getClientSecret();
        AuthorizationGrant clientCredentialsGrant = new ClientCredentialsGrant();
        ClientID clientID = new ClientID(selfServiceAppClientId);
        Secret clientSecret = new Secret(selfServiceAppClientSecret);
        ClientAuthentication clientAuth = new ClientSecretBasic(clientID, clientSecret);
        Scope scope = new Scope("SYSTEM");

        URI tokenEndpoint = new URI(getTenantQualifiedURL(OAuth2Constant.ACCESS_TOKEN_ENDPOINT,
                                tenantInfo.getDomain()));
        TokenRequest request = new TokenRequest(tokenEndpoint, clientAuth, clientCredentialsGrant, scope);
        HTTPResponse tokenHTTPResp = request.toHTTPRequest().send();
        Assert.assertNotNull(tokenHTTPResp, "Access token http response is null.");

        TokenResponse tokenResponse = TokenResponse.parse(tokenHTTPResp);
        AccessTokenResponse accessTokenResponse = tokenResponse.toSuccessResponse();
        m2mToken = accessTokenResponse.getTokens().getAccessToken().getValue();
        Assert.assertNotNull(m2mToken, "The retrieved M2M Token is null in the token response.");

        Scope scopesInResponse = accessTokenResponse.getTokens().getAccessToken().getScope();
        Assert.assertTrue(scopesInResponse.contains("internal_organization_create"),
                "Requested scope is missing in the token response");
    }

    @Test(dependsOnMethods = "getM2MAccessToken")
    public void testSelfOnboardOrganization() throws IOException {

        String body = readResource("add-greater-hospital-organization-request-body.json");
        body = body.replace("${parentId}", StringUtils.EMPTY);
        Response response = getResponseOfPostWithOAuth2(ORGANIZATION_MANAGEMENT_API_BASE_PATH, body, m2mToken);
        response.then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED)
                .header(HttpHeaders.LOCATION, notNullValue());
        String location = response.getHeader(HttpHeaders.LOCATION);
        assertNotNull(location);
        organizationID = location.substring(location.lastIndexOf("/") + 1);
        assertNotNull(organizationID);
    }

    @Test(dependsOnMethods = "testSelfOnboardOrganization")
    public void testGetOrganization() {

        Response response = getResponseOfGet(ORGANIZATION_MANAGEMENT_API_BASE_PATH + PATH_SEPARATOR
                                        + organizationID);
        validateHttpStatusCode(response, HttpStatus.SC_OK);
        Assert.assertNotNull(response.asString());
        response.then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("id", equalTo(organizationID));
    }

    @DataProvider(name = "dataProviderForFilterOrganizations")
    public Object[][] dataProviderForFilterOrganizations() {

        return new Object[][] {
                {"name co G", false, false},
                {"attributes.Country co S", true, false},
                {"attributes.Country eq Sri Lanka and name co Greater", true, false},
                {"attributes.Country eq Sri Lanka and attributes.Language eq Sinhala", true, false},
                {"attributes.Country eq USA", false, true}
        };
    }

    @Test(dependsOnMethods = "testGetOrganization", dataProvider = "dataProviderForFilterOrganizations")
    public void testFilterOrganizations(String filterQuery, boolean expectAttributes, boolean expectEmptyList) {

        String query = "?filter=" + filterQuery + "&limit=1&recursive=false";
        String endpointURL = ORGANIZATION_MANAGEMENT_API_BASE_PATH + query;
        Response response = getResponseOfGetWithOAuth2(endpointURL, m2mToken);
        Assert.assertNotNull(response.asString());
        response.then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        if (expectEmptyList) {
            response.then()
                    .assertThat().body(equalTo("{}"));
        } else {
            response.then()
                    .body("organizations.size()", equalTo(1))
                    .body("organizations[0].id", equalTo(organizationID));
            if (expectAttributes) {
                response.then()
                        .body("organizations[0].attributes.size()", equalTo(2))
                        .body("organizations[0].attributes[0].key", equalTo("Country"))
                        .body("organizations[0].attributes[0].value", equalTo("Sri Lanka"));
            }
        }
    }

    @Test(dependsOnMethods = "testFilterOrganizations")
    public void switchM2MToken() throws IOException, ParseException, InterruptedException {

        ApplicationSharePOSTRequest applicationSharePOSTRequest = new ApplicationSharePOSTRequest();
        applicationSharePOSTRequest.setShareWithAllChildren(false);
        applicationSharePOSTRequest.setSharedOrganizations(Collections.singletonList(organizationID));
        oAuth2RestClient.shareApplication(selfServiceAppId, applicationSharePOSTRequest);

        // Since application sharing is an async operation, wait for sometime before switching the organization.
        Thread.sleep(5000);

        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair(OAuth2Constant.GRANT_TYPE_NAME, "organization_switch"));
        urlParameters.add(new BasicNameValuePair("token", m2mToken));
        urlParameters.add(new BasicNameValuePair("scope", "SYSTEM"));
        urlParameters.add(new BasicNameValuePair("switching_organization", organizationID));

        HttpPost httpPost = new HttpPost(getTenantQualifiedURL(OAuth2Constant.ACCESS_TOKEN_ENDPOINT, tenant));
        httpPost.setHeader("Authorization", "Basic " + new String(Base64.encodeBase64(
                        (selfServiceAppClientId + ":" + selfServiceAppClientSecret).getBytes())));
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters));

        HttpResponse response = client.execute(httpPost);
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        EntityUtils.consume(response.getEntity());
        JSONParser parser = new JSONParser();
        org.json.simple.JSONObject json = (org.json.simple.JSONObject) parser.parse(responseString);
        Assert.assertNotNull(json, "Access token response is null.");
        Assert.assertNotNull(json.get(OAuth2Constant.ACCESS_TOKEN), "Access token is null.");
        switchedM2MToken = (String) json.get(OAuth2Constant.ACCESS_TOKEN);
        Assert.assertNotNull(switchedM2MToken);
    }

    @Test(dependsOnMethods = "switchM2MToken")
    public void createUserInOrganization() throws IOException {

        String body = readResource("add-admin-user-in-organization-request-body.json");
        HttpPost request = new HttpPost(serverURL + TENANT_PATH + tenant + PATH_SEPARATOR + ORGANIZATION_PATH
                                    + SCIM2_USERS_ENDPOINT);
        Header[] headerList = new Header[2];
        headerList[0] = new BasicHeader("Authorization", "Bearer " + switchedM2MToken);
        headerList[1] = new BasicHeader(CONTENT_TYPE_ATTRIBUTE, "application/scim+json");
        request.setHeaders(headerList);
        request.setEntity(new StringEntity(body));
        HttpResponse response = client.execute(request);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), 201);
    }

    @Test(dependsOnMethods = "createUserInOrganization")
    public void addB2BApplication() throws Exception {

        ApplicationModel application = new ApplicationModel();
        List<String> grantTypes = new ArrayList<>();
        Collections.addAll(grantTypes, "authorization_code");
        OpenIDConnectConfiguration oidcConfig = new OpenIDConnectConfiguration();
        oidcConfig.setGrantTypes(grantTypes);
        oidcConfig.setCallbackURLs(Collections.singletonList(OAuth2Constant.CALLBACK_URL));
        InboundProtocols inboundProtocolsConfig = new InboundProtocols();
        inboundProtocolsConfig.setOidc(oidcConfig);
        application.setInboundProtocolConfiguration(inboundProtocolsConfig);
        application.setName("Guardio-Business-App");
        b2bApplicationID = oAuth2RestClient.createApplication(application);
        Assert.assertNotNull(b2bApplicationID);
    }

    @Test(dependsOnMethods = "addB2BApplication")
    public void shareB2BApplication() throws JSONException {

        if (!SUPER_TENANT_DOMAIN.equals(tenant)) {
            return;
        }
        String shareApplicationUrl = ORGANIZATION_MANAGEMENT_API_BASE_PATH + "/" + SUPER_ORGANIZATION_ID
                                + "/applications/" + b2bApplicationID + "/share";
        org.json.JSONObject shareAppObject = new org.json.JSONObject();
        shareAppObject.put("shareWithAllChildren", true);
        getResponseOfPost(shareApplicationUrl, shareAppObject.toString());
    }

    @Test(dependsOnMethods = "shareB2BApplication")
    public void unShareB2BApplication() throws JSONException {

        if (!SUPER_TENANT_DOMAIN.equals(tenant)) {
            return;
        }
        String shareApplicationUrl = ORGANIZATION_MANAGEMENT_API_BASE_PATH + "/" + SUPER_ORGANIZATION_ID
                                + "/applications/" + b2bApplicationID + "/share";
        org.json.JSONObject shareAppObject = new org.json.JSONObject();
        shareAppObject.put("shareWithAllChildren", false);
        getResponseOfPost(shareApplicationUrl, shareAppObject.toString());
    }

    @Test(dependsOnMethods = "unShareB2BApplication")
    public void testOnboardChildOrganization() throws IOException {

        String body = readResource("add-smaller-hospital-organization-request-body.json");
        body = body.replace("${parentId}", organizationID);
        HttpPost request = new HttpPost(serverURL + TENANT_PATH + tenant + PATH_SEPARATOR + ORGANIZATION_PATH
                                    + API_SERVER_PATH + ORGANIZATION_MANAGEMENT_API_BASE_PATH);
        Header[] headerList = new Header[3];
        headerList[0] = new BasicHeader("Authorization", "Bearer " + switchedM2MToken);
        headerList[1] = new BasicHeader(CONTENT_TYPE_ATTRIBUTE, "application/json");
        headerList[2] = new BasicHeader(HttpHeaders.ACCEPT, "application/json");
        request.setHeaders(headerList);
        request.setEntity(new StringEntity(body));
        HttpResponse response = client.execute(request);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_CREATED);

        String jsonResponse = EntityUtils.toString(response.getEntity());
        JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        childOrganizationID = responseObject.get("id").getAsString();
        assertNotNull(childOrganizationID);
    }

    @DataProvider(name = "dataProviderForGetOrganizationsMetaAttributes")
    public Object[][] dataProviderForGetOrganizationsMetaAttributes() {

        return new Object[][] {
                {"attributes eq Country", false, false},
                {"attributes sw C and attributes ew try", false, false},
                {"attributes eq Region", true, false},
                {"attributes co A", true, true},
        };
    }

    @Test(dependsOnMethods  = "testOnboardChildOrganization",
          dataProvider      = "dataProviderForGetOrganizationsMetaAttributes")
    public void testGetOrganizationsMetaAttributes(String filter, boolean isRecursive, boolean expectEmptyList) {

        String query = "?filter=" + filter + "&limit=1&recursive=" + isRecursive;
        String endpointURL = ORGANIZATION_MANAGEMENT_API_BASE_PATH + "/meta-attributes" + query;
        Response response = getResponseOfGetWithOAuth2(endpointURL, m2mToken);
        Assert.assertNotNull(response.asString());
        response.then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);
        if (expectEmptyList) {
            response.then()
                    .assertThat().body(equalTo("{}"));
        } else if (isRecursive) {
            response.then()
                    .body("attributes.size()", equalTo(1))
                    .body("attributes[0]", equalTo("Region"));
        } else {
            response.then()
                    .body("attributes.size()", equalTo(1))
                    .body("attributes[0]", equalTo("Country"));
        }
    }

    @Test(dependsOnMethods = "testGetOrganizationsMetaAttributes")
    public void testAddDiscoveryConfig() throws IOException {

        String endpointURL = ORGANIZATION_CONFIGS_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH;
        String requestBody = readResource("add-discovery-config-request-body.json");
        Response response = getResponseOfPostWithOAuth2(endpointURL, requestBody, m2mToken);
        response.then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED)
                .contentType(ContentType.JSON)
                .body("properties[0].key", equalTo("emailDomain.enable"))
                .body("properties[0].value", equalTo("true"));
    }

    @Test(dependsOnMethods = "testAddDiscoveryConfig")
    public void testGetDiscoveryConfig() {

        String endpointURL = ORGANIZATION_CONFIGS_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH;
        Response response = getResponseOfGetWithOAuth2(endpointURL, m2mToken);
        response.then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .contentType(ContentType.JSON)
                .body("properties[0].key", equalTo("emailDomain.enable"))
                .body("properties[0].value", equalTo("true"));
    }

    @Test(dependsOnMethods = "testGetDiscoveryConfig")
    public void testAddDiscoveryAttributesToOrganization() throws IOException {

        String endpointURL = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH;
        String requestBody = readResource("add-discovery-attributes-request-body.json");
        requestBody = requestBody.replace("${organizationID}", organizationID);
        Response response = getResponseOfPostWithOAuth2(endpointURL, requestBody, m2mToken);
        validateHttpStatusCode(response, HttpStatus.SC_CREATED);
    }

    @Test(dependsOnMethods = "testAddDiscoveryAttributesToOrganization")
    public void testGetDiscoveryAttributesOfOrganizations() {

        String endpointURL = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH;
        Response response = getResponseOfGetWithOAuth2(endpointURL, m2mToken);
        response.then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body(TOTAL_RESULTS_PATH_PARAM, equalTo(1))
                .body("organizations[0].organizationId", equalTo(organizationID))
                .body("organizations[0].organizationName", equalTo("Greater Hospital"))
                .body("organizations[0].attributes[0].type", equalTo("emailDomain"))
                .body("organizations[0].attributes[0].values[0]", equalTo("abc.com"));
    }

    @Test(dependsOnMethods = "testGetDiscoveryAttributesOfOrganizations")
    public void testGetDiscoveryAttributesOfOrganization() {

        String endpointURL = ORGANIZATION_MANAGEMENT_API_BASE_PATH + PATH_SEPARATOR + organizationID
                        + ORGANIZATION_DISCOVERY_API_PATH;
        Response response = getResponseOfGetWithOAuth2(endpointURL, m2mToken);
        response.then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("attributes[0].type", equalTo("emailDomain"))
                .body("attributes[0].values[0]", equalTo("abc.com"));
    }

    @Test(dependsOnMethods = "testGetDiscoveryAttributesOfOrganization")
    public void testUpdateDiscoveryAttributesOfOrganization() throws IOException {

        String endpointURL = ORGANIZATION_MANAGEMENT_API_BASE_PATH + PATH_SEPARATOR + organizationID
                        + ORGANIZATION_DISCOVERY_API_PATH;
        String requestBody = readResource("update-discovery-attributes-request-body.json");
        Response response = getResponseOfPutWithOAuth2(endpointURL, requestBody, m2mToken);
        response.then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("attributes[0].type", equalTo("emailDomain"))
                .body("attributes[0].values", containsInAnyOrder("xyz.com", "example.com"));
    }

    @DataProvider(name = "checkDiscoveryAttributes")
    public Object[][] checkDiscoveryAttributeFilePaths() {

        return new Object[][]{
                {"check-discovery-attributes-available-request-body.json", true},
                {"check-discovery-attributes-unavailable-request-body.json", false}
        };
    }

    @Test(dependsOnMethods = "testUpdateDiscoveryAttributesOfOrganization", dataProvider = "checkDiscoveryAttributes")
    public void testCheckDiscoveryAttributeExists(String requestBodyFileName, boolean expectedAvailability)
            throws IOException {

        String endpointURL = ORGANIZATION_MANAGEMENT_API_BASE_PATH + PATH_SEPARATOR + "check-discovery";
        String requestBody = readResource(requestBodyFileName);
        Response response = getResponseOfPostWithOAuth2(endpointURL, requestBody, m2mToken);
        response.then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("available", equalTo(expectedAvailability));
    }

    @Test(dependsOnMethods = "testCheckDiscoveryAttributeExists")
    public void testDeleteDiscoveryAttributesOfOrganization() {

        String endpointURL = ORGANIZATION_MANAGEMENT_API_BASE_PATH + PATH_SEPARATOR + organizationID
                        + ORGANIZATION_DISCOVERY_API_PATH;
        Response response = getResponseOfDeleteWithOAuth2(endpointURL, m2mToken);
        validateHttpStatusCode(response, HttpStatus.SC_NO_CONTENT);
    }

    @Test(dependsOnMethods = "testDeleteDiscoveryAttributesOfOrganization")
    public void testDeleteDiscoveryConfig() {

        String endpointURL = ORGANIZATION_CONFIGS_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH;
        Response response = getResponseOfDeleteWithOAuth2(endpointURL, m2mToken);
        validateHttpStatusCode(response, HttpStatus.SC_NO_CONTENT);
    }

    @Test(dependsOnMethods = "testDeleteDiscoveryConfig")
    public void testDeleteChildOrganization() throws IOException {

        HttpDelete request = new HttpDelete(serverURL + TENANT_PATH + tenant + PATH_SEPARATOR + ORGANIZATION_PATH
                                        + API_SERVER_PATH + ORGANIZATION_MANAGEMENT_API_BASE_PATH + PATH_SEPARATOR
                                        + childOrganizationID);
        Header[] headerList = new Header[1];
        headerList[0] = new BasicHeader("Authorization", "Bearer " + switchedM2MToken);
        request.setHeaders(headerList);
        HttpResponse response = client.execute(request);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_NO_CONTENT);
    }

    @Test(dependsOnMethods = "testDeleteChildOrganization")
    public void testDisablingOrganization() throws IOException {

        String endpoint = ORGANIZATION_MANAGEMENT_API_BASE_PATH + PATH_SEPARATOR + organizationID;
        String body = readResource("disable-organization-request-body.json");
        Response response = getResponseOfPatch(endpoint, body);
        validateHttpStatusCode(response, HttpStatus.SC_OK);
    }

    @Test(dependsOnMethods = "testDisablingOrganization")
    public void testDeleteOrganization() {

        String organizationPath = ORGANIZATION_MANAGEMENT_API_BASE_PATH + "/" + organizationID;
        Response response = getResponseOfDelete(organizationPath);
        validateHttpStatusCode(response, HttpStatus.SC_NO_CONTENT);
    }

    private void deleteApplication(String applicationId) throws Exception {

        oAuth2RestClient.deleteApplication(applicationId);
    }

    @Test(dependsOnMethods = "testDeleteOrganization")
    public void createOrganizationsForPaginationTests() throws JSONException {

        organizations = createOrganizations(NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS);
        assertEquals(organizations.size(), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS);
    }

    @DataProvider(name = "organizationLimitValidationDataProvider")
    public Object[][] organizationLimitValidationDataProvider() {

        return new Object[][]{
                {10},
                {20},
                {25},
        };
    }

    @Test(dependsOnMethods = "createOrganizationsForPaginationTests",
            dataProvider = "organizationLimitValidationDataProvider")
    public void testGetPaginatedOrganizationsWithLimit(int limit) {

        String endpointURL = ORGANIZATION_MANAGEMENT_API_BASE_PATH + QUESTION_MARK + LIMIT_QUERY_PARAM + EQUAL + limit;
        Response response = getResponseOfGetWithOAuth2(endpointURL, m2mToken);

        validateHttpStatusCode(response, HttpStatus.SC_OK);

        int actualOrganizationCount = response.jsonPath().getList(ORGANIZATIONS_PATH_PARAM).size();
        int expectedOrganizationCount = Math.min(limit, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS);
        Assert.assertEquals(actualOrganizationCount, expectedOrganizationCount);

        String nextLink = response.jsonPath().getString(
                String.format("links.find { it.%s == '%s' }.%s", REL, LINK_REL_NEXT, HREF));

        String afterValue = null;

        if (nextLink != null && nextLink.contains(AFTER_QUERY_PARAM + EQUAL)) {
            afterValue = nextLink.substring(nextLink.indexOf(AFTER_QUERY_PARAM + EQUAL) + 6);
        }

        String storedAfterValue = afterValue;

        if (NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS > limit) {
            Assert.assertNotNull(storedAfterValue);
        } else {
            Assert.assertNull(storedAfterValue);
        }
    }

    @DataProvider(name = "organizationPaginationValidationProvider")
    public Object[][] organizationPaginationValidationProvider() {

        return new Object[][]{
                {1}, {2}, {5}, {6}, {10}, {17}
        };
    }

    @Test(dependsOnMethods = "createOrganizationsForPaginationTests",
            dataProvider = "organizationPaginationValidationProvider")
    public void testGetPaginatedOrganizations(int limit) {

        String after;
        String before;

        // Step 1: Call the first page.
        String firstPageUrl = ORGANIZATION_MANAGEMENT_API_BASE_PATH + QUESTION_MARK + LIMIT_QUERY_PARAM + EQUAL + limit
                + AMPERSAND + RECURSIVE_QUERY_PARAM + EQUAL + FALSE;

        Response firstPageResponse = getResponseOfGetWithOAuth2(firstPageUrl, m2mToken);

        validateHttpStatusCode(firstPageResponse, HttpStatus.SC_OK);

        List<Map<String, String>> firstPageLinks = firstPageResponse.jsonPath().getList(LINKS_PATH_PARAM);
        after = getLink(firstPageLinks, LINK_REL_NEXT);
        before = getLink(firstPageLinks, LINK_REL_PREVIOUS);

        Assert.assertNotNull(after, "After value should not be null on the first page.");
        Assert.assertNull(before, "Before value should be null on the first page.");

        // Validate the first page organizations.
        validateOrganizationsOnPage(firstPageResponse, 1, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, limit);

        // Step 2: Call the second page using the 'after' value.
        String secondPageUrl = ORGANIZATION_MANAGEMENT_API_BASE_PATH + QUESTION_MARK + LIMIT_QUERY_PARAM + EQUAL + limit
                + AMPERSAND + RECURSIVE_QUERY_PARAM + EQUAL + FALSE + AMPERSAND + AFTER_QUERY_PARAM + EQUAL + after;
        Response secondPageResponse = getResponseOfGetWithOAuth2(secondPageUrl, m2mToken);

        validateHttpStatusCode(secondPageResponse, HttpStatus.SC_OK);

        List<Map<String, String>> secondPageLinks = secondPageResponse.jsonPath().getList(LINKS_PATH_PARAM);
        before = getLink(secondPageLinks, LINK_REL_PREVIOUS);
        after = getLink(secondPageLinks, LINK_REL_NEXT);

        Assert.assertNotNull(before, "Before value should not be null on the second page.");
        if (NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS > limit * 2) {
            Assert.assertNotNull(after, "After value should not be null if there are more pages.");
        } else {
            Assert.assertNull(after, "After value should be null if this is the last page.");
        }

        // Validate the second page organizations.
        validateOrganizationsOnPage(secondPageResponse, 2, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, limit);

        // Step 3: Call the previous page using the 'before' value.
        String previousPageUrl =
                ORGANIZATION_MANAGEMENT_API_BASE_PATH + QUESTION_MARK + LIMIT_QUERY_PARAM + EQUAL + limit
                        + AMPERSAND + RECURSIVE_QUERY_PARAM + EQUAL + FALSE + AMPERSAND + BEFORE_QUERY_PARAM + EQUAL +
                        before;
        Response previousPageResponse = getResponseOfGetWithOAuth2(previousPageUrl, m2mToken);

        validateHttpStatusCode(previousPageResponse, HttpStatus.SC_OK);

        List<Map<String, String>> previousPageLinks = previousPageResponse.jsonPath().getList(LINKS_PATH_PARAM);
        after = getLink(previousPageLinks, LINK_REL_NEXT);
        before = getLink(previousPageLinks, LINK_REL_PREVIOUS);

        Assert.assertNotNull(after, "After value should not be null on the previous (first) page.");
        Assert.assertNull(before, "Before value should be null on the previous (first) page.");

        // Validate the previous page organizations.
        validateOrganizationsOnPage(previousPageResponse, 1, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, limit);
    }

    @DataProvider(name = "organizationPaginationNumericEdgeCasesOfLimitDataProvider")
    public Object[][] organizationPaginationNumericEdgeCasesOfLimitDataProvider() {

        return new Object[][]{
                {0}, {20}, {25}
        };
    }

    @Test(dependsOnMethods = "createOrganizationsForPaginationTests",
            dataProvider = "organizationPaginationNumericEdgeCasesOfLimitDataProvider")
    public void testGetPaginatedOrganizationsForNumericEdgeCasesOfLimit(int limit) {

        String limitUrl =
                ORGANIZATION_MANAGEMENT_API_BASE_PATH + QUESTION_MARK + LIMIT_QUERY_PARAM + EQUAL + limit + AMPERSAND +
                        RECURSIVE_QUERY_PARAM + EQUAL + FALSE;
        Response response = getResponseOfGetWithOAuth2(limitUrl, m2mToken);

        validateHttpStatusCode(response, HttpStatus.SC_OK);

        List<Map<String, String>> links = response.jsonPath().getList(LINKS_PATH_PARAM);

        Assert.assertNull(links, "Links should be null when all organizations are returned in one page.");

        // Validate the only page organizations.
        validateOrganizationsOnPage(response, 1, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, limit);
    }

    @Test(dependsOnMethods = "createOrganizationsForPaginationTests")
    public void testGetPaginatedOrganizationsForNonNumericEdgeCasesOfLimit() {

        // Test case 1: URL with LIMIT_QUERY_PARAM but no value.
        String endpointURLWithEmptyLimit =
                ORGANIZATION_MANAGEMENT_API_BASE_PATH + QUESTION_MARK + LIMIT_QUERY_PARAM + EQUAL + AMPERSAND +
                        RECURSIVE_QUERY_PARAM + EQUAL + FALSE;

        Response responseWithEmptyLimit = getResponseOfGetWithOAuth2(endpointURLWithEmptyLimit, m2mToken);

        validateHttpStatusCode(responseWithEmptyLimit, HttpStatus.SC_OK);

        validateOrganizationsForDefaultLimit(responseWithEmptyLimit, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS);

        // Test case 2: URL without LIMIT_QUERY_PARAM.
        String endpointURLWithoutLimit =
                ORGANIZATION_MANAGEMENT_API_BASE_PATH + QUESTION_MARK + RECURSIVE_QUERY_PARAM + EQUAL + FALSE;

        Response responseWithoutLimit = getResponseOfGetWithOAuth2(endpointURLWithoutLimit, m2mToken);

        validateHttpStatusCode(responseWithoutLimit, HttpStatus.SC_OK);

        validateOrganizationsForDefaultLimit(responseWithoutLimit, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS);
    }

    @Test(dependsOnMethods = "createOrganizationsForPaginationTests")
    public void testEnableEmailDomainDiscovery() {

        String enableDiscoveryPayload = "{\"properties\":[{\"key\":\"emailDomain.enable\",\"value\":true}]}";
        String emailDomainIsEnabled = "properties.find { it.key == 'emailDomain.enable' }.value";

        // Send POST request to enable email domain discovery
        Response response = getResponseOfPostWithOAuth2(
                ORGANIZATION_CONFIGS_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH,
                enableDiscoveryPayload,
                m2mToken);

        // Validate that the request was successful
        validateHttpStatusCode(response, HttpStatus.SC_CREATED);

        // Validate the response content
        boolean isEnabled = response.jsonPath().getBoolean(emailDomainIsEnabled);
        Assert.assertTrue(isEnabled, "Email domain discovery was not successfully enabled.");
    }

    @Test(dependsOnMethods = "testEnableEmailDomainDiscovery")
    public void testAddEmailDomainsToOrganization() {

        for (int i = 0; i < NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS; i++) {
            String organizationId = organizations.get(i).get(ORGANIZATION_ID);
            addEmailDomainsToOrganization(organizationId, String.format(ORGANIZATION_EMAIL_FORMAT_1, i),
                    String.format(ORGANIZATION_EMAIL_FORMAT_2, i));
        }

    }

    @DataProvider(name = "organizationDiscoveryLimitValidationDataProvider")
    public Object[][] organizationDiscoveryLimitValidationDataProvider() {

        return new Object[][]{
                {3}, {5}, {10}, {15}, {17}, {20}, {25}
        };
    }

    @Test(dependsOnMethods = "testAddEmailDomainsToOrganization",
            dataProvider = "organizationDiscoveryLimitValidationDataProvider")
    public void testGetPaginatedOrganizationsDiscoveryWithLimit(int limit) {

        int offset = 0;
        List<String> accumulatedOrganizationNames = new ArrayList<>();

        // Loop through each page to test the organization discovery GET API limit
        while (offset < NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS) {
            String queryUrl =
                    ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                            OFFSET_QUERY_PARAM + EQUAL + offset +
                            AMPERSAND + LIMIT_QUERY_PARAM + EQUAL + limit;
            Response response = getResponseOfGetWithOAuth2(queryUrl, m2mToken);

            validateHttpStatusCode(response, HttpStatus.SC_OK);

            List<Map<String, String>> returnedOrganizations = response.jsonPath().getList(ORGANIZATIONS_PATH_PARAM);

            Assert.assertEquals(returnedOrganizations.size(),
                    Math.min(limit, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS - offset));

            // Validate no duplicate organization names
            for (Map<String, String> org : returnedOrganizations) {
                String orgName = org.get(ORGANIZATION_NAME_ATTRIBUTE);
                assertFalse(accumulatedOrganizationNames.contains(orgName),
                        "Duplicate organization found: " + orgName);
                accumulatedOrganizationNames.add(orgName);
            }

            offset += limit;
        }

        // Sort the list based on the numeric part of the organization name
        accumulatedOrganizationNames.sort(Comparator.comparingInt(s -> Integer.parseInt(s.split("-")[1])));

        // Compare accumulated organization names with the original list (order does not matter)
        validateOrgNamesForOrganizationDiscoveryGet(accumulatedOrganizationNames);
    }

    @DataProvider(name = "organizationDiscoveryPaginationValidationProvider")
    public Object[][] organizationDiscoveryPaginationValidationProvider() {

        return new Object[][]{
                {1}, {2}, {5}, {6}, {10}, {17}
        };
    }

    @Test(dependsOnMethods = "testAddEmailDomainsToOrganization",
            dataProvider = "organizationDiscoveryPaginationValidationProvider")
    public void testGetPaginatedOrganizationsDiscovery(int limit) {

        int offset = 0;
        String nextLink;
        String previousLink;
        String queryUrl = buildQueryUrl(offset, limit);

        List<Map<String, String>> links;
        List<String> forwardAccumulatedOrganizationNames = new ArrayList<>();
        List<String> backwardAccumulatedOrganizationNames = new ArrayList<>();

        // Forward Pagination
        do {
            links = getPaginationLinksForOrganizationDiscovery(queryUrl, offset, limit,
                    forwardAccumulatedOrganizationNames, true);
            nextLink = getLink(links, LINK_REL_NEXT);
            previousLink = getLink(links, LINK_REL_PREVIOUS);
            queryUrl = buildNewQueryUrl(nextLink, queryUrl);

            validatePaginationLinksForOrganizationDiscovery(offset == 0,
                    offset + limit >= NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS,
                    nextLink, previousLink);

            offset += limit;

        } while (nextLink != null);

        // Backward Pagination
        do {
            links = getPaginationLinksForOrganizationDiscovery(queryUrl, offset, limit,
                    backwardAccumulatedOrganizationNames, false);
            nextLink = getLink(links, LINK_REL_NEXT);
            previousLink = getLink(links, LINK_REL_PREVIOUS);
            queryUrl = buildNewQueryUrl(previousLink, queryUrl);

            validatePaginationLinksForOrganizationDiscovery(offset == limit,
                    offset >= NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS,
                    nextLink, previousLink);

            offset -= limit;

        } while (previousLink != null);

        forwardAccumulatedOrganizationNames.sort(Comparator.comparingInt(s -> Integer.parseInt(s.split("-")[1])));
        validateOrgNamesForOrganizationDiscoveryGet(forwardAccumulatedOrganizationNames);

        backwardAccumulatedOrganizationNames.sort(Comparator.comparingInt(s -> Integer.parseInt(s.split("-")[1])));
        validateOrgNamesForOrganizationDiscoveryGet(backwardAccumulatedOrganizationNames);
    }

    @DataProvider(name = "organizationDiscoveryPaginationNumericEdgeCasesOfLimitDataProvider")
    public Object[][] organizationDiscoveryPaginationNumericEdgeCasesOfLimitDataProvider() {

        return new Object[][]{
                {0, 0}, {0, 20}, {0, 25},
                {2, 0}, {2, 20}, {2, 25}
        };
    }

    @Test(dependsOnMethods = "testAddEmailDomainsToOrganization",
            dataProvider = "organizationDiscoveryPaginationNumericEdgeCasesOfLimitDataProvider")
    public void testGetPaginatedOrganizationsDiscoveryForNumericEdgeCasesOfLimit(int offset, int limit) {

        String queryUrl = buildQueryUrl(offset, limit);

        Response response = getResponseOfGetWithOAuth2(queryUrl, m2mToken);

        validateHttpStatusCode(response, HttpStatus.SC_OK);

        // Validate the response content
        int actualCount = response.jsonPath().getInt(COUNT_PATH_PARAM);
        int totalResults = response.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM);
        int startIndex = response.jsonPath().getInt(START_INDEX_PATH_PARAM);
        List<Map<String, String>> links = response.jsonPath().getList(LINKS_PATH_PARAM);
        List<Map<String, String>> returnedOrganizations = response.jsonPath().getList("organizations");

        int expectedCount = Math.min(limit, (NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS - offset));

        Assert.assertEquals(actualCount, expectedCount,
                "Unexpected number of organizations returned for limit: " + limit);
        Assert.assertEquals(totalResults, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS,
                "Total results should match the number of organizations available.");
        Assert.assertEquals(startIndex, offset + 1,
                "Start index should always be 1 greater than the offset.");

        validateOrganizationDiscoveryLimitEdgeCaseLinks(links, limit, offset);
        validateOrganizationDiscoveryLimitEdgeCaseOrganizations(returnedOrganizations, limit, offset);
    }

    @Test(dependsOnMethods = "testAddEmailDomainsToOrganization")
    public void testGetPaginatedOrganizationsDiscoveryForNonNumericEdgeCasesOfLimit() {

        // Test case 1: URL with LIMIT_QUERY_PARAM but no value.
        String endpointURLWithEmptyLimit =
                ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                        FILTER_QUERY_PARAM + EQUAL + AMPERSAND + LIMIT_QUERY_PARAM + EQUAL + AMPERSAND +
                        OFFSET_QUERY_PARAM + EQUAL + ZERO;

        Response responseWithEmptyLimit = getResponseOfGetWithOAuth2(endpointURLWithEmptyLimit, m2mToken);
        validateHttpStatusCode(responseWithEmptyLimit, HttpStatus.SC_OK);

        validateResponseForOrganizationDiscoveryLDefaultCases(responseWithEmptyLimit);

        // Test case 2: URL without LIMIT_QUERY_PARAM.
        String endpointURLWithoutLimit =
                ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                        FILTER_QUERY_PARAM + EQUAL + AMPERSAND + OFFSET_QUERY_PARAM + EQUAL + ZERO;

        Response responseWithoutLimit = getResponseOfGetWithOAuth2(endpointURLWithoutLimit, m2mToken);
        validateHttpStatusCode(responseWithoutLimit, HttpStatus.SC_OK);

        validateResponseForOrganizationDiscoveryLDefaultCases(responseWithoutLimit);
    }

    @DataProvider(name = "organizationDiscoveryOffsetValidationDataProvider")
    public Object[][] organizationDiscoveryOffsetValidationDataProvider() {

        return new Object[][]{
                {0, 1}, {0, 5}, {0, 10},
                {5, 1}, {5, 5}, {5, 10},
                {10, 1}, {10, 5}, {10, 10}
        };
    }

    @Test(dependsOnMethods = "testAddEmailDomainsToOrganization",
            dataProvider = "organizationDiscoveryOffsetValidationDataProvider")
    public void testGetPaginatedOrganizationsDiscoveryWithOffset(int offset, int limit) {

        String queryUrl = buildQueryUrl(offset, limit);

        Response response = getResponseOfGetWithOAuth2(queryUrl, m2mToken);

        validateHttpStatusCode(response, HttpStatus.SC_OK);

        // Validate the response content
        int totalResults = response.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM);
        int startIndex = response.jsonPath().getInt(START_INDEX_PATH_PARAM);
        int count = response.jsonPath().getInt(COUNT_PATH_PARAM);
        List<Map<String, String>> links = response.jsonPath().getList(LINKS_PATH_PARAM);

        int expectedCount = Math.min(limit, totalResults - offset);

        Assert.assertEquals(totalResults, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(startIndex, offset + 1, "Start index should be offset + 1.");
        Assert.assertEquals(count, expectedCount, "The count of returned organizations is incorrect.");

        validateOrganizationDiscoveryOffsetLinks(links, limit, offset);
    }

    private void validateOrganizationDiscoveryOffsetLinks(List<Map<String, String>> links, int limit,
                                                                  int offset) {

        String nextLink = getLink(links, LINK_REL_NEXT);
        if (offset + limit < NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS) {
            Assert.assertNotNull(nextLink, "The 'next' link should be present in first/middle pages.");
            int expectedOffset = offset + limit;
            validateOrganizationDiscoveryOffsetIsInLinks(nextLink, expectedOffset);
        } else {
            Assert.assertNull(nextLink, "The 'next' link should not be present in the last page.");
        }

        String previousLink = getLink(links, LINK_REL_PREVIOUS);
        if (offset > 0) {
            Assert.assertNotNull(previousLink, "The 'previous' link should be present in last/middle pages.");
            int expectedOffset = Math.max((offset - limit), 0);
            validateOrganizationDiscoveryOffsetIsInLinks(previousLink, expectedOffset);
        } else {
            Assert.assertNull(previousLink, "The 'previous' link should not be present in the first page.");
        }
    }

    private void validateOrganizationDiscoveryOffsetIsInLinks(String link, int expectedOffset) {

        int offsetStartIndex = link.indexOf(OFFSET_QUERY_PARAM + EQUAL);

        if (offsetStartIndex != -1) {
            offsetStartIndex += (OFFSET_QUERY_PARAM + EQUAL).length();
            int offsetEndIndex = link.indexOf(AMPERSAND, offsetStartIndex);

            if (offsetEndIndex == -1) offsetEndIndex = link.length();

            int actualOffset = Integer.parseInt(link.substring(offsetStartIndex, offsetEndIndex));

            Assert.assertEquals(actualOffset, expectedOffset, "Offset in the link is incorrect.");
        } else {
            Assert.fail("Offset parameter is missing in the link.");
        }
    }

    @DataProvider(name = "numericEdgeCasesOfOffsetAndOffsetWithLimitDataProvider")
    public Object[][] numericEdgeCasesOfOffsetAndOffsetWithLimitDataProvider() {
        return new Object[][]{
                {20, 5},
                {20, 17},
                {20, 20},
                {20, 25},
                {20, 89},
                {25, 5},
                {25, 17},
                {25, 20},
                {25, 25},
                {25, 89}
        };
    }

    @Test(dependsOnMethods = "testAddEmailDomainsToOrganization", dataProvider = "numericEdgeCasesOfOffsetAndOffsetWithLimitDataProvider")
    public void testGetPaginatedOrganizationsDiscoveryForNumericEdgeCasesOfOffsetAndOffsetWithLimit(int offset,
                                                                                                 int limit) {

        String queryUrl = buildQueryUrl(offset, limit);
        Response response = getResponseOfGetWithOAuth2(queryUrl, m2mToken);

         validateHttpStatusCode(response, HttpStatus.SC_OK);

        int totalResults = response.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM);
        int startIndex = response.jsonPath().getInt(START_INDEX_PATH_PARAM);
        int count = response.jsonPath().getInt(COUNT_PATH_PARAM);
        List<Map<String, String>> links = response.jsonPath().getList(LINKS_PATH_PARAM);

        // Validate based on the offset and limit
        Assert.assertEquals(totalResults, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(startIndex, offset + 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(count, 0, COUNT_MISMATCH_ERROR);

        // Validate links
        String nextLink = getLink(links, LINK_REL_NEXT);
        String previousLink = getLink(links, LINK_REL_PREVIOUS);
        Assert.assertNull(nextLink, "Next link should be null.");
        Assert.assertNotNull(previousLink, "Previous link should be present.");

        int expectedOffset = getExpectedOffsetInLinksForOffsetAndLimitEdgeCases(offset, limit);
        validateOrganizationDiscoveryOffsetIsInLinks(previousLink, expectedOffset);

    }

    private int getExpectedOffsetInLinksForOffsetAndLimitEdgeCases(int offset, int limit) {

        if (offset == NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS) {
            return Math.max(0, offset - limit);
        } else if (offset > NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS) {
            int left = offset - limit;
            while (left >= NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS) left -= limit;

            return Math.max(0, left);
        } else {
            return 0;
        }
    }

    @Test(dependsOnMethods = "testAddEmailDomainsToOrganization")
    public void testGetPaginatedOrganizationsDiscoveryForNonNumericEdgeCasesOfOffsetAndOffsetWithLimit() {

        // Case 1: When offset param is present (limit = 0)
        String queryUrl1 = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                OFFSET_QUERY_PARAM + EQUAL + AMPERSAND + LIMIT_QUERY_PARAM + EQUAL + ZERO;
        Response response1 = getResponseOfGetWithOAuth2(queryUrl1, m2mToken);

        validateHttpStatusCode(response1, HttpStatus.SC_OK);

        Assert.assertEquals(response1.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(response1.jsonPath().getInt(START_INDEX_PATH_PARAM), 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(response1.jsonPath().getInt(COUNT_PATH_PARAM), 0, COUNT_MISMATCH_ERROR);

        List<Map<String, String>> links1 = response1.jsonPath().getList(LINKS_PATH_PARAM);
        String nextLink1 = getLink(links1, LINK_REL_NEXT);
        Assert.assertNotNull(nextLink1, "Next link should be present.");

        // Case 2: When offset param is present (limit = 5)
        String queryUrl2 = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                OFFSET_QUERY_PARAM + EQUAL + AMPERSAND + LIMIT_QUERY_PARAM + EQUAL + 5;
        Response response2 = getResponseOfGetWithOAuth2(queryUrl2, m2mToken);

        validateHttpStatusCode(response2, HttpStatus.SC_OK);

        Assert.assertEquals(response2.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(response2.jsonPath().getInt(START_INDEX_PATH_PARAM), 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(response2.jsonPath().getInt(COUNT_PATH_PARAM), 5, COUNT_MISMATCH_ERROR);

        List<Map<String, String>> links2 = response2.jsonPath().getList(LINKS_PATH_PARAM);
        String nextLink2 = getLink(links2, LINK_REL_NEXT);
        Assert.assertNotNull(nextLink2, "Next link should be present.");

        // Case 3: When offset param is present (limit = NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS)
        String queryUrl3 = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                OFFSET_QUERY_PARAM + EQUAL + AMPERSAND + LIMIT_QUERY_PARAM + EQUAL + NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS;
        Response response3 = getResponseOfGetWithOAuth2(queryUrl3, m2mToken);

        validateHttpStatusCode(response3, HttpStatus.SC_OK);

        Assert.assertEquals(response3.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(response3.jsonPath().getInt(START_INDEX_PATH_PARAM), 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(response3.jsonPath().getInt(COUNT_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, COUNT_MISMATCH_ERROR);

        List<Map<String, String>> links3 = response3.jsonPath().getList(LINKS_PATH_PARAM);
        String nextLink3 = getLink(links3, LINK_REL_NEXT);
        Assert.assertNull(nextLink3, "Next link should be present.");

        // Case 4: When offset param is present (limit > NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS)
        String queryUrl4 = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                OFFSET_QUERY_PARAM + EQUAL + AMPERSAND + LIMIT_QUERY_PARAM + EQUAL + 25;
        Response response4 = getResponseOfGetWithOAuth2(queryUrl4, m2mToken);

        validateHttpStatusCode(response4, HttpStatus.SC_OK);

        Assert.assertEquals(response4.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(response4.jsonPath().getInt(START_INDEX_PATH_PARAM), 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(response4.jsonPath().getInt(COUNT_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, COUNT_MISMATCH_ERROR);

        List<Map<String, String>> links4 = response4.jsonPath().getList(LINKS_PATH_PARAM);
        String nextLink4 = getLink(links4, LINK_REL_NEXT);
        Assert.assertNull(nextLink4, "Next link should be present.");

        // Case 5: When offset param is not present (limit = 0)
        String queryUrl5 = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                LIMIT_QUERY_PARAM + EQUAL + ZERO;
        Response response5 = getResponseOfGetWithOAuth2(queryUrl5, m2mToken);

        validateHttpStatusCode(response5, HttpStatus.SC_OK);

        Assert.assertEquals(response5.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(response5.jsonPath().getInt(START_INDEX_PATH_PARAM), 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(response5.jsonPath().getInt(COUNT_PATH_PARAM), 0, COUNT_MISMATCH_ERROR);

        List<Map<String, String>> links5 = response5.jsonPath().getList(LINKS_PATH_PARAM);
        String nextLink5 = getLink(links5, LINK_REL_NEXT);
        Assert.assertNotNull(nextLink5, "Links should not be null.");

        // Case 6: When offset param is not present (limit = 5)
        String queryUrl6 = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                LIMIT_QUERY_PARAM + EQUAL + 5;
        Response response6 = getResponseOfGetWithOAuth2(queryUrl6, m2mToken);

        validateHttpStatusCode(response6, HttpStatus.SC_OK);

        Assert.assertEquals(response6.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(response6.jsonPath().getInt(START_INDEX_PATH_PARAM), 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(response6.jsonPath().getInt(COUNT_PATH_PARAM), 5, COUNT_MISMATCH_ERROR);

        List<Map<String, String>> links6 = response6.jsonPath().getList(LINKS_PATH_PARAM);
        String nextLink6 = getLink(links6, LINK_REL_NEXT);
        Assert.assertNotNull(nextLink6, "Links should not be null.");

        // Case 7: When offset param is not present (limit = NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS)
        String queryUrl7 = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                LIMIT_QUERY_PARAM + EQUAL + NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS;
        Response response7 = getResponseOfGetWithOAuth2(queryUrl7, m2mToken);

        validateHttpStatusCode(response7, HttpStatus.SC_OK);

        Assert.assertEquals(response7.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(response7.jsonPath().getInt(START_INDEX_PATH_PARAM), 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(response7.jsonPath().getInt(COUNT_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, COUNT_MISMATCH_ERROR);

        List<Map<String, String>> links7 = response7.jsonPath().getList(LINKS_PATH_PARAM);
        Assert.assertTrue(links7.isEmpty(), "Links should be empty.");

        // Case 8: When offset param is not present (limit > NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS)
        String queryUrl8 = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                LIMIT_QUERY_PARAM + EQUAL + 25;
        Response response8 = getResponseOfGetWithOAuth2(queryUrl8, m2mToken);

        validateHttpStatusCode(response8, HttpStatus.SC_OK);

        Assert.assertEquals(response8.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(response8.jsonPath().getInt(START_INDEX_PATH_PARAM), 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(response8.jsonPath().getInt(COUNT_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, COUNT_MISMATCH_ERROR);

        List<Map<String, String>> links8 = response8.jsonPath().getList(LINKS_PATH_PARAM);
        Assert.assertTrue(links8.isEmpty(), "Links should be empty.");

        // Case 9: Offset= and limit=
        String queryUrl9 = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                OFFSET_QUERY_PARAM + EQUAL + AMPERSAND + LIMIT_QUERY_PARAM + EQUAL;
        Response response9 = getResponseOfGetWithOAuth2(queryUrl9, m2mToken);

        validateHttpStatusCode(response9, HttpStatus.SC_OK);

        Assert.assertEquals(response9.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(response9.jsonPath().getInt(START_INDEX_PATH_PARAM), 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(response9.jsonPath().getInt(COUNT_PATH_PARAM), Math.min(DEFAULT_ORG_LIMIT,
                        NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS),
                COUNT_MISMATCH_ERROR);

        List<Map<String, String>> links9 = response9.jsonPath().getList(LINKS_PATH_PARAM);
        if (NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS > DEFAULT_ORG_LIMIT) {
            Assert.assertNotNull(getLink(links9, LINK_REL_NEXT),
                    "'next' link should be present when organizations exceed default limit.");
        } else {
            Assert.assertNull(getLink(links9, LINK_REL_NEXT),
                    "'next' link should not be present when organizations are within default limit.");
        }

        // Case 10: Offset is not present and limit is not present
        String queryUrl10 = ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                FILTER_QUERY_PARAM + EQUAL;
        Response response10 = getResponseOfGetWithOAuth2(queryUrl10, m2mToken);

        validateHttpStatusCode(response10, HttpStatus.SC_OK);

        Assert.assertEquals(response10.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM), NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, TOTAL_RESULT_MISMATCH_ERROR);
        Assert.assertEquals(response10.jsonPath().getInt(START_INDEX_PATH_PARAM), 1, START_INDEX_MISMATCH_ERROR);
        Assert.assertEquals(response10.jsonPath().getInt(COUNT_PATH_PARAM), Math.min(DEFAULT_ORG_LIMIT,
                NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS), COUNT_MISMATCH_ERROR);

        List<Map<String, String>> links10 = response10.jsonPath().getList(LINKS_PATH_PARAM);
        if (NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS > DEFAULT_ORG_LIMIT) {
            Assert.assertNotNull(getLink(links10, LINK_REL_NEXT),
                    "'next' link should be present when organizations exceed default limit.");
        } else {
            Assert.assertNull(getLink(links10, LINK_REL_NEXT),
                    "'next' link should not be present when organizations are within default limit.");
        }

    }


    private void validateOrganizationsOnPage(Response response, int pageNum, int totalOrganizations, int limit) {

        // Validate the organization count.
        int expectedOrgCount = Math.min(limit, totalOrganizations - (pageNum - 1) * limit);
        List<Map<String, String>> actualOrganizations = response.jsonPath().getList(ORGANIZATIONS_PATH_PARAM);
        int actualOrgCount = (actualOrganizations != null) ? actualOrganizations.size() : 0;

        Assert.assertEquals(actualOrgCount, expectedOrgCount,
                "Organization count mismatch on page " + pageNum);

        // Validate the organization names.
        List<String> actualOrgNames = response.jsonPath().getList(ORGANIZATIONS_PATH_PARAM + ".name");

        for (int i = 0; i < expectedOrgCount; i++) {
            int orgIndex = totalOrganizations - ((pageNum - 1) * limit) - i - 1;

            String expectedOrgName = String.format(ORGANIZATION_NAME_FORMAT, orgIndex);
            Assert.assertEquals(actualOrgNames.get(i), expectedOrgName,
                    "Organization name mismatch on page " + pageNum + " at index " + i);
        }
    }

    private void validateOrganizationsForDefaultLimit(Response response, int totalOrganizations) {

        // Validate the organization count.
        int expectedOrgCount = Math.min(DEFAULT_ORG_LIMIT, totalOrganizations);
        List<Map<String, String>> actualOrganizations = response.jsonPath().getList(ORGANIZATIONS_PATH_PARAM);
        int actualOrgCount = (actualOrganizations != null) ? actualOrganizations.size() : 0;

        Assert.assertEquals(actualOrgCount, expectedOrgCount,
                "Organization count mismatch with default limit.");

        // Validate the organization names.
        List<String> actualOrgNames = response.jsonPath().getList(ORGANIZATIONS_PATH_PARAM + ".name");

        for (int i = 0; i < expectedOrgCount; i++) {
            int orgIndex = totalOrganizations - i - 1;

            String expectedOrgName = String.format(ORGANIZATION_NAME_FORMAT, orgIndex);
            Assert.assertEquals(actualOrgNames.get(i), expectedOrgName,
                    "Organization name mismatch with default limit at index " + i);
        }

        // Validate pagination links.
        List<Map<String, String>> links = response.jsonPath().getList(LINKS_PATH_PARAM);

        if (totalOrganizations > DEFAULT_ORG_LIMIT) {
            String after = getLink(links, LINK_REL_NEXT);
            Assert.assertNotNull(after,
                    "'after' link should be present when organizations exceed default limit.");
        } else {
            Assert.assertNull(getLink(links, LINK_REL_NEXT),
                    "'after' link should not be present when organizations are within default limit.");
        }
    }

    private void validateOrgNamesForOrganizationDiscoveryGet(List<String> accumulatedOrganizationNames) {

        // Ensure both sets contain the same organization names
        for (int i = 0; i < NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS; i++) {
            assertEquals(accumulatedOrganizationNames.get(i), organizations.get(i).get(ORGANIZATION_NAME),
                    "Organization names do not match.");
        }
    }

    private void validatePaginationLinksForOrganizationDiscovery(boolean isFirstPage, boolean isLastPage,
                                                                 String nextLink,
                                                                 String previousLink) {

        if (isFirstPage) {
            Assert.assertNotNull(nextLink, "Next link should be available on the first page.");
            Assert.assertNull(previousLink, "Previous link should be null on the first page.");
        } else if (isLastPage) {
            Assert.assertNull(nextLink, "Next link should be null on the last page.");
            Assert.assertNotNull(previousLink, "Previous link should be available on the last page.");
        } else {
            Assert.assertNotNull(nextLink, "Next link should be available on middle pages.");
            Assert.assertNotNull(previousLink, "Previous link should be available on middle pages.");
        }
    }

    private void validateOrganizationDiscoveryLimitEdgeCaseLinks(List<Map<String, String>> links, int limit, int offset) {

        if (limit == 0) {
            if (offset == 0) {
                Assert.assertNotNull(getLink(links, LINK_REL_NEXT),
                        "'next' link should be present when the limit and offset is 0.");
            } else {
                Assert.assertNotNull(getLink(links, LINK_REL_NEXT),
                        "'next' link should be present when the limit is 0 but the offset is non-zero.");
                Assert.assertNotNull(getLink(links, LINK_REL_PREVIOUS),
                        "'previous' link should be present when the limit is 0 but the offset is non-zero.");
            }
        } else {
            if (offset == 0) {
                Assert.assertTrue(links.isEmpty(),
                        "'links' should be empty for non-zero edge case limits.");
            } else {
                Assert.assertNotNull(getLink(links, LINK_REL_PREVIOUS),
                        "'previous' link should be present for non-zero edge case limits with non-zero offset.");
            }
        }

    }

    private void validateOrganizationDiscoveryLimitEdgeCaseOrganizations(List<Map<String, String>> organizations,
                                                                    int limit, int offset) {

        if (limit == 0) {
            Assert.assertNull(organizations,
                    "No organizations should be returned when limit is 0.");
        } else {
            Assert.assertEquals(organizations.size(), Math.min(limit,
                            (NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS - offset)),
                    "Number of organizations in the response does not match the expected count.");

            // Validation to ensure correct organization data is returned - Only for non-zero offset.
            /* Since, the organizations are ordered in ORG_ID, we cannot validate the OrganizationName results
            for non-zero offsets.*/
            if (offset == 0) {
                validateOrgNamesOfOrganizationDiscoveryGet(organizations);
            }
        }
    }

    private void validateOrgNamesOfOrganizationDiscoveryGet(List<Map<String, String>> organizations) {

        List<String> accumulatedOrganizationNames = new ArrayList<>();
        for (Map<String, String> org : organizations) {
            String orgName = org.get(ORGANIZATION_NAME_ATTRIBUTE);
            accumulatedOrganizationNames.add(orgName);
        }
        accumulatedOrganizationNames.sort(Comparator.comparingInt(s -> Integer.parseInt(s.split("-")[1])));

        validateOrgNamesForOrganizationDiscoveryGet(accumulatedOrganizationNames);
    }

    private void validateResponseForOrganizationDiscoveryLDefaultCases(Response response) {

        int actualCount = response.jsonPath().getInt(COUNT_PATH_PARAM);
        int totalResults = response.jsonPath().getInt(TOTAL_RESULTS_PATH_PARAM);
        int startIndex = response.jsonPath().getInt(START_INDEX_PATH_PARAM);
        List<Map<String, String>> links = response.jsonPath().getList(LINKS_PATH_PARAM);
        List<Map<String, String>> returnedOrganizations = response.jsonPath().getList("organizations");

        Assert.assertEquals(actualCount, DEFAULT_ORG_LIMIT, "Unexpected count of organizations returned.");
        Assert.assertEquals(totalResults, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS, "Unexpected total results.");
        Assert.assertEquals(startIndex, 1, "Start index should be 1.");

        if (totalResults > DEFAULT_ORG_LIMIT) {
            Assert.assertNotNull(getLink(links, LINK_REL_NEXT), "'next' link should be present.");
        } else {
            Assert.assertTrue(links.isEmpty(), "'links' should be empty for non-zero edge case limits.");
        }

        Assert.assertEquals(returnedOrganizations.size(),
                Math.min(DEFAULT_ORG_LIMIT, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS),
                "Number of organizations in the response does not match the expected count.");
    }

    private String getLink(List<Map<String, String>> links, String rel) {

        for (Map<String, String> link : links) {
            if (rel.equals(link.get(REL))) {
                String href = link.get(HREF);
                if (href.contains(AFTER_QUERY_PARAM + EQUAL)) {
                    return href.substring(href.indexOf(AFTER_QUERY_PARAM + EQUAL) + AFTER_QUERY_PARAM.length() + 1);
                } else if (href.contains(BEFORE_QUERY_PARAM + EQUAL)) {
                    return href.substring(href.indexOf(BEFORE_QUERY_PARAM + EQUAL) + BEFORE_QUERY_PARAM.length() + 1);
                } else {
                    return href;
                }
            }
        }
        return null;
    }

    private List<Map<String, String>> getPaginationLinksForOrganizationDiscovery(
            String queryUrl, int offset, int limit, List<String> accumulatedOrganizationNames, boolean isForward) {

        Response response = getResponseOfGetWithOAuth2(queryUrl, m2mToken);
        validateHttpStatusCode(response, HttpStatus.SC_OK);

        List<Map<String, String>> returnedOrganizations = response.jsonPath().getList(ORGANIZATIONS_PATH_PARAM);

        int expectedSize = isForward
                ? Math.min(limit, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS - offset)
                : Math.min(limit, NUM_OF_ORGANIZATIONS_FOR_PAGINATION_TESTS - offset + limit);

        Assert.assertEquals(returnedOrganizations.size(), expectedSize);

        addReturnedOrganizationsToList(returnedOrganizations, accumulatedOrganizationNames);

        return response.jsonPath().getList(LINKS_PATH_PARAM);
    }

    private void addEmailDomainsToOrganization(String organizationId, String... domains) {

        String addDomainsPayload = String.format(
                "{" +
                        "\"attributes\": [{" +
                        "\"type\": \"emailDomain\"," +
                        "\"values\": [\"%s\"]" +
                        "}]," +
                        "\"organizationId\": \"%s\"" +
                        "}",
                String.join("\",\"", domains),
                organizationId);

        Response response =
                getResponseOfPostWithOAuth2(ORGANIZATION_MANAGEMENT_API_BASE_PATH +
                        ORGANIZATION_DISCOVERY_API_PATH, addDomainsPayload, m2mToken);
        validateHttpStatusCode(response, HttpStatus.SC_CREATED);
    }

    private void addReturnedOrganizationsToList(List<Map<String, String>> returnedOrganizations,
                                                List<String> accumulatedOrganizationNames) {

        for (Map<String, String> org : returnedOrganizations) {
            accumulatedOrganizationNames.add(org.get(ORGANIZATION_NAME_ATTRIBUTE));
        }
    }

    private List<Map<String, String>> createOrganizations(int numberOfOrganizations) throws JSONException {

        List<Map<String, String>> newOrganizations = new ArrayList<>();

        for (int i = 0; i < numberOfOrganizations; i++) {
            JSONObject body = new JSONObject()
                    .put(ORGANIZATION_NAME, String.format(ORGANIZATION_NAME_FORMAT, i));

            Response response =
                    getResponseOfPostWithOAuth2(ORGANIZATION_MANAGEMENT_API_BASE_PATH, body.toString(), m2mToken);

            if (response.getStatusCode() == HttpStatus.SC_CREATED) {
                // Extract the organization ID (UUID) from the response body.
                JSONObject responseBody = new JSONObject(response.getBody().asString());
                String organizationId = responseBody.getString("id");

                // Store the created organization details.
                Map<String, String> org = new HashMap<>();
                org.put(ORGANIZATION_NAME, String.format(ORGANIZATION_NAME_FORMAT, i));
                org.put(ORGANIZATION_ID, organizationId);
                newOrganizations.add(org);
            } else {
                throw new RuntimeException("Failed to create organization " + i);
            }
        }

        return newOrganizations;
    }

    private String buildQueryUrl(int offset, int limit) {

        return ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH + QUESTION_MARK +
                OFFSET_QUERY_PARAM + EQUAL + offset + AMPERSAND + LIMIT_QUERY_PARAM + EQUAL + limit;
    }

    private String buildNewQueryUrl(String link, String queryUrl) {

        return link != null ?
                link.substring(link.lastIndexOf(
                        ORGANIZATION_MANAGEMENT_API_BASE_PATH + ORGANIZATION_DISCOVERY_API_PATH)) : queryUrl;
    }


    //OFFSET= LIMIT=10
    //OFFSET= (NO LIMIT)
    //LIMIT=OFFSET=
    //LIMIT= OFFSET=n (n<total, n=total and n>total)
}
