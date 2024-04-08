const express = require('express');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const cors = require('cors');

const app = express();
const port = 3000;

// Use cors middleware to enable CORS
// to allow the client-side application to communicate with it
app.use(cors());

const upload = multer(); // Configuring multer for memory storage

// Endpoint to handle file uploads
app.post('/upload', upload.array('files'), (req, res) => {
  const files = req.files;
  const paths = req.body.paths; // 'paths' is sent as an array of paths corresponding to 'files'

  if (files && paths && files.length === paths.length) {
    files.forEach((file, index) => {
      // The path where you want to save the file, using the path provided by the client
      const fullPath = path.join(__dirname, 'uploads', paths[index]);
      // console.log(`Processing file: ${fullPath}`);
      console.log(`Processing file: ${files[index].originalname}`);

      // Ensure the directory structure exists
      const directory = path.dirname(fullPath);
      if (!fs.existsSync(directory)) {
        fs.mkdirSync(directory, { recursive: true });
      }

      // Write the file
      fs.writeFileSync(fullPath, file.buffer);
    });

    res.send('Files uploaded successfully');
  } else {
    if (files.length === 1) {
      files.forEach((file, index) => {
        const fullPath = path.join(__dirname, 'uploads', file.originalname);
        fs.writeFileSync(fullPath, file.buffer);
      });
    } else {
      // 422 to reflect that the server understands the content type of the request entity,
      // but it was unable to process the contained instructions:
      let message = 'Mismatch between files and paths or missing data';
      res.status(422).send(message);
      console.log(message); // It has to be "log"
    }
  }
});

app.listen(port, () => {
  console.log(`Server running on port ${port}`);
});
