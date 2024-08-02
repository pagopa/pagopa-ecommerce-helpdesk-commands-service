module.exports = function (req, res, next) {
  const db = req.app.db
  console.log(`${new Date().toISOString()} - Received request: ${req.method} ${req.path}
  Headers: 
  ${JSON.stringify(req.headers)}
  Body:  
  ${JSON.stringify(req.body)}
  `);
  if (req.method === 'POST') {
    // Converts POST to GET
    req.method = 'GET'
  }

  const requestPath = req.path.toString();
  const requestBody = req.body;

  if (requestPath.includes("refundError")) {
    //handle transaction already refunfed
    res
    .status(400)
    .jsonp(db.get("refundError"));
  } else if (requestPath.includes("transactionNotFound")) {
    //handle not found transaction
    res
    .status(404)
    .jsonp(db.get("transactionNotFound"));
  }
  else {
      // Continue to default JSON Server router
      next();
  }
}
