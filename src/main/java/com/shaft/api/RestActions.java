package com.shaft.api;

import static io.restassured.RestAssured.given;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.testng.Assert;

import com.shaft.io.ReportManager;
import com.shaft.support.JavaActions;
import com.shaft.validation.Assertions;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.http.Header;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.path.json.JsonPath;
import io.restassured.path.json.exception.JsonPathException;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class RestActions {
    private static final String ARGUMENTSEPARATOR = "?";

    private String headerAuthorization;
    private Map<String, String> sessionCookies;
    private Map<String, String> sessionHeaders;
    private String serviceURI;

    public RestActions(String serviceURI) {
	headerAuthorization = "";
	sessionCookies = new HashMap<>();
	sessionHeaders = new HashMap<>();
	this.serviceURI = serviceURI;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// [private] Reporting Actions
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void passAction(String actionName, String testData, Response response) {
	String message = "Successfully performed action [" + actionName + "].";
	if (testData != null) {
	    message = message + " With the following test data [" + testData + "].";
	}

	Boolean discreetLogging = ReportManager.isDiscreteLogging();
	if (actionName.toLowerCase().contains("getresponse") && actionName.toLowerCase().contains("value")) {
	    if (discreetLogging) {
		ReportManager.logDiscrete(message);
	    } else {
		ReportManager.log(message);
		if (response != null) {
		    ReportManager.attachAsStep("API Response", "REST Body", response.getBody().asString());
		}
	    }
	}
    }

    private void passAction(String actionName, String testData) {
	passAction(actionName, testData, null);
    }

    private void failAction(String actionName, String testData, Response response) {
	String message = "Failed to perform action [" + actionName + "].";
	if (testData != null) {
	    message = message + " With the following test data [" + testData + "].";
	}
	ReportManager.log(message);
	if (response != null) {
	    ReportManager.attachAsStep("API Response", "REST Body", response.getBody().asString());
	}
	Assert.fail(message);
    }

    private void failAction(String actionName, String testData) {
	failAction(actionName, testData, null);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// [private] Preparation and Support Actions
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private String prepareRequestURL(String urlArguments, String serviceName) {
	if (urlArguments != null && !urlArguments.equals("")) {
	    return serviceURI + serviceName + ARGUMENTSEPARATOR + urlArguments;
	} else {
	    return serviceURI + serviceName;
	}
    }

    private void prepareRequestHeaderAuthorization(String[] credentials) {
	if (headerAuthorization.equals("") && credentials.length == 2) {
	    headerAuthorization = "Basic " + JavaActions.convertBase64(credentials[0] + ":" + credentials[1]);

	    sessionHeaders.put("Authorization", headerAuthorization);
	}
    }

    private Response sendRequest(String requestType, String request, RequestSpecification specs,
	    ContentType contentType) {
	if (sessionCookies.size() == 0 && sessionHeaders.size() > 0) {
	    switch (requestType.toLowerCase()) {
	    case "post":
		return given().headers(sessionHeaders).contentType(contentType).spec(specs).when().post(request)
			.andReturn();
	    case "patch":
		return given().headers(sessionHeaders).contentType(contentType).spec(specs).when().patch(request)
			.andReturn();
	    case "get":
		return given().headers(sessionHeaders).contentType(contentType).spec(specs).when().get(request)
			.andReturn();
	    case "delete":
		return given().headers(sessionHeaders).contentType(contentType).spec(specs).when().delete(request)
			.andReturn();
	    default:
		break;
	    }
	} else if (sessionCookies.size() == 0 && sessionHeaders.size() == 0) {
	    switch (requestType.toLowerCase()) {
	    case "post":
		return given().contentType(contentType).spec(specs).when().post(request).andReturn();
	    case "patch":
		return given().contentType(contentType).spec(specs).when().patch(request).andReturn();
	    case "get":
		return given().contentType(contentType).spec(specs).when().get(request).andReturn();
	    case "delete":
		return given().contentType(contentType).spec(specs).when().delete(request).andReturn();
	    default:
		break;
	    }
	} else {
	    switch (requestType.toLowerCase()) {
	    case "post":
		return given().headers(sessionHeaders).cookies(sessionCookies).contentType(contentType).spec(specs)
			.when().post(request).andReturn();
	    case "patch":
		return given().headers(sessionHeaders).cookies(sessionCookies).contentType(contentType).spec(specs)
			.when().patch(request).andReturn();
	    case "get":
		return given().headers(sessionHeaders).cookies(sessionCookies).contentType(contentType).spec(specs)
			.when().get(request).andReturn();
	    case "delete":
		return given().headers(sessionHeaders).cookies(sessionCookies).contentType(contentType).spec(specs)
			.when().delete(request).andReturn();
	    default:
		break;
	    }
	}
	return null;
    }

    private void extractCookiesFromResponse(Response response) {
	if (response.getDetailedCookies().size() > 0) {
	    if (sessionCookies == null) {
		sessionCookies = response.getCookies();
	    } else {
		for (Cookie cookie : response.getDetailedCookies()) {
		    sessionCookies.put(cookie.getName(), cookie.getValue());

		    if (cookie.getName().equals("XSRF-TOKEN")) {
			sessionHeaders.put("X-XSRF-TOKEN", cookie.getValue());
		    }
		}
	    }
	}
    }

    private void extractHeadersFromResponse(Response response) {
	if (response.getHeaders().size() > 0) {
	    for (Header header : response.getHeaders()) {
		if (header.getName().equals("X-XSRF-TOKEN") || header.getName().equals("Set-Cookie")) {
		    sessionHeaders.put(header.getName(), header.getValue());
		}
	    }
	}

	try {
	    if (response.jsonPath().getString("type").equals("Bearer")) {
		headerAuthorization = "Bearer " + getResponseJSONValue(response, "token");
		sessionHeaders.put("Authorization", headerAuthorization);
		sessionHeaders.put("Content-Type", "application/json");
	    }
	} catch (JsonPathException | NullPointerException e) {
	    // do nothing if the "type" variable was not found
	    // or if response was not json

	    // JsonPathException | NullPointerException
	}
    }

    private void assertResponseStatusCode(String request, Response response, String targetStatusCode) {
	try {
	    Boolean discreetLoggingState = ReportManager.isDiscreteLogging();
	    ReportManager.setDiscreteLogging(true);
	    Assertions.assertEquals(targetStatusCode, String.valueOf(response.getStatusCode()), 1, true);
	    ReportManager.setDiscreteLogging(discreetLoggingState);
	    passAction("performRequest", request + ", Response Time: " + response.timeIn(TimeUnit.MILLISECONDS) + "ms",
		    response);
	} catch (AssertionError e) {
	    failAction("performRequest", request + ", Response Time: " + response.timeIn(TimeUnit.MILLISECONDS) + "ms",
		    response);
	}
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// [Public] Core REST Actions
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Attempts to perform POST/PATCH/GET/DELETE request to a REST API, then checks
     * the response status code, if it matches the target code the step is passed
     * and the response is returned. Otherwise the action fails and NULL is
     * returned.
     * 
     * @param requestType      POST/PATCH/GET/DELETE
     * @param targetStatusCode default success code is 200
     * @param serviceName      /servicePATH/serviceNAME
     * @param urlArguments     '&amp;' separated arguments without a preceding '?',
     *                         is nullable, Example:
     *                         "username=test&amp;password=test"
     * @param formParameters   a list of key/value pairs that will be sent as
     *                         parameters with this API call, is nullable, Example:
     *                         Arrays.asList(Arrays.asList("itemId", "123"),
     *                         Arrays.asList("contents", XMLcontents));
     * @param body             Specify an Object request content that will
     *                         automatically be serialized to JSON or XML and sent
     *                         with the request. If the object is a primitive or
     *                         Number the object will be converted to a String and
     *                         put in the request body. This works for the POST, PUT
     *                         and PATCH methods only. Trying to do this for the
     *                         other http methods will cause an exception to be
     *                         thrown, is nullable in case there is no body for that
     *                         request
     * @param contentType      Enumeration of common IANA content-types. This may be
     *                         used to specify a request or response content-type
     *                         more easily than specifying the full string each
     *                         time. Example: ContentType.ANY
     * @param credentials      an optional array of strings that holds the username,
     *                         password that will be used for the
     *                         headerAuthorization of this request
     * @return Response; returns the full response object for further manipulation
     */
    public Response performRequest(String requestType, String targetStatusCode, String serviceName, String urlArguments,
	    List<List<String>> formParameters, Object body, ContentType contentType, String... credentials) {

	RequestSpecBuilder builder = new RequestSpecBuilder();

	if (body != null && contentType != null && !body.toString().equals("")) {
	    try {
		switch (contentType) {
		case JSON:
		    builder.setBody(body, ObjectMapperType.GSON);
		    break;
		case XML:
		    builder.setBody(body, ObjectMapperType.JAXB);
		    break;
		default:
		    builder.setBody(body);
		    break;
		}
	    } catch (Exception e) {
		ReportManager.log(e);
		failAction("performRequest", "Issue with parsing body content");

	    }
	} else if (formParameters != null && !formParameters.isEmpty() && !formParameters.get(0).get(0).equals("")) {
	    formParameters.forEach(param -> {
		builder.addParam(param.get(0), param.get(1));
	    });
	}

	RequestSpecification specs = builder.build();

	String request = prepareRequestURL(urlArguments, serviceName);
	prepareRequestHeaderAuthorization(credentials);

	Response response = null;
	try {
	    if (requestType.equalsIgnoreCase("post") || requestType.equalsIgnoreCase("patch")
		    || requestType.equalsIgnoreCase("get") || requestType.equalsIgnoreCase("delete")) {
		response = sendRequest(requestType, request, specs, contentType);
	    } else {
		failAction("performRequest", request);
	    }

	    if (response != null) {
		extractCookiesFromResponse(response);
		extractHeadersFromResponse(response);

		assertResponseStatusCode(request, response, targetStatusCode);
	    }
	} catch (Exception e) {
	    ReportManager.log(e);
	    if (response != null) {
		failAction("performRequest",
			request + ", Response Time: " + response.timeIn(TimeUnit.MILLISECONDS) + "ms", response);
	    } else {
		failAction("performRequest", request);
	    }
	}
	return response;
    }

    /**
     * Extracts a string value from the response body by parsing the target jsonpath
     * 
     * @param response the full response object returned by 'performRequest()'
     *                 method
     * @param jsonPath the JSONPath expression that will be evaluated in order to
     *                 extract the desired value [without the trailing $.], please
     *                 refer to these urls for examples:
     *                 https://support.smartbear.com/alertsite/docs/monitors/api/endpoint/jsonpath.html
     *                 http://jsonpath.com/
     * @return a string value that contains the extracted object
     */
    public String getResponseJSONValue(Response response, String jsonPath) {
	String searchPool = response.jsonPath().getString(jsonPath);
	if (searchPool != null) {
	    passAction("getResponseJSONValue", jsonPath);
	    return searchPool;
	} else {
	    ReportManager.log("Couldn't find anything that matches with the desired jsonPath [" + jsonPath + "]");
	    failAction("getResponseJSONValue", jsonPath);
	    return "";
	}
    }

    public String getResponseJSONValue(Object response, String jsonPath) {
	@SuppressWarnings("unchecked")
	JSONObject obj = new JSONObject((java.util.HashMap<String, String>) response);

	String searchPool = JsonPath.from(obj.toString()).getString(jsonPath);
	if (searchPool != null) {
	    passAction("getResponseJSONValue", jsonPath);
	    return searchPool;
	} else {
	    ReportManager.log("Couldn't find anything that matches with the desired jsonPath [" + jsonPath + "]");
	    failAction("getResponseJSONValue", jsonPath);
	    return "";
	}
    }

    public List<Object> getResponseJSONValueAsList(Response response, String jsonPath) {
	List<Object> searchPool = response.jsonPath().getList(jsonPath);
	if (searchPool != null) {
	    passAction("getResponseJSONValueAsList", jsonPath);
	    return searchPool;
	} else {
	    ReportManager.log("Couldn't find anything that matches with the desired jsonPath [" + jsonPath + "]");
	    failAction("getResponseJSONValueAsList", jsonPath);
	    return Arrays.asList("");
	}
    }

    public String getResponseXMLValue(Response response, String xmlPath) {
	String searchPool = response.xmlPath().getString(xmlPath);
	if (searchPool != null) {
	    passAction("getResponseXMLValue", xmlPath);
	    return searchPool;
	} else {
	    ReportManager.log("Couldn't find anything that matches with the desired xmlPath [" + xmlPath + "]");
	    failAction("getResponseXMLValue", xmlPath);
	    return "";
	}
    }

    public String getResponseXMLValue(Object response, String xmlPath) {
	@SuppressWarnings("unchecked")
	JSONObject obj = new JSONObject((java.util.HashMap<String, String>) response);

	String searchPool = XmlPath.from(obj.toString()).getString(xmlPath);
	if (searchPool != null) {
	    passAction("getResponseXMLValue", xmlPath);
	    return searchPool;
	} else {
	    ReportManager.log("Couldn't find anything that matches with the desired xmlPath [" + xmlPath + "]");
	    failAction("getResponseXMLValue", xmlPath);
	    return "";
	}
    }

    public List<Object> getResponseXMLValueAsList(Response response, String xmlPath) {
	List<Object> searchPool = response.xmlPath().getList(xmlPath);
	if (searchPool != null) {
	    passAction("getResponseXMLValueAsList", xmlPath);
	    return searchPool;
	} else {
	    ReportManager.log("Couldn't find anything that matches with the desired xmlPath [" + xmlPath + "]");
	    failAction("getResponseXMLValueAsList", xmlPath);
	    return Arrays.asList("");
	}
    }

    public int getResponseStatusCode(Response response) {
	return response.getStatusCode();
    }

}
