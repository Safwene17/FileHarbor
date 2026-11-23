
const express = require('express');
const path = require('path');

const app = express();

const PORT = process.env.PORT || 8080;
const DIST_FOLDER = path.join(__dirname, 'dist', 'frontend', 'browser');

// Serve Angular static files
app.use(express.static(DIST_FOLDER));

// Angular client-side routing fallback
app.use((req, res, next) => {
    if (req.method !== 'GET') {
        return next();
    }

    res.sendFile(path.join(DIST_FOLDER, 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`FileHarbor frontend running on port ${PORT}`);
});
