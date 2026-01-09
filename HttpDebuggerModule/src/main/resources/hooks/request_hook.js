/**
 * Request Hook Demo
 * 
 * Available objects:
 * - request: The request object (method, url, headers, params, body)
 * - logger: Logger instance
 */

function preProcess(context) {
    logger.info("Executing Pre-request Hook...");

    // Add a timestamp header
    var req = context.getRequest();
    req.getHeaders().put("X-Request-Time", new Date().toISOString());

    // Log the URL
    logger.info("Request URL: " + req.getUrl());

    // Example: Add an auth token if not present
    if (!req.getHeaders().containsKey("Authorization")) {
        req.getHeaders().put("Authorization", "Bearer demo-token-123");
    }
}

function postProcess(context) {
    logger.info("Executing Post-request Hook...");

    var resp = context.getResponse();
    if (resp) {
        logger.info("Response Status: " + resp.getStatusCode());
    }
}
