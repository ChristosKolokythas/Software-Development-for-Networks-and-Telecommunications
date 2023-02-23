const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const fs =require('fs');

const app = express();

app.use(bodyParser.json());
app.use(cors()); // enable CORS

app.put('/api/devices', (req, res) => {
  const newData = req.body;
  fs.writeFileSync('data.json', JSON.stringify(newData));
  res.status(200).send("PUT Request Called");

});
app.get('/api/devices',(req,res)=>{
  const data = JSON.parse(fs.readFileSync('data.json', 'utf8'));
  res.json(data);
});
app.listen(3000, () => {
  console.log('Server running on port 3000');
});