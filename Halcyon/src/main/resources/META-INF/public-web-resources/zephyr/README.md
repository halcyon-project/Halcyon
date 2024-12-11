# Annotation Tools (Zephyr)

## Overview

This codebase provides a set of annotation tools that can be used within a Three.js environment. The tools include features like rectangle drawing, ruler measurement, hollow brush, and more. These annotations are controlled via a toolbar which is dynamically initialized with configuration data.

## Files Structure

- `testing.html` - Example HTML file to test the tools.
- `toolbar.js` - JavaScript module responsible for initializing and managing the toolbar.
- `ruler.js` - Ruler tool implementation.
- `hollow-brush.js` - Hollow brush tool implementation.
- `sparql.js` - Handles SPARQL query execution.
- `annotations/` - Directory containing individual annotation tools.
- `helpers/` - Directory containing helper scripts

## Getting Started

### 1. Setting up the Environment

Ensure you have a basic understanding of HTML, JavaScript, and Three.js. The codebase assumes this knowledge for configuration and usage.

### 2. Example JSON Config

In practice, your configuration data would be fetched from a server. However, for testing purposes, an example JSON config might look like this:

```json
{
  "tools": [
    {
      "name": "Rectangle",
      "id": "rectangle-tool",
      "icon": "rect-icon.png",
      "enabled": true,
      "action": "drawRectangle()"
    },
    {
      "name": "Ruler",
      "id": "ruler-tool",
      "icon": "ruler-icon.png",
      "enabled": false,
      "action": "measureDistance()"
    }
  ]
}
```

### 3. Initialize Toolbar with Config

The toolbar is initialized in `toolbar.js`. Here's how you can initialize it:

```javascript
// Fetch the config from the server or use a local example
fetch('/path/to/config.json')
  .then(response => response.json())
  .then(config => {
    const toolbar = new Toolbar();
    toolbar.init(config.tools);
  })
  .catch(error => console.error('Failed to load config:', error));
```

### 4. Adding Items to the Toolbar

To add a new item (tool) to the toolbar, follow these steps:

1. **Define the Tool in Config**

   Add an entry for your tool in the JSON configuration file.

   ```json
   {
     "name": "New Tool",
     "id": "new-tool-id",
     "icon": "new-icon.png",
     "enabled": true,
     "action": "executeNewToolAction()"
   }
   ```

2. **Implement Tool Logic**

   Create a new JavaScript file for your tool, implementing the required functionality.

   ```javascript
   function executeNewToolAction() {
     // Your tool logic here
     console.log('Executing new tool action');
   }
   ```

3. **Integrate with Toolbar**

   Ensure that the toolbar is aware of this new tool and can handle its actions properly.

4. **Update HTML and CSS**

   If your tool requires additional UI elements, update the HTML and CSS accordingly.

## Usage

### 1. Testing in testing.html

- Open `demo.html` in a web browser.
- The toolbar should load with the tools defined in the config.
- Interact with the toolbar to use different annotation tools.

### 2. Customizing Tools

- Modify the JSON configuration file to enable/disable tools as needed.
- Update tool logic files to customize behavior or add new features.

## Maintenance

### 1. Code Updates

- When updating a tool, ensure that changes do not break existing functionality.
- Test thoroughly after making updates.

### 2. Configuration Management

- Manage the JSON config file carefully, especially if it is fetched from a server.
- Ensure compatibility between the toolbar and tool configurations.

### 3. Documentation

- Keep this README updated with any changes to the codebase or configuration files.

## Troubleshooting

- **Tool not working**: Check the console for errors and verify that the tool's action function is defined correctly.
- **Config issues**: Ensure the JSON config file is valid and accessible.
- **UI problems**: Verify HTML and CSS are correctly set up and match the toolbar logic.

For further assistance, refer to the comments in the codebase or contact the development team.

<br>
