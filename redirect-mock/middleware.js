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

  const requestBody = req.body;
  console.log(requestBody.idTransaction)
  if (requestBody.idTransaction === '3fa85f6457174562b3fc2c963f66afa7') {
    //handle transaction already refunfed
    res
    .status(400)
    .jsonp(db.get("refundError"));
  } else if (requestBody.idTransaction !== 'ecf06892c9e04ae39626dfdfda631b94') {
    //handle not found transaction
    res.sendStatus(404);
  }
  else {
      // Continue to default JSON Server router
      next();
  }
}
