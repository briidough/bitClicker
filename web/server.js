const express = require('express');
const path = require('path');

const BASE = (process.env.BASE_PATH || '').replace(/\/$/, '');
const PORT = 4003;

const app = express();

app.use(BASE || '/', express.static(path.join(__dirname, 'public')));

app.get([BASE || '/', `${BASE}/`], (_req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`bitClicker Web on :${PORT}  BASE_PATH="${BASE}"`);
});
